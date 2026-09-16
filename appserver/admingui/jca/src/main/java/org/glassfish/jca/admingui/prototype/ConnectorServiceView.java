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

package org.glassfish.jca.admingui.prototype;

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

/**
 * The connector service of a configuration.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code jca/connectorService.jsf}. The attributes
 * apply to every resource adapter of the configuration, so the page has one instance and no list.
 */
@Named
@ViewScoped
public class ConnectorServiceView implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_CONFIG = "server-config";
    /** The policies the JSFTemplating page offers. */
    private static final List<String> POLICIES = List.of("derived", "global");

    @Inject
    private AdminRestService rest;

    private String configName;
    private Map<String, Object> attributes = Map.of();
    private String shutdownTimeout = "";
    private String classLoadingPolicy = "";

    @PostConstruct
    protected void load() {
        if (configName == null) {
            String name = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("configName");
            configName = name == null || name.isEmpty() ? DEFAULT_CONFIG : name;
        }
        attributes = rest.attributes(serviceUrl());
        shutdownTimeout = text(attributes.get("shutdownTimeoutInSeconds"));
        classLoadingPolicy = text(attributes.get("classLoadingPolicy"));
    }

    public void save() {
        try {
            Map<String, Object> changed = new HashMap<>(attributes);
            changed.put("shutdownTimeoutInSeconds", shutdownTimeout);
            changed.put("classLoadingPolicy", classLoadingPolicy);
            rest.create(serviceUrl(), changed, List.of());
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    public String getConfigName() {
        return configName;
    }

    public String getShutdownTimeout() {
        return shutdownTimeout;
    }

    public void setShutdownTimeout(String shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public String getClassLoadingPolicy() {
        return classLoadingPolicy;
    }

    public void setClassLoadingPolicy(String classLoadingPolicy) {
        this.classLoadingPolicy = classLoadingPolicy;
    }

    public List<String> getPolicies() {
        return POLICIES;
    }

    private String serviceUrl() {
        return rest.url("configs", "config", configName, "connector-service");
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
