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
package org.neo4j.storageengine.api;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Objects;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.ReadableChannel;
import org.neo4j.io.fs.WritableChannel;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.context.CursorContext;

/**
 * A representation of store ID.
 */
public class StoreId extends StoreVersionIdentifier {
    public static final StoreId UNKNOWN = new StoreId(0, 0, "", "", 0, 0);
    private final long creationTime;
    private final long random;

    public StoreId(
            long creationTime,
            long random,
            String storageEngineName,
            String formatFamilyName,
            int majorVersion,
            int minorVersion) {
        super(storageEngineName, formatFamilyName, majorVersion, minorVersion);
        this.creationTime = creationTime;
        this.random = random;
    }

    public static StoreId generateNew(
            String storageEngineName, String formatFamilyName, int majorVersion, int minorVersion) {
        return new StoreId(
                System.currentTimeMillis(),
                new SecureRandom().nextLong(),
                storageEngineName,
                formatFamilyName,
                majorVersion,
                minorVersion);
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getRandom() {
        return random;
    }

    public void serialize(WritableChannel channel) throws IOException {
        StoreIdSerialization.serialize(this, channel);
    }

    /**
     * Returns {@code true} if the submitted {@code anotherId} is an upgrade successor of this store ID.
     * This means that the IDs have all the same attributes, but possibly differ only in the minor store version part
     * and the minor store version of the submitted {@code anotherId} is greater than or equal to the minor version
     * of this store version.
     */
    public boolean isSameOrUpgradeSuccessor(StoreId anotherId) {
        return creationTime == anotherId.creationTime
                && random == anotherId.random
                && super.isSameOrUpgradeSuccessor(anotherId);
    }

    /**
     * Checks if the store version represented by this store ID is known to and fully supported by these binaries.
     * <p>
     * This method is interesting for cluster-related operations when store version identifiers are sent
     * between cluster members that can be on different versions of the binaries.
     * A store version represented by a store ID does not need to correspond to store format known to these binaries,
     * in which case this method will return {@code false}. Another case when this method will respond negatively
     * is when the store version is recognised, but the corresponding format is a legacy one used
     * only for migration purposes.
     */
    public boolean isStoreVersionFullySupportedLocally() {
        var maybeStorageEngine = StorageEngineFactory.allAvailableStorageEngines().stream()
                .filter(e -> e.name().equals(getStorageEngineName()))
                .findAny();
        return maybeStorageEngine
                .flatMap(engineFactory -> engineFactory.versionInformation(this))
                .map(storeVersion -> !storeVersion.onlyForMigration())
                .orElse(false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StoreId storeId = (StoreId) o;
        return creationTime == storeId.creationTime
                && random == storeId.random
                && getMajorVersion() == storeId.getMajorVersion()
                && getMinorVersion() == storeId.getMinorVersion()
                && getStorageEngineName().equals(storeId.getStorageEngineName())
                && getFormatFamilyName().equals(storeId.getFormatFamilyName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                creationTime,
                random,
                getStorageEngineName(),
                getFormatFamilyName(),
                getMajorVersion(),
                getMinorVersion());
    }

    @Override
    public String toString() {
        return "StoreId{" + "creationTime="
                + creationTime + ", random="
                + random + ", storageEngineName='"
                + getStorageEngineName() + '\'' + ", formatFamilyName='"
                + getFormatFamilyName() + '\'' + ", majorVersion="
                + getMajorVersion() + ", minorVersion="
                + getMinorVersion() + '}';
    }

    public static StoreId deserialize(ReadableChannel channel) throws IOException {
        return StoreIdSerialization.deserialize(channel);
    }

    /**
     * Retrieves ID of the store represented by the submitted layout.
     * <p>
     * This method will return {@code null} if the layout points to an empty or uninitialised store.
     */
    public static StoreId retrieveFromStore(
            FileSystemAbstraction fs, DatabaseLayout databaseLayout, PageCache pageCache, CursorContext cursorContext)
            throws IOException {
        var maybeEngine = StorageEngineFactory.selectStorageEngine(fs, databaseLayout, pageCache);
        if (maybeEngine.isEmpty()) {
            return null;
        }

        return maybeEngine.get().retrieveStoreId(fs, databaseLayout, pageCache, cursorContext);
    }
}
