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
package org.neo4j.index.internal.gbptree;

import java.nio.file.OpenOption;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.ImmutableSet;
import org.junit.jupiter.api.Disabled;
import org.neo4j.io.pagecache.PageCacheOpenOptions;

@Disabled("TODO mvcc: GBPTree requires reentrant write locks on page cache pages")
public class GBPTreeFixedSizeWithReservedBytesIT extends GBPTreeFixedSizeIT {

    @Override
    ImmutableSet<OpenOption> getOpenOptions() {
        return Sets.immutable.of(PageCacheOpenOptions.MULTI_VERSIONED);
    }
}