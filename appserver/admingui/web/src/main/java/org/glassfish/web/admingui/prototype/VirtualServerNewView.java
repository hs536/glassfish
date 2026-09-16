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
 * A new virtual server.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/configuration/virtualServerNew.jsf}. The
 * virtual server is created with the attributes the create command takes, and the others are sent to it afterwards, as
 * the JSFTemplating page does.
 */
@Named
@ViewScoped
public class VirtualServerNewView implements Serializable {

    private static final long serialVersionUID = 1L;
    /** The attributes the create command takes. */
    private static final List<String> CREATE_ATTRIBUTES = List.of("id", "state", "hosts", "logFile", "defaultWebModule", "networkListeners");
    /** The attributes sent to the created virtual server. */
    private static final List<String> REST_ATTRIBUTES = List.of("ssoEnabled", "ssoCookieHttpOnly", "accessLoggingEnabled", "docroot", "accessLog");

    @Inject
    private AdminRestService rest;

    private String configName;
    private final Map<String, Object> values = new HashMap<>();
    private List<String> selectedListeners = new ArrayList<>();
    private final PropertyRows properties = new PropertyRows();
    private List<String> listeners = List.of();
    private List<String> webModules = List.of();

    @PostConstruct
    protected void open() {
        String name = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("configName");
        configName = name == null || name.isEmpty() ? VirtualServers.DEFAULT_CONFIG : name;
        values.putAll(VirtualServers.defaults(rest, configName));
        // The JSFTemplating page starts with these values instead of the defaults of the command
        values.put("docroot", "");
        values.put("accessLog", "");
        values.put("ssoEnabled", "inherit");
        values.put("accessLoggingEnabled", "inherit");
        listeners = VirtualServers.networkListeners(rest, configName);
        webModules = VirtualServers.webModules(rest);
    }

    /** Creates the virtual server, its properties and the reference of its default web module. */
    public void create() {
        String name = VirtualServers.text(values.get("id"));
        try {
            // Checked before anything is created, so that nothing is created when a confidential property does not match
            List<Map<String, String>> propertiesToSend = properties.toSend();
            values.put("networkListeners", String.join(",", selectedListeners));
            Map<String, Object> attributes = new HashMap<>();
            CREATE_ATTRIBUTES.forEach(key -> attributes.put(key, values.get(key)));
            attributes.put("target", configName);
            rest.create(VirtualServers.url(rest, configName), attributes, List.of());

            String selfUrl = rest.child(VirtualServers.url(rest, configName), name);
            Map<String, Object> rest2 = new HashMap<>();
            REST_ATTRIBUTES.forEach(key -> rest2.put(key, values.get(key)));
            rest.create(selfUrl, rest2, List.of("ssoCookieHttpOnly"));
            if (!propertiesToSend.isEmpty()) {
                rest.postJson(selfUrl + "/property.json", propertiesToSend);
            }
            VirtualServers.ensureDefaultWebModule(rest, configName, name, VirtualServers.text(values.get("defaultWebModule")));
            loadPage(getListPage());
        } catch (IllegalArgumentException e) {
            ConsoleMessages.error(e.getMessage());
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    public String getConfigName() {
        return configName;
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

    private static void loadPage(String url) {
        FacesContext.getCurrentInstance().getPartialViewContext().getEvalScripts().add("admingui.ajax.loadPage({url: '" + url + "'});");
    }
}
