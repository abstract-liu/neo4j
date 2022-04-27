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
package org.neo4j.internal.batchimport.input.csv;

import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;
import static org.neo4j.csv.reader.Readables.individualFiles;
import static org.neo4j.csv.reader.Readables.iterator;
import static org.neo4j.internal.batchimport.input.csv.CsvInput.idExtractor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.neo4j.collection.RawIterator;
import org.neo4j.csv.reader.CharReadable;
import org.neo4j.csv.reader.CharSeeker;
import org.neo4j.csv.reader.Configuration;
import org.neo4j.csv.reader.Extractor;
import org.neo4j.csv.reader.Extractors;
import org.neo4j.csv.reader.Mark;
import org.neo4j.function.Factory;
import org.neo4j.internal.batchimport.input.DuplicateHeaderException;
import org.neo4j.internal.batchimport.input.Group;
import org.neo4j.internal.batchimport.input.Groups;
import org.neo4j.internal.batchimport.input.HeaderException;
import org.neo4j.internal.batchimport.input.IdType;
import org.neo4j.internal.batchimport.input.csv.Header.Entry;
import org.neo4j.internal.batchimport.input.csv.Header.Monitor;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.util.Preconditions;
import org.neo4j.values.storable.CSVHeaderInformation;
import org.neo4j.values.storable.PointValue;
import org.neo4j.values.storable.TemporalValue;
import org.neo4j.values.storable.Value;

/**
 * Provides common implementations of factories required by f.ex {@link CsvInput}.
 */
public class DataFactories {
    private static final Supplier<ZoneId> DEFAULT_TIME_ZONE = () -> UTC;

    private DataFactories() {}

    /**
     * Creates a {@link DataFactory} where data exists in multiple files. If the first line of the first file is a header,
     * E.g. {@link #defaultFormatNodeFileHeader()} can be used to extract that.
     *
     * @param decorator Decorator for this data.
     * @param charset {@link Charset} to read data in.
     * @param files the files making up the data.
     *
     * @return {@link DataFactory} that returns a {@link CharSeeker} over all the supplied {@code files}.
     */
    public static DataFactory data(final Decorator decorator, final Charset charset, final Path... files) {
        if (files.length == 0) {
            throw new IllegalArgumentException("No files specified");
        }

        return config -> new Data() {
            @Override
            public RawIterator<CharReadable, IOException> stream() {
                return individualFiles(charset, files);
            }

            @Override
            public Decorator decorator() {
                return decorator;
            }
        };
    }

    /**
     * @param decorator Decorator for this data.
     * @param readable we need to have this as a {@link Factory} since one data file may be opened and scanned
     * multiple times.
     * @return {@link DataFactory} that returns a {@link CharSeeker} over the supplied {@code readable}
     */
    public static DataFactory data(final Decorator decorator, final Supplier<CharReadable> readable) {
        return config -> new Data() {
            @Override
            public RawIterator<CharReadable, IOException> stream() {
                return iterator(reader -> reader, readable.get());
            }

            @Override
            public Decorator decorator() {
                return decorator;
            }
        };
    }

    /**
     * Header parser that will read header information, using the default node header format,
     * from the top of the data file.
     *
     * This header factory can be used even when the header exists in a separate file, if that file
     * is the first in the list of files supplied to {@link #data}.
     * @param defaultTimeZone A supplier of the time zone to be used for temporal values when not specified explicitly
     * @param normalizeTypes whether or not to normalize types.
     */
    public static Header.Factory defaultFormatNodeFileHeader(Supplier<ZoneId> defaultTimeZone, boolean normalizeTypes) {
        return new DefaultNodeFileHeaderParser(defaultTimeZone, normalizeTypes);
    }

    /**
     * Like {@link #defaultFormatNodeFileHeader(Supplier, boolean)}} with UTC as the default time zone.
     * @param normalizeTypes whether or not to normalize types.
     */
    public static Header.Factory defaultFormatNodeFileHeader(boolean normalizeTypes) {
        return defaultFormatNodeFileHeader(DEFAULT_TIME_ZONE, normalizeTypes);
    }

    /**
     * Like {@link #defaultFormatNodeFileHeader(boolean)}} with no normalization.
     */
    public static Header.Factory defaultFormatNodeFileHeader() {
        return defaultFormatNodeFileHeader(false);
    }

