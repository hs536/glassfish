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
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.PropertyRows;

/**
 * The edit JDBC resource page.
 *
 * <p>
 * Prototype (docs/試作計画.md P-4). It sends the same admin REST requests as the JSFTemplating page
 * ({@code jdbc/jdbcResourceEdit.jsf}, {@code common/resourceNode/resourceEditTabs.inc},
 * {@code common/resourceNode/resourceEditPageButtons.inc}): the resource attributes, its properties, and, in a domain
 * with only the server, the enabled state of the server's resource reference. Resources scoped to an application are
 * not covered by the prototype.
 */
@Named
@ViewScoped
public class JdbcResourceEditView implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String CHILD_TYPE = "jdbc-resource";
    private static final String CORE_STRINGS = "org.glassfish.admingui.core.Strings";
    private static final String JDBC_STRINGS = "org.glassfish.jdbc.admingui.Strings";
    private static final String DEFAULT_DEPLOYMENT_ORDER = "100";

    @Inject
    private AdminRestService rest;

    private String name;
    private boolean found;
    private boolean onlyServer;
    private Map<String, Object> attributes = new HashMap<>();
    private List<String> pools = List.of();
    private String logicalJndiName = "";
    private String poolName;
    private String deploymentOrder;
    private String description;
    private boolean enabled;
    private String status = "";
    private PropertyRows properties = new PropertyRows();

    @PostConstruct
    void load() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (name == null) {
            name = context.getExternalContext().getRequestParameterMap().get("name");
        }
        if (name == null || name.isEmpty()) {
            found = false;
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    strings(CORE_STRINGS).getString("common.jndiName") + " ?", null));
            return;
        }
        String self = selfUrl();
        attributes = new HashMap<>(rest.attributes(self));
        found = !attributes.isEmpty();
        if (!found) {
            return;
        }
        pools = rest.childNames(rest.url("resources", "jdbc-connection-pool"));
        poolName = text(attributes.get("poolName"));
        deploymentOrder = text(attributes.get("deploymentOrder"));
        description = text(attributes.get("description"));
        logicalJndiName = logicalJndiName();
        properties = propertyRows(rest.get(self + "/property.json", null));

        Map<String, String> targets = targets();
        onlyServer = targets.size() == 1;
        boolean resourceEnabled = "true".equals(text(attributes.get("enabled")));
        if (onlyServer) {
            Map<String, Object> reference = rest.attributes(rest.child(rest.url("servers", "server", "server", "resource-ref"), name));
            enabled = resourceEnabled && (reference.isEmpty() || "true".equals(text(reference.get("enabled"))));
        } else {
            status = status(targets);
        }
    }

    public void save() {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            Map<String, Object> values = new HashMap<>(attributes);
            values.remove("jndiName");
            values.put("poolName", poolName);
            values.put("deploymentOrder", deploymentOrder);
            values.put("description", description);
            // The enabled state is kept on the resource references; the resource itself stays enabled
            values.put("enabled", "true");
            rest.create(selfUrl(), values, List.of("enabled"));

            if (onlyServer) {
                String references = rest.url("servers", "server", "server", "resource-ref");
                Map<String, Object> reference = rest.attributes(rest.child(references, name));
                if (reference.isEmpty()) {
                    rest.create(references, Map.of("id", name, "enabled", String.valueOf(enabled)), List.of("enabled"));
                } else {
                    rest.create(rest.child(references, name), Map.of("enabled", String.valueOf(enabled)), List.of("enabled"));
                }
            }

            rest.postJson(selfUrl() + "/property.json", properties.toSend());
            load();
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, strings(CORE_STRINGS).getString("msg.saveSuccessful"), null));
        } catch (RuntimeException e) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
        }
    }

    /**
     * Replaces the values that have a default with the default, as the Load Defaults button of the JSFTemplating page
     * does ({@code gf.getDefaultValues} with the current values).
     */
    public void loadDefaults() {
        Map<String, String> defaults = rest.defaults(rest.url("resources", CHILD_TYPE));
        poolName = defaults.getOrDefault("poolName", poolName);
        deploymentOrder = defaults.getOrDefault("deploymentOrder", DEFAULT_DEPLOYMENT_ORDER);
        description = defaults.getOrDefault("description", description);
    }

    /** The page of the Target tab (JSFTemplating), which returns to this page with its General tab. */
    public String getTargetPage() {
        String contextPath = FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath();
        String generalPage = contextPath + "/jdbc/jdbcResourceEdit.jsf?name=" + encode(name);
        return contextPath + "/common/resourceNode/resourceEditTargets.jsf?name=" + encode(name) + "&generalPage=" + encode(generalPage);
    }

    public String getPoolHelp() {
        FacesContext context = FacesContext.getCurrentInstance();
        String text = strings(JDBC_STRINGS).getString("jdbcResource.poolHelp");
        return context.getApplication().evaluateExpressionGet(context, text, String.class);
    }

    private String selfUrl() {
        return rest.url("resources", CHILD_TYPE, name);
    }

    private String logicalJndiName() {
        String requested = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("logicalJndiName");
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        Map<String, Object> response = rest.get(rest.url("resources", "list-jdbc-resources"), null);
        if (AdminRestService.extraProperties(response).get("jdbcResources") instanceof List<?> list) {
            for (Object resource : list) {
                if (resource instanceof Map<?, ?> map && name.equals(map.get("name")) && map.get("logical-jndi-name") != null) {
                    return map.get("logical-jndi-name").toString();
                }
            }
        }
        return "";
    }

    private static PropertyRows propertyRows(Map<String, Object> response) {
        PropertyRows rows = new PropertyRows();
        if (AdminRestService.extraProperties(response).get("properties") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add();
                    PropertyRows.Row row = rows.getRows().get(rows.getRows().size() - 1);
                    row.setName(text(map.get("name")));
                    row.setValue(text(map.get("value")));
                    row.setDescription(text(map.get("description")));
                }
            }
        }
        return rows;
    }

    /** The targets as REST URL to target name: the server, standalone instances, clusters. */
    private Map<String, String> targets() {
        Map<String, String> targets = new LinkedHashMap<>();
        targets.put(rest.url("servers", "server", "server"), "server");
        Map<String, Object> instances = rest.get(rest.url("list-instances"), Map.of("standaloneonly", "true", "nostatus", "true"));
        if (AdminRestService.extraProperties(instances).get("instanceList") instanceof List<?> list) {
            for (Object instance : list) {
                if (instance instanceof Map<?, ?> map && map.get("name") != null) {
                    targets.put(rest.url("servers", "server", map.get("name").toString()), map.get("name").toString());
                }
            }
        }
        for (String cluster : rest.childNames(rest.url("clusters", "cluster"))) {
            targets.put(rest.url("clusters", "cluster", cluster), cluster);
        }
        return targets;
    }

    /** "Enabled on n of m Target(s)", as {@code gf.getTargetEnableInfo} shows for a resource. */
    private String status(Map<String, String> targets) {
        int referenced = 0;
        int enabledReferences = 0;
        for (String target : targets.keySet()) {
            if (rest.childNames(target + "/resource-ref").contains(name)) {
                referenced++;
                Map<String, Object> reference = rest.attributes(rest.child(target + "/resource-ref", name));
                if ("true".equals(text(reference.get("enabled")))) {
                    enabledReferences++;
                }
            }
        }
        ResourceBundle strings = strings(CORE_STRINGS);
        return referenced == 0 ? strings.getString("deploy.noTarget")
                : MessageFormat.format(strings.getString("deploy.someEnabled"), enabledReferences, referenced);
    }

    private ResourceBundle strings(String baseName) {
        FacesContext context = FacesContext.getCurrentInstance();
        ClassLoader loader = JDBC_STRINGS.equals(baseName) ? getClass().getClassLoader() : Thread.currentThread().getContextClassLoader();
        return ResourceBundle.getBundle(baseName, context.getViewRoot().getLocale(), loader);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    public boolean isFound() {
        return found;
    }

    public boolean isOnlyServer() {
        return onlyServer;
    }

    public String getName() {
        return name;
    }

    public String getLogicalJndiName() {
        return logicalJndiName;
    }

    public List<String> getPools() {
        return pools;
    }

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    public String getDeploymentOrder() {
        return deploymentOrder;
    }

    public void setDeploymentOrder(String deploymentOrder) {
        this.deploymentOrder = deploymentOrder;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStatus() {
        return status;
    }

    public PropertyRows getProperties() {
        return properties;
    }
}
