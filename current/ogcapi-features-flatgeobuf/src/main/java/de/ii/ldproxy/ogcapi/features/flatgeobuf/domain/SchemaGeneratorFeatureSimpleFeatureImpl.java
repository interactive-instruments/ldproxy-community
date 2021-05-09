/**
 * Copyright 2021 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.features.flatgeobuf.domain;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiDataV2;
import de.ii.ldproxy.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ldproxy.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ldproxy.ogcapi.features.core.domain.SchemaGeneratorFeature;
import de.ii.ldproxy.ogcapi.features.core.domain.SchemaInfo;
import de.ii.xtraplatform.features.domain.FeatureProvider2;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.geometries.domain.SimpleFeatureGeometry;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Provides
@Instantiate
public class SchemaGeneratorFeatureSimpleFeatureImpl extends SchemaGeneratorFeature implements SchemaGeneratorFeatureSimpleFeature {

    private final ConcurrentMap<Integer, ConcurrentMap<String, ConcurrentMap<SCHEMA_TYPE, Map<String, FeatureSchema>>>> schemaMap = new ConcurrentHashMap<>();

    private final SchemaInfo schemaInfo;
    private final FeaturesCoreProviders providers;

    public SchemaGeneratorFeatureSimpleFeatureImpl(@Requires SchemaInfo schemaInfo,
                                                   @Requires FeaturesCoreProviders providers) {
        this.schemaInfo = schemaInfo;
        this.providers = providers;
    }

    // TODO currently only for RETURNABLES_FLAT
    @Override
    public Map<String, FeatureSchema> getSchemaSimpleFeature(OgcApiDataV2 apiData, String collectionId, int maxMultiplicity, List<String> propertySubset, SCHEMA_TYPE type) {
        int apiHashCode = apiData.hashCode();
        if (!schemaMap.containsKey(apiHashCode))
            schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
        if (!schemaMap.get(apiHashCode).containsKey(collectionId))
            schemaMap.get(apiHashCode).put(collectionId, new ConcurrentHashMap<>());
        if (!schemaMap.get(apiHashCode).get(collectionId).containsKey(type)) {

            FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections()
                                                                   .get(collectionId);
            String featureTypeId = apiData.getCollections()
                                          .get(collectionId)
                                          .getExtension(FeaturesCoreConfiguration.class)
                                          .map(cfg -> cfg.getFeatureType().orElse(collectionId))
                                          .orElse(collectionId);
            FeatureProvider2 featureProvider = providers.getFeatureProvider(apiData, collectionData);
            FeatureSchema featureType = featureProvider.getData()
                                                       .getTypes()
                                                       .get(featureTypeId);

            Map<String, FeatureSchema> properties = processProperties(featureType, type,true, propertySubset, maxMultiplicity);

            schemaMap.get(apiHashCode)
                     .get(collectionId)
                     .put(type, properties);
        }
        return schemaMap.get(apiHashCode).get(collectionId).get(type);
    }

    private void addProperty(ImmutableMap.Builder<String, FeatureSchema> mapBuilder, ImmutableFeatureSchema.Builder schemaBuilder, String propertyName, int maxMultiplicity) {
        if (Objects.nonNull(propertyName)) {
            if (propertyName.contains("[]")) {
                for (int i=1; i<=maxMultiplicity; i++) {
                    String attName = propertyName.replaceFirst("\\[\\]", "." + i);
                    addProperty(mapBuilder, schemaBuilder, attName, maxMultiplicity);
                }
            } else {
                mapBuilder.put(propertyName, schemaBuilder.name(propertyName)
                                                          .build());
            }
        }
    }

    private Map<String, FeatureSchema> processProperties(FeatureSchema schema, SCHEMA_TYPE type, boolean isFeature, List<String> propertySubset, int maxMultiplicity) {

        ImmutableMap.Builder<String, FeatureSchema> mapBuilder = maxMultiplicity > 1
                ? new ImmutableSortedMap.Builder(Ordering.natural())
                : new ImmutableMap.Builder();
        // maps from the dotted path name to the path name with array brackets
        Map<String,String> propertyNameMap = schemaInfo.getPropertyNames(schema,true).stream()
                                                       .map(name -> new AbstractMap.SimpleImmutableEntry<String,String>(name.replace("[]",""), name))
                                                       .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String,String> nameTitleMap = schemaInfo.getNameTitleMap(schema);

        List<FeatureSchema> properties = schema.getAllNestedProperties();
        final boolean[] geometry = {false};
        properties.stream()
                  .forEachOrdered(property -> {
                      SchemaBase.Type propType = property.getType();
                      String propertyPath = String.join(".", property.getFullPath());
                      String propertyName = property.isObject() ? property.getName() : propertyNameMap.get(propertyPath);
                      String label = nameTitleMap.get(propertyPath);
                      Optional<String> description = property.getDescription();
                      ImmutableFeatureSchema.Builder schemaBuilder = new ImmutableFeatureSchema.Builder()
                              .label(label)
                              .description(description)
                              .constraints(property.getConstraints());
                      switch (propType) {
                          case DATETIME:
                          case BOOLEAN:
                          case INTEGER:
                          case FLOAT:
                          case UNKNOWN:
                          case STRING:
                              schemaBuilder.type(propType);
                              addProperty(mapBuilder, schemaBuilder, propertyName, maxMultiplicity);
                              break;
                          case VALUE_ARRAY:
                              schemaBuilder.type(property.getValueType().orElse(SchemaBase.Type.UNKNOWN));
                              addProperty(mapBuilder, schemaBuilder, propertyName, maxMultiplicity);
                              break;
                          case OBJECT:
                          case OBJECT_ARRAY:
                              // ignore intermediate objects in flattening mode, only process leaf properties
                              break;
                          case GEOMETRY:
                              if (!geometry[0]) {
                                  // only use the first geometry
                                  schemaBuilder.type(SchemaBase.Type.GEOMETRY);
                                  schemaBuilder.geometryType(property.getGeometryType().orElse(SimpleFeatureGeometry.ANY));
                                  addProperty(mapBuilder, schemaBuilder,"geometry", 1);
                                  geometry[0] = true;
                              }
                              break;
                      }
                  });
        return mapBuilder.build();
    }
}
