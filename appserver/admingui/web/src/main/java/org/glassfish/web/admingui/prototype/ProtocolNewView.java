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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;
import org.glassfish.admingui.common.util.Settings;

/**
 * A new protocol, with its HTTP and file cache settings.
 *
 * <p>
 * Prototype (adr/0008, adr/0009): the Facelets version of {@code web/grizzly/protocolNew.jsf}. The three resources are
 * created one after the other, as on the JSFTemplating page: the protocol, its HTTP settings and their file cache.
 * The values the page starts with are the ones the server would use, which for HTTP and the file cache are those of the
 * listener of the admin console.
 */
@Named
@ViewScoped
public class ProtocolNewView implements Serializable {

    private static final long serialVersionUID = 1L;
    /** The protocol whose settings are the ones a new protocol starts with, as on the JSFTemplating page. */
    private static final String SAMPLE_PROTOCOL = "admin-listener";

    @Inject
    private AdminRestService rest;

    private String configName;
    private final Settings protocol = new Settings();
    private final HttpSettings http = new HttpSettings();
    private final Settings fileCache = new Settings();

    @PostConstruct
    protected void open() {
        String config = GrizzlyTabs.parameter("configName");
        configName = config == null || config.isEmpty() ? HttpListeners.DEFAULT_CONFIG : config;
        protocol.replace(rest.defaults(HttpListeners.protocolsUrl(rest, configName)));
        protocol.getValues().put("target", configName);
        http.setVirtualServers(HttpListeners.virtualServers(rest, configName));
        http.replace(rest.defaults(sampleUrl("http")));
        http.getValues().put("defaultVirtualServer", "server");
        fileCache.replace(rest.defaults(sampleUrl("http", "file-cache")));
    }

    /** Creates the protocol, its HTTP settings and their file cache. */
    public void create() {
        String name = protocol.text("name");
        try {
            if (rest.childNames(HttpListeners.protocolsUrl(rest, configName)).contains(name)) {
                ConsoleMessages.error(WebStrings.get("grizzly.protocol.alreadyExist", name));
                return;
            }
            rest.create(HttpListeners.protocolsUrl(rest, configName), protocol.toSend(), List.of("securityEnabled"));

            // create-http takes only the virtual server; the rest of the settings are saved right after it
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("target", configName);
            created.put("defaultVirtualServer", http.text("defaultVirtualServer"));
            rest.create(protocolUrl(name, "create-http"), created, List.of());
            rest.create(protocolUrl(name, "http"), http.toSend(), HttpSettings.BOOLEANS);
            rest.create(protocolUrl(name, "http", "file-cache"), fileCache.toSend(), FileCacheEditView.BOOLEANS);
            loadPage(getListPage());
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    public String getConfigName() {
        return configName;
    }

    /** The name and the security of the protocol itself. */
    public Settings getProtocol() {
        return protocol;
    }

    public HttpSettings getHttp() {
        return http;
    }

    public Settings getFileCache() {
        return fileCache;
    }

    public String getListPage() {
        return GrizzlyTabs.contextPath() + "/web/grizzly/protocols.jsf?configName=" + GrizzlyTabs.encode(configName);
    }

    private String protocolUrl(String name, String... segments) {
        return rest.child(rest.child(HttpListeners.protocolsUrl(rest, configName), name), segments);
    }

    /** The resource whose default values a new protocol starts with. */
    private String sampleUrl(String... segments) {
        return rest.child(rest.child(HttpListeners.protocolsUrl(rest, configName), SAMPLE_PROTOCOL), segments);
    }

    private static void loadPage(String url) {
        FacesContext.getCurrentInstance().getPartialViewContext().getEvalScripts()
                .add("admingui.ajax.loadPage({url: '" + url + "'});");
    }
}
