/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.sitemaps;

import de.ii.ldproxy.ogcapi.domain.AbstractOgcApiFeaturesGenericMapping;
import de.ii.xtraplatform.features.domain.FeatureProperty;
import de.ii.xtraplatform.features.domain.FeatureTransformer2;
import de.ii.xtraplatform.features.domain.FeatureType;
import de.ii.xtraplatform.geometries.domain.SimpleFeatureGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * @author zahnen
 */
public class ItemSitesReader implements FeatureTransformer2 {

    private final String baseUrl;
    private List<Site> sites;

    public ItemSitesReader(String baseUrl) {
        this.baseUrl = baseUrl;
        this.sites = new ArrayList<>();
    }

    public List<Site> getSites() {
        return sites;
    }

    @Override
    public String getTargetFormat() {
        return AbstractOgcApiFeaturesGenericMapping.BASE_TYPE;
    }

    @Override
    public void onStart(OptionalLong optionalLong, OptionalLong optionalLong1) throws Exception {

    }

    @Override
    public void onEnd() throws Exception {

    }

    @Override
    public void onFeatureStart(FeatureType featureType) throws Exception {

    }

    @Override
    public void onFeatureEnd() throws Exception {

    }

    @Override
    public void onPropertyStart(FeatureProperty featureProperty, List<Integer> list) throws Exception {

    }

    @Override
    public void onPropertyText(String id) throws Exception {
        this.sites.add(new Site(baseUrl + "/" + id + "?f=html"));
    }

    @Override
    public void onPropertyEnd() throws Exception {

    }

    @Override
    public void onGeometryStart(FeatureProperty featureProperty, SimpleFeatureGeometry simpleFeatureGeometry, Integer integer) throws Exception {

    }

    @Override
    public void onGeometryNestedStart() throws Exception {

    }

    @Override
    public void onGeometryCoordinates(String s) throws Exception {

    }

    @Override
    public void onGeometryNestedEnd() throws Exception {

    }

    @Override
    public void onGeometryEnd() throws Exception {

    }
}
