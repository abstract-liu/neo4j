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
package org.neo4j.server.http.cypher.integration;

import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;

import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.server.http.cypher.format.DefaultJsonFactory;
import org.neo4j.server.http.cypher.format.api.RecordEvent;
import org.neo4j.server.http.cypher.format.output.json.ResultDataContent;
import org.neo4j.server.http.cypher.format.output.json.ResultDataContentWriter;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

class RestRepresentationWriterIT
{
    private JsonGenerator jsonGenerator;
    private ByteArrayOutputStream stream;
    private ResultDataContentWriter contentWriter;

    @BeforeEach
    void createOutputFormat() throws Exception
    {
        stream = new ByteArrayOutputStream();
        JsonGenerator g = DefaultJsonFactory.INSTANCE.get().createGenerator( stream );
        this.jsonGenerator = DefaultJsonFactory.INSTANCE.get().createGenerator( stream );
        this.contentWriter = ResultDataContent.rest.writer( new URI( "http://localhost/" ) );
    }

    @Test
    void canFormatNode() throws IOException
    {
        DatabaseManagementService managementService = new TestDatabaseManagementServiceBuilder().impermanent().build();
        GraphDatabaseService db = managementService.database( DEFAULT_DATABASE_NAME );
        try ( Transaction transaction = db.beginTx() )
        {
            final Node n = transaction.createNode();
            RecordEvent recordEvent = new RecordEvent( Collections.singletonList( "key" ), k -> n );
            jsonGenerator.writeStartObject();
            this.contentWriter.write( jsonGenerator, recordEvent, null );
            jsonGenerator.writeEndObject();
            jsonGenerator.flush();
            jsonGenerator.close();
        }
        finally
        {
            managementService.shutdown();
        }
        assertTrue( stream.toString().matches( ".*\"self\"\\w*:\\w*\"http://localhost/node/0\",.*" ) );
    }
}