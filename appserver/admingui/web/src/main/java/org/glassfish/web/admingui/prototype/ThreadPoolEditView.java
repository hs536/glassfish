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
 * An existing thread pool.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/configuration/threadPoolEdit.jsf}. The
 * Load Defaults button replaces the values that have a default with it, as the JSFTemplating page does.
 */
@Named
@ViewScoped
public class ThreadPoolEditView implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private AdminRestService rest;

    private String configName;
    private String name;
    private boolean found;
    private final Map<String, Object> values = new HashMap<>();

    @PostConstruct
    protected void load() {
        if (name == null) {
            Map<String, String> parameters = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
            String config = parameters.get("configName");
            configName = config == null || config.isEmpty() ? ThreadPools.DEFAULT_CONFIG : config;
            name = parameters.get("name");
        }
        values.clear();
        found = name != null && !name.isEmpty();
        if (!found) {
            return;
        }
        Map<String, Object> attributes = rest.attributes(selfUrl());
        found = !attributes.isEmpty();
        if (found) {
            values.putAll(attributes);
        }
    }

    public void save() {
        try {
            rest.create(selfUrl(), new HashMap<>(values), List.of("virtual"));
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** Replaces the values that have a default with the default, as the Load Defaults button of the JSFTemplating page does. */
    public void loadDefaults() {
        Map<String, String> defaults = rest.defaults(ThreadPools.url(rest, configName));
        for (String key : values.keySet()) {
            String value = defaults.get(key);
            if (value != null) {
                values.put(key, value);
            }
        }
    }

    public String getConfigName() {
        return configName;
    }

    public String getName() {
        return name;
    }

    public boolean isFound() {
        return found;
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

    public String getListPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                + "/web/configuration/threadPools.jsf?configName=" + URLEncoder.encode(configName, StandardCharsets.UTF_8);
    }

    private String selfUrl() {
        return rest.child(ThreadPools.url(rest, configName), name);
    }
}
