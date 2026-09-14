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
package org.glassfish.admingui.common.util;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.validator.ValidatorException;
import jakarta.inject.Inject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The new page of a resource type: the attribute values, the enabled state for the chosen targets, the additional
 * properties, and the create action. A page bean extends this class and names the resource type and its list page.
 *
 * <p>
 * Prototype (adr/0008): the Facelets counterpart of the new button of
 * {@code common/resourceNode/resourceEditPageButtons.inc}. It sends the same admin REST requests: the resource with
 * the target {@code domain}, a resource reference for each chosen target, and the additional properties.
 */
public abstract class ResourceNewView implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private AdminRestService rest;

    @Inject
    private TargetSelection targets;

    private final Map<String, Object> values = new HashMap<>();
    private boolean enabled = true;
    private final PropertyRows properties = new PropertyRows();

    /** The REST child type of the resources, for example {@code jdbc-resource}. */
    protected abstract String childType();

    /** The list page shown after the resource is created, for example {@code /jdbc/jdbcResources.jsf}. */
    protected abstract String listPage();

    protected AdminRestService rest() {
        return rest;
    }

    @PostConstruct
    protected void loadDefaults() {
        values.putAll(rest.defaults(rest.url("resources", childType())));
    }

    /** The attribute values, bound by the page, for example {@code #{view.values['poolName']}}. */
    public Map<String, Object> getValues() {
        return values;
    }

    /** The enabled state of the resource references for the chosen targets. */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public PropertyRows getProperties() {
        return properties;
    }

    public void create() {
        FacesContext context = FacesContext.getCurrentInstance();
        String name = ResourceListView.text(values.get("name"));
        try {
            Map<String, Object> attributes = new HashMap<>(values);
            // The enabled state is kept on the resource references; the resource itself is always enabled
            attributes.put("enabled", "true");
            attributes.put("target", "domain");
            rest.create(rest.url("resources", childType()), attributes, List.of("enabled"));
            ResourceTargets resourceTargets = ResourceTargets.load(rest);
            for (String target : targets.getSelected()) {
                Map<String, Object> reference = Map.of("id", name, "enabled", String.valueOf(enabled), "target", target);
                rest.create(resourceTargets.referencesUrl(target), reference, List.of("enabled"));
            }
            rest.postJson(rest.url("resources", childType(), name, "property.json"), properties.toSend());
            String page = context.getExternalContext().getRequestContextPath() + listPage();
            context.getPartialViewContext().getEvalScripts().add("admingui.ajax.loadPage({url: '" + page + "'});");
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** A JNDI name must not contain a backslash (as {@code checkForBackslash} checks on the JSFTemplating pages). */
    public void validateName(FacesContext context, UIComponent component, Object value) {
        if (value != null && value.toString().contains("\\")) {
            String message = ConsoleMessages.core("msg.JS.resources.resName") + " " + ConsoleMessages.core("common.jndiName");
            throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR, message, null));
        }
    }
}
