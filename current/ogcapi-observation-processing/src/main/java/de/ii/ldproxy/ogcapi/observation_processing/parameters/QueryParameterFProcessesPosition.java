/**
 * Copyright 2021 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.observation_processing.parameters;

import de.ii.ldproxy.ogcapi.common.domain.QueryParameterF;
import de.ii.ldproxy.ogcapi.domain.*;
import de.ii.ldproxy.ogcapi.features.core.domain.processing.FeatureProcessInfo;
import de.ii.ldproxy.ogcapi.observation_processing.api.DapaResultFormatExtension;
import de.ii.ldproxy.ogcapi.observation_processing.api.ObservationProcess;
import de.ii.ldproxy.ogcapi.observation_processing.application.ObservationProcessingConfiguration;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static de.ii.ldproxy.ogcapi.observation_processing.api.DapaResultFormatExtension.DAPA_PATH_ELEMENT;

@Component
@Provides
@Instantiate
public class QueryParameterFProcessesPosition extends QueryParameterF {

    private final FeatureProcessInfo featureProcessInfo;

    protected QueryParameterFProcessesPosition(@Requires ExtensionRegistry extensionRegistry,
                                               @Requires FeatureProcessInfo featureProcessInfo) {
        super(extensionRegistry);
        this.featureProcessInfo = featureProcessInfo;
    }

    @Override
    public String getId() {
        return "fProcesses-position";
    }

    @Override
    protected boolean isApplicable(OgcApiDataV2 apiData, String definitionPath) {
        return featureProcessInfo.matches(apiData, ObservationProcess.class, definitionPath,"position");
    }

    @Override
    protected Class<? extends FormatExtension> getFormatClass() {
        return DapaResultFormatExtension.class;
    }

    @Override
    public boolean isEnabledForApi(OgcApiDataV2 apiData) {
        return super.isEnabledForApi(apiData) ||
                apiData.getCollections()
                        .values()
                        .stream()
                        .filter(FeatureTypeConfigurationOgcApi::getEnabled)
                        .anyMatch(featureType -> isEnabledForApi(apiData, featureType.getId()));
}

    @Override
    public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
        return ObservationProcessingConfiguration.class;
    }

    @Override
    public Schema getSchema(OgcApiDataV2 apiData) {
        int apiHashCode = apiData.hashCode();
        if (!schemaMap.containsKey(apiHashCode))
            schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
        if (!schemaMap.get(apiHashCode).containsKey("*")) {
            List<String> fEnum = new ArrayList<>();
            extensionRegistry.getExtensionsForType(getFormatClass())
                    .stream()
                    .filter(f -> f.isEnabledForApi(apiData))
                    .filter(f -> !f.getMediaType().parameter().equals("*"))
                    .filter(f -> f.getContent(apiData, "/collections/{collectionId}/"+DAPA_PATH_ELEMENT+"/position")!=null)
                    .forEach(f -> fEnum.add(f.getMediaType().parameter()));
            schemaMap.get(apiHashCode).put("*", new StringSchema()._enum(fEnum));
        }
        return schemaMap.get(apiHashCode).get("*");
    }

    @Override
    public Schema getSchema(OgcApiDataV2 apiData, String collectionId) {
        int apiHashCode = apiData.hashCode();
        if (!schemaMap.containsKey(apiHashCode))
            schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
        if (!schemaMap.get(apiHashCode).containsKey(collectionId)) {
            List<String> fEnum = new ArrayList<>();
            extensionRegistry.getExtensionsForType(getFormatClass())
                    .stream()
                    .filter(f -> f.isEnabledForApi(apiData, collectionId))
                    .filter(f -> !f.getMediaType().parameter().equals("*"))
                    .filter(f -> f.getContent(apiData, "/collections/"+collectionId+"/"+DAPA_PATH_ELEMENT+"/position")!=null)
                    .forEach(f -> fEnum.add(f.getMediaType().parameter()));
            schemaMap.get(apiHashCode).put(collectionId, new StringSchema()._enum(fEnum));
        }
        return schemaMap.get(apiHashCode).get(collectionId);
    }
}
