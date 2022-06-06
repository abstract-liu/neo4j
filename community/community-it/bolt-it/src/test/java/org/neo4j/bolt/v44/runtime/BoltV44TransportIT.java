/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.bolt.v44.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.neo4j.bolt.testing.assertions.BoltConnectionAssertions.assertThat;
import static org.neo4j.bolt.testing.messages.BoltV44Wire.begin;
import static org.neo4j.bolt.testing.messages.BoltV44Wire.commit;
import static org.neo4j.bolt.testing.messages.BoltV44Wire.hello;
import static org.neo4j.bolt.testing.messages.BoltV44Wire.pull;
import static org.neo4j.bolt.testing.messages.BoltV44Wire.reset;
import static org.neo4j.bolt.testing.messages.BoltV44Wire.rollback;
import static org.neo4j.bolt.testing.messages.BoltV44Wire.route;
import static org.neo4j.bolt.testing.messages.BoltV44Wire.run;
import static org.neo4j.values.storable.Values.longValue;

import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.bolt.AbstractBoltITBase;
import org.neo4j.bolt.protocol.v40.bookmark.BookmarkWithDatabaseId;
import org.neo4j.bolt.protocol.v44.BoltProtocolV44;
import org.neo4j.bolt.testing.client.TransportConnection;
import org.neo4j.bolt.transport.Neo4jWithSocketExtension;
import org.neo4j.test.extension.testdirectory.EphemeralTestDirectoryExtension;

