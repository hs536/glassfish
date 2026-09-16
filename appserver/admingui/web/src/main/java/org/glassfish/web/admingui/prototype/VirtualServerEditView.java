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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;
import org.glassfish.admingui.common.util.PropertyRows;

/**
 * An existing virtual server.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/configuration/virtualServerEdit.jsf}.
 */
@Named
@ViewScoped
public class VirtualServerEditView implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private AdminRestService rest;

    private String configName;
    private String name;
    private boolean found;
    private final Map<String, Object> values = new HashMap<>();
    private List<String> selectedListeners = new ArrayList<>();
    private PropertyRows properties = new PropertyRows();
    private List<String> listeners = List.of();
    private List<String> webModules = List.of();

    @PostConstruct
    protected void load() {
        if (name == null) {
            Map<String, String> parameters = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
            String config = parameters.get("configName");
            configName = config == null || config.isEmpty() ? VirtualServers.DEFAULT_CONFIG : config;
            name = parameters.get("name");
        }
        values.clear();
        found = name != null && !name.isEmpty();
        if (!found) {
            return;
        }
        Map<String, Object> attributes = rest.attributes(selfUrl());
        found = !attributes.isEmpty();
        if (!found) {
            return;
        }
        values.putAll(attributes);
        selectedListeners = new ArrayList<>(List.of(VirtualServers.text(values.get("networkListeners")).split(",")));
        selectedListeners.removeIf(String::isBlank);
        listeners = VirtualServers.networkListeners(rest, configName);
        webModules = VirtualServers.webModules(rest);
        properties = PropertyRows.read(rest, selfUrl());
    }

    /** Saves the attributes, the reference of the default web module and the properties, as the JSFTemplating page does. */
    public void save() {
        try {
            List<Map<String, String>> propertiesToSend = properties.toSend();
            Map<String, Object> attributes = new HashMap<>(values);
            attributes.put("networkListeners", String.join(",", selectedListeners));
            rest.create(selfUrl(), attributes, List.of("ssoCookieHttpOnly"));
            VirtualServers.ensureDefaultWebModule(rest, configName, name, VirtualServers.text(values.get("defaultWebModule")));
            rest.postJson(selfUrl() + "/property.json", propertiesToSend);
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (IllegalArgumentException e) {
            ConsoleMessages.error(e.getMessage());
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    public String getConfigName() {
        return configName;
    }

    public String getName() {
        return name;
    }

    public boolean isFound() {
        return found;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    /** The checkbox of the attribute, which is sent as the text {@code true} or {@code false}. */
    public boolean isSsoCookieHttpOnly() {
        return Boolean.parseBoolean(VirtualServers.text(values.get("ssoCookieHttpOnly")));
    }

    public void setSsoCookieHttpOnly(boolean ssoCookieHttpOnly) {
        values.put("ssoCookieHttpOnly", String.valueOf(ssoCookieHttpOnly));
    }

    public List<String> getSelectedListeners() {
        return selectedListeners;
    }

    public void setSelectedListeners(List<String> selectedListeners) {
        this.selectedListeners = selectedListeners;
    }

    public PropertyRows getProperties() {
        return properties;
    }

    public List<String> getListeners() {
        return listeners;
    }

    public List<String> getWebModules() {
        return webModules;
    }

    public List<String> getStates() {
        return VirtualServers.STATES;
    }

    public List<String> getInheritChoices() {
        return VirtualServers.INHERIT_CHOICES;
    }

    public String getListPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                + "/web/configuration/virtualServers.jsf?configName=" + URLEncoder.encode(configName, StandardCharsets.UTF_8);
    }

    private String selfUrl() {
        return rest.child(VirtualServers.url(rest, configName), name);
    }
}
