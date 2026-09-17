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
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;
import org.glassfish.admingui.common.util.Settings;

/**
 * An existing protocol, shown as the first of its tabs.
 *
 * <p>
 * Prototype (adr/0008, adr/0009): the Facelets version of {@code web/grizzly/protocolEdit.jsf}. The protocols of the
 * admin console are shown but not changed, and the protocols of the secure admin have no other tabs, as on the
 * JSFTemplating page.
 */
@Named
@ViewScoped
public class ProtocolEditView implements Serializable {

    private static final long serialVersionUID = 1L;
    /** The protocols the console does not change, which the JSFTemplating page names as constants of the server. */
    private static final List<String> ADMIN_PROTOCOLS = List.of("sec-admin-listener", "admin-listener");
    /** The protocols of the secure admin, whose other tabs the JSFTemplating page hides. */
    private static final List<String> SECURE_ADMIN_PROTOCOLS = List.of("sec-admin-listener", "admin-http-redirect");
    private static final String ADMIN_VIRTUAL_SERVER = "__asadmin";
    private static final String SECURE_ADMIN_ENABLED = "secureAdminEnabled";

    @Inject
    private AdminRestService rest;

    private GrizzlyTabs tabs;
    private final Settings protocol = new Settings();
    private boolean found;
    private boolean otherTabs = true;

    @PostConstruct
    protected void open() {
        tabs = GrizzlyTabs.fromRequest("web/grizzly/protocols.jsf");
        load();
        otherTabs = !isSecureAdminProtocol();
    }

    private void load() {
        Map<String, Object> attributes = rest.attributesOrEmpty(protocolUrl());
        found = !attributes.isEmpty();
        protocol.replace(attributes);
    }

    public void save() {
        try {
            rest.create(protocolUrl(), protocol.toSend(), List.of("securityEnabled"));
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** The pages of the other tabs and the page to go back to. */
    public GrizzlyTabs getTabs() {
        return tabs;
    }

    public String getConfigName() {
        return tabs.getConfigName();
    }

    public String getName() {
        return tabs.getProtocolName();
    }

    public boolean isFound() {
        return found;
    }

    /** The security of the protocol. */
    public Settings getProtocol() {
        return protocol;
    }

    /** The protocols of the admin console are shown as they are, as on the JSFTemplating page. */
    public boolean isReadOnly() {
        return ADMIN_PROTOCOLS.contains(tabs.getProtocolName());
    }

    /** False for a protocol of the secure admin, whose other tabs the JSFTemplating page hides. */
    public boolean isOtherTabs() {
        return otherTabs;
    }

    /** True when the secure admin is on and this protocol is one of its own or the one of the admin listener. */
    private boolean isSecureAdminProtocol() {
        Object secureAdmin = FacesContext.getCurrentInstance().getExternalContext().getSessionMap().get(SECURE_ADMIN_ENABLED);
        if (!Boolean.parseBoolean(HttpListeners.text(secureAdmin))) {
            return false;
        }
        if (SECURE_ADMIN_PROTOCOLS.contains(tabs.getProtocolName())) {
            return true;
        }
        Map<String, Object> adminVirtualServer = rest.attributesOrEmpty(
                rest.child(HttpListeners.virtualServersUrl(rest, tabs.getConfigName()), ADMIN_VIRTUAL_SERVER));
        String listener = HttpListeners.text(adminVirtualServer.get("networkListeners"));
        return !listener.isEmpty()
                && tabs.getProtocolName().equals(HttpListeners.protocolOf(rest, tabs.getConfigName(), listener));
    }

    private String protocolUrl() {
        return rest.child(HttpListeners.protocolsUrl(rest, tabs.getConfigName()), tabs.getProtocolName());
    }
}