@EphemeralTestDirectoryExtension
@Neo4jWithSocketExtension
public class BoltV44TransportIT extends AbstractBoltITBase {
    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldReturnTheRoutingTableInTheSuccessMessageWhenItReceivesTheRouteMessage(
            TransportConnection.Factory connectionFactory) throws Exception {
        init(connectionFactory);

        connection.send(route());

        assertThat(connection)
                .receivesSuccess(metadata -> assertThat(metadata).hasEntrySatisfying("rt", rt -> assertThat(rt)
                        .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                        .satisfies(BoltV44TransportIT::assertRoutingTableHasCorrectShape)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldReturnTheRoutingTableInTheSuccessMessageWhenItReceivesTheRouteMessageWithBookmark(
            TransportConnection.Factory connectionFactory) throws Exception {
        init(connectionFactory);

        var lastClosedTransactionId = getLastClosedTransactionId();
        var routeBookmark = new BookmarkWithDatabaseId(lastClosedTransactionId, getDatabaseId());

        connection.send(route(null, List.of(routeBookmark.toString()), null));

        assertThat(connection).receivesSuccess(metadata -> {
            assertThat(metadata.containsKey("rt")).isTrue();
            assertThat(metadata.get("rt"))
                    .isInstanceOf(Map.class)
                    .satisfies(rt -> assertRoutingTableHasCorrectShape((Map<?, ?>) rt));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldReturnFailureIfRoutingTableFailedToReturn(TransportConnection.Factory connectionFactory)
            throws Exception {
        init(connectionFactory);

        connection.send(route(null, null, "DOESNT_EXIST!"));
        assertThat(connection).receivesFailure();

        connection.send(reset());
        assertThat(connection).receivesSuccess();

        connection.send(route());
        assertThat(connection).receivesSuccess(metadata -> {
            assertThat(metadata.containsKey("rt")).isTrue();
            assertThat(metadata.get("rt"))
                    .isInstanceOf(Map.class)
                    .satisfies(rt -> assertRoutingTableHasCorrectShape((Map<?, ?>) rt));
        });
    }

    private static void assertRoutingTableHasCorrectShape(Map<?, ?> routingTable) {
        assertAll(
                () -> {
                    assertThat(routingTable.containsKey("ttl")).isTrue();
                    assertThat(routingTable.get("ttl")).isInstanceOf(Long.class);
                },
                () -> {
                    assertThat(routingTable.containsKey("servers")).isTrue();
                    assertThat(routingTable.get("servers"))
                            .isInstanceOf(List.class)
                            .satisfies(s -> {
                                var servers = (List<?>) s;
                                for (var srv : servers) {
                                    assertThat(srv).isInstanceOf(Map.class);
                                    var server = (Map<?, ?>) srv;
                                    assertAll(
                                            () -> {
                                                assertThat(server.containsKey("role"))
                                                        .isTrue();
                                                assertThat(server.get("role")).isIn("READ", "WRITE", "ROUTE");
                                            },
                                            () -> {
                                                assertThat(server.containsKey("addresses"))
                                                        .isTrue();
                                                assertThat(server.get("addresses"))
                                                        .isInstanceOf(List.class)
                                                        .satisfies(ad -> {
                                                            var addresses = (List<?>) ad;
                                                            for (var address : addresses) {
                                                                assertThat(address)
                                                                        .isInstanceOf(String.class);
                                                            }
                                                        });
                                            });
                                }
                            });
                });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldReturnUpdatedBookmarkAfterAutoCommitTransaction(TransportConnection.Factory connectionFactory)
            throws Exception {
        init(connectionFactory);

        // bookmark is expected to advance once the auto-commit transaction is committed
        var lastClosedTransactionId = getLastClosedTransactionId();
        var expectedBookmark = new BookmarkWithDatabaseId(lastClosedTransactionId + 1, getDatabaseId()).toString();

        connection.send(run("CREATE ()"));
        connection.send(pull());

        assertThat(connection).receivesSuccess().receivesSuccess(map -> assertThat(map)
                .containsEntry("bookmark", expectedBookmark));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldReturnUpdatedBookmarkAfterExplicitTransaction(TransportConnection.Factory connectionFactory)
            throws Exception {
        init(connectionFactory);

        // bookmark is expected to advance once the auto-commit transaction is committed
        var lastClosedTransactionId = getLastClosedTransactionId();
        var expectedBookmark = new BookmarkWithDatabaseId(lastClosedTransactionId + 1, getDatabaseId()).toString();

        connection.send(begin());
        assertThat(connection).receivesSuccess();

        connection.send(run("CREATE ()")).send(pull());

        assertThat(connection).receivesSuccess().receivesSuccess(meta -> assertThat(meta)
                .doesNotContainKey("bookmark"));

        connection.send(commit());
        assertThat(connection).receivesSuccess(meta -> assertThat(meta).containsEntry("bookmark", expectedBookmark));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldStreamWhenStatementIdNotProvided(TransportConnection.Factory connectionFactory) throws Exception {
        init(connectionFactory);

        // begin a transaction
        connection.send(begin());
        assertThat(connection).receivesSuccess();

        // execute a query
        connection.send(run("UNWIND range(30, 40) AS x RETURN x"));
        assertThat(connection)
                .receivesSuccess(
                        meta -> assertThat(meta).containsEntry("qid", 0L).containsKeys("fields", "t_first"));

        // request 5 records but do not provide qid
        connection.send(pull(5));
        assertThat(connection)
                .receivesRecord(longValue(30L))
                .receivesRecord(longValue(31L))
                .receivesRecord(longValue(32L))
                .receivesRecord(longValue(33L))
                .receivesRecord(longValue(34L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // request 2 more records but do not provide qid
        connection.send(pull(2));
        assertThat(connection)
                .receivesRecord(longValue(35L))
                .receivesRecord(longValue(36L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // request 3 more records and provide qid
        connection.send(pull(3L, 0));

        assertThat(connection)
                .receivesRecord(longValue(37L))
                .receivesRecord(longValue(38L))
                .receivesRecord(longValue(39L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // request 10 more records but do not provide qid, only 1 more record is available
        connection.send(pull(10L));
        assertThat(connection).receivesRecord(longValue(40L)).receivesSuccess(meta -> assertThat(meta)
                .containsKey("t_last"));

        // rollback the transaction
        connection.send(rollback());
        assertThat(connection).receivesSuccess();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldSendAndReceiveStatementIds(TransportConnection.Factory connectionFactory) throws Exception {
        init(connectionFactory);

        // begin a transaction
        connection.send(begin());
        assertThat(connection).receivesSuccess();

        // execute query #0
        connection.send(run("UNWIND range(1, 10) AS x RETURN x"));
        assertThat(connection)
                .receivesSuccess(
                        meta -> assertThat(meta).containsEntry("qid", 0L).containsKeys("fields", "t_first"));

        // request 3 records for query #0
        connection.send(pull(3L, 0));
        assertThat(connection)
                .receivesRecord(longValue(1L))
                .receivesRecord(longValue(2L))
                .receivesRecord(longValue(3L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // execute query #1
        connection.send(run("UNWIND range(11, 20) AS x RETURN x"));
        assertThat(connection)
                .receivesSuccess(
                        meta -> assertThat(meta).containsEntry("qid", 1L).containsKeys("fields", "t_first"));

        // request 2 records for query #1
        connection.send(pull(2, 1));
        assertThat(connection)
                .receivesRecord(longValue(11L))
                .receivesRecord(longValue(12L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // execute query #2
        connection.send(run("UNWIND range(21, 30) AS x RETURN x"));
        assertThat(connection)
                .receivesSuccess(
                        meta -> assertThat(meta).containsEntry("qid", 2L).containsKeys("fields", "t_first"));

        // request 4 records for query #2
        // no qid - should use the statement from the latest RUN
        connection.send(pull(4));

        assertThat(connection)
                .receivesRecord(longValue(21L))
                .receivesRecord(longValue(22L))
                .receivesRecord(longValue(23L))
                .receivesRecord(longValue(24L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // execute query #3
        connection.send(run("UNWIND range(31, 40) AS x RETURN x"));
        assertThat(connection)
                .receivesSuccess(
                        meta -> assertThat(meta).containsEntry("qid", 3L).containsKeys("fields", "t_first"));

        // request 1 record for query #3
        connection.send(pull(1, 3));
        assertThat(connection).receivesRecord(longValue(31L)).receivesSuccess(meta -> assertThat(meta)
                .containsEntry("has_more", true));

        // request 2 records for query #0
        connection.send(pull(2, 0));
        assertThat(connection)
                .receivesRecord(longValue(4L))
                .receivesRecord(longValue(5L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // request 9 records for query #3
        connection.send(pull(9, 3));
        assertThat(connection)
                .receivesRecord(longValue(32L))
                .receivesRecord(longValue(33L))
                .receivesRecord(longValue(34L))
                .receivesRecord(longValue(35L))
                .receivesRecord(longValue(36L))
                .receivesRecord(longValue(37L))
                .receivesRecord(longValue(38L))
                .receivesRecord(longValue(39L))
                .receivesRecord(longValue(40L))
                .receivesSuccess(meta -> assertThat(meta).containsKey("t_last").doesNotContainKey("has_more"));

        // commit the transaction
        connection.send(commit());
        assertThat(connection).receivesSuccess();
    }

    @Override
    protected void negotiateBolt() throws Exception {
        connection.send(BoltProtocolV44.VERSION);
        assertThat(connection).negotiates(BoltProtocolV44.VERSION);

        connection.send(hello());
        assertThat(connection).receivesSuccess();
    }
}