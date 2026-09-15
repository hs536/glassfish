/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.full.admingui.prototype;

import java.util.Map;
import java.util.TreeMap;

import org.glassfish.admingui.common.util.AdminRestService;

/**
 * What the custom resource pages share (prototype, adr/0008).
 */
final class CustomResourcePages {

    static final String CHILD_TYPE = "custom-resource";

    private CustomResourcePages() {
    }

    /** The built-in resource types and their factory classes, as the admin REST command returns them. */
    static Map<String, String> builtInTypes(AdminRestService rest) {
        Map<String, Object> response = rest.get(rest.url("resources", "get-built-in-custom-resources"), Map.of());
        Map<String, String> types = new TreeMap<>();
        if (AdminRestService.extraProperties(response).get("builtInCustomResources") instanceof Map<?, ?> builtIn) {
            builtIn.forEach((type, factoryClass) -> types.put(String.valueOf(type), String.valueOf(factoryClass)));
        }
        return types;
    }

    static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