    /**
     * Header parser that will read header information, using the default relationship header format,
     * from the top of the data file.
     *
     * This header factory can be used even when the header exists in a separate file, if that file
     * is the first in the list of files supplied to {@link #data}.
     * @param defaultTimeZone A supplier of the time zone to be used for temporal values when not specified explicitly
     * @param normalizeTypes whether or not to normalize types.
     */
    public static Header.Factory defaultFormatRelationshipFileHeader(
            Supplier<ZoneId> defaultTimeZone, boolean normalizeTypes) {
        return new DefaultRelationshipFileHeaderParser(defaultTimeZone, normalizeTypes);
    }

    /**
     * Like {@link #defaultFormatRelationshipFileHeader(Supplier, boolean)} with UTC as the default time zone.
     * @param normalizeTypes whether or not to normalize types.
     */
    public static Header.Factory defaultFormatRelationshipFileHeader(boolean normalizeTypes) {
        return defaultFormatRelationshipFileHeader(DEFAULT_TIME_ZONE, normalizeTypes);
    }

    /**
     * Like {@link #defaultFormatRelationshipFileHeader(boolean)} with no normalization.
     */
    public static Header.Factory defaultFormatRelationshipFileHeader() {
        return defaultFormatRelationshipFileHeader(DEFAULT_TIME_ZONE, false);
    }

