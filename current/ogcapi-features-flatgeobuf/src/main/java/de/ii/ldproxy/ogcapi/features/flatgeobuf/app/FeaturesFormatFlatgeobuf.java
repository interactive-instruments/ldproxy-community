/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.features.flatgeobuf.app;

import com.google.common.collect.ImmutableList;
import de.ii.ldproxy.ogcapi.domain.ApiMediaType;
import de.ii.ldproxy.ogcapi.domain.ApiMediaTypeContent;
import de.ii.ldproxy.ogcapi.domain.ConformanceClass;
import de.ii.ldproxy.ogcapi.domain.ExtensionConfiguration;
import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.ImmutableApiMediaType;
import de.ii.ldproxy.ogcapi.domain.ImmutableApiMediaTypeContent;
import de.ii.ldproxy.ogcapi.domain.OgcApiDataV2;
import de.ii.ldproxy.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ldproxy.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ldproxy.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ldproxy.ogcapi.features.core.domain.SchemaGeneratorFeature;
import de.ii.ldproxy.ogcapi.features.flatgeobuf.domain.FlatgeobufConfiguration;
import de.ii.ldproxy.ogcapi.features.flatgeobuf.domain.SchemaGeneratorFeatureSimpleFeature;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTransformer2;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@Provides
@Instantiate
public class FeaturesFormatFlatgeobuf implements ConformanceClass, FeatureFormatExtension {

    public static final ApiMediaType MEDIA_TYPE = new ImmutableApiMediaType.Builder()
            .type(new MediaType("application", "flatgeobuf"))
            .label("FlatGeobuf")
            .parameter("fgb")
            .build();
    public static final ApiMediaType COLLECTION_MEDIA_TYPE = new ImmutableApiMediaType.Builder()
            .type(new MediaType("application", "json"))
            .label("JSON")
            .parameter("json")
            .build();

    private final FeaturesCoreProviders providers;
    private final SchemaGeneratorFeatureSimpleFeature schemaGeneratorFeatureSimpleFeature;

    public FeaturesFormatFlatgeobuf(@Requires FeaturesCoreProviders providers,
                                    @Requires SchemaGeneratorFeatureSimpleFeature schemaGeneratorFeatureSimpleFeature/*,
                                    @Requires SchemaGeneratorFeatureOpenApi schemaGeneratorFeature,
                                    @Requires SchemaGeneratorFeatureCollectionOpenApi schemaGeneratorFeatureCollection*/) {
        this.providers = providers;
        this.schemaGeneratorFeatureSimpleFeature = schemaGeneratorFeatureSimpleFeature;
    }

    @Override
    public List<String> getConformanceClassUris() {
        return ImmutableList.of();
    }

    @Override
    public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
        return FlatgeobufConfiguration.class;
    }

    @Override
    public ApiMediaType getMediaType() {
        return MEDIA_TYPE;
    }

    @Override
    public ApiMediaType getCollectionMediaType() {
        return COLLECTION_MEDIA_TYPE;
    }

    @Override
    public ApiMediaTypeContent getContent(OgcApiDataV2 apiData, String path) {
        // TODO Should we describe the schema used in the binary file? As an OpenAPI schema?
        String schemaRef = "#/components/schemas/anyBinary";
        Schema schema = new BinarySchema();
        String collectionId = path.split("/", 4)[2];
        Optional<FlatgeobufConfiguration> configuration = apiData.getCollections()
                                                                 .get(collectionId)
                                                                 .getExtension(FlatgeobufConfiguration.class);
        return new ImmutableApiMediaTypeContent.Builder()
                .schema(schema)
                .schemaRef(schemaRef)
                .ogcApiMediaType(MEDIA_TYPE)
                .build();
    }

    @Override
    public boolean canTransformFeatures() {
        return true;
    }

    @Override
    public Optional<FeatureTransformer2> getFeatureTransformer(FeatureTransformationContext transformationContext,
                                                               Optional<Locale> language) {

        OgcApiDataV2 apiData = transformationContext.getApiData();
        String collectionId = transformationContext.getCollectionId();
        FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
        int maxMultiplicity = collectionData.getExtension(FlatgeobufConfiguration.class).get().getMaxMultiplicity();
        boolean is3d = false;
        try {
            EpsgCrs crs = transformationContext.getCrsTransformer().isPresent()
                    ? transformationContext.getCrsTransformer().get().getTargetCrs()
                    : providers.getFeatureProvider(apiData).getData().getNativeCrs().orElse(EpsgCrs.of(4326, EpsgCrs.Force.LON_LAT));
            is3d = CRS.decode("EPSG:" + crs.getCode()).getCoordinateSystem().getDimension()==3;
        } catch (Exception e) {
            // use the default
        }
        Map<String, FeatureSchema> properties = schemaGeneratorFeatureSimpleFeature.getSchemaSimpleFeature(apiData, collectionId, maxMultiplicity,
                                                                                                           transformationContext.getFields(),
                                                                                                           SchemaGeneratorFeature.SCHEMA_TYPE.RETURNABLES_FLAT);

        return Optional.of(new FeatureTransformerFlatgeobuf(ImmutableFeatureTransformationContextFlatgeobuf.builder()
                                                                                                           .from(transformationContext)
                                                                                                           .properties(properties)
                                                                                                           .maxMultiplicity(maxMultiplicity)
                                                                                                           .is3d(is3d)
                                                                                                           .build()));
    }

}
