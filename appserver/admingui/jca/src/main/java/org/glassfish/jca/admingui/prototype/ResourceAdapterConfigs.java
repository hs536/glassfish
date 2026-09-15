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

package org.glassfish.jca.admingui.prototype;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.PropertyRows;

/**
 * The choices and the configuration properties of the resource adapter config pages.
 *
 * <p>
 * Prototype (adr/0008): shared by the new and the edit page.
 */
final class ResourceAdapterConfigs {

    static final String CHILD_TYPE = "resource-adapter-config";
    private static final String JMS_ADAPTER = "jmsra";
    private static final String PROPERTIES_COMMAND = "get-resource-adapter-config-properties";

    private ResourceAdapterConfigs() {
    }

    /**
     * The resource adapters a config can be created for, as the JSFTemplating page lists them: the JMS adapter when the
     * JMS plugin is present, then the connector modules of the deployed applications, a module of an enterprise
     * application without its {@code .rar} extension ({@code filterOutRarExtension}).
     */
    static List<String> adapters(AdminRestService rest) {
        List<String> adapters = new ArrayList<>();
        if (ConnectorModules.jmsExists()) {
            adapters.add(JMS_ADAPTER);
        }
        for (String module : ConnectorModules.names(rest)) {
            adapters.add(module.contains("#") && module.endsWith(".rar") ? module.substring(0, module.length() - ".rar".length()) : module);
        }
        return adapters;
    }

    /** The thread pools that can run the work of an adapter, with an empty choice first. */
    static List<String> threadPools(AdminRestService rest) {
        List<String> pools = new ArrayList<>();
        pools.add("");
        pools.addAll(rest.childNames(rest.url("configs", "config", "server-config", "thread-pools", "thread-pool")));
        return pools;
    }

    /** The configuration properties of a deployed resource adapter, and the names of the confidential ones. */
    static ConfigProperties configProperties(AdminRestService rest, String adapter) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> confidential = new ArrayList<>();
        if (adapter != null && !adapter.isEmpty()) {
            Map<String, Object> response = rest.get(rest.url("resources", PROPERTIES_COMMAND), new LinkedHashMap<>(Map.of("rarName", adapter)));
            Map<String, Object> extraProperties = AdminRestService.extraProperties(response);
            if (extraProperties.get("configProps") instanceof Map<?, ?> properties) {
                properties.forEach((name, value) -> values.put(String.valueOf(name), value == null ? "" : value.toString()));
            }
            if (extraProperties.get("confidentialConfigProps") instanceof List<?> names) {
                names.forEach(name -> confidential.add(String.valueOf(name)));
            }
        }
        return new ConfigProperties(values, confidential);
    }

    /**
     * Marks the confidential properties of the rows, if the resource adapter of the config is deployed. A config can
     * have the name of an adapter that is not deployed, and then the command fails (B-39); the rows are then shown
     * without confidential values.
     */
    static void markConfidential(AdminRestService rest, String adapter, PropertyRows rows) {
        try {
            rows.markConfidential(configProperties(rest, adapter).confidential());
        } catch (RuntimeException e) {
            // The properties of the adapter are unknown, so no row is confidential
        }
    }

    record ConfigProperties(Map<String, String> values, List<String> confidential) {
    }
}
