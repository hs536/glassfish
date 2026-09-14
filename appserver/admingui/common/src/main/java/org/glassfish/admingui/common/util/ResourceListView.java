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
package org.glassfish.admingui.common.util;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The list page of a resource type: the resources with their targets, sorting, selection, and the delete, enable and
 * disable actions. A page bean extends this class and names the resource type.
 *
 * <p>
 * Prototype (adr/0008): the Facelets counterpart of {@code common/resourceNode/resourceHandlers.inc} and the
 * resources table fragments. It sends the same admin REST requests, except that the references of a target are
 * listed once per target instead of once per resource.
 */
public abstract class ResourceListView implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private AdminRestService rest;

    private final List<ResourceRow> rows = new ArrayList<>();
    private boolean onlyServer;
    private String sortKey = "name";
    private boolean ascending = true;

    /** The REST child type of the resources, for example {@code jdbc-resource}. */
    protected abstract String childType();

    /** The list command that returns the logical JNDI names, for example {@code list-jdbc-resources}; none by default. */
    protected String logicalNamesCommand() {
        return null;
    }

    /** The key of the list in the response of {@link #logicalNamesCommand()}, for example {@code jdbcResources}. */
    protected String logicalNamesKey() {
        return null;
    }

    /** The target of the delete request of a resource; {@code domain} by default. */
    protected String deleteTarget() {
        return "domain";
    }

    protected AdminRestService rest() {
        return rest;
    }

    @PostConstruct
    protected void load() {
        rows.clear();
        ResourceTargets targets = ResourceTargets.load(rest);
        onlyServer = targets.onlyServer();
        Map<String, List<String>> references = targets.references(rest);
        Map<String, String> logicalNames = ResourceLookups.logicalJndiNames(rest, logicalNamesCommand(), logicalNamesKey());
        String collection = rest.url("resources", childType());
        for (String name : rest.childNames(collection)) {
            ResourceRow row = new ResourceRow(name);
            rest.attributes(rest.child(collection, name)).forEach((key, value) -> row.getAttributes().put(key, text(value)));
            row.setEnabled("true".equals(row.attribute("enabled")));
            row.setLogicalJndiName(logicalNames.getOrDefault(name, ""));
            for (Map.Entry<String, List<String>> target : references.entrySet()) {
                if (target.getValue().contains(name)) {
                    Map<String, Object> reference = rest.attributes(rest.child(targets.referencesUrl(target.getKey()), name));
                    row.addTarget(target.getKey(), "true".equals(text(reference.get("enabled"))));
                }
            }
            rows.add(row);
        }
        sortRows();
    }

    public List<ResourceRow> getRows() {
        return rows;
    }

    /** True when the domain has only the server as target; the list then shows an enabled flag instead of a status. */
    public boolean isOnlyServer() {
        return onlyServer;
    }

    public boolean isAnySelected() {
        return rows.stream().anyMatch(ResourceRow::isSelected);
    }

    /** "Enabled on n of m Target(s)", or "No Associated Target". */
    public String status(ResourceRow row) {
        return row.getTargets().isEmpty() ? ConsoleMessages.core("deploy.noTarget")
                : ConsoleMessages.core("deploy.someEnabled", row.getEnabledTargets(), row.getTargets().size());
    }

    /** The enabled state shown when the server is the only target. */
    public boolean enabledOnServer(ResourceRow row) {
        return row.isEnabled() && (row.getTargets().isEmpty() || row.getEnabledTargets() > 0);
    }

    public void selectAll() {
        rows.forEach(row -> row.setSelected(true));
    }

    public void deselectAll() {
        rows.forEach(row -> row.setSelected(false));
    }

    public String getSortKey() {
        return sortKey;
    }

    public boolean isAscending() {
        return ascending;
    }

    /** Sorts by the given key ({@code name}, {@code logicalJndiName}, {@code status} or an attribute); again to reverse. */
    public void sort(String key) {
        ascending = !key.equals(sortKey) || !ascending;
        sortKey = key;
        sortRows();
    }

    public void delete() {
        apply(row -> {
            ResourceTargets targets = ResourceTargets.load(rest);
            for (String target : row.getTargets()) {
                rest.delete(rest.child(targets.referencesUrl(target), row.getName()), Map.of("target", target));
            }
            rest.delete(rest.url("resources", childType(), row.getName()), Map.of("target", deleteTarget()));
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
            ResourceTargets targets = ResourceTargets.load(rest);
            for (String target : row.getTargets()) {
                rest.post(rest.child(targets.referencesUrl(target), row.getName()), Map.of("enabled", String.valueOf(enabled)));
            }
        }, successKey);
    }

    private void apply(Consumer<ResourceRow> action, String successKey) {
        try {
            rows.stream().filter(ResourceRow::isSelected).forEach(action);
            if (successKey != null) {
                ConsoleMessages.info(ConsoleMessages.core(successKey));
            }
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
        load();
    }

    private void sortRows() {
        Function<ResourceRow, String> value = switch (sortKey) {
            case "name" -> ResourceRow::getName;
            case "logicalJndiName" -> ResourceRow::getLogicalJndiName;
            case "status" -> this::status;
            default -> row -> row.attribute(sortKey);
        };
        Comparator<ResourceRow> order = Comparator.comparing(value, String.CASE_INSENSITIVE_ORDER);
        rows.sort(ascending ? order : order.reversed());
    }

    static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
