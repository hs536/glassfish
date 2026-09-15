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
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;
import org.glassfish.admingui.common.util.PropertyRows;

/**
 * The Additional Properties tab of a connector connection pool (prototype, adr/0008). The properties that the
 * resource adapter declares confidential for the connection definition are entered twice in password fields. The other
 * tabs are other pages. Pools of an application are not covered (X-27).
 *
 * <p>
 * The Facelets counterpart of {@code jca/connectorConnectionPoolProperty.jsf} with
 * {@code resourceNode/poolNameSection.inc} and {@code resourceNode/confidentialPropsTable.inc}.
 */
@Named
@ViewScoped
public class ConnectorConnectionPoolPropertyView implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private AdminRestService rest;

    private String name;
    private boolean found;
    private boolean ping;
    private PropertyRows properties = new PropertyRows();

    @PostConstruct
    protected void load() {
        if (name == null) {
            name = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("name");
        }
        found = name != null && !name.isEmpty();
        if (!found) {
            return;
        }
        Map<String, Object> attributes = rest.attributes(poolUrl());
        found = !attributes.isEmpty();
        if (!found) {
            return;
        }
        ping = "true".equals(String.valueOf(attributes.get("ping")));
        properties = PropertyRows.read(rest, poolUrl());
        properties.markConfidential(confidentialNames(text(attributes.get("connectionDefinitionName")), text(attributes.get("resourceAdapterName"))));
    }

    /**
     * Saves the properties; nothing is saved when the two values of a confidential property differ. When the pool has
     * Ping set, the pool is pinged after the save and the result is shown instead of the saved message, as on the
     * JSFTemplating page.
     */
    public void save() {
        try {
            rest.postJson(poolUrl() + "/property.json", properties.toSend());
            if (ping) {
                pingAfterSave();
            } else {
                ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
            }
            load();
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

    public PropertyRows getProperties() {
        return properties;
    }

    public String getGeneralPage() {
        return tabPage("/jca/connectorConnectionPoolEdit.jsf");
    }

    public String getAdvancedPage() {
        return tabPage("/jca/connectorConnectionPoolAdvance.jsf");
    }

    public String getSecurityMapsPage() {
        return tabPage("/jca/connectorSecurityMaps.jsf");
    }

    private void pingAfterSave() {
        try {
            rest.get(rest.url("resources", "ping-connection-pool.json"), Map.of("id", name));
            ConsoleMessages.info(ConsoleMessages.core("msg.PingSucceed"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(JcaStrings.get("msg.warning.poolSavedPingFailed"));
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** The configuration properties that the resource adapter declares confidential for the connection definition. */
    private List<String> confidentialNames(String connectionDefinition, String adapter) {
        Map<String, Object> response = rest.get(rest.url("resources", "get-mcf-config-properties"),
                Map.of("connectionDefnName", connectionDefinition, "rarname", adapter));
        Object names = AdminRestService.extraProperties(response).get("confidentialConfigProps");
        return names instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private String tabPage(String page) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + page + "?name="
                + URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    private String poolUrl() {
        return rest.url("resources", ConnectorConnectionPoolEditView.CHILD_TYPE, name);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
