/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.filtertransformer;

import java.util.Map;

/**
 * @author zahnen
 */
public interface FilterTransformer {

    Map<String, String> resolveParameters(Map<String, String> parameters);
}
