/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.nearby;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ldproxy.ogcapi.features.core.api.FeatureTransformationContext;
import de.ii.xtraplatform.geometries.domain.SimpleFeatureGeometry;
import de.ii.xtraplatform.geometries.domain.ImmutableCoordinatesTransformer;
import org.apache.http.NameValuePair;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.io.Writer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author zahnen
 */
public class NearbyQuery {
    private final List<AroundRelationQuery> relations;
    private final boolean resolve;
    private final List<List<Coordinate>> coordinateSequences;
    private final GeometryFactory geometryFactory;
    private final NearbyConfiguration aroundRelationConfiguration;
    private Envelope currentBbox;

    public NearbyQuery(FeatureTransformationContext transformationContext) {
        Optional<NearbyConfiguration> aroundRelationConfiguration = getAroundRelationConfiguration(transformationContext);

        if (aroundRelationConfiguration.isPresent()) {
            this.aroundRelationConfiguration = aroundRelationConfiguration.get();

            List<NameValuePair> queryParams = transformationContext.getOgcApiRequest()
                                                                   .getUriCustomizer()
                                                                   .getQueryParams();
            Map<String, String> query = queryParams.stream()
                                                   .map(nameValuePair -> new AbstractMap.SimpleImmutableEntry<>(nameValuePair.getName()
                                                                                                                             .toLowerCase(), nameValuePair.getValue()))
                                                   .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));


            this.resolve = Boolean.valueOf(query.get("resolve"));


            double factor = transformationContext.getCrsTransformer()
                                                 .isPresent() ? transformationContext.getCrsTransformer()
                                                                                     .get()
                                                                                     .getTargetUnitEquivalentInMeters() : 1;

            ImmutableList.Builder<AroundRelationQuery> builder = ImmutableList.builder();

            List<String> names = splitStringList(query, "relations");
            List<Integer> limits = splitIntegerList(query, "limit");
            List<Integer> offsets = splitIntegerList(query, "offset");

            for (int i = 0; i < names.size(); i++) {
                final String name = names.get(i);

                //match with config
                Optional<NearbyConfiguration.Relation> configuration = aroundRelationConfiguration.get()
                                                                                                           .getRelations()
                                                                                                           .stream()
                                                                                                           .filter(relation -> relation.getId()
                                                                                                                                      .equals(name))
                                                                                                           .findFirst();

                if (configuration.isPresent()) {
                    int limit = limits.size() > i ? limits.get(i) : 5;
                    int offset = offsets.size() > i ? offsets.get(i) : 0;

                    builder.add(new AroundRelationQuery(name, limit, offset, configuration.get(), factor));
                }
            }

            this.relations = builder.build();
        } else {
            this.resolve = false;
            this.relations = ImmutableList.of();
            this.aroundRelationConfiguration = null;
        }
        this.coordinateSequences = new ArrayList<>();
        this.geometryFactory = new GeometryFactory();
    }

    public boolean isActive() {
        return !relations.isEmpty();
    }

    public boolean isReady() {
        return isActive() && Objects.nonNull(currentBbox);
    }

    public boolean isResolve() {
        return resolve;
    }

    public List<AroundRelationQuery> getQueries() {
        return relations;
    }

    public List<NearbyConfiguration.Relation> getRelations() {
        return Objects.nonNull(aroundRelationConfiguration) ? aroundRelationConfiguration.getRelations() : ImmutableList.of();
    }

    public void addCoordinates(String coordinates, ImmutableCoordinatesTransformer.Builder builder) throws IOException {
        ArrayList<Coordinate> coordinateList = new ArrayList<>();
        Writer writer = builder
                .coordinatesWriter(ImmutableCoordinatesWriterJts.of(coordinateList, 2))
                .build();

        writer.write(coordinates);
        writer.close();

        coordinateSequences.add(coordinateList);
    }

    public void computeBbox(SimpleFeatureGeometry geometryType) {
        if (!coordinateSequences.isEmpty()) {
            switch (geometryType) {

                case POINT:
                    break;
                case MULTI_POINT:
                    break;
                case LINE_STRING:
                    break;
                case MULTI_LINE_STRING:
                    break;
                case POLYGON:
                case MULTI_POLYGON:
                    LinearRing ring = geometryFactory.createLinearRing(coordinateSequences.get(0)
                                                                                          .toArray(new Coordinate[0]));
                    //TODO holes are not relevant for bbox, are they?
                    LinearRing holes[] = null; // use LinearRing[] to represent holes
                    Polygon polygon = geometryFactory.createPolygon(ring, holes);
                    this.currentBbox = polygon.getEnvelopeInternal();
                    break;
                case GEOMETRY_COLLECTION:
                    break;
                case NONE:
                    break;
            }

            this.coordinateSequences.clear();
        }
    }

    private List<Integer> splitIntegerList(Map<String, String> query, String key) {
        return query.containsKey(key)
                ? Splitter.on(',')
                          .trimResults()
                          .omitEmptyStrings()
                          .splitToList(query.get(key))
                          .stream()
                          .map(limit -> {
                              try {
                                  return Integer.valueOf(limit);
                              } catch (Throwable e) {
                                  return 0;
                              }
                          })
                          .collect(Collectors.toList())
                : ImmutableList.of();
    }

    private List<String> splitStringList(Map<String, String> query, String key) {
        return query.containsKey(key)
                ? Splitter.on(',')
                          .trimResults()
                          .omitEmptyStrings()
                          .splitToList(query.get(key))
                : ImmutableList.of();
    }

    private Optional<NearbyConfiguration> getAroundRelationConfiguration(FeatureTransformationContext transformationContext) {
        try {
            return transformationContext.getApiData()
                                        .getCollections()
                                        .get(transformationContext.getCollectionId())
                                        .getExtension(NearbyConfiguration.class);
        } catch (Throwable e) {
            return Optional.empty();
        }
    }

    public class AroundRelationQuery {
        final String name;
        final int limit;
        final int offset;
        final NearbyConfiguration.Relation configuration;
        final double expandFactor;

        AroundRelationQuery(String name, int limit, int offset, NearbyConfiguration.Relation relation, double factor) {
            this.name = name;
            this.limit = limit;
            this.offset = offset;
            this.configuration = relation;
            this.expandFactor = factor;
        }

        public NearbyConfiguration.Relation getConfiguration() {
            return configuration;
        }

        public String getBbox() {
            Envelope bbox = currentBbox;

            if (configuration.getBufferInMeters()
                             .isPresent()) {
                double expand = configuration.getBufferInMeters()
                                             .getAsDouble() / expandFactor;

                bbox = new Envelope(currentBbox);
                bbox.expandBy(expand);
            }
            return String.format(Locale.US, "%f,%f,%f,%f", bbox.getMinX(), bbox.getMinY(), bbox.getMaxX(), bbox.getMaxY());
        }
    }
}
