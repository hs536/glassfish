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

package org.glassfish.jdbc.admingui.prototype;

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
import java.util.ResourceBundle;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.AttributeFlags;
import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * The General tab of a JDBC connection pool (prototype, adr/0008): the general attributes, the pool settings and the
 * transaction settings, with the save, load defaults, flush and ping actions. The Advanced and Additional Properties
 * tabs are other pages. Pools of an application are not covered (X-27).
 *
 * <p>
 * The Facelets counterpart of {@code jdbc/jdbcConnectionPoolEdit.jsf}, {@code jdbc/poolPropertyEdit.inc} and
 * {@code jdbc/jdbcConnectionPoolEditButtons.inc}.
 */
@Named
@ViewScoped
public class JdbcConnectionPoolEditView implements Serializable {

    private static final long serialVersionUID = 1L;

    static final String CHILD_TYPE = "jdbc-connection-pool";

    /** The attributes of the General tab; the other attributes are on the Advanced tab. */
    private static final List<String> GENERAL_ATTRIBUTES = List.of("name", "resType", "datasourceClassname", "driverClassname", "ping",
            "description", "deploymentOrder", "steadyPoolSize", "maxPoolSize", "poolResizeQuantity", "idleTimeoutInSeconds",
            "maxWaitTimeInMillis", "nonTransactionalConnections", "transactionIsolationLevel", "isIsolationLevelGuaranteed");

    /** The boolean attributes sent as {@code false} when they are not set, as the JSFTemplating page lists them. */
    private static final List<String> CONVERT_TO_FALSE = List.of("ping", "isConnectionValidationRequired", "failAllConnections",
            "allowNonComponentCallers", "nonTransactionalConnections", "isIsolationLevelGuaranteed");

    private static final List<String> RESOURCE_TYPES = List.of("", "javax.sql.DataSource", "javax.sql.XADataSource",
            "javax.sql.ConnectionPoolDataSource", "java.sql.Driver");

    private static final List<String> ISOLATION_LEVELS = List.of("", "read-uncommitted", "read-committed", "repeatable-read", "serializable");

    private static final String DRIVER = "java.sql.Driver";

    private static final String STRINGS = "org.glassfish.jdbc.admingui.Strings";

    @Inject
    private AdminRestService rest;

    private String name;
    private boolean found;
    private final Map<String, Object> values = new HashMap<>();

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
    }

    /** Saves the general attributes, and pings the pool when Ping is set, as the save button of the JSFTemplating page does. */
    public void save() {
        String className = text(values.get(isDriver() ? "driverClassname" : "datasourceClassname"));
        if (className.isEmpty()) {
            ConsoleMessages.error(jdbcMessage("msg.Error.classNameCannotBeEmpty"));
            return;
        }
        try {
            Map<String, Object> attributes = new HashMap<>(values);
            // The disabled class name field is not submitted: without a value it is not sent, as on the JSFTemplating page
            String disabledClassName = isDriver() ? "datasourceClassname" : "driverClassname";
            if (attributes.get(disabledClassName) == null) {
                attributes.remove(disabledClassName);
            }
            // An empty field or choice is sent as an empty value (X-29)
            attributes.replaceAll((key, value) -> value == null ? "" : value);
            rest.create(poolUrl(), attributes, CONVERT_TO_FALSE);
            if ("true".equals(values.get("ping"))) {
                rest.get(rest.url("resources", "ping-connection-pool.json"), Map.of("id", name));
            }
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
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

    /** Flushes the pool. A failed command is shown with the message of the server. */
    public void flush() {
        try {
            report(rest.post(rest.url("resources", "flush-connection-pool.json"), Map.of("id", name)), "msg.FlushSucceed");
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /**
     * Pings the pool. As the JSFTemplating page does, the properties of the pool are sent again first, so that the ping
     * uses them.
     */
    public void ping() {
        try {
            Object properties = AdminRestService.extraProperties(rest.get(poolUrl() + "/property.json", Map.of())).get("properties");
            rest.postJson(poolUrl() + "/property.json", properties == null ? List.of() : properties);
            report(rest.get(rest.url("resources", "ping-connection-pool.json"), Map.of("id", name)), "msg.PingSucceed");
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

    /** True when the resource type is {@code java.sql.Driver}: then the driver classname is used, else the datasource classname. */
    public boolean isDriver() {
        return DRIVER.equals(values.get("resType"));
    }

    public List<String> getResourceTypes() {
        return RESOURCE_TYPES;
    }

    public List<String> getIsolationLevels() {
        return ISOLATION_LEVELS;
    }

    public String getAdvancedPage() {
        return tabPage("/jdbc/jdbcConnectionPoolAdvance.jsf");
    }

    public String getPropertiesPage() {
        return tabPage("/jdbc/jdbcConnectionPoolProperty.jsf");
    }

    private String tabPage(String page) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + page + "?name="
                + URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    private String poolUrl() {
        return rest.url("resources", CHILD_TYPE, name);
    }

    private static void report(Map<String, Object> response, String successKey) {
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map && "SUCCESS".equals(String.valueOf(map.get("exit_code")))) {
            ConsoleMessages.info(ConsoleMessages.core(successKey));
        } else if (data instanceof Map<?, ?> map) {
            ConsoleMessages.error(String.valueOf(map.get("message")));
        }
    }

    private static String jdbcMessage(String key) {
        FacesContext context = FacesContext.getCurrentInstance();
        return ResourceBundle.getBundle(STRINGS, context.getViewRoot().getLocale(), JdbcConnectionPoolEditView.class.getClassLoader()).getString(key);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
