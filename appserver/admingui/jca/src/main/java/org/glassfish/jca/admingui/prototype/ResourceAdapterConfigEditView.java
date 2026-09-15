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
import org.glassfish.admingui.common.util.PropertyRows;

/**
 * An existing resource adapter config.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code jca/resourceAdapterConfigEdit.jsf}. The name
 * of the config is the name of its resource adapter and cannot be changed.
 */
@Named
@ViewScoped
public class ResourceAdapterConfigEditView implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private AdminRestService rest;

    private String name;
    private boolean found;
    private Map<String, Object> attributes = Map.of();
    private String threadPoolIds = "";
    private String deploymentOrder = "";
    private PropertyRows properties = new PropertyRows();
    private List<String> threadPools = List.of();

    @PostConstruct
    protected void load() {
        if (name == null) {
            name = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("name");
        }
        attributes = name == null || name.isEmpty() ? Map.of() : rest.attributes(selfUrl());
        found = !attributes.isEmpty();
        if (!found) {
            return;
        }
        threadPoolIds = text(attributes.get("threadPoolIds"));
        deploymentOrder = text(attributes.get("deploymentOrder"));
        threadPools = ResourceAdapterConfigs.threadPools(rest);
        properties = PropertyRows.read(rest, selfUrl());
        ResourceAdapterConfigs.markConfidential(rest, name, properties);
    }

    /** Saves the attributes of the config and then its properties, as the JSFTemplating page does. */
    public void save() {
        try {
            List<Map<String, String>> propertiesToSend = properties.toSend();
            Map<String, Object> changed = new HashMap<>(attributes);
            // The name of the config is the name of its resource adapter and is not sent again
            changed.remove("resourceAdapterName");
            changed.put("threadPoolIds", threadPoolIds);
            changed.put("deploymentOrder", deploymentOrder);
            rest.create(selfUrl(), changed, List.of());
            rest.postJson(selfUrl() + "/property.json", propertiesToSend);
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (IllegalArgumentException e) {
            ConsoleMessages.error(e.getMessage());
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    public String getName() {
        return name;
    }

    public boolean isFound() {
        return found;
    }

    public String getThreadPoolIds() {
        return threadPoolIds;
    }

    public void setThreadPoolIds(String threadPoolIds) {
        this.threadPoolIds = threadPoolIds;
    }

    public String getDeploymentOrder() {
        return deploymentOrder;
    }

    public void setDeploymentOrder(String deploymentOrder) {
        this.deploymentOrder = deploymentOrder;
    }

    public PropertyRows getProperties() {
        return properties;
    }

    public List<String> getThreadPools() {
        return threadPools;
    }

    public String getListPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/jca/resourceAdapterConfigs.jsf";
    }

    private String selfUrl() {
        return rest.url("resources", ResourceAdapterConfigs.CHILD_TYPE, name);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
