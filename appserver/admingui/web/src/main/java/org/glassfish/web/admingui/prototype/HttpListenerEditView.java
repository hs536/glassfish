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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * An existing HTTP listener.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/configuration/httpListenerEdit.jsf}. The
 * page saves the HTTP settings of the protocol, the protocol and the listener, as the JSFTemplating page does. The SSL
 * tab is still a JSFTemplating page.
 */
@Named
@ViewScoped
public class HttpListenerEditView implements Serializable {

    private static final long serialVersionUID = 1L;
    /** The protocol of the admin listener when the secure admin is on. */
    private static final String SECURE_ADMIN_PROTOCOL = "sec-admin-listener";
    private static final String PU_PROTOCOL = "pu-protocol";

    @Inject
    private AdminRestService rest;

    private String configName;
    private String name;
    private String protocol;
    private boolean found;
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, Object> protocolValues = new HashMap<>();
    private final Map<String, Object> httpValues = new HashMap<>();
    private List<String> virtualServers = List.of();
    private List<String> threadPools = List.of();

    @PostConstruct
    protected void load() {
        if (name == null) {
            Map<String, String> parameters = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
            String config = parameters.get("configName");
            configName = config == null || config.isEmpty() ? HttpListeners.DEFAULT_CONFIG : config;
            name = parameters.get("name");
        }
        values.clear();
        protocolValues.clear();
        httpValues.clear();
        found = name != null && !name.isEmpty();
        if (!found) {
            return;
        }
        Map<String, Object> attributes = rest.attributes(listenerUrl());
        found = !attributes.isEmpty();
        if (!found) {
            return;
        }
        values.putAll(attributes);
        protocol = HttpListeners.text(values.get("protocol"));
        // The admin listener uses a port unification protocol when the secure admin is on
        if (HttpListeners.ADMIN_LISTENER.equals(name) && PU_PROTOCOL.equals(protocol)) {
            protocol = SECURE_ADMIN_PROTOCOL;
        }
        protocolValues.putAll(rest.attributes(protocolUrl()));
        httpValues.putAll(HttpListeners.attributesOrEmpty(rest, rest.child(protocolUrl(), "http")));
        virtualServers = HttpListeners.virtualServers(rest, configName);
        threadPools = HttpListeners.threadPools(rest, configName);
    }

    public void save() {
        try {
            rest.post(rest.child(protocolUrl(), "http"), new HashMap<>(httpValues));
            rest.post(protocolUrl(), new HashMap<>(protocolValues));
            rest.post(listenerUrl(), new HashMap<>(values));
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
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

    public Map<String, Object> getHttpValues() {
        return httpValues;
    }

    /** The checkboxes of the attributes, which are sent as the text {@code true} or {@code false}. */
    public boolean isEnabled() {
        return Boolean.parseBoolean(HttpListeners.text(values.get("enabled")));
    }

    public void setEnabled(boolean enabled) {
        values.put("enabled", String.valueOf(enabled));
    }

    public boolean isJkEnabled() {
        return Boolean.parseBoolean(HttpListeners.text(values.get("jkEnabled")));
    }

    public void setJkEnabled(boolean jkEnabled) {
        values.put("jkEnabled", String.valueOf(jkEnabled));
    }

    public boolean isSecurityEnabled() {
        return Boolean.parseBoolean(HttpListeners.text(protocolValues.get("securityEnabled")));
    }

    public void setSecurityEnabled(boolean securityEnabled) {
        protocolValues.put("securityEnabled", String.valueOf(securityEnabled));
    }

    /** The security of the admin listener is not changed here, as on the JSFTemplating page. */
    public boolean isSecurityReadOnly() {
        return HttpListeners.ADMIN_LISTENER.equals(name);
    }

    public String getPort() {
        return HttpListeners.text(values.get("port"));
    }

    public void setPort(String port) {
        values.put("port", port);
    }

    public String getAddress() {
        return HttpListeners.text(values.get("address"));
    }

    public void setAddress(String address) {
        values.put("address", address);
    }

    public String getThreadPool() {
        return HttpListeners.text(values.get("threadPool"));
    }

    public void setThreadPool(String threadPool) {
        values.put("threadPool", threadPool);
    }

    public String getDefaultVirtualServer() {
        return HttpListeners.text(httpValues.get("defaultVirtualServer"));
    }

    public void setDefaultVirtualServer(String virtualServer) {
        httpValues.put("defaultVirtualServer", virtualServer);
    }

    public String getServerName() {
        return HttpListeners.text(httpValues.get("serverName"));
    }

    public void setServerName(String serverName) {
        httpValues.put("serverName", serverName);
    }

    public List<String> getVirtualServers() {
        return virtualServers;
    }

    public List<String> getThreadPools() {
        return threadPools;
    }

    public String getThreadPoolPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                + "/web/configuration/threadPoolEdit.jsf?name=" + encode(getThreadPool()) + "&configName=" + encode(configName);
    }

    /** The SSL tab, which is still a JSFTemplating page and takes the protocol of the listener. */
    public String getSslPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                + "/web/configuration/httpListenerSSL.jsf?configName=" + encode(configName) + "&name=" + encode(protocol)
                + "&listenerName=" + encode(name) + "&cancelTo=web/configuration/httpListeners.jsf";
    }

    public String getListPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                + "/web/configuration/httpListeners.jsf?configName=" + encode(configName);
    }

    private String listenerUrl() {
        return rest.child(HttpListeners.listenersUrl(rest, configName), name);
    }

    private String protocolUrl() {
        return rest.child(HttpListeners.protocolsUrl(rest, configName), protocol);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
