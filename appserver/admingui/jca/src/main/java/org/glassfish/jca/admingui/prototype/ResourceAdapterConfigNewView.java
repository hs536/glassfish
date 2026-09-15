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
import java.util.Objects;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;
import org.glassfish.admingui.common.util.PropertyRows;

/**
 * A new resource adapter config.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code jca/resourceAdapterConfigNew.jsf}. The name
 * is either a deployed resource adapter chosen from the list, whose configuration properties fill the table, or a name
 * typed in the field.
 */
@Named
@ViewScoped
public class ResourceAdapterConfigNewView implements Serializable {

    private static final long serialVersionUID = 1L;
    /** The option that takes the name from the list of the deployed resource adapters. */
    static final String FROM_LIST = "dropdown";
    /** The option that takes the name from the text field. */
    static final String TYPED = "text";

    @Inject
    private AdminRestService rest;

    private String nameOption = FROM_LIST;
    private String nameFromList = "";
    private String nameText;
    private String threadPoolIds = "";
    private final PropertyRows properties = new PropertyRows();
    private List<String> adapters = List.of();
    private List<String> threadPools = List.of();

    @PostConstruct
    protected void open() {
        adapters = ResourceAdapterConfigs.adapters(rest);
        threadPools = ResourceAdapterConfigs.threadPools(rest);
        nameFromList = adapters.isEmpty() ? "" : adapters.get(0);
        loadConfigProperties();
    }

    /** The properties follow the chosen name, so they are loaded when the option or the adapter changes. */
    public void setNameOption(String nameOption) {
        if (!Objects.equals(this.nameOption, nameOption)) {
            this.nameOption = nameOption;
            loadConfigProperties();
        }
    }

    public void setNameFromList(String nameFromList) {
        if (!Objects.equals(this.nameFromList, nameFromList)) {
            this.nameFromList = nameFromList;
            loadConfigProperties();
        }
    }

    /** Creates the config with its properties and opens the list of the configs. */
    public void create() {
        String name = isFromList() ? nameFromList : nameText;
        if (name == null || name.isEmpty()) {
            ConsoleMessages.error(JcaStrings.get("msg.Error.resourceAdapterNameCannotBeEmpty"));
            return;
        }
        if (name.contains("\\")) {
            ConsoleMessages.error(ConsoleMessages.core("msg.JS.resources.resName") + " " + JcaStrings.get("resourceAdapterConfig.Name"));
            return;
        }
        try {
            // Checked before the config is created, so that nothing is created when a confidential property does not match
            List<Map<String, String>> propertiesToSend = properties.toSend();
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("name", name);
            attributes.put("threadPoolIds", threadPoolIds);
            rest.create(rest.url("resources", ResourceAdapterConfigs.CHILD_TYPE), attributes, List.of());
            if (!propertiesToSend.isEmpty()) {
                rest.postJson(rest.url("resources", ResourceAdapterConfigs.CHILD_TYPE, name, "property.json"), propertiesToSend);
            }
            loadPage(getListPage());
        } catch (IllegalArgumentException e) {
            ConsoleMessages.error(e.getMessage());
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    public String getNameOption() {
        return nameOption;
    }

    public boolean isFromList() {
        return FROM_LIST.equals(nameOption);
    }

    public String getNameFromList() {
        return nameFromList;
    }

    public String getNameText() {
        return nameText;
    }

    public void setNameText(String nameText) {
        this.nameText = nameText;
    }

    public String getThreadPoolIds() {
        return threadPoolIds;
    }

    public void setThreadPoolIds(String threadPoolIds) {
        this.threadPoolIds = threadPoolIds;
    }

    public PropertyRows getProperties() {
        return properties;
    }

    public List<String> getAdapters() {
        return adapters;
    }

    public List<String> getThreadPools() {
        return threadPools;
    }

    public String getListPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/jca/resourceAdapterConfigs.jsf";
    }

    /** The properties of the chosen adapter; a typed name has none, as the JSFTemplating page shows none. */
    private void loadConfigProperties() {
        if (!isFromList()) {
            properties.replace(Map.of());
            return;
        }
        ResourceAdapterConfigs.ConfigProperties configProperties = ResourceAdapterConfigs.configProperties(rest, nameFromList);
        properties.replace(configProperties.values());
        properties.markConfidential(configProperties.confidential());
    }

    private static void loadPage(String url) {
        FacesContext.getCurrentInstance().getPartialViewContext().getEvalScripts().add("admingui.ajax.loadPage({url: '" + url + "'});");
    }
}
