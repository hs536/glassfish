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
 * A new thread pool.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/configuration/threadPoolNew.jsf}.
 */
@Named
@ViewScoped
public class ThreadPoolNewView implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private AdminRestService rest;

    private String configName;
    private final Map<String, Object> values = new HashMap<>();

    @PostConstruct
    protected void open() {
        String config = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("configName");
        configName = config == null || config.isEmpty() ? ThreadPools.DEFAULT_CONFIG : config;
        values.putAll(rest.defaults(ThreadPools.url(rest, configName)));
    }

    public void create() {
        try {
            Map<String, Object> attributes = new HashMap<>(values);
            // An attribute without a value is left to the server, as on the JSFTemplating page (X-29)
            attributes.values().removeIf(value -> value == null || value.toString().isEmpty());
            attributes.put("target", configName);
            rest.create(ThreadPools.url(rest, configName), attributes, List.of("virtual"));
            loadPage(getListPage());
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    public String getConfigName() {
        return configName;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    /** The checkbox of the attribute, which is sent as the text {@code true} or {@code false}. */
    public boolean isVirtual() {
        return Boolean.parseBoolean(ThreadPools.text(values.get("virtual")));
    }

    public void setVirtual(boolean virtual) {
        values.put("virtual", String.valueOf(virtual));
    }

    public String getName() {
        return ThreadPools.text(values.get("name"));
    }

    public void setName(String name) {
        values.put("name", name);
    }

    public String getListPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                + "/web/configuration/threadPools.jsf?configName=" + URLEncoder.encode(configName, StandardCharsets.UTF_8);
    }

    private static void loadPage(String url) {
        FacesContext.getCurrentInstance().getPartialViewContext().getEvalScripts().add("admingui.ajax.loadPage({url: '" + url + "'});");
    }
}
