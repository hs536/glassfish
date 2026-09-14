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
package org.glassfish.jdbc.admingui.prototype;

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
import java.util.ResourceBundle;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.PropertyRows;
import org.glassfish.admingui.common.util.TargetSelection;

/**
 * The new JDBC resource page.
 *
 * <p>
 * Prototype (docs/試作計画.md P-4, P-7). It sends the same admin REST requests as the JSFTemplating page
 * ({@code jdbc/jdbcResourceNew.jsf}, {@code common/resourceNode/resourceEditPageButtons.inc}): the resource with the
 * target {@code domain}, a resource reference for each chosen target, and the additional properties.
 */
@Named
@ViewScoped
public class JdbcResourceNewView implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String CHILD_TYPE = "jdbc-resource";
    private static final String CORE_STRINGS = "org.glassfish.admingui.core.Strings";
    private static final String JDBC_STRINGS = "org.glassfish.jdbc.admingui.Strings";
    private static final String LIST_PAGE = "/jdbc/jdbcResources.jsf";

    @Inject
    private AdminRestService rest;

    @Inject
    private TargetSelection targets;

    private Map<String, String> defaults = new HashMap<>();
    private List<String> pools = List.of();
    private String name = "";
    private String poolName;
    private String description = "";
    private boolean enabled = true;
    private final PropertyRows properties = new PropertyRows();

    @PostConstruct
    void load() {
        pools = rest.childNames(rest.url("resources", "jdbc-connection-pool"));
        defaults = rest.defaults(rest.url("resources", CHILD_TYPE));
        poolName = defaults.get("poolName");
    }

    public void create() {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            Map<String, Object> attributes = new HashMap<>(defaults);
            attributes.put("name", name);
            attributes.put("poolName", poolName);
            attributes.put("description", description);
            // The enabled state is kept on the resource references; the resource itself is always enabled
            attributes.put("enabled", "true");
            attributes.put("target", "domain");
            rest.create(rest.url("resources", CHILD_TYPE), attributes, List.of("enabled"));

            for (String target : targets.getSelected()) {
                String references = targets.isCluster(target)
                        ? rest.url("clusters", "cluster", target, "resource-ref")
                        : rest.url("servers", "server", target, "resource-ref");
                Map<String, Object> reference = Map.of("id", name, "enabled", String.valueOf(enabled), "target", target);
                rest.create(references, reference, List.of("enabled"));
            }

            rest.postJson(rest.url("resources", CHILD_TYPE, name, "property.json"), properties.toSend());
            String listPage = context.getExternalContext().getRequestContextPath() + LIST_PAGE;
            context.getPartialViewContext().getEvalScripts().add("admingui.ajax.loadPage({url: '" + listPage + "'});");
        } catch (RuntimeException e) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
        }
    }

    /** A JNDI name must not contain a backslash (as {@code checkForBackslash} checks on the JSFTemplating page). */
    public void validateName(FacesContext context, UIComponent component, Object value) {
        if (value != null && value.toString().contains("\\")) {
            String message = strings(CORE_STRINGS).getString("msg.JS.resources.resName") + " "
                    + strings(CORE_STRINGS).getString("common.jndiName");
            throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR, message, null));
        }
    }

    /** The help text of the pool field. The message contains markup and an expression for the context path. */
    public String getPoolHelp() {
        FacesContext context = FacesContext.getCurrentInstance();
        String text = strings(JDBC_STRINGS).getString("jdbcResource.poolHelp");
        return context.getApplication().evaluateExpressionGet(context, text, String.class);
    }

    private ResourceBundle strings(String baseName) {
        FacesContext context = FacesContext.getCurrentInstance();
        ClassLoader loader = JDBC_STRINGS.equals(baseName) ? getClass().getClassLoader() : Thread.currentThread().getContextClassLoader();
        return ResourceBundle.getBundle(baseName, context.getViewRoot().getLocale(), loader);
    }

    public List<String> getPools() {
        return pools;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public PropertyRows getProperties() {
        return properties;
    }
}
