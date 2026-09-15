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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;

/**
 * The choices of an admin object resource: the resource adapters, the resource types of the chosen adapter, and the
 * class names of the chosen type, with the configuration properties of the adapter and type.
 *
 * <p>
 * Prototype (adr/0008): the Facelets counterpart of the handlers {@code gfr.getApplicationsBySnifferType} and
 * {@code gf.getAdminObjectResourceWizard}, and of the requests to {@code get-admin-object-interface-names},
 * {@code get-admin-object-class-names} and {@code get-admin-object-config-properties} on the JSFTemplating pages.
 */
public class AdminObjectChoices implements Serializable {

    private static final long serialVersionUID = 1L;

    static final String CHILD_TYPE = "admin-object-resource";

    /** The target sent with the resource itself when it is created or deleted, as the JSFTemplating pages send it. */
    static final String RESOURCE_TARGET = "server-config";

    private static final String JMS_ADAPTER = "jmsra";

    private List<String> adapters = List.of();
    private List<String> types = List.of();
    private List<String> classNames = List.of();

    /** The configuration properties of an adapter and type, and the names of the confidential ones. */
    record ConfigProperties(Map<String, String> values, List<String> confidential) {
    }

    /**
     * Loads the resource adapters: the connector modules of the deployed applications ({@code application#module} for
     * a module of an enterprise application), then the JMS adapter when the JMS plugin is present. Without the JMS
     * plugin the list starts with an empty choice. The JMS adapter is chosen when the values have no adapter.
     */
    void loadAdapters(AdminRestService rest, Map<String, Object> values) {
        boolean jms = ConnectorModules.jmsExists();
        List<String> names = new ArrayList<>();
        if (!jms) {
            names.add("");
        }
        names.addAll(ConnectorModules.names(rest));
        if (jms) {
            names.add(JMS_ADAPTER);
            if (text(values.get("resAdapter")).isEmpty()) {
                values.put("resAdapter", JMS_ADAPTER);
            }
        }
        adapters = names;
    }

    /**
     * Loads the resource types of the chosen adapter and the class names of the chosen type. When the values have no
     * resource type or class name, the first one is chosen.
     */
    void update(AdminRestService rest, Map<String, Object> values) {
        String adapter = text(values.get("resAdapter"));
        types = adapter.isEmpty() ? List.of() : names(rest, "get-admin-object-interface-names", Map.of("rarName", adapter), "adminObjectInterfaceNames");
        choose(values, "resType", types);
        String type = text(values.get("resType"));
        classNames = type.isEmpty() ? List.of() : names(rest, "get-admin-object-class-names", query(adapter, type), "adminObjectClassNames");
        choose(values, "className", classNames);
    }

    /** The configuration properties of the chosen adapter and type; none when either is not chosen. */
    ConfigProperties configProperties(AdminRestService rest, Map<String, Object> values) {
        String adapter = text(values.get("resAdapter"));
        String type = text(values.get("resType"));
        Map<String, String> properties = new LinkedHashMap<>();
        List<String> confidential = new ArrayList<>();
        if (adapter.isEmpty() || type.isEmpty()) {
            return new ConfigProperties(properties, confidential);
        }
        Map<String, Object> response = rest.get(rest.url("resources", "get-admin-object-config-properties"), query(adapter, type));
        Map<String, Object> extraProperties = AdminRestService.extraProperties(response);
        if (extraProperties.get("adminObjectConfigProps") instanceof Map<?, ?> map) {
            map.forEach((name, value) -> properties.put(String.valueOf(name), text(value)));
        }
        if (extraProperties.get("confidentialConfigProps") instanceof List<?> list) {
            list.forEach(name -> confidential.add(String.valueOf(name)));
        }
        return new ConfigProperties(properties, confidential);
    }

    public List<String> getAdapters() {
        return adapters;
    }

    public List<String> getTypes() {
        return types;
    }

    public List<String> getClassNames() {
        return classNames;
    }

    private static List<String> names(AdminRestService rest, String command, Map<String, Object> query, String key) {
        List<String> names = new ArrayList<>();
        Map<String, Object> response = rest.get(rest.url("resources", command), new HashMap<>(query));
        if (AdminRestService.extraProperties(response).get(key) instanceof List<?> list) {
            list.forEach(name -> names.add(String.valueOf(name)));
        }
        return names;
    }

    private static Map<String, Object> query(String adapter, String type) {
        return Map.of("rarName", adapter, "adminObjectInterface", type);
    }

    private static void choose(Map<String, Object> values, String key, List<String> choices) {
        if (text(values.get(key)).isEmpty() && !choices.isEmpty()) {
            values.put(key, choices.get(0));
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
