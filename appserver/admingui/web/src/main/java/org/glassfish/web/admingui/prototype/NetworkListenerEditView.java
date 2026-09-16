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
 * An existing network listener.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/grizzly/networkListenerEdit.jsf}. The
 * page saves the listener and its protocol. The SSL, HTTP and File Cache tabs are still JSFTemplating pages, and they
 * are hidden for the listener of the secure admin, as on the JSFTemplating page.
 */
@Named
@ViewScoped
public class NetworkListenerEditView implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String PU_PROTOCOL = "pu-protocol";
    private static final String SECURE_ADMIN_PROTOCOL = "sec-admin-listener";
    private static final String ADMIN_VIRTUAL_SERVER = "__asadmin";
    private static final String SECURE_ADMIN_ENABLED = "secureAdminEnabled";

    @Inject
    private AdminRestService rest;

    private String configName;
    private String name;
    private String protocol;
    private boolean found;
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, Object> protocolValues = new HashMap<>();
    private List<String> threadPools = List.of();
    private List<String> transports = List.of();
    private boolean otherTabs = true;

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
        if (PU_PROTOCOL.equals(protocol)) {
            protocol = SECURE_ADMIN_PROTOCOL;
        }
        protocolValues.putAll(HttpListeners.attributesOrEmpty(rest, protocolUrl()));
        threadPools = HttpListeners.threadPools(rest, configName);
        transports = HttpListeners.transports(rest, configName);
        otherTabs = !isSecureAdminListener();
    }

    public void save() {
        try {
            rest.create(listenerUrl(), new HashMap<>(values), List.of("enabled", "jkEnabled"));
            rest.create(protocolUrl(), new HashMap<>(protocolValues), List.of("securityEnabled"));
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

    public String getProtocol() {
        return protocol;
    }

    public boolean isFound() {
        return found;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    /** The check boxes of the attributes, which are sent as the text {@code true} or {@code false}. */
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

    /** The admin listener of the domain is not changed here, as on the JSFTemplating page. */
    public boolean isReadOnly() {
        return HttpListeners.ADMIN_LISTENER.equals(name) && HttpListeners.DEFAULT_CONFIG.equals(configName);
    }

    /** False for the listener of the secure admin, whose other tabs the JSFTemplating page hides. */
    public boolean isOtherTabs() {
        return otherTabs;
    }

    public List<String> getThreadPools() {
        return threadPools;
    }

    public List<String> getTransports() {
        return transports;
    }

    public String getThreadPoolPage() {
        return contextPath() + "/web/configuration/threadPoolEdit.jsf?configName=" + encode(configName) + "&name="
                + encode(HttpListeners.text(values.get("threadPool")));
    }

    public String getSslPage() {
        return tabPage("listenerSSLEdit.jsf");
    }

    public String getHttpPage() {
        return tabPage("listenerHttpEdit.jsf");
    }

    public String getFileCachePage() {
        return tabPage("listenerFileCache.jsf");
    }

    public String getListPage() {
        return contextPath() + "/web/grizzly/networkListeners.jsf?configName=" + encode(configName);
    }

    /** True when the secure admin is on and this listener is the one of the admin virtual server. */
    private boolean isSecureAdminListener() {
        Object secureAdmin = FacesContext.getCurrentInstance().getExternalContext().getSessionMap().get(SECURE_ADMIN_ENABLED);
        if (!Boolean.parseBoolean(HttpListeners.text(secureAdmin))) {
            return false;
        }
        Map<String, Object> adminVirtualServer = HttpListeners.attributesOrEmpty(rest,
                rest.child(HttpListeners.virtualServersUrl(rest, configName), ADMIN_VIRTUAL_SERVER));
        return name.equals(HttpListeners.text(adminVirtualServer.get("networkListeners")));
    }

    private String tabPage(String page) {
        return contextPath() + "/web/grizzly/" + page + "?configName=" + encode(configName) + "&name=" + encode(protocol)
                + "&listenerName=" + encode(name) + "&cancelTo=web/grizzly/networkListeners.jsf";
    }

    private String listenerUrl() {
        return rest.child(HttpListeners.listenersUrl(rest, configName), name);
    }

    private String protocolUrl() {
        return rest.child(HttpListeners.protocolsUrl(rest, configName), protocol);
    }

    private static String contextPath() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
