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
package org.neo4j.bolt.protocol.common.handler.messages;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.neo4j.bolt.protocol.common.connector.connection.Connection;
import org.neo4j.bolt.protocol.v40.messaging.request.ResetMessage;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.logging.Log;

public class ResetMessageHandler extends SimpleChannelInboundHandler<ResetMessage> {
    private final Log log;

    private Connection connection;

    public ResetMessageHandler(InternalLogProvider logging) {
        this.log = logging.getLog(ResetMessageHandler.class);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.connection = Connection.getConnection(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ResetMessage msg) throws Exception {
        log.debug("Interrupted state machine");
        this.connection.interrupt();

        ctx.fireChannelRead(msg);
    }
}