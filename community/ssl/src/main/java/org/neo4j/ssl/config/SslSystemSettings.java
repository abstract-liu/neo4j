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
package org.neo4j.ssl.config;

import io.netty.handler.ssl.SslProvider;

import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.configuration.Description;
import org.neo4j.configuration.Internal;
import org.neo4j.configuration.SettingImpl;
import org.neo4j.configuration.SettingsDeclaration;
import org.neo4j.graphdb.config.Setting;

import static org.neo4j.configuration.SettingValueParsers.BOOL;
import static org.neo4j.configuration.SettingValueParsers.ofEnum;

@ServiceProvider
public class SslSystemSettings implements SettingsDeclaration
{
    @Description( "Netty SSL provider" )
    public static final Setting<SslProvider> netty_ssl_provider =
            SettingImpl.newBuilder( "dbms.netty.ssl.provider", ofEnum( SslProvider.class ), SslProvider.JDK ).build();

    @Internal
    @Description( "Don't try and read dot-prefixed files or dot-prefixed directories in ssl policy directories" )
    public static final Setting<Boolean> ignore_dotfiles =
            SettingImpl.newBuilder( "unsupported.dbms.ssl.system.ignore_dot_files", BOOL, false ).build();
}