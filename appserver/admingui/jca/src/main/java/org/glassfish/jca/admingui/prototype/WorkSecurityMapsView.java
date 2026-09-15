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
 * The list of the work security maps.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code jca/workSecurityMaps.jsf}.
 */
@Named
@ViewScoped
public class WorkSecurityMapsView implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String SORT_KEY = "name";

    @Inject
    private AdminRestService rest;

    private final List<ResourceRow> rows = new ArrayList<>();
    private boolean ascending = true;

    @PostConstruct
    protected void load() {
        rows.clear();
        rest.childNames(mapsUrl()).forEach(map -> rows.add(new ResourceRow(map)));
        sortRows();
    }

    /** Deletes the selected maps, each with the name of its resource adapter, as the JSFTemplating page does. */
    public void delete() {
        try {
            for (ResourceRow row : rows) {
                if (row.isSelected()) {
                    String url = rest.child(mapsUrl(), row.getName());
                    Object adapter = rest.attributes(url).get("resourceAdapterName");
                    rest.delete(url, Map.of("raname", adapter == null ? "" : adapter.toString()));
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

    public List<ResourceRow> getRows() {
        return rows;
    }

    public String editPage(String map) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/jca/workSecurityMapEdit.jsf?mapName="
                + URLEncoder.encode(map, StandardCharsets.UTF_8);
    }

    private void sortRows() {
        Comparator<ResourceRow> order = Comparator.comparing(ResourceRow::getName, String.CASE_INSENSITIVE_ORDER);
        rows.sort(ascending ? order : order.reversed());
    }

    private String mapsUrl() {
        return rest.url("resources", WorkSecurityMappings.CHILD_TYPE);
    }
}
