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

package org.glassfish.web.admingui.prototype;

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
 * The list of the HTTP listeners of a configuration.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/configuration/httpListeners.jsf}.
 */
@Named
@ViewScoped
public class HttpListenersView implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String SORT_KEY = "name";

    @Inject
    private AdminRestService rest;

    private String configName;
    private final List<ResourceRow> rows = new ArrayList<>();
    private boolean ascending = true;

    @PostConstruct
    protected void load() {
        if (configName == null) {
            String name = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("configName");
            configName = name == null || name.isEmpty() ? HttpListeners.DEFAULT_CONFIG : name;
        }
        rows.clear();
        String url = HttpListeners.listenersUrl(rest, configName);
        for (String name : rest.childNames(url)) {
            ResourceRow row = new ResourceRow(name);
            Map<String, Object> attributes = rest.attributes(rest.child(url, name));
            row.getAttributes().put("port", HttpListeners.text(attributes.get("port")));
            row.getAttributes().put("address", HttpListeners.text(attributes.get("address")));
            row.getAttributes().put("enabled", HttpListeners.text(attributes.get("enabled")));
            rows.add(row);
        }
        sortRows();
    }

    public void delete() {
        try {
            for (ResourceRow row : rows) {
                if (row.isSelected()) {
                    HttpListeners.delete(rest, configName, row.getName());
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

    /** Sorts by the name, the only sortable column of the prototype; again to reverse. */
    public void sort(String key) {
        ascending = !ascending;
        sortRows();
    }

    public List<ResourceRow> getRows() {
        return rows;
    }

    public String getConfigName() {
        return configName;
    }

    public String getNewPage() {
        return "/web/configuration/httpListenerNew.jsf?configName=" + encode(configName);
    }

    public String editPage(String name) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                + "/web/configuration/httpListenerEdit.jsf?configName=" + encode(configName) + "&name=" + encode(name);
    }

    private void sortRows() {
        Comparator<ResourceRow> order = Comparator.comparing(ResourceRow::getName, String.CASE_INSENSITIVE_ORDER);
        rows.sort(ascending ? order : order.reversed());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
