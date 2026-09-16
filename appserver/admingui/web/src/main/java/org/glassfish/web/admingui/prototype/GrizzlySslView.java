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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.glassfish.admingui.common.util.SslEditView;

/**
 * The SSL settings of a protocol of the network configuration.
 *
 * <p>
 * Prototype (adr/0006, adr/0008): what the SSL pages of a network listener and of a protocol have in common, which is
 * everything but the way the {@code <ssl>} element is created and the tabs above the page.
 */
public abstract class GrizzlySslView extends SslEditView {

    private static final long serialVersionUID = 1L;

    private String configName;
    private String protocolName;
    private String cancelTo;

    /** Reads the page parameters and then the settings. */
    protected void open() {
        String config = parameter("configName");
        configName = config == null || config.isEmpty() ? HttpListeners.DEFAULT_CONFIG : config;
        protocolName = text(parameter("name"));
        cancelTo = text(parameter("cancelTo"));
        load();
    }

    @Override
    protected String configName() {
        return configName;
    }

    @Override
    protected String sslUrl() {
        return rest.child(protocolUrl(), "ssl");
    }

    public String getConfigName() {
        return configName;
    }

    public String getProtocolName() {
        return protocolName;
    }

    /** The page the Cancel button goes back to, which the page that opened this one chose. */
    public String getCancelPage() {
        return contextPath() + "/" + cancelTo + "?configName=" + encode(configName);
    }

    protected String protocolUrl() {
        return rest.child(HttpListeners.protocolsUrl(rest, configName), protocolName);
    }

    /** The page of one of the other tabs, with the parameters they all take. */
    protected String tabPage(String page, String listenerName) {
        return contextPath() + "/web/grizzly/" + page + "?configName=" + encode(configName) + "&name=" + encode(protocolName)
                + (listenerName == null ? "" : "&listenerName=" + encode(listenerName)) + "&cancelTo=" + cancelTo;
    }

    protected static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
