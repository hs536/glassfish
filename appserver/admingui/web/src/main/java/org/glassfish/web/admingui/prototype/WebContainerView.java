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
import jakarta.inject.Inject;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;
import org.glassfish.admingui.common.util.PropertyRows;

/**
 * A page of the web container of a configuration.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the four pages of the web container edit one configuration object each and
 * show the same tabs. This class holds what they have in common: the attributes, the additional properties and the
 * links of the tabs.
 */
public abstract class WebContainerView implements Serializable {

    private static final long serialVersionUID = 1L;
    static final String DEFAULT_CONFIG = "server-config";

    @Inject
    private AdminRestService rest;

    private String configName;
    private final Map<String, Object> values = new HashMap<>();
    private PropertyRows properties = new PropertyRows();

    /** The path of the configuration object the page edits, below {@code configs/config/<name>}. */
    protected abstract List<String> path();

    /** False for the page that has no attributes of its own, only additional properties. */
    protected boolean hasAttributes() {
        return true;
    }

    protected AdminRestService rest() {
        return rest;
    }

    @PostConstruct
    protected void load() {
        if (configName == null) {
            String name = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("configName");
            configName = name == null || name.isEmpty() ? DEFAULT_CONFIG : name;
        }
        values.clear();
        if (hasAttributes()) {
            values.putAll(rest.attributes(selfUrl()));
        }
        properties = PropertyRows.read(rest, selfUrl());
    }

    /** Saves the attributes, when the page has them, and then the additional properties. */
    public void save() {
        try {
            List<Map<String, String>> propertiesToSend = properties.toSend();
            if (hasAttributes()) {
                rest.create(selfUrl(), new HashMap<>(values), List.of());
            }
            rest.postJson(selfUrl() + "/property.json", propertiesToSend);
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (IllegalArgumentException e) {
            ConsoleMessages.error(e.getMessage());
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

    public PropertyRows getProperties() {
        return properties;
    }

    public String getGeneralPage() {
        return page("webContainerGeneral.jsf");
    }

    public String getSessionPage() {
        return page("webContainerSession.jsf");
    }

    public String getManagerPage() {
        return page("webContainerManager.jsf");
    }

    public String getStorePage() {
        return page("webContainerStore.jsf");
    }

    private String page(String page) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/web/configuration/" + page + "?configName="
                + URLEncoder.encode(configName, StandardCharsets.UTF_8);
    }

    private String selfUrl() {
        String url = rest.url("configs", "config", configName);
        for (String segment : path()) {
            url = rest.child(url, segment);
        }
        return url;
    }
}
