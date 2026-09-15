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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * The edit security map page of a connector connection pool (prototype, adr/0008): the user groups or principals, and
 * the password of the backend principal. The password is not shown (S-20): an empty password field keeps the password.
 *
 * <p>
 * The Facelets counterpart of {@code jca/connectorSecurityMapEdit.jsf}, {@code jca/connectorSecurityMapAttr.inc},
 * {@code jca/securityMapButtons.inc} and the script {@code jca/securityMapjs.inc}.
 */
@Named
@ViewScoped
public class ConnectorSecurityMapEditView implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private AdminRestService rest;

    private String poolName;
    private String mapName;
    private boolean found;
    private String option = SecurityMapNames.USERS;
    private String userGroups;
    private String principals;
    private String userName;
    private String password;

    @PostConstruct
    protected void load() {
        if (poolName == null) {
            Map<String, String> parameters = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
            poolName = parameters.get("name");
            mapName = parameters.get("mapName");
        }
        found = poolName != null && !poolName.isEmpty() && mapName != null && !mapName.isEmpty()
                && !rest.attributes(mapUrl()).isEmpty();
        if (!found) {
            return;
        }
        List<String> groups = SecurityMapNames.read(rest, mapUrl() + "/user-group");
        List<String> principalNames = SecurityMapNames.read(rest, mapUrl() + "/principal");
        userGroups = SecurityMapNames.join(groups);
        principals = SecurityMapNames.join(principalNames);
        option = groups.isEmpty() && !principalNames.isEmpty() ? SecurityMapNames.PRINCIPALS : SecurityMapNames.USERS;
        Object backendUser = rest.attributes(mapUrl() + "/backend-principal").get("userName");
        userName = backendUser == null ? "" : backendUser.toString();
        password = null;
    }

    /**
     * Saves the map with {@code update-connector-security-map}, as the JSFTemplating page does: the names of the chosen
     * option that the map does not have are added, and the other names of the map are removed. A name in both lists is
     * neither added nor removed, since the command cannot do both.
     */
    public void save() {
        boolean users = SecurityMapNames.USERS.equals(option);
        List<String> added = new ArrayList<>(SecurityMapNames.split(users ? userGroups : principals));
        if (added.isEmpty()) {
            ConsoleMessages.error(JcaStrings.get(users ? "msg.JS.securityMap.enterUserGroups" : "msg.JS.securityMap.enterPrincipals"));
            return;
        }
        try {
            List<String> removedGroups = new ArrayList<>(SecurityMapNames.read(rest, mapUrl() + "/user-group"));
            List<String> removedPrincipals = new ArrayList<>(SecurityMapNames.read(rest, mapUrl() + "/principal"));
            List<String> current = users ? removedGroups : removedPrincipals;
            List<String> kept = added.stream().filter(current::contains).toList();
            added.removeAll(kept);
            current.removeAll(kept);

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("poolname", poolName);
            attributes.put("mapname", mapName);
            attributes.put(users ? "addusergroups" : "addprincipals", SecurityMapNames.join(added));
            attributes.put("removeprincipals", SecurityMapNames.join(removedPrincipals));
            attributes.put("removeusergroups", SecurityMapNames.join(removedGroups));
            if (password != null && !password.isEmpty()) {
                attributes.put("mappedpassword", password);
            }
            rest.post(rest.url("resources", "update-connector-security-map"), attributes);
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** Choosing an option empties the field of the other option, as the JSFTemplating page does. */
    public void optionChanged() {
        if (SecurityMapNames.USERS.equals(option)) {
            principals = "";
        } else {
            userGroups = "";
        }
    }

    public String getPoolName() {
        return poolName;
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

    public boolean isUsers() {
        return SecurityMapNames.USERS.equals(option);
    }

    public String getUserGroups() {
        return userGroups;
    }

    public void setUserGroups(String userGroups) {
        this.userGroups = userGroups;
    }

    public String getPrincipals() {
        return principals;
    }

    public void setPrincipals(String principals) {
        this.principals = principals;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getListPage() {
        return page("/jca/connectorSecurityMaps.jsf");
    }

    public String getGeneralPage() {
        return page("/jca/connectorConnectionPoolEdit.jsf");
    }

    public String getAdvancedPage() {
        return page("/jca/connectorConnectionPoolAdvance.jsf");
    }

    public String getPropertiesPage() {
        return page("/jca/connectorConnectionPoolProperty.jsf");
    }

    private String page(String page) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + page + "?name="
                + URLEncoder.encode(poolName, StandardCharsets.UTF_8);
    }

    private String mapUrl() {
        return rest.url("resources", ConnectorConnectionPoolEditView.CHILD_TYPE, poolName, ConnectorSecurityMapsView.CHILD_TYPE, mapName);
    }
}
