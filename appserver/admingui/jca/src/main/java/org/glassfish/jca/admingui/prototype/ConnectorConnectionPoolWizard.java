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

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.AttributeFlags;
import org.glassfish.admingui.common.util.PropertyRows;

/**
 * The state of the new connector connection pool wizard (prototype, adr/0008, X-34): the choices of the first page,
 * and the attributes and properties of the second page, kept for the session as the JSFTemplating wizard keeps them in
 * the session attributes {@code wizardMap}, {@code wizardPoolExtra} and {@code wizardPoolProperties}.
 *
 * <p>
 * The Facelets counterpart of the handlers {@code gf.getConnectorConnectionPoolWizard},
 * {@code gf.updateConnectorConnectionPoolWizard} and {@code updateConnectorConnectionPoolWizardStep2}.
 */
@Named
@SessionScoped
public class ConnectorConnectionPoolWizard implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final List<String> TRANSACTION_SUPPORT = List.of("", "XATransaction", "LocalTransaction", "NoTransaction");

    /** The boolean attributes sent as {@code false} when they are not set, as the JSFTemplating wizard lists them. */
    private static final List<String> CONVERT_TO_FALSE = List.of("ping", "failAllConnections", "isConnectionValidationRequired");

    @Inject
    private AdminRestService rest;

    private String name;
    private String adapter;
    private String definition;
    private List<String> adapters = List.of();
    private List<String> definitions = List.of();
    /** The adapter and connection definition that the properties were read for. */
    private String listedFor;
    private final Map<String, Object> values = new HashMap<>();
    /** The names of the attributes that have a default value. */
    private final Set<String> defaultNames = new HashSet<>();
    private PropertyRows properties = new PropertyRows();

    /** Starts a new wizard with the default attribute values of a pool and the resource adapters. */
    public void begin() {
        end();
        Map<String, String> defaults = rest.defaults(rest.url("resources", ConnectorConnectionPoolEditView.CHILD_TYPE));
        defaultNames.addAll(defaults.keySet());
        values.putAll(defaults);
        List<String> names = new ArrayList<>();
        names.add("");
        names.addAll(ConnectorModules.poolAdapters(rest));
        adapters = names;
    }

    /** Forgets the values of the wizard. */
    public void end() {
        name = null;
        adapter = null;
        definition = null;
        adapters = List.of();
        definitions = List.of();
        listedFor = null;
        values.clear();
        defaultNames.clear();
        properties = new PropertyRows();
    }

    /** Lists the connection definitions of the chosen adapter; the first one is chosen when the current one is not among them. */
    public void listDefinitions() {
        definitions = isEmpty(adapter) ? List.of()
                : names(AdminRestService.extraProperties(rest.get(rest.url("resources", "get-connection-definition-names"), Map.of("rarName", adapter)))
                        .get("defnNames"));
        if (!definitions.contains(definition)) {
            definition = definitions.isEmpty() ? null : definitions.get(0);
        }
    }

    /**
     * Reads the configuration properties of the adapter and connection definition, with the confidential ones marked,
     * unless they were read for the same choice: the values entered on the second page are kept when the choice does not
     * change.
     */
    public void listProperties() {
        if (isEmpty(adapter) || isEmpty(definition)) {
            return;
        }
        String choice = adapter + "|" + definition;
        if (choice.equals(listedFor)) {
            return;
        }
        Map<String, Object> extraProperties = AdminRestService.extraProperties(rest.get(rest.url("resources", "get-mcf-config-properties"),
                Map.of("rarname", adapter, "connectionDefnName", definition)));
        Map<String, String> defaults = new LinkedHashMap<>();
        if (extraProperties.get("mcfConfigProps") instanceof Map<?, ?> map) {
            map.forEach((key, value) -> defaults.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
        }
        PropertyRows rows = new PropertyRows();
        rows.replace(defaults);
        rows.markConfidential(names(extraProperties.get("confidentialConfigProps")));
        properties = rows;
        listedFor = choice;
    }

    /**
     * Creates the pool, then its properties. The two values of each confidential property are compared first, so that
     * nothing is created when they differ (B-35). The pool is not pinged when it is created; see {@link #ping()}.
     *
     * @throws IllegalArgumentException when the two values of a confidential property differ
     */
    public void create() {
        List<Map<String, String>> propertiesToSend = properties.toSend();
        Map<String, Object> attributes = new HashMap<>(values);
        // A field left empty for an attribute without a default value (the description, the transaction support) is not
        // sent, as the JSFTemplating wizard does not send it
        attributes.entrySet().removeIf(entry -> "".equals(entry.getValue()) && !defaultNames.contains(entry.getKey()));
        attributes.put("name", name);
        attributes.put("resourceAdapterName", adapter);
        attributes.put("connectionDefinitionName", definition);
        attributes.put("ping", "false");
        rest.create(rest.url("resources", ConnectorConnectionPoolEditView.CHILD_TYPE), attributes, CONVERT_TO_FALSE);
        rest.postJson(rest.url("resources", ConnectorConnectionPoolEditView.CHILD_TYPE, name) + "/property.json", propertiesToSend);
    }

    /**
     * Pings the created pool.
     *
     * @throws RuntimeException with the message of the server when the ping fails
     */
    public void ping() {
        rest.get(rest.url("resources", "ping-connection-pool"), Map.of("id", name));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAdapter() {
        return adapter;
    }

    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public List<String> getAdapters() {
        return adapters;
    }

    public List<String> getDefinitions() {
        return definitions;
    }

    public List<String> getTransactionSupports() {
        return TRANSACTION_SUPPORT;
    }

    /** The attribute values of the second page, bound by the page. */
    public Map<String, Object> getValues() {
        return values;
    }

    public Map<String, Boolean> getFlags() {
        return new AttributeFlags(values);
    }

    public boolean isPing() {
        return "true".equals(String.valueOf(values.get("ping")));
    }

    public PropertyRows getProperties() {
        return properties;
    }

    private static List<String> names(Object names) {
        return names instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
