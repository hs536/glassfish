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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * A new HTTP listener.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/configuration/httpListenerNew.jsf}. The
 * page creates a protocol named after the listener, its HTTP settings and then the listener itself, as the
 * JSFTemplating page does.
 */
@Named
@ViewScoped
public class HttpListenerNewView implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_THREAD_POOL = "http-thread-pool";

    @Inject
    private AdminRestService rest;

    private String configName;
    private String name;
    private String port;
    private String address;
    private boolean enabled = true;
    private boolean securityEnabled;
    private boolean jkEnabled;
    private String defaultVirtualServer = "";
    private String threadPool = DEFAULT_THREAD_POOL;
    private String serverName;
    private String transport = "";
    private List<String> virtualServers = List.of();
    private List<String> threadPools = List.of();

    @PostConstruct
    protected void open() {
        String config = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("configName");
        configName = config == null || config.isEmpty() ? HttpListeners.DEFAULT_CONFIG : config;
        Map<String, String> defaults = rest.defaults(HttpListeners.listenersUrl(rest, configName));
        address = defaults.get("address");
        virtualServers = HttpListeners.virtualServers(rest, configName);
        threadPools = HttpListeners.threadPools(rest, configName);
        List<String> transports = HttpListeners.transports(rest, configName);
        transport = transports.isEmpty() ? "" : transports.get(0);
        defaultVirtualServer = virtualServers.contains("server") ? "server" : (virtualServers.isEmpty() ? "" : virtualServers.get(0));
    }

    /** Creates the protocol, its HTTP settings and the listener, and opens the list. */
    public void create() {
        try {
            if (rest.childNames(HttpListeners.listenersUrl(rest, configName)).contains(name)) {
                ConsoleMessages.error(WebStrings.get("grizzly.networkListener.alreadyExist", name));
                return;
            }
            String protocol = name + HttpListeners.PROTOCOL_SUFFIX;
            if (rest.childNames(HttpListeners.protocolsUrl(rest, configName)).contains(protocol)) {
                ConsoleMessages.error(WebStrings.get("grizzly.protocol.alreadyExist", protocol));
                return;
            }
            Map<String, Object> protocolAttributes = new LinkedHashMap<>();
            protocolAttributes.put("name", protocol);
            protocolAttributes.put("securityEnabled", String.valueOf(securityEnabled));
            protocolAttributes.put("target", configName);
            rest.post(HttpListeners.protocolsUrl(rest, configName), protocolAttributes);

            Map<String, Object> httpAttributes = new LinkedHashMap<>();
            httpAttributes.put("target", configName);
            httpAttributes.put("defaultVirtualServer", defaultVirtualServer);
            if (serverName != null && !serverName.isEmpty()) {
                httpAttributes.put("serverName", serverName);
            }
            rest.post(rest.child(HttpListeners.protocolsUrl(rest, configName), protocol, "create-http"), httpAttributes);

            Map<String, Object> listenerAttributes = new HashMap<>();
            listenerAttributes.put("name", name);
            listenerAttributes.put("port", port);
            listenerAttributes.put("transport", transport);
            listenerAttributes.put("threadPool", threadPool);
            listenerAttributes.put("target", configName);
            listenerAttributes.put("protocol", protocol);
            listenerAttributes.put("jkEnabled", String.valueOf(jkEnabled));
            // An empty address is left to the server, and a listener is enabled unless the box is cleared, as on the
            // JSFTemplating page
            if (address != null && !address.isEmpty()) {
                listenerAttributes.put("address", address);
            }
            if (!enabled) {
                listenerAttributes.put("enabled", "false");
            }
            rest.post(HttpListeners.listenersUrl(rest, configName), listenerAttributes);
            loadPage(getListPage());
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

    public void setName(String name) {
        this.name = name;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSecurityEnabled() {
        return securityEnabled;
    }

    public void setSecurityEnabled(boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    public boolean isJkEnabled() {
        return jkEnabled;
    }

    public void setJkEnabled(boolean jkEnabled) {
        this.jkEnabled = jkEnabled;
    }

    public String getDefaultVirtualServer() {
        return defaultVirtualServer;
    }

    public void setDefaultVirtualServer(String defaultVirtualServer) {
        this.defaultVirtualServer = defaultVirtualServer;
    }

    public String getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(String threadPool) {
        this.threadPool = threadPool;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public List<String> getVirtualServers() {
        return virtualServers;
    }

    public List<String> getThreadPools() {
        return threadPools;
    }

    /** The security of the admin listener cannot be changed here; a new listener is never the admin listener. */
    public boolean isSecurityReadOnly() {
        return false;
    }

    public String getThreadPoolPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                + "/web/configuration/threadPoolEdit.jsf?name=" + encode(threadPool) + "&configName=" + encode(configName);
    }

    public String getListPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                + "/web/configuration/httpListeners.jsf?configName=" + encode(configName);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static void loadPage(String url) {
        FacesContext.getCurrentInstance().getPartialViewContext().getEvalScripts().add("admingui.ajax.loadPage({url: '" + url + "'});");
    }
}
