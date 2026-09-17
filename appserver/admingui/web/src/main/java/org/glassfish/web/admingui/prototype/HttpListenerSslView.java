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
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import java.util.Map;

/**
 * The SSL tab of an HTTP listener.
 *
 * <p>
 * Prototype (adr/0006, adr/0008): the Facelets version of {@code web/configuration/httpListenerSSL.jsf}. The settings
 * are those of the protocol of the listener, as on the SSL tab of a network listener; only the two tabs above the page
 * are the ones of an HTTP listener.
 */
@Named
@ViewScoped
public class HttpListenerSslView extends GrizzlySslView {

    private static final long serialVersionUID = 1L;

    @PostConstruct
    protected void start() {
        open("web/configuration/httpListeners.jsf");
    }

    @Override
    protected String createSslUrl() {
        return rest.child(HttpListeners.listenersUrl(rest, getConfigName()), getTabs().getListenerName(), "create-ssl");
    }

    @Override
    protected Map<String, Object> createParameters() {
        return Map.of("target", getConfigName());
    }

    /** The other tab of an HTTP listener, which is the listener itself. */
    public String getGeneralPage() {
        return GrizzlyTabs.contextPath() + "/web/configuration/httpListenerEdit.jsf?configName="
                + GrizzlyTabs.encode(getConfigName()) + "&name=" + GrizzlyTabs.encode(getTabs().getListenerName())
                + "&cancelTo=web/configuration/httpListeners.jsf";
    }
}
