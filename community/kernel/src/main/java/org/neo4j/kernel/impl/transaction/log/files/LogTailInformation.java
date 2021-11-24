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
package org.neo4j.kernel.impl.transaction.log.files;

import org.neo4j.kernel.impl.transaction.log.files.checkpoint.CheckpointInfo;
import org.neo4j.storageengine.api.StoreId;

public class LogTailInformation
{
    public final CheckpointInfo lastCheckPoint;
    public final long firstTxIdAfterLastCheckPoint;
    public final boolean filesNotFound;
    public final long currentLogVersion;
    public final byte latestLogEntryVersion;
    private final boolean recordAfterCheckpoint;
    private final StoreId storeId;

    public LogTailInformation( boolean recordAfterCheckpoint, long firstTxIdAfterLastCheckPoint, boolean filesNotFound, long currentLogVersion,
            byte latestLogEntryVersion )
    {
        this( null, recordAfterCheckpoint, firstTxIdAfterLastCheckPoint, filesNotFound, currentLogVersion, latestLogEntryVersion, StoreId.UNKNOWN );
    }

    public LogTailInformation( CheckpointInfo lastCheckPoint, boolean recordAfterCheckpoint, long firstTxIdAfterLastCheckPoint, boolean filesNotFound,
            long currentLogVersion, byte latestLogEntryVersion, StoreId storeId )
    {
        this.lastCheckPoint = lastCheckPoint;
        this.firstTxIdAfterLastCheckPoint = firstTxIdAfterLastCheckPoint;
        this.filesNotFound = filesNotFound;
        this.currentLogVersion = currentLogVersion;
        this.latestLogEntryVersion = latestLogEntryVersion;
        this.recordAfterCheckpoint = recordAfterCheckpoint;
        this.storeId = storeId;
    }

    public boolean commitsAfterLastCheckpoint()
    {
        return recordAfterCheckpoint;
    }

    public boolean logsMissing()
    {
        return lastCheckPoint == null && filesNotFound;
    }

    public boolean hasUnreadableBytesInCheckpointLogs()
    {
        return lastCheckPoint != null && !lastCheckPoint.getChannelPositionAfterCheckpoint().equals( lastCheckPoint.getCheckpointFilePostReadPosition() );
    }

    public boolean isRecoveryRequired()
    {
        return recordAfterCheckpoint || logsMissing() || hasUnreadableBytesInCheckpointLogs();
    }

    public StoreId getStoreId()
    {
        return storeId;
    }

    @Override
    public String toString()
    {
        return "LogTailInformation{" + "lastCheckPoint=" + lastCheckPoint + ", firstTxIdAfterLastCheckPoint=" + firstTxIdAfterLastCheckPoint +
                ", filesNotFound=" + filesNotFound + ", currentLogVersion=" + currentLogVersion + ", latestLogEntryVersion=" +
                latestLogEntryVersion + ", recordAfterCheckpoint=" + recordAfterCheckpoint + '}';
    }
}
