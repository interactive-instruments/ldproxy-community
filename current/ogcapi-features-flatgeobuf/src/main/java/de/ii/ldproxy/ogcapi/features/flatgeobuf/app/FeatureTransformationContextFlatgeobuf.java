/**
 * Copyright 2021 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.features.flatgeobuf.app;

import de.ii.ldproxy.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
public abstract class FeatureTransformationContextFlatgeobuf implements FeatureTransformationContext {

    public abstract Map<String, FeatureSchema> getProperties();
    public abstract boolean getIs3d();
    public abstract int getMaxMultiplicity();
}
