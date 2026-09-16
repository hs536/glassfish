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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * A new network listener.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/grizzly/networkListenerNew.jsf}. The
 * listener either gets a new protocol of its own, with the HTTP settings of a virtual server, or uses a protocol that
 * is already there.
 */
@Named
@ViewScoped
public class NetworkListenerNewView implements Serializable {

    private static final long serialVersionUID = 1L;
    /** The choice that creates a protocol for the listener. */
    static final String CREATE = "create";
    /** The choice that uses a protocol that is already there. */
    static final String EXISTING = "existing";

    @Inject
    private AdminRestService rest;

    private String configName;
    private String name;
    private String protocolChoice = CREATE;
    private String newProtocolName;
    private String defaultVirtualServer = "server";
    private String existingProtocolName;
    private String port;
    private String address;
    private boolean enabled = true;
    private boolean securityEnabled;
    private boolean jkEnabled;
    private String threadPool;
    private String transport;
    private List<String> virtualServers = List.of();
    private List<String> protocols = List.of();
    private List<String> threadPools = List.of();
    private List<String> transports = List.of();

    @PostConstruct
    protected void open() {
        String config = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("configName");
        configName = config == null || config.isEmpty() ? HttpListeners.DEFAULT_CONFIG : config;
        Map<String, String> defaults = rest.defaults(HttpListeners.listenersUrl(rest, configName));
        address = defaults.get("address");
        virtualServers = HttpListeners.virtualServers(rest, configName);
        protocols = rest.childNames(HttpListeners.protocolsUrl(rest, configName));
        threadPools = HttpListeners.threadPools(rest, configName);
        transports = HttpListeners.transports(rest, configName);
        // The drop-downs of the JSFTemplating page have no chosen value, so the first one is used
        threadPool = threadPools.isEmpty() ? "" : threadPools.get(0);
        transport = transports.isEmpty() ? "" : transports.get(0);
        existingProtocolName = protocols.isEmpty() ? "" : protocols.get(0);
        if (!virtualServers.contains(defaultVirtualServer) && !virtualServers.isEmpty()) {
            defaultVirtualServer = virtualServers.get(0);
        }
    }

    /** Creates the protocol when it is a new one, and then the listener. */
    public void create() {
        try {
            if (rest.childNames(HttpListeners.listenersUrl(rest, configName)).contains(name)) {
                ConsoleMessages.error(WebStrings.get("grizzly.networkListener.alreadyExist", name));
                return;
            }
            String protocol;
            if (CREATE.equals(protocolChoice)) {
                protocol = newProtocolName;
                if (rest.childNames(HttpListeners.protocolsUrl(rest, configName)).contains(protocol)) {
                    ConsoleMessages.error(WebStrings.get("grizzly.protocol.alreadyExist", protocol));
                    return;
                }
                Map<String, Object> protocolAttributes = new LinkedHashMap<>();
                protocolAttributes.put("name", protocol);
                protocolAttributes.put("target", configName);
                protocolAttributes.put("securityEnabled", String.valueOf(securityEnabled));
                rest.create(HttpListeners.protocolsUrl(rest, configName), protocolAttributes, List.of());

                Map<String, Object> httpAttributes = new LinkedHashMap<>();
                httpAttributes.put("defaultVirtualServer", defaultVirtualServer);
                httpAttributes.put("target", configName);
                rest.create(rest.child(HttpListeners.protocolsUrl(rest, configName), protocol, "create-http"), httpAttributes, List.of());
            } else {
                protocol = existingProtocolName;
                // The JSFTemplating page sends the name of the protocol back to it, which changes nothing
                rest.post(rest.child(HttpListeners.protocolsUrl(rest, configName), protocol), Map.of("Name", protocol));
            }
            Map<String, Object> listener = new LinkedHashMap<>();
            listener.put("name", name);
            // An empty address is left to the server, as on the JSFTemplating page
            if (address != null && !address.isEmpty()) {
                listener.put("address", address);
            }
            listener.put("port", port);
            listener.put("transport", transport);
            listener.put("threadpool", threadPool);
            listener.put("enabled", String.valueOf(enabled));
            listener.put("jkenabled", String.valueOf(jkEnabled));
            listener.put("protocol", protocol);
            listener.put("target", configName);
            rest.create(HttpListeners.listenersUrl(rest, configName), listener, List.of());
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

    /**
     * Setting the name of the listener suggests the name of its protocol, as {@code setProtocolName} does on the
     * JSFTemplating page. A name the user typed is kept.
     */
    public void setName(String name) {
        if (newProtocolName == null || newProtocolName.isEmpty() || newProtocolName.equals(suggestedProtocolName())) {
            newProtocolName = name == null || name.isEmpty() ? "" : name + HttpListeners.PROTOCOL_SUFFIX;
        }
        this.name = name;
    }

    private String suggestedProtocolName() {
        return name == null || name.isEmpty() ? "" : name + HttpListeners.PROTOCOL_SUFFIX;
    }

    public String getProtocolChoice() {
        return protocolChoice;
    }

    public void setProtocolChoice(String protocolChoice) {
        this.protocolChoice = protocolChoice;
    }

    public boolean isCreateProtocol() {
        return CREATE.equals(protocolChoice);
    }

    public String getNewProtocolName() {
        return newProtocolName;
    }

    public void setNewProtocolName(String newProtocolName) {
        this.newProtocolName = newProtocolName;
    }

    public String getDefaultVirtualServer() {
        return defaultVirtualServer;
    }

    public void setDefaultVirtualServer(String defaultVirtualServer) {
        this.defaultVirtualServer = defaultVirtualServer;
    }

    public String getExistingProtocolName() {
        return existingProtocolName;
    }

    public void setExistingProtocolName(String existingProtocolName) {
        this.existingProtocolName = existingProtocolName;
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

    public String getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(String threadPool) {
        this.threadPool = threadPool;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public List<String> getVirtualServers() {
        return virtualServers;
    }

    public List<String> getProtocols() {
        return protocols;
    }

    public List<String> getThreadPools() {
        return threadPools;
    }

    public List<String> getTransports() {
        return transports;
    }

    public String getListPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                + "/web/grizzly/networkListeners.jsf?configName=" + URLEncoder.encode(configName, StandardCharsets.UTF_8);
    }

    private static void loadPage(String url) {
        FacesContext.getCurrentInstance().getPartialViewContext().getEvalScripts().add("admingui.ajax.loadPage({url: '" + url + "'});");
    }
}
