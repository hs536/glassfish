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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.AttributeFlags;
import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * The Advanced tab of a connector connection pool (prototype, adr/0008): the connection settings, with the save and
 * load defaults actions. The other tabs are other pages. Pools of an application are not covered (X-27).
 *
 * <p>
 * The Facelets counterpart of {@code jca/connectorConnectionPoolAdvance.jsf},
 * {@code jca/connectorConnectionPoolAdvancedAttr.inc} and {@code jca/connectorConnectionPoolAdvanceButtons.inc}, with
 * its script for lazy association.
 */
@Named
@ViewScoped
public class ConnectorConnectionPoolAdvanceView implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The attributes of the General tab, which this tab does not send. Unlike the list of the General tab, the name and
     * the deployment order are not in it, so they are sent again, as the JSFTemplating page does.
     */
    private static final List<String> GENERAL_ATTRIBUTES = List.of("resourceAdapterName", "connectionDefinitionName", "ping",
            "description", "steadyPoolSize", "maxPoolSize", "poolResizeQuantity", "idleTimeoutInSeconds", "maxWaitTimeInMillis",
            "isConnectionValidationRequired", "failAllConnections", "transactionSupport");

    /** The boolean attributes sent as {@code false} when they are not set, as the JSFTemplating page lists them. */
    private static final List<String> CONVERT_TO_FALSE = List.of("pooling", "failAllConnections", "isConnectionValidationRequired",
            "associateWithThread", "connectionLeakReclaim", "lazyConnectionAssociation", "lazyConnectionEnlistment", "matchConnections");

    private static final String ASSOCIATION = "lazyConnectionAssociation";
    private static final String ENLISTMENT = "lazyConnectionEnlistment";

    @Inject
    private AdminRestService rest;

    private String name;
    private boolean found;
    private final Map<String, Object> values = new HashMap<>();

    @PostConstruct
    protected void load() {
        if (name == null) {
            name = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("name");
        }
        values.clear();
        found = name != null && !name.isEmpty();
        if (!found) {
            return;
        }
        Map<String, Object> attributes = rest.attributes(poolUrl());
        found = !attributes.isEmpty();
        values.putAll(attributes);
        GENERAL_ATTRIBUTES.forEach(values::remove);
    }

    /**
     * Saves the attributes. Lazy association saves lazy enlistment too, and lazy enlistment is saved on its own first, as
     * the JSFTemplating page does (its GlassFish issue 15618).
     */
    public void save() {
        try {
            if (isEnlistmentFixed()) {
                values.put(ENLISTMENT, "true");
            }
            Map<String, Object> enlistment = new HashMap<>();
            enlistment.put(ENLISTMENT, values.get(ENLISTMENT));
            rest.create(poolUrl(), enlistment, List.of(ENLISTMENT));
            Map<String, Object> attributes = new HashMap<>(values);
            attributes.remove(ENLISTMENT);
            attributes.remove("jndiName");
            rest.create(poolUrl(), attributes, CONVERT_TO_FALSE);
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** Replaces the values that have a default with the default. */
    public void loadDefaults() {
        Map<String, String> defaults = rest.defaults(rest.url("resources", ConnectorConnectionPoolEditView.CHILD_TYPE));
        for (String key : values.keySet()) {
            String value = defaults.get(key);
            if (value != null) {
                values.put(key, value);
            }
        }
    }

    /** Lazy association needs lazy enlistment: checking it checks lazy enlistment, which the page then disables. */
    public void associationChanged() {
        if (isEnlistmentFixed()) {
            values.put(ENLISTMENT, "true");
        }
    }

    public String getName() {
        return name;
    }

    public boolean isFound() {
        return found;
    }

    /** The attribute values, bound by the page. */
    public Map<String, Object> getValues() {
        return values;
    }

    public Map<String, Boolean> getFlags() {
        return new AttributeFlags(values);
    }

    public boolean isEnlistmentFixed() {
        return "true".equals(String.valueOf(values.get(ASSOCIATION)));
    }

    public String getGeneralPage() {
        return tabPage("/jca/connectorConnectionPoolEdit.jsf");
    }

    public String getPropertiesPage() {
        return tabPage("/jca/connectorConnectionPoolProperty.jsf");
    }

    public String getSecurityMapsPage() {
        return tabPage("/jca/connectorSecurityMaps.jsf");
    }

    private String tabPage(String page) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + page + "?name="
                + URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    private String poolUrl() {
        return rest.url("resources", ConnectorConnectionPoolEditView.CHILD_TYPE, name);
    }
}
