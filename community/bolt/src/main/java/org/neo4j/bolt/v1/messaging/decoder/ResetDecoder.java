/*
 * Copyright (c) 2002-2018 "Neo4j,"
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
package org.neo4j.bolt.v1.messaging.decoder;

import java.io.IOException;

import org.neo4j.bolt.logging.BoltMessageLogger;
import org.neo4j.bolt.messaging.Neo4jPack;
import org.neo4j.bolt.messaging.RequestMessage;
import org.neo4j.bolt.messaging.RequestMessageDecoder;
import org.neo4j.bolt.runtime.BoltConnection;
import org.neo4j.bolt.runtime.BoltResponseHandler;
import org.neo4j.bolt.v1.messaging.message.Reset;

public class ResetDecoder implements RequestMessageDecoder
{
    private final BoltConnection connection;
    private final BoltResponseHandler responseHandler;
    private final BoltMessageLogger messageLogger;

    public ResetDecoder( BoltConnection connection, BoltResponseHandler responseHandler, BoltMessageLogger messageLogger )
    {
        this.connection = connection;
        this.responseHandler = responseHandler;
        this.messageLogger = messageLogger;
    }

    @Override
    public int signature()
    {
        return Reset.SIGNATURE;
    }

    @Override
    public BoltResponseHandler responseHandler()
    {
        return responseHandler;
    }

    @Override
    public RequestMessage decode( Neo4jPack.Unpacker unpacker ) throws IOException
    {
        messageLogger.clientEvent( "INTERRUPT" );
        messageLogger.logReset();
        connection.interrupt();
        return Reset.INSTANCE;
    }
}
