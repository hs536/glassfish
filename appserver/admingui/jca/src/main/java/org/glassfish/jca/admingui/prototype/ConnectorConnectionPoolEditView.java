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

import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.AttributeFlags;
import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * The General tab of a connector connection pool (prototype, adr/0008): the resource adapter and connection
 * definition, the pool settings and the connection validation, with the save, load defaults, flush and ping actions.
 * The Advanced, Additional Properties and Security Maps tabs are other pages. Pools of an application are not covered
 * (X-27).
 *
 * <p>
 * The Facelets counterpart of {@code jca/connectorConnectionPoolEdit.jsf} and
 * {@code jca/connectorConnectionPoolAttrEdit.inc}.
 */
@Named
@ViewScoped
public class ConnectorConnectionPoolEditView implements Serializable {

    private static final long serialVersionUID = 1L;

    static final String CHILD_TYPE = "connector-connection-pool";

    /** The attributes of the General tab; the other attributes are on the Advanced tab. */
    private static final List<String> GENERAL_ATTRIBUTES = List.of("name", "resourceAdapterName", "connectionDefinitionName", "ping",
            "deploymentOrder", "description", "steadyPoolSize", "maxPoolSize", "poolResizeQuantity", "idleTimeoutInSeconds",
            "maxWaitTimeInMillis", "isConnectionValidationRequired", "failAllConnections", "transactionSupport");

    /**
     * The boolean attributes sent as {@code false} when they are not set, as the JSFTemplating page lists them
     * ({@code ConnectionLeakReclaim} with its upper case letter).
     */
    private static final List<String> CONVERT_TO_FALSE = List.of("ping", "isConnectionValidationRequired", "failAllConnections",
            "associateWithThread", "ConnectionLeakReclaim", "lazyConnectionAssociation", "lazyConnectionEnlistment", "matchConnections");

    private static final List<String> TRANSACTION_SUPPORT = List.of("", "XATransaction", "LocalTransaction", "NoTransaction");

    @Inject
    private AdminRestService rest;

    private String name;
    private boolean found;
    private final Map<String, Object> values = new HashMap<>();
    private List<String> adapters = List.of();
    private List<String> connectionDefinitions = List.of();

    @PostConstruct
    protected void load() {
        if (name == null) {
            name = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("name");
        }
        values.clear();
        found = name != null && !name.isEmpty();
        if (!found) {
            return;
        }
        Map<String, Object> attributes = rest.attributes(poolUrl());
        found = !attributes.isEmpty();
        for (String attribute : GENERAL_ATTRIBUTES) {
            if (attributes.containsKey(attribute)) {
                values.put(attribute, attributes.get(attribute));
            }
        }
        adapters = ConnectorModules.poolAdapters(rest);
        connectionDefinitions = connectionDefinitionNames(text(values.get("resourceAdapterName")));
    }

    /**
     * Saves the general attributes. When Ping is chosen, the pool is pinged after it is saved, and the result of the ping
     * is shown instead of the saved message, as on the JSFTemplating page.
     */
    public void save() {
        try {
            Map<String, Object> attributes = new HashMap<>(values);
            // An empty field or choice is sent as an empty value (X-29)
            attributes.replaceAll((key, value) -> value == null ? "" : value);
            rest.create(poolUrl(), attributes, CONVERT_TO_FALSE);
            if ("true".equals(String.valueOf(values.get("ping")))) {
                pingAfterSave();
            } else {
                ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
            }
            load();
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    private void pingAfterSave() {
        try {
            rest.get(rest.url("resources", "ping-connection-pool"), Map.of("id", name));
            ConsoleMessages.info(ConsoleMessages.core("msg.PingSucceed"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(JcaStrings.get("msg.warning.poolSavedPingFailed"));
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** Replaces the values that have a default with the default. */
    public void loadDefaults() {
        Map<String, String> defaults = rest.defaults(rest.url("resources", CHILD_TYPE));
        for (String key : values.keySet()) {
            String value = defaults.get(key);
            if (value != null) {
                values.put(key, value);
            }
        }
    }

    /** Choosing another resource adapter lists its connection definitions; the first one is chosen when the current one is not among them. */
    public void adapterChanged() {
        try {
            connectionDefinitions = connectionDefinitionNames(text(values.get("resourceAdapterName")));
            if (!connectionDefinitions.contains(text(values.get("connectionDefinitionName")))) {
                values.put("connectionDefinitionName", connectionDefinitions.isEmpty() ? null : connectionDefinitions.get(0));
            }
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** Flushes the pool. A failed command is shown with the message of the server. */
    public void flush() {
        try {
            rest.post(rest.url("resources", "flush-connection-pool"), Map.of("id", name));
            ConsoleMessages.info(ConsoleMessages.core("msg.FlushSucceed"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** Pings the pool. A failed command is shown with the message of the server. */
    public void ping() {
        try {
            rest.get(rest.url("resources", "ping-connection-pool"), Map.of("id", name));
            ConsoleMessages.info(ConsoleMessages.core("msg.PingSucceed"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    public String getName() {
        return name;
    }

    public boolean isFound() {
        return found;
    }

    /** The general attribute values, bound by the page. */
    public Map<String, Object> getValues() {
        return values;
    }

    public Map<String, Boolean> getFlags() {
        return new AttributeFlags(values);
    }

    public List<String> getAdapters() {
        return adapters;
    }

    public List<String> getConnectionDefinitions() {
        return connectionDefinitions;
    }

    public List<String> getTransactionSupports() {
        return TRANSACTION_SUPPORT;
    }

    public String getAdvancedPage() {
        return tabPage("/jca/connectorConnectionPoolAdvance.jsf");
    }

    public String getPropertiesPage() {
        return tabPage("/jca/connectorConnectionPoolProperty.jsf");
    }

    public String getSecurityMapsPage() {
        return tabPage("/jca/connectorSecurityMaps.jsf");
    }

    private List<String> connectionDefinitionNames(String adapter) {
        if (adapter.isEmpty()) {
            return List.of();
        }
        return names(rest.get(rest.url("resources", "get-connection-definition-names"), Map.of("rarName", adapter)), "defnNames");
    }

    private String tabPage(String page) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + page + "?name="
                + URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    private String poolUrl() {
        return rest.url("resources", CHILD_TYPE, name);
    }

    private static List<String> names(Map<String, Object> response, String key) {
        Object names = AdminRestService.extraProperties(response).get(key);
        return names instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
