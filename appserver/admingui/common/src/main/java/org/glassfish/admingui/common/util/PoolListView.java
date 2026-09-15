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
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The list page of a connection pool type: the pools with their attributes, sorting, the delete action (with the
 * resources that use the pools) and the new action, which starts the new pool wizard.
 *
 * <p>
 * Prototype (adr/0008): the Facelets counterpart of {@code jdbc/poolTable.inc}, {@code jca/poolTable.inc} and their
 * buttons. Unlike a resource, a pool has no targets.
 */
public abstract class PoolListView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The session attributes of the new pool wizard of the JSFTemplating pages, cleared before a new wizard starts. */
    private static final List<String> WIZARD_STATE = List.of("valueMap", "wizardPoolExtra", "wizardPoolProperties");

    @Inject
    private AdminRestService rest;

    private final List<ResourceRow> rows = new ArrayList<>();
    private String sortKey = "name";
    private boolean ascending = true;

    /** The REST child type of the pools, for example {@code jdbc-connection-pool}. */
    protected abstract String childType();

    /** The first page of the new pool wizard, for example {@code /jdbc/jdbcConnectionPoolNew1.jsf}. */
    protected abstract String newPage();

    /** Adds attributes computed for the page to a row, after its attributes are loaded; nothing by default. */
    protected void addAttributes(ResourceRow row) {
    }

    protected AdminRestService rest() {
        return rest;
    }

    @PostConstruct
    protected void load() {
        rows.clear();
        String collection = rest.url("resources", childType());
        for (String name : rest.childNames(collection)) {
            ResourceRow row = new ResourceRow(name);
            rest.attributes(rest.child(collection, name)).forEach((key, value) -> row.getAttributes().put(key, ResourceListView.text(value)));
            addAttributes(row);
            rows.add(row);
        }
        sortRows();
    }

    public List<ResourceRow> getRows() {
        return rows;
    }

    public boolean isAnySelected() {
        return rows.stream().anyMatch(ResourceRow::isSelected);
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

    /** Sorts by the given key ({@code name} or an attribute); again to reverse. */
    public void sort(String key) {
        ascending = !key.equals(sortKey) || !ascending;
        sortKey = key;
        sortRows();
    }

    /** Deletes the selected pools and the resources that use them, as {@code gf.deleteCascade} with {@code cascade=true}. */
    public void delete() {
        try {
            for (ResourceRow row : rows) {
                if (row.isSelected()) {
                    rest.delete(rest.url("resources", childType(), row.getName()), Map.of("cascade", "true"));
                }
            }
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
        load();
    }

    /** Clears the state of an earlier new pool wizard and opens its first page, as the new button of the JSFTemplating pages does. */
    public void newItem() {
        FacesContext context = FacesContext.getCurrentInstance();
        Map<String, Object> session = context.getExternalContext().getSessionMap();
        WIZARD_STATE.forEach(session::remove);
        String page = context.getExternalContext().getRequestContextPath() + newPage();
        context.getPartialViewContext().getEvalScripts().add("admingui.ajax.loadPage({url: '" + page + "'});");
    }

    private void sortRows() {
        Function<ResourceRow, String> value = "name".equals(sortKey) ? ResourceRow::getName : row -> ResourceListView.text(row.getAttributes().get(sortKey));
        Comparator<ResourceRow> order = Comparator.comparing(value, String.CASE_INSENSITIVE_ORDER);
        rows.sort(ascending ? order : order.reversed());
    }
}
