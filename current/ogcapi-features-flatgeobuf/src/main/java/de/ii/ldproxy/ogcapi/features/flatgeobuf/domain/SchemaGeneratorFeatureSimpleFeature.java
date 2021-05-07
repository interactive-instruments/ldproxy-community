package de.ii.ldproxy.ogcapi.features.flatgeobuf.domain;

import de.ii.ldproxy.ogcapi.domain.OgcApiDataV2;
import de.ii.ldproxy.ogcapi.features.core.domain.SchemaGeneratorFeature;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.util.List;
import java.util.Map;

public interface SchemaGeneratorFeatureSimpleFeature {

    Map<String, FeatureSchema> getSchemaSimpleFeature(OgcApiDataV2 apiData,
                                                      String collectionId,
                                                      int maxMultiplicity,
                                                      List<String> propertySubset,
                                                      SchemaGeneratorFeature.SCHEMA_TYPE type);

}
