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
package org.neo4j.bolt.testing.messages;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.neo4j.bolt.negotiation.ProtocolVersion;
import org.neo4j.bolt.protocol.common.connector.connection.Feature;
import org.neo4j.bolt.protocol.v41.message.request.RoutingContext;
import org.neo4j.packstream.io.PackstreamBuf;
import org.neo4j.values.virtual.MapValue;

public interface BoltWire {

    /**
     * Identifies the revision of the protocol which is targeted by this wire implementation.
     *
     * @return a protocol version.
     */
    ProtocolVersion getProtocolVersion();

    /**
     * Enables a given set of features within this bolt wire.
     *
     * @param features an array of features.
     */
    void enable(Feature... features);

    /**
     * Checks whether the wire implementation recognizes the desired feature(s) as optional negotiated functionality.
     *
     * @param features an array of features.
     */
    boolean isOptionalFeature(Feature... features);

    default ByteBuf hello() {
        return this.hello(null);
    }

    default ByteBuf hello(String principal, String credentials) {
        return this.hello(Map.of("principal", principal, "credentials", credentials, "scheme", "basic"));
    }

    default ByteBuf hello(String principal, String credentials, String realm) {
        return this.hello(
                Map.of("principal", principal, "credentials", credentials, "realm", realm, "scheme", "basic"));
    }

    default ByteBuf hello(Map<String, Object> meta) {
        return this.hello(meta, null);
    }

    ByteBuf hello(Map<String, Object> meta, RoutingContext context);

    default ByteBuf begin() {
        return begin(null, null, null, null);
    }

    default ByteBuf begin(String db) {
        return this.begin(db, null, null, null);
    }

    default ByteBuf begin(Collection<String> bookmarks) {
        return this.begin(null, null, bookmarks, null);
    }

    default ByteBuf begin(String db, String impersonatedUser) {
        return this.begin(db, impersonatedUser, null, null);
    }

    ByteBuf begin(String db, String impersonatedUser, Collection<String> bookmarks, String transactionType);

    default ByteBuf discard() {
        return this.discard(-1);
    }

    ByteBuf discard(long n);

    default ByteBuf pull() {
        return this.pull(-1);
    }

    ByteBuf pull(long n);

    ByteBuf pull(long n, long qid);

    default ByteBuf run() {
        return this.run("RETURN 1");
    }

    default ByteBuf run(String statement) {
        return this.run(statement, null);
    }

    default ByteBuf run(String statement, MapValue params) {
        return this.run(statement, params, null);
    }

    ByteBuf run(String statement, MapValue params, MapValue meta);

    ByteBuf rollback();

    ByteBuf commit();

    ByteBuf reset();

    ByteBuf goodbye();

    default ByteBuf route() {
        return this.route(null);
    }

    default ByteBuf route(String impersonatedUser) {
        return this.route(null, null, null, impersonatedUser);
    }

    default ByteBuf route(RoutingContext context, Collection<String> bookmarks, String db) {
        return this.route(context, bookmarks, db, null);
    }

    ByteBuf route(RoutingContext context, Collection<String> bookmarks, String db, String impersonatedUser);

    void nodeValue(PackstreamBuf buf, String elementId, int id, List<String> labels);

    void relationshipValue(
            PackstreamBuf buf,
            String elementId,
            int id,
            String startElementId,
            int startId,
            String endElementId,
            int endId,
            String type);

    void unboundRelationshipValue(PackstreamBuf buf, String elementId, int id, String type);
}
