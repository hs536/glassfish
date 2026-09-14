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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import org.glassfish.admingui.common.util.AdminRestService;

/**
 * The JDBC resources list page: the resources with their targets, and the delete, enable and disable actions.
 *
 * <p>
 * Prototype (docs/試作計画.md P-4). It sends the same admin REST requests as the JSFTemplating page
 * ({@code jdbc/resourcesTable.inc}, {@code common/resourceNode/resourceHandlers.inc}), except that the resource
 * references of a target are listed once per target instead of once per resource.
 */
@Named
@ViewScoped
public class JdbcResourcesView implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String CHILD_TYPE = "jdbc-resource";
    private static final String CORE_STRINGS = "org.glassfish.admingui.core.Strings";

    @Inject
    private AdminRestService rest;

    private final List<Row> rows = new ArrayList<>();
    private boolean onlyServer;

    @PostConstruct
    void load() {
        rows.clear();
        Map<String, String> targets = targets();
        onlyServer = targets.size() == 1;
        Map<String, List<String>> referencesByTarget = new LinkedHashMap<>();
        targets.keySet().forEach(url -> referencesByTarget.put(url, rest.childNames(url + "/resource-ref")));
        Map<String, String> logicalNames = logicalJndiNames();

        String collection = rest.url("resources", CHILD_TYPE);
        for (String name : rest.childNames(collection)) {
            Map<String, Object> attributes = rest.attributes(rest.child(collection, name));
            Row row = new Row(name);
            row.logicalJndiName = logicalNames.getOrDefault(name, "");
            row.poolName = text(attributes.get("poolName"));
            row.description = text(attributes.get("description"));
            row.enabled = "true".equals(text(attributes.get("enabled")));
            for (Map.Entry<String, List<String>> references : referencesByTarget.entrySet()) {
                if (references.getValue().contains(name)) {
                    row.targets.put(references.getKey(), targets.get(references.getKey()));
                    Map<String, Object> reference = rest.attributes(rest.child(references.getKey() + "/resource-ref", name));
                    if ("true".equals(text(reference.get("enabled")))) {
                        row.enabledTargets++;
                    }
                }
            }
            rows.add(row);
        }
    }

    public List<Row> getRows() {
        return rows;
    }

    /** True when the domain has no cluster and no standalone instance; the list then shows an enabled flag. */
    public boolean isOnlyServer() {
        return onlyServer;
    }

    public boolean isAnySelected() {
        return rows.stream().anyMatch(Row::isSelected);
    }

    public String status(Row row) {
        ResourceBundle strings = coreStrings();
        if (row.targets.isEmpty()) {
            return strings.getString("deploy.noTarget");
        }
        return MessageFormat.format(strings.getString("deploy.someEnabled"), row.enabledTargets, row.targets.size());
    }

    public boolean enabledOnServer(Row row) {
        return row.enabled && (row.targets.isEmpty() || row.enabledTargets > 0);
    }

    public void selectAll() {
        rows.forEach(row -> row.selected = true);
    }

    public void deselectAll() {
        rows.forEach(row -> row.selected = false);
    }

    public void delete() {
        apply(row -> {
            for (Map.Entry<String, String> target : row.targets.entrySet()) {
                rest.delete(rest.child(target.getKey() + "/resource-ref", row.name), Map.of("target", target.getValue()));
            }
            rest.delete(rest.url("resources", CHILD_TYPE, row.name), Map.of("target", "domain"));
        }, null);
    }

    public void enable() {
        setEnabled(true, "msg.enableResourceSuccessful");
    }

    public void disable() {
        setEnabled(false, "msg.disableResourceSuccessful");
    }

    private void setEnabled(boolean enabled, String successKey) {
        apply(row -> {
            for (String target : row.targets.keySet()) {
                rest.post(rest.child(target + "/resource-ref", row.name), Map.of("enabled", String.valueOf(enabled)));
            }
        }, successKey);
    }

    private void apply(Consumer<Row> action, String successKey) {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            rows.stream().filter(Row::isSelected).forEach(action);
            if (successKey != null) {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, coreStrings().getString(successKey), null));
            }
        } catch (RuntimeException e) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
        }
        load();
    }

    /** The targets a resource can be referenced from, as REST URL to target name: the server, standalone instances, clusters. */
    private Map<String, String> targets() {
        Map<String, String> targets = new LinkedHashMap<>();
        targets.put(rest.url("servers", "server", "server"), "server");
        Map<String, Object> instances = rest.get(rest.url("list-instances"), Map.of("standaloneonly", "true", "nostatus", "true"));
        if (AdminRestService.extraProperties(instances).get("instanceList") instanceof List<?> list) {
            for (Object instance : list) {
                if (instance instanceof Map<?, ?> map && map.get("name") != null) {
                    String name = map.get("name").toString();
                    targets.put(rest.url("servers", "server", name), name);
                }
            }
        }
        for (String cluster : rest.childNames(rest.url("clusters", "cluster"))) {
            targets.put(rest.url("clusters", "cluster", cluster), cluster);
        }
        return targets;
    }

    private Map<String, String> logicalJndiNames() {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Object> response = rest.get(rest.url("resources", "list-jdbc-resources"), null);
        if (AdminRestService.extraProperties(response).get("jdbcResources") instanceof List<?> list) {
            for (Object resource : list) {
                if (resource instanceof Map<?, ?> map && map.get("name") != null && map.get("logical-jndi-name") != null) {
                    names.put(map.get("name").toString(), map.get("logical-jndi-name").toString());
                }
            }
        }
        return names;
    }

    private static ResourceBundle coreStrings() {
        FacesContext context = FacesContext.getCurrentInstance();
        return ResourceBundle.getBundle(CORE_STRINGS, context.getViewRoot().getLocale(), Thread.currentThread().getContextClassLoader());
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    /** One JDBC resource in the list. */
    public static final class Row implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String name;
        private String logicalJndiName = "";
        private String poolName = "";
        private String description = "";
        private boolean enabled;
        private boolean selected;
        private int enabledTargets;
        /** REST URL of each target that references the resource, to the target name. */
        private final Map<String, String> targets = new LinkedHashMap<>();

        Row(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String getLogicalJndiName() {
            return logicalJndiName;
        }

        public String getPoolName() {
            return poolName;
        }

        public String getDescription() {
            return description;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }
    }
}
