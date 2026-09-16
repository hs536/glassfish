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

import jakarta.faces.context.FacesContext;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The pages a protocol of the network configuration is edited on, and how they are reached.
 *
 * <p>
 * Prototype (adr/0008): the settings of a protocol are shown on four tabs, once for the protocol itself and once for a
 * network listener that uses it. Which pages the tabs open depends only on that; the tabs themselves are the fragment
 * {@code /web/grizzly/tabs.xhtml}.
 */
public class GrizzlyTabs implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String configName;
    private final String protocolName;
    private final String listenerName;
    private final String cancelTo;

    /** The page the parameters of the request point at. */
    public static GrizzlyTabs fromRequest(String defaultCancelTo) {
        String config = parameter("configName");
        String cancel = parameter("cancelTo");
        return new GrizzlyTabs(config == null || config.isEmpty() ? HttpListeners.DEFAULT_CONFIG : config,
                text(parameter("name")), text(parameter("listenerName")),
                cancel == null || cancel.isEmpty() ? defaultCancelTo : cancel);
    }

    public GrizzlyTabs(String configName, String protocolName, String listenerName, String cancelTo) {
        this.configName = configName;
        this.protocolName = protocolName;
        this.listenerName = listenerName;
        this.cancelTo = cancelTo;
    }

    public String getConfigName() {
        return configName;
    }

    public String getProtocolName() {
        return protocolName;
    }

    public String getListenerName() {
        return listenerName;
    }

    /** True when the pages were opened from a network listener, which decides the tabs and the first one of them. */
    public boolean isListener() {
        return !listenerName.isEmpty();
    }

    /** The first tab: the listener itself, or the protocol itself. */
    public String getGeneralPage() {
        return isListener()
                ? page("networkListenerEdit.jsf", listenerName, false)
                : page("protocolEdit.jsf", protocolName, false);
    }

    public String getSslPage() {
        return page(isListener() ? "listenerSSLEdit.jsf" : "protocolSSLEdit.jsf", protocolName, true);
    }

    public String getHttpPage() {
        return page(isListener() ? "listenerHttpEdit.jsf" : "protocolHttp.jsf", protocolName, true);
    }

    public String getFileCachePage() {
        return page(isListener() ? "listenerFileCache.jsf" : "protocolFileCache.jsf", protocolName, true);
    }

    /** The page the Cancel button goes back to, which the page that opened this one chose. */
    public String getCancelPage() {
        return contextPath() + "/" + cancelTo + "?configName=" + encode(configName);
    }

    private String page(String name, String pageName, boolean withListener) {
        return contextPath() + "/web/grizzly/" + name + "?configName=" + encode(configName) + "&name=" + encode(pageName)
                + (withListener && isListener() ? "&listenerName=" + encode(listenerName) : "") + "&cancelTo=" + cancelTo;
    }

    static String parameter(String name) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get(name);
    }

    static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    static String contextPath() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath();
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
