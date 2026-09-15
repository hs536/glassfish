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
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.validator.ValidatorException;
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
 * A new work security map.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code jca/workSecurityMapNew.jsf}. The map is
 * created with the group mappings or the principal mappings, whichever option is chosen.
 */
@Named
@ViewScoped
public class WorkSecurityMapNewView implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private AdminRestService rest;

    private String mapName;
    private String option = SecurityMapNames.USERS;
    private String groups;
    private String principals;
    private String adapter;
    private String description;
    private List<String> adapters = List.of();

    @PostConstruct
    protected void open() {
        adapters = WorkSecurityMappings.adapters(rest);
        adapter = adapters.isEmpty() ? "" : adapters.get(0);
    }

    /** Creates the map and opens the list of the maps. */
    public void create() {
        WorkSecurityMappings.Kind kind = isGroupsOption() ? WorkSecurityMappings.GROUPS : WorkSecurityMappings.PRINCIPALS;
        Map<String, String> mappings = WorkSecurityMappings.parse(isGroupsOption() ? groups : principals);
        if (mappings.isEmpty()) {
            ConsoleMessages.error(WorkSecurityMapEditView.emptyMappingMessage(isGroupsOption()));
            return;
        }
        try {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("id", mapName);
            attributes.put("raname", adapter);
            if (description != null && !description.isEmpty()) {
                attributes.put("description", description);
            }
            attributes.put(kind.createParameter(), WorkSecurityMappings.format(mappings));
            rest.create(rest.url("resources", WorkSecurityMappings.CHILD_TYPE), attributes, List.of());
            loadPage(getListPage());
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** A map name must not contain a backslash, as {@code checkForBackslash} checks on the JSFTemplating page. */
    public void validateName(FacesContext context, UIComponent component, Object value) {
        if (value != null && value.toString().contains("\\")) {
            String message = ConsoleMessages.core("msg.JS.resources.resName") + " " + JcaStrings.get("connectorSecurityMap.securityMapName");
            throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR, message, null));
        }
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public boolean isGroupsOption() {
        return SecurityMapNames.USERS.equals(option);
    }

    public String getGroups() {
        return groups;
    }

    public void setGroups(String groups) {
        this.groups = groups;
    }

    public String getPrincipals() {
        return principals;
    }

    public void setPrincipals(String principals) {
        this.principals = principals;
    }

    public String getAdapter() {
        return adapter;
    }

    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    public List<String> getAdapters() {
        return adapters;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getListPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/jca/workSecurityMaps.jsf";
    }

    private static void loadPage(String url) {
        FacesContext.getCurrentInstance().getPartialViewContext().getEvalScripts().add("admingui.ajax.loadPage({url: '" + url + "'});");
    }
}
