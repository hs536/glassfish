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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * The new security map page of a connector connection pool (prototype, adr/0008): the map name, the user groups or
 * principals, and the backend principal.
 *
 * <p>
 * The Facelets counterpart of {@code jca/connectorSecurityMapNew.jsf}, {@code jca/connectorSecurityMapAttr.inc},
 * {@code jca/securityMapButtons.inc} and the script {@code jca/securityMapjs.inc}.
 */
@Named
@ViewScoped
public class ConnectorSecurityMapNewView implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private AdminRestService rest;

    private String poolName;
    private String mapName;
    private String option = SecurityMapNames.USERS;
    private String userGroups;
    private String principals;
    private String userName;
    private String password;

    @PostConstruct
    protected void open() {
        poolName = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("name");
    }

    /** Creates the map with the names of the chosen option and opens the maps of the pool. */
    public void create() {
        boolean users = SecurityMapNames.USERS.equals(option);
        List<String> names = SecurityMapNames.split(users ? userGroups : principals);
        if (names.isEmpty()) {
            ConsoleMessages.error(JcaStrings.get(users ? "msg.JS.securityMap.enterUserGroups" : "msg.JS.securityMap.enterPrincipals"));
            return;
        }
        try {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("name", mapName);
            attributes.put("poolName", poolName);
            attributes.put(users ? "userGroups" : "principals", SecurityMapNames.join(names));
            attributes.put("mappedUserName", userName);
            attributes.put("mappedPassword", password);
            rest.create(rest.url("resources", ConnectorConnectionPoolEditView.CHILD_TYPE, poolName, ConnectorSecurityMapsView.CHILD_TYPE),
                    attributes, List.of());
            loadPage(getListPage());
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

    /** A map name must not contain a backslash, as {@code checkForBackslash} checks on the JSFTemplating page. */
    public void validateName(FacesContext context, UIComponent component, Object value) {
        if (value != null && value.toString().contains("\\")) {
            String message = ConsoleMessages.core("msg.JS.resources.resName") + " " + JcaStrings.get("connectorSecurityMap.securityMapName");
            throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR, message, null));
        }
    }

    public String getPoolName() {
        return poolName;
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

    public void setUserName(String userName) {
        this.userName = userName;
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

    private static void loadPage(String url) {
        FacesContext.getCurrentInstance().getPartialViewContext().getEvalScripts().add("admingui.ajax.loadPage({url: '" + url + "'});");
    }
}
