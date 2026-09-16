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
 * The list of the protocols of a configuration.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/grizzly/protocols.jsf}. The new and the
 * edit page are still JSFTemplating pages. Deleting a protocol deletes the network listeners that use it, as the
 * JSFTemplating page does.
 */
@Named
@ViewScoped
public class ProtocolsView implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String SORT_KEY = "name";
    /** The protocols the server manages itself, which the JSFTemplating page leaves out ({@code ServerTags}). */
    private static final List<String> HIDDEN = List.of("pu-protocol", "admin-http-redirect");

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
        String url = HttpListeners.protocolsUrl(rest, configName);
        Map<String, List<String>> listeners = listenersByProtocol();
        for (String name : rest.childNames(url)) {
            if (HIDDEN.contains(name)) {
                continue;
            }
            ResourceRow row = new ResourceRow(name);
            row.getAttributes().put("securityEnabled", HttpListeners.text(rest.attributes(rest.child(url, name)).get("securityEnabled")));
            row.getTargets().addAll(listeners.getOrDefault(name, List.of()));
            rows.add(row);
        }
        sortRows();
    }

    /** Deletes the selected protocols with the listeners that use them. */
    public void delete() {
        try {
            for (ResourceRow row : rows) {
                if (row.isSelected()) {
                    for (String listener : row.getTargets()) {
                        HttpListeners.delete(rest, configName, listener);
                    }
                    String url = rest.child(HttpListeners.protocolsUrl(rest, configName), row.getName());
                    if (!rest.childNames(HttpListeners.protocolsUrl(rest, configName)).contains(row.getName())) {
                        continue;
                    }
                    rest.delete(url, Map.of("target", configName));
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
        return "/web/grizzly/protocolNew.jsf?configName=" + encode(configName);
    }

    /** The edit page, which is still a JSFTemplating page. */
    public String editPage(String name) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/web/grizzly/protocolEdit.jsf?configName="
                + encode(configName) + "&name=" + encode(name) + "&cancelTo=web/grizzly/protocols.jsf";
    }

    /** The edit page of a network listener, which is still a JSFTemplating page. */
    public String listenerPage(String name) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                + "/web/grizzly/networkListenerEdit.jsf?configName=" + encode(configName) + "&name=" + encode(name)
                + "&cancelTo=web/grizzly/protocols.jsf";
    }

    /** The names of the listeners of each protocol. */
    private Map<String, List<String>> listenersByProtocol() {
        Map<String, List<String>> listeners = new java.util.LinkedHashMap<>();
        String url = HttpListeners.listenersUrl(rest, configName);
        for (String listener : rest.childNames(url)) {
            String protocol = HttpListeners.text(rest.attributes(rest.child(url, listener)).get("protocol"));
            listeners.computeIfAbsent(protocol, key -> new ArrayList<>()).add(listener);
        }
        return listeners;
    }

    private void sortRows() {
        Comparator<ResourceRow> order = Comparator.comparing(ResourceRow::getName, String.CASE_INSENSITIVE_ORDER);
        rows.sort(ascending ? order : order.reversed());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
