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
package org.neo4j.kernel.impl.api.index;

import org.neo4j.common.EntityType;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.internal.schema.SchemaState;
import org.neo4j.kernel.impl.api.index.stats.IndexStatisticsStore;
import org.neo4j.logging.LogProvider;

/**
 * Factory that is able to create either {@link MultipleIndexPopulator} or {@link BatchingMultipleIndexPopulator}
 * depending on the given config.
 *
 * @see GraphDatabaseSettings#multi_threaded_schema_index_population_enabled
 */
public abstract class MultiPopulatorFactory
{
    private MultiPopulatorFactory()
    {
    }

    public abstract MultipleIndexPopulator create( IndexStoreView storeView, LogProvider logProvider, EntityType type, SchemaState schemaState,
            IndexStatisticsStore indexStatisticsStore );

    public static MultiPopulatorFactory forConfig( Config config )
    {
        boolean multiThreaded = config.get( GraphDatabaseSettings.multi_threaded_schema_index_population_enabled );
        return multiThreaded ? new MultiThreadedPopulatorFactory() : new SingleThreadedPopulatorFactory();
    }

    private static class SingleThreadedPopulatorFactory extends MultiPopulatorFactory
    {
        @Override
        public MultipleIndexPopulator create( IndexStoreView storeView, LogProvider logProvider, EntityType type, SchemaState schemaState,
                IndexStatisticsStore indexStatisticsStore )
        {
            return new MultipleIndexPopulator( storeView, logProvider, type, schemaState, indexStatisticsStore );
        }
    }

    private static class MultiThreadedPopulatorFactory extends MultiPopulatorFactory
    {
        @Override
        public MultipleIndexPopulator create( IndexStoreView storeView, LogProvider logProvider, EntityType type, SchemaState schemaState,
                IndexStatisticsStore indexStatisticsStore )
        {
            return new BatchingMultipleIndexPopulator( storeView, logProvider, type, schemaState, indexStatisticsStore );
        }
    }
}