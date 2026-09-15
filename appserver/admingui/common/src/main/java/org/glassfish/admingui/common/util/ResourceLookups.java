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
package org.glassfish.admingui.common.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lookups shared by the resource pages.
 *
 * <p>
 * Prototype (adr/0008).
 */
final class ResourceLookups {

    private ResourceLookups() {
    }

    /**
     * The logical JNDI names by resource name, from a list command such as {@code list-jdbc-resources}; empty when the
     * resource type has no such command.
     */
    static Map<String, String> logicalJndiNames(AdminRestService rest, String command, String key) {
        Map<String, String> names = new LinkedHashMap<>();
        if (command == null || key == null) {
            return names;
        }
        Map<String, Object> response = rest.get(rest.url("resources", command), null);
        if (AdminRestService.extraProperties(response).get(key) instanceof List<?> list) {
            for (Object resource : list) {
                if (resource instanceof Map<?, ?> map && map.get("name") != null && map.get("logical-jndi-name") != null) {
                    names.put(map.get("name").toString(), map.get("logical-jndi-name").toString());
                }
            }
        }
        return names;
    }

}