    public static Entry[] parseHeaderEntries(
            CharSeeker dataSeeker,
            Configuration config,
            IdType idType,
            Groups groups,
            Supplier<ZoneId> defaultTimeZone,
            HeaderEntryFactory entryFactory,
            Monitor monitor) {
        try {
            Mark mark = new Mark();
            Extractors extractors = new Extractors(
                    config.arrayDelimiter(), config.emptyQuotedStringsAsNull(), config.trimStrings(), defaultTimeZone);
            Extractor<?> idExtractor = idExtractor(idType, extractors);
            int delimiter = config.delimiter();
            List<Entry> columns = new ArrayList<>();
            for (int i = 0; !mark.isEndOfLine() && dataSeeker.seek(mark, delimiter); i++) {
                String entryString = dataSeeker.tryExtract(mark, extractors.string())
                        ? extractors.string().value()
                        : null;
                HeaderEntrySpec spec = entryString != null ? parseHeaderEntrySpec(entryString) : null;
                if (spec == null || Type.IGNORE.name().equals(spec.type())) {
                    columns.add(new Entry(null, Type.IGNORE, Group.GLOBAL, null));
                } else {
                    columns.add(entryFactory.create(
                            dataSeeker.sourceDescription(), i, spec, extractors, idExtractor, groups, monitor));
                }
            }
            return columns.toArray(new Entry[0]);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private abstract static class AbstractDefaultFileHeaderParser implements Header.Factory {
        private final Type[] mandatoryTypes;
        private final Supplier<ZoneId> defaultTimeZone;
        private final boolean normalizeTypes;
        private final HeaderEntryFactory entryFactory;

        AbstractDefaultFileHeaderParser(
                Supplier<ZoneId> defaultTimeZone,
                boolean createGroups,
                boolean normalizeTypes,
                Type... mandatoryTypes) {
            this.defaultTimeZone = defaultTimeZone;
            this.normalizeTypes = normalizeTypes;
            this.mandatoryTypes = mandatoryTypes;
            this.entryFactory = (sourceDescription, entryIndex, spec, extractors, idExtractor, groups, monitor) -> {
                Group group = createGroups ? groups.getOrCreate(spec.group()) : groups.get(spec.group());
                return entry(sourceDescription, entryIndex, spec, group, extractors, idExtractor, monitor);
            };
        }

        @Override
        public Header create(
                CharSeeker dataSeeker, Configuration config, IdType idType, Groups groups, Monitor monitor) {
            Entry[] entries =
                    parseHeaderEntries(dataSeeker, config, idType, groups, defaultTimeZone, entryFactory, monitor);
            validateHeader(entries, dataSeeker);
            return new Header(entries);
        }

        private void validateHeader(Entry[] entries, CharSeeker dataSeeker) {
            Map<String, Entry> properties = new HashMap<>();
            EnumMap<Type, Entry> singletonEntries = new EnumMap<>(Type.class);
            for (Entry entry : entries) {
                switch (entry.type()) {
                    case PROPERTY:
                        Entry existingPropertyEntry = properties.get(entry.name());
                        if (existingPropertyEntry != null) {
                            throw new DuplicateHeaderException(
                                    existingPropertyEntry, entry, dataSeeker.sourceDescription());
                        }
                        properties.put(entry.name(), entry);
                        break;

                    case ID:
                    case START_ID:
                    case END_ID:
                    case TYPE:
                        Entry existingSingletonEntry = singletonEntries.get(entry.type());
                        if (existingSingletonEntry != null) {
                            throw new DuplicateHeaderException(
                                    existingSingletonEntry, entry, dataSeeker.sourceDescription());
                        }
                        singletonEntries.put(entry.type(), entry);
                        break;
                    default:
                        // No need to validate other headers
                        break;
                }
            }

            for (Type type : mandatoryTypes) {
                if (!singletonEntries.containsKey(type)) {
                    throw new HeaderException(
                            format("Missing header of type %s, among entries %s", type, Arrays.toString(entries)));
                }
            }
        }

        static boolean isRecognizedType(String typeSpec) {
            for (Type type : Type.values()) {
                if (type.name().equalsIgnoreCase(typeSpec)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isDefined() {
            return false;
        }

        Extractor<?> propertyExtractor(
                String sourceDescription, String name, String typeSpec, Extractors extractors, Monitor monitor) {
            Extractor<?> extractor = parsePropertyType(typeSpec, extractors);
            if (normalizeTypes) {
                // This basically mean that e.g. a specified type "float" will actually be "double", "int", "short" and
                // all that will be "long".
                String fromType = extractor.name();
                Extractor<?> normalized = extractor.normalize();
                if (!normalized.equals(extractor)) {
                    String toType = normalized.name();
                    monitor.typeNormalized(sourceDescription, name, fromType, toType);
                    return normalized;
                }
            }
            return extractor;
        }

        /**
         * @param idExtractor we supply the id extractor explicitly because it's a configuration,
         * or at least input-global concern and not a concern of this particular header.
         */
        protected abstract Header.Entry entry(
                String sourceDescription,
                int index,
                HeaderEntrySpec spec,
                Group group,
                Extractors extractors,
                Extractor<?> idExtractor,
                Monitor monitor);
    }

    private static HeaderEntrySpec parseHeaderEntrySpec(String rawHeaderField) {
        // rawHeaderField specification: <name><:type>(<group>){<options>}
        // example: id:ID(persons){option1:something,option2:'something else'}

        String name;
        String type = null;
        String groupName = null;
        Map<String, String> options = new HashMap<>();

        // The options
        {
            int optionsStartIndex = rawHeaderField.indexOf('{');
            if (optionsStartIndex != -1) {
                int optionsEndIndex = rawHeaderField.lastIndexOf('}');
                Preconditions.checkState(
                        optionsEndIndex != -1 && optionsEndIndex > optionsStartIndex,
                        "Expected a closing '}' in header %s",
                        rawHeaderField);
                String rawOptions =
                        rawHeaderField.substring(optionsStartIndex, optionsEndIndex + 1); // including the curlies
                options = Value.parseStringMap(rawOptions);
                rawHeaderField = rawHeaderField.substring(0, optionsStartIndex);
            }
        }

        // The group
        {
            int groupStartIndex = rawHeaderField.indexOf('(');
            if (groupStartIndex != -1) {
                int groupEndIndex = rawHeaderField.lastIndexOf(')');
                Preconditions.checkState(
                        groupEndIndex != -1 && groupEndIndex > groupStartIndex, "Expected a closing ')'");
                groupName = rawHeaderField.substring(groupStartIndex + 1, groupEndIndex);
                rawHeaderField = rawHeaderField.substring(0, groupStartIndex);
            }
        }

        // The type
        {
            int typeIndex = rawHeaderField.indexOf(':');
            if (typeIndex != -1) {
                type = rawHeaderField.substring(typeIndex + 1);
                rawHeaderField = rawHeaderField.substring(0, typeIndex);
            }
        }

        // The name
        name = rawHeaderField.isEmpty() ? null : rawHeaderField;

        return new HeaderEntrySpec(name, type, groupName, options);
    }

    record HeaderEntrySpec(String name, String type, String group, Map<String, String> options) {}

    interface HeaderEntryFactory {
        Entry create(
                String sourceDescription,
                int entryIndex,
                HeaderEntrySpec spec,
                Extractors extractors,
                Extractor<?> idExtractor,
                Groups groups,
                Monitor monitor);
    }

    private static class DefaultNodeFileHeaderParser extends AbstractDefaultFileHeaderParser {
        DefaultNodeFileHeaderParser(Supplier<ZoneId> defaultTimeZone, boolean normalizeTypes) {
            super(defaultTimeZone, true, normalizeTypes);
        }

        @Override
        protected Header.Entry entry(
                String sourceDescription,
                int index,
                HeaderEntrySpec spec,
                Group group,
                Extractors extractors,
                Extractor<?> idExtractor,
                Monitor monitor) {
            // For nodes it's simply ID,LABEL,PROPERTY. typeSpec can be either ID,LABEL or a type of property,
            // like 'int' or 'string_array' or similar, or empty for 'string' property.
            Type type;
            Extractor<?> extractor;
            CSVHeaderInformation optionalParameter = null;
            if (spec.type() == null) {
                type = Type.PROPERTY;
                extractor = extractors.string();
            } else {
                if (spec.type().equalsIgnoreCase(Type.ID.name())) {
                    type = Type.ID;
                    extractor = idExtractor;
                } else if (spec.type().equalsIgnoreCase(Type.LABEL.name())) {
                    type = Type.LABEL;
                    extractor = extractors.stringArray();
                } else if (isRecognizedType(spec.type())) {
                    throw new HeaderException("Unexpected node header type '" + spec.type() + "'");
                } else {
                    type = Type.PROPERTY;
                    extractor = propertyExtractor(sourceDescription, spec.name(), spec.type(), extractors, monitor);
                    optionalParameter = parseOptionalParameter(extractor, spec.options());
                }
            }
            return new Header.Entry(spec.name(), type, group, extractor, spec.options(), optionalParameter);
        }
    }

    private static class DefaultRelationshipFileHeaderParser extends AbstractDefaultFileHeaderParser {
        DefaultRelationshipFileHeaderParser(Supplier<ZoneId> defaultTimeZone, boolean normalizeTypes) {
            // Don't have TYPE as mandatory since a decorator could provide that
            super(defaultTimeZone, false, normalizeTypes, Type.START_ID, Type.END_ID);
        }

        @Override
        protected Header.Entry entry(
                String sourceDescription,
                int index,
                HeaderEntrySpec spec,
                Group group,
                Extractors extractors,
                Extractor<?> idExtractor,
                Monitor monitor) {
            Type type;
            Extractor<?> extractor;
            CSVHeaderInformation optionalParameter = null;
            if (spec.type() == null) { // Property
                type = Type.PROPERTY;
                extractor = extractors.string();
            } else {
                if (spec.type().equalsIgnoreCase(Type.START_ID.name())) {
                    type = Type.START_ID;
                    extractor = idExtractor;
                } else if (spec.type().equalsIgnoreCase(Type.END_ID.name())) {
                    type = Type.END_ID;
                    extractor = idExtractor;
                } else if (spec.type().equalsIgnoreCase(Type.TYPE.name())) {
                    type = Type.TYPE;
                    extractor = extractors.string();
                } else if (isRecognizedType(spec.type())) {
                    throw new HeaderException("Unexpected relationship header type '" + spec.type() + "'");
                } else {
                    type = Type.PROPERTY;
                    extractor = propertyExtractor(sourceDescription, spec.name(), spec.type(), extractors, monitor);
                    optionalParameter = parseOptionalParameter(extractor, spec.options());
                }
            }
            return new Header.Entry(spec.name(), type, group, extractor, spec.options(), optionalParameter);
        }
    }

    private static CSVHeaderInformation parseOptionalParameter(Extractor<?> extractor, Map<String, String> options) {
        if (!options.isEmpty()) {
            if (extractor instanceof Extractors.PointExtractor || extractor instanceof Extractors.PointArrayExtractor) {
                return PointValue.parseHeaderInformation(options);
            } else if (extractor instanceof Extractors.TimeExtractor
                    || extractor instanceof Extractors.DateTimeExtractor
                    || extractor instanceof Extractors.TimeArrayExtractor
                    || extractor instanceof Extractors.DateTimeArrayExtractor) {
                return TemporalValue.parseHeaderInformation(options);
            }
        }
        return null;
    }

    private static Extractor<?> parsePropertyType(String typeSpec, Extractors extractors) {
        try {
            return extractors.valueOf(typeSpec);
        } catch (IllegalArgumentException e) {
            throw new HeaderException("Unable to parse header", e);
        }
    }

    public static Iterable<DataFactory> datas(DataFactory... factories) {
        return Iterables.iterable(factories);
    }
}
