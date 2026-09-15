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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * An existing work security map.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code jca/workSecurityMapEdit.jsf}. A map keeps the
 * kind of its mappings: the option of the other kind is disabled.
 */
@Named
@ViewScoped
public class WorkSecurityMapEditView implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String UPDATE_COMMAND = "update-connector-work-security-map";

    @Inject
    private AdminRestService rest;

    private String mapName;
    private boolean found;
    private Map<String, Object> attributes = Map.of();
    private String option = SecurityMapNames.USERS;
    private boolean groupsMapped;
    private boolean principalsMapped;
    private String groups;
    private String principals;
    private String adapter;
    private String description;
    private String deploymentOrder;
    private List<String> adapters = List.of();

    @PostConstruct
    protected void load() {
        if (mapName == null) {
            mapName = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("mapName");
        }
        attributes = mapName == null || mapName.isEmpty() ? Map.of() : rest.attributes(mapUrl());
        found = !attributes.isEmpty();
        if (!found) {
            return;
        }
        adapter = text(attributes.get("resourceAdapterName"));
        description = text(attributes.get("description"));
        deploymentOrder = text(attributes.get("deploymentOrder"));
        Map<String, String> groupMappings = WorkSecurityMappings.read(rest, mapUrl(), WorkSecurityMappings.GROUPS);
        Map<String, String> principalMappings = groupMappings.isEmpty()
                ? WorkSecurityMappings.read(rest, mapUrl(), WorkSecurityMappings.PRINCIPALS) : Map.of();
        groupsMapped = !groupMappings.isEmpty();
        principalsMapped = !principalMappings.isEmpty();
        groups = WorkSecurityMappings.format(groupMappings);
        principals = WorkSecurityMappings.format(principalMappings);
        option = groupsMapped ? SecurityMapNames.USERS : SecurityMapNames.PRINCIPALS;
        adapters = WorkSecurityMappings.adapters(rest);
    }

    /**
     * Saves the map, as the JSFTemplating page does: first its attributes, then the mappings of its kind. A mapping of an
     * EIS name the map has is changed when its mapped name differs, the other mappings are added, and the mappings that
     * are no longer in the field are removed.
     */
    public void save() {
        WorkSecurityMappings.Kind kind = isGroupsOption() ? WorkSecurityMappings.GROUPS : WorkSecurityMappings.PRINCIPALS;
        Map<String, String> mappings = WorkSecurityMappings.parse(isGroupsOption() ? groups : principals);
        if (mappings.isEmpty()) {
            ConsoleMessages.error(emptyMappingMessage(isGroupsOption()));
            return;
        }
        try {
            Map<String, Object> changed = new HashMap<>(attributes);
            changed.remove("enabled");
            changed.put("resourceAdapterName", adapter);
            changed.put("description", description);
            changed.put("deploymentOrder", deploymentOrder);
            rest.create(mapUrl(), changed, List.of());

            Map<String, String> current = new HashMap<>(WorkSecurityMappings.read(rest, mapUrl(), kind));
            List<String> added = new ArrayList<>();
            for (Map.Entry<String, String> mapping : mappings.entrySet()) {
                String mapped = current.remove(mapping.getKey());
                if (mapped == null) {
                    added.add(mapping.getKey() + "=" + mapping.getValue());
                } else if (!mapped.equals(mapping.getValue())) {
                    rest.post(rest.child(mapUrl(), kind.childType(), mapping.getKey()), Map.of(kind.mappedName(), mapping.getValue()));
                }
            }
            Map<String, Object> update = new HashMap<>();
            update.put("raname", adapter);
            update.put(kind.addParameter(), String.join(",", added));
            update.put(kind.removeParameter(), String.join(",", current.keySet()));
            rest.post(rest.child(mapUrl(), UPDATE_COMMAND), update);
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** The message when the field of the chosen option is empty, as {@code isSecurityMappingPresent} shows it. */
    static String emptyMappingMessage(boolean groups) {
        return ConsoleMessages.core("msg.JS.Error.securityMappingCannotBeEmpty") + " "
                + JcaStrings.get(groups ? "workSecurityMap.GroupMapping" : "workSecurityMap.PrincipalMapping");
    }

    public String getMapName() {
        return mapName;
    }

    public boolean isFound() {
        return found;
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

    /** True when the map has group mappings, so it cannot be changed to principal mappings. */
    public boolean isGroupsMapped() {
        return groupsMapped;
    }

    /** True when the map has principal mappings, so it cannot be changed to group mappings. */
    public boolean isPrincipalsMapped() {
        return principalsMapped;
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

    public String getDeploymentOrder() {
        return deploymentOrder;
    }

    public void setDeploymentOrder(String deploymentOrder) {
        this.deploymentOrder = deploymentOrder;
    }

    public String getListPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/jca/workSecurityMaps.jsf";
    }

    private String mapUrl() {
        return rest.url("resources", WorkSecurityMappings.CHILD_TYPE, mapName);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
