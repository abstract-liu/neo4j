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
package org.neo4j.bolt.v4.messaging;

import static org.neo4j.bolt.v4.messaging.MessageMetadataParser.ABSENT_DB_NAME;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.neo4j.bolt.runtime.AccessMode;
import org.neo4j.bolt.runtime.Bookmark;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.VirtualValues;

public class RunMessage extends org.neo4j.bolt.v3.messaging.request.RunMessage {
    private final String databaseName;

    public RunMessage(String statement) {
        this(statement, VirtualValues.EMPTY_MAP);
    }

    public RunMessage(String statement, MapValue params) {
        this(statement, params, VirtualValues.EMPTY_MAP);
    }

    public RunMessage(String statement, MapValue params, MapValue meta) {
        this(statement, params, meta, List.of(), null, AccessMode.WRITE, Map.of(), ABSENT_DB_NAME);
    }

    public RunMessage(
            String statement,
            MapValue params,
            MapValue meta,
            List<Bookmark> bookmarks,
            Duration txTimeout,
            AccessMode accessMode,
            Map<String, Object> txMetadata,
            String databaseName) {
        super(statement, params, meta, bookmarks, txTimeout, accessMode, txMetadata);
        this.databaseName = databaseName;
    }

    public String databaseName() {
        return databaseName;
    }
}