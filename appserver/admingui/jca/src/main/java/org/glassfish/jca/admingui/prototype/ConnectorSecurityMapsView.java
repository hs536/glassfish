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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;
import org.glassfish.admingui.common.util.ResourceRow;

/**
 * The Security Maps tab of a connector connection pool (prototype, adr/0008): the maps of the pool, sorted by name,
 * with the new and delete actions. Pools of an application are not covered (X-27).
 *
 * <p>
 * The Facelets counterpart of {@code jca/connectorSecurityMaps.jsf} and {@code jca/securityMapsTable.inc}.
 */
@Named
@ViewScoped
public class ConnectorSecurityMapsView implements Serializable {

    private static final long serialVersionUID = 1L;

    static final String CHILD_TYPE = "security-map";

    private static final String SORT_KEY = "name";

    @Inject
    private AdminRestService rest;

    private String name;
    private final List<ResourceRow> rows = new ArrayList<>();
    private boolean ascending = true;

    @PostConstruct
    protected void load() {
        if (name == null) {
            name = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("name");
        }
        rows.clear();
        if (isFound()) {
            rest.childNames(mapsUrl()).forEach(map -> rows.add(new ResourceRow(map)));
        }
        sortRows();
    }

    /** Deletes the selected maps, as the JSFTemplating page does (with the pool name). */
    public void delete() {
        try {
            for (ResourceRow row : rows) {
                if (row.isSelected()) {
                    rest.delete(rest.child(mapsUrl(), row.getName()), Map.of("poolName", name));
                }
            }
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
        load();
    }

    public void selectAll() {
        rows.forEach(row -> row.setSelected(true));
    }

    public void deselectAll() {
        rows.forEach(row -> row.setSelected(false));
    }

    public boolean isAnySelected() {
        return rows.stream().anyMatch(ResourceRow::isSelected);
    }

    public String getSortKey() {
        return SORT_KEY;
    }

    public boolean isAscending() {
        return ascending;
    }

    /** Sorts by the name, the only sortable column; again to reverse. */
    public void sort(String key) {
        ascending = !ascending;
        sortRows();
    }

    public String getName() {
        return name;
    }

    public boolean isFound() {
        return name != null && !name.isEmpty();
    }

    public List<ResourceRow> getRows() {
        return rows;
    }

    /** The new map page, relative to the context root. */
    public String getNewPage() {
        return "/jca/connectorSecurityMapNew.jsf?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    public String editPage(String map) {
        return page("/jca/connectorSecurityMapEdit.jsf") + "&mapName=" + URLEncoder.encode(map, StandardCharsets.UTF_8);
    }

    public String getGeneralPage() {
        return page("/jca/connectorConnectionPoolEdit.jsf");
    }

    public String getAdvancedPage() {
        return page("/jca/connectorConnectionPoolAdvance.jsf");
    }

    public String getPropertiesPage() {
        return page("/jca/connectorConnectionPoolProperty.jsf");
    }

    private void sortRows() {
        Comparator<ResourceRow> order = Comparator.comparing(ResourceRow::getName, String.CASE_INSENSITIVE_ORDER);
        rows.sort(ascending ? order : order.reversed());
    }

    private String page(String page) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + page + "?name="
                + URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    private String mapsUrl() {
        return rest.url("resources", ConnectorConnectionPoolEditView.CHILD_TYPE, name, CHILD_TYPE);
    }
}
