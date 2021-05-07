/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.graphql;

import de.ii.ldproxy.ogcapi.domain.ImmutableLandingPage;
import de.ii.ldproxy.ogcapi.domain.ImmutableOgcApiLink;
import de.ii.ldproxy.ogcapi.domain.OgcApiApiDataV2;
import de.ii.ldproxy.ogcapi.domain.OgcApiLandingPageExtension;
import de.ii.ldproxy.ogcapi.domain.OgcApiLink;
import de.ii.ldproxy.ogcapi.domain.OgcApiMediaType;
import de.ii.ldproxy.ogcapi.domain.URICustomizer;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.osgi.framework.BundleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * add styles information to the landing page
 *
 */
@Component
@Provides
@Instantiate
public class GraphqlOnLandingPage implements OgcApiLandingPageExtension {

    public GraphqlOnLandingPage(@org.apache.felix.ipojo.annotations.Context BundleContext bundleContext) {

    }

    @Override
    public boolean isEnabledForApi(OgcApiApiDataV2 apiData) {
        return true;//isExtensionEnabled(apiData, StylesConfiguration.class);
    }

    @Override
    public ImmutableLandingPage.Builder process(ImmutableLandingPage.Builder landingPageBuilder,
                                                OgcApiApiDataV2 apiData,
                                                URICustomizer uriCustomizer,
                                                OgcApiMediaType mediaType,
                                                List<OgcApiMediaType> alternateMediaTypes,
                                                Optional<Locale> language) {

        /*if (!isEnabledForApi(apiData)) {
            return landingPageBuilder;
        }*/

        List<OgcApiLink> ogcApiLinks = new ArrayList<>();
        ogcApiLinks.add(new ImmutableOgcApiLink.Builder()
                .href(uriCustomizer.copy()
                        .ensureLastPathSegments("graphql")
                        .removeParameters("f")
                        .ensureNoTrailingSlash()
                        .toString()
                )
                .rel("graphql")
                .title("Graphql clients")
                .build());
        landingPageBuilder.addAllLinks(ogcApiLinks);

        return landingPageBuilder;
    }
}
