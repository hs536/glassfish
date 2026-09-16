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
import org.glassfish.admingui.common.util.PropertyRows;

/**
 * The HTTP service page: the single sign-on of the service and the rotation of its access log.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/configuration/accessLog.jsf}. The page
 * edits two configuration objects, the HTTP service and its access log, and the properties of the service.
 */
@Named
@ViewScoped
public class AccessLogView implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_CONFIG = "server-config";
    /** The rotation policies the JSFTemplating page offers. */
    private static final List<String> POLICIES = List.of("time");

    @Inject
    private AdminRestService rest;

    private String configName;
    private final Map<String, Object> serviceValues = new HashMap<>();
    private final Map<String, Object> logValues = new HashMap<>();
    private PropertyRows properties = new PropertyRows();

    @PostConstruct
    protected void load() {
        if (configName == null) {
            String name = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("configName");
            configName = name == null || name.isEmpty() ? DEFAULT_CONFIG : name;
        }
        serviceValues.clear();
        logValues.clear();
        serviceValues.putAll(rest.attributes(serviceUrl()));
        logValues.putAll(rest.attributes(logUrl()));
        properties = PropertyRows.read(rest, serviceUrl());
    }

    /** Saves the HTTP service, its access log and then the properties, as the JSFTemplating page does. */
    public void save() {
        try {
            List<Map<String, String>> propertiesToSend = properties.toSend();
            rest.create(serviceUrl(), new HashMap<>(serviceValues), List.of("ssoEnabled", "accessLoggingEnabled"));
            rest.create(logUrl(), new HashMap<>(logValues), List.of("rotationEnabled"));
            rest.postJson(serviceUrl() + "/property.json", propertiesToSend);
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

    public Map<String, Object> getLogValues() {
        return logValues;
    }

    public PropertyRows getProperties() {
        return properties;
    }

    public List<String> getPolicies() {
        return POLICIES;
    }

    /** The check boxes of the attributes, which are sent as the text {@code true} or {@code false}. */
    public boolean isSsoEnabled() {
        return flag(serviceValues, "ssoEnabled");
    }

    public void setSsoEnabled(boolean value) {
        serviceValues.put("ssoEnabled", String.valueOf(value));
    }

    public boolean isAccessLoggingEnabled() {
        return flag(serviceValues, "accessLoggingEnabled");
    }

    public void setAccessLoggingEnabled(boolean value) {
        serviceValues.put("accessLoggingEnabled", String.valueOf(value));
    }

    public boolean isRotationEnabled() {
        return flag(logValues, "rotationEnabled");
    }

    public void setRotationEnabled(boolean value) {
        logValues.put("rotationEnabled", String.valueOf(value));
    }

    private static boolean flag(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private String serviceUrl() {
        return rest.url("configs", "config", configName, "http-service");
    }

    private String logUrl() {
        return rest.child(serviceUrl(), "access-log");
    }
}
