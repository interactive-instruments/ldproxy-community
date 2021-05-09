/**
 * Copyright 2021 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.features.flatgeobuf.app;

import com.google.common.collect.ImmutableMap;
import com.google.flatbuffers.FlatBufferBuilder;
import de.ii.ldproxy.ogcapi.features.flatgeobuf.domain.FlatgeobufConfiguration;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.geometries.domain.SimpleFeatureGeometry;
import org.geotools.referencing.CRS;
import org.geotools.resources.NIOUtilities;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.referencing.FactoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wololo.flatgeobuf.ColumnMeta;
import org.wololo.flatgeobuf.Constants;
import org.wololo.flatgeobuf.GeometryConversions;
import org.wololo.flatgeobuf.HeaderMeta;
import org.wololo.flatgeobuf.generated.ColumnType;
import org.wololo.flatgeobuf.generated.Feature;
import org.wololo.flatgeobuf.generated.GeometryType;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

public class FeatureTransformerFlatgeobuf extends FeatureTransformerSimpleFeature {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureTransformerFlatgeobuf.class);

    private FlatBufferBuilder builder;
    private HeaderMeta headerMeta;
    private final Map<String, FeatureSchema> featureProperties;

    public FeatureTransformerFlatgeobuf(FeatureTransformationContextFlatgeobuf transformationContext) {
        super(FlatgeobufConfiguration.class,
              transformationContext.getApiData(), transformationContext.getCollectionId(),
              transformationContext.getCodelists(), transformationContext.getServiceUrl(),
              transformationContext.isFeatureCollection(), transformationContext.getOutputStream(),
              transformationContext.getCrsTransformer().orElse(null), transformationContext.shouldSwapCoordinates(),
              transformationContext.getFields(), transformationContext.getMaxMultiplicity(), ImmutableMap.of());
        this.builder = new FlatBufferBuilder(16 * 1024); // 16kB
        this.featureProperties = transformationContext.getProperties();
    }

    @Override
    public String getTargetFormat() {
        return FeaturesFormatFlatgeobuf.MEDIA_TYPE.type().toString();
    }

    @Override
    public void onStart(OptionalLong numberReturned, OptionalLong numberMatched) throws IOException {
        outputStream.write(Constants.MAGIC_BYTES);

        headerMeta = getHeader(null, featureProperties, numberReturned.orElse(0)); // 0 = unknown
        HeaderMeta.write(headerMeta, outputStream, builder);
        builder.clear();
    }

    private HeaderMeta getHeader(String name, Map<String, FeatureSchema> properties, long featureCount) {
        List<ColumnMeta> columns = new ArrayList<>();

        HeaderMeta headerMeta = new HeaderMeta();
        headerMeta.name = name;
        headerMeta.featuresCount = featureCount;

        byte geometryType = GeometryType.Unknown;
        for (Map.Entry<String, FeatureSchema> entry : properties.entrySet()) {
            FeatureSchema schema = entry.getValue();
            if (schema.getType()==SchemaBase.Type.GEOMETRY) {
                switch (schema.getGeometryType().orElse(SimpleFeatureGeometry.ANY)) {
                    case POINT:
                        geometryType = GeometryConversions.toGeometryType(Point.class);
                        break;
                    case MULTI_POINT:
                        geometryType = GeometryConversions.toGeometryType(MultiPoint.class);
                        break;
                    case LINE_STRING:
                        geometryType = GeometryConversions.toGeometryType(LineString.class);
                        break;
                    case MULTI_LINE_STRING:
                        geometryType = GeometryConversions.toGeometryType(MultiLineString.class);
                        break;
                    case POLYGON:
                        geometryType = GeometryConversions.toGeometryType(Polygon.class);
                        break;
                    case MULTI_POLYGON:
                        geometryType = GeometryConversions.toGeometryType(MultiPolygon.class);
                        break;
                    case GEOMETRY_COLLECTION:
                        geometryType = GeometryConversions.toGeometryType(GeometryCollection.class);
                        break;
                }
                headerMeta.srid = Objects.nonNull(this.crsTransformer)
                        ? this.crsTransformer.getTargetCrs().getCode()
                        : 4326;
                try {
                    if (Objects.nonNull(this.crsTransformer) &&
                            CRS.decode("EPSG:"+this.crsTransformer.getTargetCrs().getCode()).getCoordinateSystem().getDimension()==3) {
                        headerMeta.hasZ = true;
                    }
                } catch (FactoryException e) {
                    // nothing to do, we assume 2d
                }
            } else {
                ColumnMeta column = new ColumnMeta();
                column.name = entry.getKey();
                switch (schema.getType()) {
                    case BOOLEAN:
                        column.type = ColumnType.Bool;
                        break;
                    case INTEGER:
                        column.type = ColumnType.Int;
                        break;
                    case FLOAT:
                        column.type = ColumnType.Double;
                        break;
                    case DATETIME:
                        column.type = ColumnType.DateTime;
                        break;
                    case STRING:
                        column.type = ColumnType.String;
                        break;
                    default:
                        LOGGER.warn("Property {} with unknown type {} mapped to String in FlatGeobuf output.", column.name, schema.getType().toString());
                        column.type = ColumnType.String;
                }
                schema.getLabel().ifPresent(s -> column.title = s);
                schema.getDescription().ifPresent(s -> column.description = s);
                schema.getConstraints().ifPresent(c -> {
                    c.getRequired().ifPresent(b -> column.nullable = !b);
                });
                schema.getRole().ifPresent(r -> column.unique = r.equals(SchemaBase.Role.ID));
                columns.add(column);
            }
        }

        headerMeta.columns = columns;
        headerMeta.geometryType = geometryType;

        return headerMeta;
    }

    @Override
    public void onEnd() {
    }

    @Override
    public void onFeatureEnd() throws IOException {
        final int propertiesOffset = addProperties(currentProperties, builder, headerMeta);

        // promote to primitives to multi, if this is the specified geometry type;
        // data from some sources (e.g., Shapefile) supports to mix primitives and aggregates
        if (currentGeometry instanceof Polygon && headerMeta.geometryType==GeometryType.MultiPolygon) {
            Polygon[] array = new Polygon[1];
            array[0] = (Polygon) currentGeometry;
            currentGeometry = currentGeometry.getFactory().createMultiPolygon(array);
        } else if (currentGeometry instanceof LineString && headerMeta.geometryType==GeometryType.MultiLineString) {
            LineString[] array = new LineString[1];
            array[0] = (LineString) currentGeometry;
            currentGeometry = currentGeometry.getFactory().createMultiLineString(array);
        } else if (currentGeometry instanceof Point && headerMeta.geometryType==GeometryType.MultiPoint) {
            Point[] array = new Point[1];
            array[0] = (Point) currentGeometry;
            currentGeometry = currentGeometry.getFactory().createMultiPoint(array);
        }
        final int geometryOffset = Objects.nonNull(currentGeometry)
                ? GeometryConversions.serialize(builder, currentGeometry, headerMeta.geometryType)
                : 0;
        final int featureOffset = Feature.createFeature(builder, geometryOffset, propertiesOffset, 0);
        builder.finishSizePrefixed(featureOffset);
        WritableByteChannel channel = Channels.newChannel(outputStream);
        ByteBuffer dataBuffer = builder.dataBuffer();
        while (dataBuffer.hasRemaining()) {
            channel.write(dataBuffer);
        }
        builder.clear();
    }

    private static int addProperties(Map<String, Object> properties, FlatBufferBuilder builder, HeaderMeta headerMeta) {

        int size = 1024; // 1kB
        boolean done = false;
        ByteBuffer propBuffer = NIOUtilities.allocate(size);
        propBuffer.order(ByteOrder.LITTLE_ENDIAN);
        while (!done) {
            try {
                for (short i = 0; i < headerMeta.columns.size(); i++) {
                    ColumnMeta column = headerMeta.columns.get(i);
                    byte type = column.type;
                    Object value = properties.get(column.name);
                    if (value == null) {
                        continue;
                    }
                    if (type == ColumnType.Bool) {
                        propBuffer.putShort(i);
                        propBuffer.put((byte) ((boolean) value ? 1 : 0));
                    } else if (type == ColumnType.Int) {
                        propBuffer.putShort(i);
                        if (value instanceof Long)
                            propBuffer.putInt(((Long) value).intValue());
                        else
                            propBuffer.putInt((int) value);
                    } else if (type == ColumnType.Double) {
                        propBuffer.putShort(i);
                        propBuffer.putDouble((double) value);
                    } else if (type == ColumnType.String ||
                               type == ColumnType.DateTime) {
                        propBuffer.putShort(i);
                        byte[] stringBytes = value.toString().getBytes(StandardCharsets.UTF_8);
                        propBuffer.putInt(stringBytes.length);
                        propBuffer.put(stringBytes);
                    } else {
                        LOGGER.warn("Property {} with unknown type {} skipped in FlatGeobuf output.", column.name, type);
                    }
                }
                done = true;
            } catch (BufferOverflowException ex) {
                // increase properties buffer until it is large enough
                NIOUtilities.returnToCache(propBuffer);
                size *= 2;
                propBuffer = NIOUtilities.allocate(size);
                propBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
        }

        int propertiesOffset = 0;
        if (propBuffer.position() > 0) {
            propBuffer.flip();
            propertiesOffset = Feature.createPropertiesVector(builder, propBuffer);
        }
        NIOUtilities.returnToCache(propBuffer);
        return propertiesOffset;
    }
}
