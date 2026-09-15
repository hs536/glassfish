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

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.AttributeFlags;
import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * The Advanced tab of a JDBC connection pool (prototype, adr/0008): the statement, connection and connection validation
 * settings, with the save and load defaults actions. The General and Additional Properties tabs are other pages. Pools
 * of an application are not covered (X-27).
 *
 * <p>
 * The Facelets counterpart of {@code jdbc/jdbcConnectionPoolAdvance.jsf}, {@code jdbc/advancePool.inc},
 * {@code jdbc/jdbcConnectionPoolAdvanceButtons.inc} and the scripts {@code jdbc/lazyConnectionJS.inc} and
 * {@code jdbc/jdbcConnectionPoolAdvanceJS.inc}. Where the scripts enabled and disabled fields and filled the lists of
 * table and class names, the page renders the rows again and the names are read on the server.
 */
@Named
@ViewScoped
public class JdbcConnectionPoolAdvanceView implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The attributes of the General tab, which this tab does not send. Unlike the list of the General tab, the name and
     * the deployment order are not in it, so they are sent again, as the JSFTemplating page does.
     */
    private static final List<String> GENERAL_ATTRIBUTES = List.of("resType", "datasourceClassname", "driverClassname", "ping",
            "description", "steadyPoolSize", "maxPoolSize", "poolResizeQuantity", "idleTimeoutInSeconds", "maxWaitTimeInMillis",
            "nonTransactionalConnections", "transactionIsolationLevel", "isIsolationLevelGuaranteed");

    /** The boolean attributes sent as {@code false} when they are not set, as the JSFTemplating page lists them. */
    private static final List<String> CONVERT_TO_FALSE = List.of("wrapJdbcObjects", "pooling", "connectionLeakReclaim",
            "statementLeakReclaim", "lazyConnectionAssociation", "lazyConnectionEnlistment", "associateWithThread", "matchConnections",
            "allowNonComponentCallers", "isConnectionValidationRequired", "failAllConnections");

    private static final List<String> VALIDATION_METHODS = List.of("auto-commit", "meta-data", "custom-validation", "table");

    private static final String DRIVER = "java.sql.Driver";

    static final String DROPDOWN = "dropdown";
    static final String TEXT = "text";

    @Inject
    private AdminRestService rest;

    private String name;
    private boolean found;
    private String className;
    /** The attribute values as read, to tell a field left empty from a value removed. */
    private final Map<String, Object> loaded = new HashMap<>();
    private final Map<String, Object> values = new HashMap<>();
    private final Choice table = new Choice();
    private final Choice validationClass = new Choice();

    @PostConstruct
    protected void load() {
        if (name == null) {
            name = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("name");
        }
        loaded.clear();
        values.clear();
        found = name != null && !name.isEmpty();
        if (!found) {
            return;
        }
        Map<String, Object> attributes = rest.attributes(poolUrl());
        found = !attributes.isEmpty();
        loaded.putAll(attributes);
        values.putAll(attributes);
        GENERAL_ATTRIBUTES.forEach(values::remove);
        className = text(attributes.get(DRIVER.equals(attributes.get("resType")) ? "driverClassname" : "datasourceClassname"));

        table.reset(TEXT);
        table.text = text(values.get("validationTableName"));
        validationClass.reset(DROPDOWN);
        if (isClassNameMethod()) {
            String validationClassname = text(values.get("validationClassname"));
            validationClass.names = validationClassNames();
            if (validationClass.names.contains(validationClassname)) {
                validationClass.selected = validationClassname;
            } else if (validationClass.names.isEmpty() || !validationClassname.isEmpty()) {
                validationClass.option = TEXT;
                validationClass.text = validationClassname;
            }
        }
    }

    /**
     * Saves the attributes. As the JSFTemplating page does, the validation class name, or the connection validation when
     * it is not required, is saved on its own first, and lazy association saves lazy enlistment too.
     */
    public void save() {
        try {
            String url = poolUrl();
            if (isTableMethod()) {
                values.put("validationTableName", table.value());
            }
            if (isClassNameMethod()) {
                values.put("validationClassname", validationClass.value());
                rest.create(url, single("validationClassname"), List.of());
            }
            if (!isValidationRequired()) {
                rest.create(url, single("isConnectionValidationRequired"), CONVERT_TO_FALSE);
            }
            if (isEnlistmentFixed()) {
                values.put("lazyConnectionEnlistment", "true");
            }
            Map<String, Object> attributes = new HashMap<>(values);
            attributes.remove("jndiName");
            // An empty field of an attribute without a value leaves it unchanged and is not sent. Sent in the same request
            // as a change of lazy association, it makes the server log an invalid combination (B-33)
            attributes.entrySet().removeIf(entry -> "".equals(entry.getValue()) && loaded.containsKey(entry.getKey())
                    && loaded.get(entry.getKey()) == null);
            rest.create(url, attributes, CONVERT_TO_FALSE);
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** Replaces the values that have a default with the default. */
    public void loadDefaults() {
        Map<String, String> defaults = rest.defaults(rest.url("resources", JdbcConnectionPoolEditView.CHILD_TYPE));
        for (String key : values.keySet()) {
            String value = defaults.get(key);
            if (value != null) {
                values.put(key, value);
            }
        }
    }

    /** Lists the table names of the database of the pool, once, as the JSFTemplating page does. */
    public void populateTableNames() {
        if (!table.names.isEmpty()) {
            return;
        }
        try {
            table.names = names(rest.get(rest.url("resources", "get-validation-table-names"), Map.of("poolName", name)), "validationTableNames");
            String tableName = text(values.get("validationTableName"));
            if (table.names.contains(tableName)) {
                table.selected = tableName;
            }
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** Lists the validation class names when custom validation is chosen and they are not listed yet. */
    public void validationChanged() {
        if (!isClassNameMethod() || !validationClass.names.isEmpty()) {
            return;
        }
        try {
            validationClass.names = validationClassNames();
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** Lazy association needs lazy enlistment: checking it checks lazy enlistment, which the page then disables. */
    public void associationChanged() {
        if (isEnlistmentFixed()) {
            values.put("lazyConnectionEnlistment", "true");
        }
    }

    public String getName() {
        return name;
    }

    public boolean isFound() {
        return found;
    }

    /** The attribute values, bound by the page. */
    public Map<String, Object> getValues() {
        return values;
    }

    public Map<String, Boolean> getFlags() {
        return new AttributeFlags(values);
    }

    public List<String> getValidationMethods() {
        return VALIDATION_METHODS;
    }

    /** The validation table name. */
    public Choice getTable() {
        return table;
    }

    /** The validation class name. */
    public Choice getValidationClass() {
        return validationClass;
    }

    public boolean isValidationRequired() {
        return "true".equals(String.valueOf(values.get("isConnectionValidationRequired")));
    }

    /** True when the connection is validated with a table: then the table name is used. */
    public boolean isTableMethod() {
        return isValidationRequired() && "table".equals(values.get("connectionValidationMethod"));
    }

    /** True when the connection is validated with a class: then the validation class name is used. */
    public boolean isClassNameMethod() {
        return isValidationRequired() && "custom-validation".equals(values.get("connectionValidationMethod"));
    }

    public boolean isEnlistmentFixed() {
        return "true".equals(String.valueOf(values.get("lazyConnectionAssociation")));
    }

    public String getGeneralPage() {
        return tabPage("/jdbc/jdbcConnectionPoolEdit.jsf");
    }

    public String getPropertiesPage() {
        return tabPage("/jdbc/jdbcConnectionPoolProperty.jsf");
    }

    private String tabPage(String page) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + page + "?name="
                + URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    private List<String> validationClassNames() {
        return names(rest.get(rest.url("resources", "get-validation-class-names"), Map.of("className", className)), "validationClassNames");
    }

    private Map<String, Object> single(String key) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(key, values.get(key));
        return attributes;
    }

    private String poolUrl() {
        return rest.url("resources", JdbcConnectionPoolEditView.CHILD_TYPE, name);
    }

    private static List<String> names(Map<String, Object> response, String key) {
        Object names = AdminRestService.extraProperties(response).get(key);
        return names instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    /** A name chosen from a list read from the server, or typed: the option tells which. */
    public static final class Choice implements Serializable {

        private static final long serialVersionUID = 1L;

        private List<String> names = List.of();
        private String option;
        private String selected;
        private String text;

        void reset(String option) {
            this.names = List.of();
            this.option = option;
            this.selected = null;
            this.text = null;
        }

        String value() {
            return DROPDOWN.equals(option) ? selected : text;
        }

        public List<String> getNames() {
            return names;
        }

        public String getOption() {
            return option;
        }

        public void setOption(String option) {
            this.option = option;
        }

        public String getSelected() {
            return selected;
        }

        public void setSelected(String selected) {
            this.selected = selected;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public boolean isDropdown() {
            return DROPDOWN.equals(option);
        }
    }
}
