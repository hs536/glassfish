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

import jakarta.inject.Inject;

import java.io.Serializable;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * The HTTP settings of a protocol, shown as the HTTP tab.
 *
 * <p>
 * Prototype (adr/0008): the Facelets counterpart of {@code web/grizzly/http.layout} and {@code httpAttr.inc}, which the
 * pages of a protocol and of a network listener share. A protocol without HTTP settings only shows them; the
 * JSFTemplating page has no Save button either in that case.
 */
public abstract class HttpEditView implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    protected AdminRestService rest;

    private GrizzlyTabs tabs;
    private final HttpSettings http = new HttpSettings();
    private boolean found;
    private String previousVirtualServer = "";

    /** Reads the page parameters and then the settings. */
    protected void open(String defaultCancelTo) {
        tabs = GrizzlyTabs.fromRequest(defaultCancelTo);
        load();
    }

    private void load() {
        http.setVirtualServers(HttpListeners.virtualServers(rest, tabs.getConfigName()));
        Map<String, Object> attributes = rest.attributesOrEmpty(httpUrl());
        found = !attributes.isEmpty();
        if (found) {
            http.replace(attributes);
            previousVirtualServer = http.text("defaultVirtualServer");
        } else {
            http.replace(rest.defaultsOrEmpty(httpUrl()));
            http.getValues().put("defaultVirtualServer", "server");
            previousVirtualServer = "";
        }
    }

    /** Saves the settings and moves the listeners of the protocol when the default virtual server changed. */
    public void save() {
        try {
            rest.create(httpUrl(), http.toSend(), HttpSettings.BOOLEANS);
            moveListeners();
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** Puts the values the server would use for new HTTP settings into the fields, without saving them. */
    public void loadDefaults() {
        http.getValues().putAll(rest.defaultsOrEmpty(httpUrl()));
    }

    /**
     * Moves every network listener of this protocol from the virtual server it was on to the one that is chosen now.
     * The JSFTemplating page only removes them from the old one (issue B-46).
     */
    private void moveListeners() {
        String chosen = http.text("defaultVirtualServer");
        if (previousVirtualServer.isEmpty() || previousVirtualServer.equals(chosen)) {
            return;
        }
        for (String listener : HttpListeners.listenersOf(rest, tabs.getConfigName(), tabs.getProtocolName())) {
            HttpListeners.moveToVirtualServer(rest, tabs.getConfigName(), listener, previousVirtualServer, chosen);
        }
        previousVirtualServer = chosen;
    }

    /** The pages of the other tabs and the page to go back to. */
    public GrizzlyTabs getTabs() {
        return tabs;
    }

    public String getConfigName() {
        return tabs.getConfigName();
    }

    public String getProtocolName() {
        return tabs.getProtocolName();
    }

    /** True when the protocol has HTTP settings, which are the only ones the page saves. */
    public boolean isFound() {
        return found;
    }

    /** The settings the page shows. */
    public HttpSettings getSettings() {
        return http;
    }

    private String httpUrl() {
        return rest.child(HttpListeners.protocolsUrl(rest, tabs.getConfigName()), tabs.getProtocolName(), "http");
    }
}
