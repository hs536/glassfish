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
 * The state of the new JDBC connection pool wizard (prototype, adr/0008): the choices of the first page, and the
 * attributes and properties of the second page. The two pages are separate views, so the state is kept for the
 * session, as the JSFTemplating wizard keeps it in the session attributes {@code wizardMap}, {@code wizardPoolExtra}
 * and {@code wizardPoolProperties}.
 *
 * <p>
 * The Facelets counterpart of the wizard handlers of {@code JdbcTempHandler}: {@code setJDBCPoolWizard},
 * {@code gf.updateJDBCPoolWizardStep1}, {@code gf.updateJdbcConnectionPoolPropertiesTable} and
 * {@code updateJdbcConnectionPoolWizardStep2}.
 */
@Named
@SessionScoped
public class JdbcConnectionPoolWizard implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final List<String> RESOURCE_TYPES = List.of("", "javax.sql.DataSource", "javax.sql.XADataSource",
            "javax.sql.ConnectionPoolDataSource", "java.sql.Driver");

    private static final List<String> ISOLATION_LEVELS = List.of("", "read-uncommitted", "read-committed", "repeatable-read", "serializable");

    /** The boolean attributes sent as {@code false} when they are not set, as the JSFTemplating wizard lists them. */
    private static final List<String> CONVERT_TO_FALSE = List.of("ping", "isConnectionValidationRequired", "failAllConnections",
            "allowNonComponentCallers", "nonTransactionalConnections", "isIsolationLevelGuaranteed");

    private static final String DRIVER = "java.sql.Driver";

    @Inject
    private AdminRestService rest;

    private String name;
    private String resType;
    private String vendor;
    private String vendorText;
    private boolean introspect;
    private List<String> vendors = List.of();
    /** The resource type, vendor and introspection that the class names were read for. */
    private String listedFor;

    private boolean dataSource = true;
    private List<String> classNames = List.of();
    private String datasourceClassname;
    private String datasourceClassnameText;
    private String driverClassname;
    private String driverClassnameText;
    private final Map<String, Object> values = new HashMap<>();
    /** The names of the attributes that have a default value. */
    private final Set<String> defaultNames = new HashSet<>();
    private PropertyRows properties = new PropertyRows();

    /** Starts a new wizard with the default attribute values of a pool and the database vendors. */
    public void begin() {
        end();
        Map<String, String> defaults = rest.defaults(rest.url("resources", JdbcConnectionPoolEditView.CHILD_TYPE));
        defaultNames.addAll(defaults.keySet());
        values.putAll(defaults);
        List<String> names = new ArrayList<>();
        names.add("");
        names.addAll(names(rest.get(rest.url("resources", "get-database-vendor-names"), null), "vendorNames"));
        vendors = names;
    }

    /** Forgets the values of the wizard. */
    public void end() {
        name = null;
        resType = null;
        vendor = null;
        vendorText = null;
        introspect = false;
        vendors = List.of();
        listedFor = null;
        dataSource = true;
        classNames = List.of();
        datasourceClassname = null;
        datasourceClassnameText = null;
        driverClassname = null;
        driverClassnameText = null;
        values.clear();
        defaultNames.clear();
        properties = new PropertyRows();
    }

    /**
     * Reads the class names for the resource type and the vendor, and the properties of the first class, unless they
     * were read for the same choice. Without a resource type or a vendor, the class name is typed on the second page.
     */
    public void listClasses() {
        String chosenVendor = getChosenVendor();
        String choice = resType + "|" + chosenVendor + "|" + introspect;
        if (choice.equals(listedFor)) {
            return;
        }
        dataSource = !DRIVER.equals(resType);
        if (!isEmpty(resType) && !isEmpty(chosenVendor)) {
            classNames = names(rest.get(rest.url("resources", "get-jdbc-driver-class-names"),
                    Map.of("dbVendor", chosenVendor, "restype", resType, "introspect", String.valueOf(introspect))), "driverClassNames");
            String first = classNames.isEmpty() ? "" : classNames.get(0);
            if (dataSource) {
                datasourceClassname = first;
                driverClassnameText = "";
            } else {
                driverClassname = first;
                datasourceClassnameText = "";
            }
            properties = connectionDefinitionProperties(first);
        } else {
            datasourceClassnameText = "";
        }
        listedFor = choice;
    }

    /** Reads the properties of the chosen data source class, replacing the rows. */
    public void listProperties() {
        properties = connectionDefinitionProperties(datasourceClassname);
    }

    /**
     * Creates the pool, then its properties. Class names typed on the second page take precedence over the chosen ones.
     * The pool is not pinged when it is created; see {@link #ping()}.
     *
     * @throws IllegalArgumentException when no class name is given
     */
    public void create() {
        String datasource;
        String driver;
        if (!isEmpty(datasourceClassnameText) || !isEmpty(driverClassnameText)) {
            datasource = datasourceClassnameText;
            driver = driverClassnameText;
        } else if (!isEmpty(datasourceClassname) || !isEmpty(driverClassname)) {
            datasource = datasourceClassname;
            driver = driverClassname;
        } else {
            throw new IllegalArgumentException(JdbcStrings.get("msg.Error.classNameCannotBeEmpty"));
        }
        List<Map<String, String>> propertiesToSend = properties.toSend();
        Map<String, Object> attributes = new HashMap<>(values);
        // A field left empty for an attribute without a default value (the description, the isolation level) is not
        // sent, as the JSFTemplating wizard does not send it
        attributes.entrySet().removeIf(entry -> "".equals(entry.getValue()) && !defaultNames.contains(entry.getKey()));
        attributes.put("name", name);
        attributes.put("resType", resType);
        attributes.put("datasourceClassname", datasource);
        attributes.put("driverClassname", driver);
        attributes.put("ping", "false");
        rest.create(rest.url("resources", JdbcConnectionPoolEditView.CHILD_TYPE), attributes, CONVERT_TO_FALSE);
        rest.postJson(rest.url("resources", JdbcConnectionPoolEditView.CHILD_TYPE, name) + "/property.json", propertiesToSend);
    }

    /**
     * Pings the created pool.
     *
     * @throws RuntimeException with the message of the server when the ping fails
     */
    public void ping() {
        rest.get(rest.url("resources", "ping-connection-pool.json"), Map.of("id", name));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResType() {
        return resType;
    }

    public void setResType(String resType) {
        this.resType = resType;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getVendorText() {
        return vendorText;
    }

    public void setVendorText(String vendorText) {
        this.vendorText = vendorText;
    }

    /** The vendor the class names are read for: the typed vendor, or else the chosen one. */
    public String getChosenVendor() {
        return isEmpty(vendorText) ? vendor : vendorText;
    }

    public boolean isIntrospect() {
        return introspect;
    }

    public void setIntrospect(boolean introspect) {
        this.introspect = introspect;
    }

    public List<String> getVendors() {
        return vendors;
    }

    public List<String> getResourceTypes() {
        return RESOURCE_TYPES;
    }

    public List<String> getIsolationLevels() {
        return ISOLATION_LEVELS;
    }

    /** True unless the resource type is {@code java.sql.Driver}: then the data source class name is used. */
    public boolean isDataSource() {
        return dataSource;
    }

    public List<String> getDataSourceClassNames() {
        return dataSource ? classNames : List.of();
    }

    public List<String> getDriverClassNames() {
        return dataSource ? List.of() : classNames;
    }

    public String getDatasourceClassname() {
        return datasourceClassname;
    }

    public void setDatasourceClassname(String datasourceClassname) {
        this.datasourceClassname = datasourceClassname;
    }

    public String getDatasourceClassnameText() {
        return datasourceClassnameText;
    }

    public void setDatasourceClassnameText(String datasourceClassnameText) {
        this.datasourceClassnameText = datasourceClassnameText;
    }

    public String getDriverClassname() {
        return driverClassname;
    }

    public void setDriverClassname(String driverClassname) {
        this.driverClassname = driverClassname;
    }

    public String getDriverClassnameText() {
        return driverClassnameText;
    }

    public void setDriverClassnameText(String driverClassnameText) {
        this.driverClassnameText = driverClassnameText;
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

    private PropertyRows connectionDefinitionProperties(String className) {
        Map<String, Object> response = rest.get(rest.url("resources", "get-connection-definition-properties-and-defaults"),
                Map.of("connectionDefinitionClass", className == null ? "" : className, "restype", resType == null ? "" : resType));
        Map<String, String> defaults = new LinkedHashMap<>();
        if (AdminRestService.extraProperties(response).get("connectionDefinitionPropertiesAndDefaults") instanceof Map<?, ?> map) {
            map.forEach((key, value) -> defaults.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
        }
        PropertyRows rows = new PropertyRows();
        rows.replace(defaults);
        return rows;
    }

    private static List<String> names(Map<String, Object> response, String key) {
        Object names = AdminRestService.extraProperties(response).get(key);
        return names instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
