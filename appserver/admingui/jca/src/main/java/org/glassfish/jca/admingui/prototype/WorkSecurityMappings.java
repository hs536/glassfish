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
import java.util.stream.Collectors;

import org.glassfish.admingui.common.util.AdminRestService;

/**
 * The group and principal mappings of a work security map, and the resource adapters it can use.
 *
 * <p>
 * Prototype (adr/0008): the mappings are written as comma separated {@code eis-name=mapped-name} pairs in one field.
 */
final class WorkSecurityMappings {

    static final String CHILD_TYPE = "work-security-map";
    /** The group mappings of a map, with the attributes of one mapping. */
    static final Kind GROUPS = new Kind("group-map", "eisGroup", "mappedGroup", "groupsmap", "addgroups", "removegroups");
    /** The principal mappings of a map, with the attributes of one mapping. */
    static final Kind PRINCIPALS = new Kind("principal-map", "eisPrincipal", "mappedPrincipal", "principalsmap", "addprincipals",
            "removeprincipals");
    private static final String JMS_ADAPTER = "jmsra";

    /** A kind of mapping: its child resource, its attributes and the parameters of the commands. */
    record Kind(String childType, String eisName, String mappedName, String createParameter, String addParameter, String removeParameter) {
    }

    private WorkSecurityMappings() {
    }

    /** The mappings in comma separated text, without the spaces around the names, in the order of the text. */
    static Map<String, String> parse(String text) {
        Map<String, String> mappings = new LinkedHashMap<>();
        if (text != null) {
            for (String pair : text.split(",")) {
                if (pair.isBlank()) {
                    continue;
                }
                int separator = pair.indexOf('=');
                if (separator < 0) {
                    mappings.put(pair.strip(), "");
                } else {
                    mappings.put(pair.substring(0, separator).strip(), pair.substring(separator + 1).strip());
                }
            }
        }
        return mappings;
    }

    static String format(Map<String, String> mappings) {
        return mappings.entrySet().stream().map(mapping -> mapping.getKey() + "=" + mapping.getValue()).collect(Collectors.joining(","));
    }

    /** The mappings of the given kind of a map, sorted by the EIS name. */
    static Map<String, String> read(AdminRestService rest, String mapUrl, Kind kind) {
        Map<String, String> mappings = new LinkedHashMap<>();
        String url = rest.child(mapUrl, kind.childType());
        for (String name : rest.childNames(url)) {
            Object mapped = rest.attributes(rest.child(url, name)).get(kind.mappedName());
            mappings.put(name, mapped == null ? "" : mapped.toString());
        }
        return mappings;
    }

    /**
     * The resource adapters a work security map can use, as the JSFTemplating pages list them: the connector modules of
     * the deployed applications, then the JMS adapter when the JMS plugin is present.
     */
    static List<String> adapters(AdminRestService rest) {
        List<String> adapters = new ArrayList<>(ConnectorModules.names(rest));
        if (ConnectorModules.jmsExists()) {
            adapters.add(JMS_ADAPTER);
        }
        return adapters;
    }
}
