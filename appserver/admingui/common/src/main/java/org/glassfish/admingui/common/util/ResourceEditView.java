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
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The edit page of a resource type: the attribute values, the status on its targets, the additional properties, and
 * the save and load defaults actions. A page bean extends this class and names the resource type and its edit page.
 *
 * <p>
 * Prototype (adr/0008): the Facelets counterpart of {@code common/resourceNode/resourceEditTabs.inc} and the save
 * button of {@code common/resourceNode/resourceEditPageButtons.inc}. Resources of an application are not covered.
 */
public abstract class ResourceEditView implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_DEPLOYMENT_ORDER = "100";

    @Inject
    private AdminRestService rest;

    private String name;
    private boolean found;
    private boolean onlyServer;
    private final Map<String, Object> values = new HashMap<>();
    private String logicalJndiName = "";
    private boolean enabled;
    private String status = "";
    private PropertyRows properties = new PropertyRows();

    /** The REST child type of the resources, for example {@code jdbc-resource}. */
    protected abstract String childType();

    /** The path of the edit page, for example {@code /jdbc/jdbcResourceEdit.jsf}. */
    protected abstract String editPage();

    /** The list command that returns the logical JNDI names; none by default. */
    protected String logicalNamesCommand() {
        return null;
    }

    /** The key of the list in the response of {@link #logicalNamesCommand()}. */
    protected String logicalNamesKey() {
        return null;
    }

    /** Attributes that are shown but not sent when saving. */
    protected Set<String> readOnlyAttributes() {
        return Set.of("jndiName");
    }

    /** Attributes sent as {@code false} when they have no value; {@code enabled} by default. */
    protected List<String> convertToFalse() {
        return List.of("enabled");
    }

    protected AdminRestService rest() {
        return rest;
    }

    /**
     * The additional properties to send when the resource is saved; the rows of the properties table by default.
     *
     * @throws IllegalArgumentException when the two values of a confidential property differ
     */
    protected List<Map<String, String>> propertiesToSend() {
        return properties.toSend();
    }

    /**
     * Saves the resource itself, before its server reference and properties: the attributes (the values without the
     * read-only ones, with the enabled state) are sent to the resource by default.
     */
    protected void saveResource(Map<String, Object> attributes) {
        rest.create(selfUrl(), attributes, convertToFalse());
    }

    /**
     * The URL of the resource whose additional properties the page edits; the resource itself by default. It is called
     * after the attribute values are loaded.
     */
    protected String propertiesResource() {
        return selfUrl();
    }

    @PostConstruct
    protected void load() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (name == null) {
            name = context.getExternalContext().getRequestParameterMap().get("name");
        }
        if (name == null || name.isEmpty()) {
            found = false;
            ConsoleMessages.error(ConsoleMessages.core("common.jndiName") + " ?");
            return;
        }
        Map<String, Object> attributes = rest.attributes(selfUrl());
        found = !attributes.isEmpty();
        if (!found) {
            return;
        }
        values.clear();
        attributes.forEach((key, value) -> values.put(key, ResourceListView.text(value)));
        logicalJndiName = logicalJndiName(context);
        properties = ResourceLookups.properties(rest, propertiesResource());

        ResourceTargets targets = ResourceTargets.load(rest);
        onlyServer = targets.onlyServer();
        boolean resourceEnabled = "true".equals(values.get("enabled"));
        if (onlyServer) {
            Map<String, Object> reference = rest.attributes(rest.child(targets.referencesUrl("server"), name));
            enabled = resourceEnabled && (reference.isEmpty() || "true".equals(ResourceListView.text(reference.get("enabled"))));
        } else {
            status = status(targets);
        }
    }

    public void save() {
        try {
            // Checked before any request, so that nothing is saved when a confidential property does not match
            List<Map<String, String>> propertiesToSend = propertiesToSend();
            Map<String, Object> attributes = new HashMap<>(values);
            readOnlyAttributes().forEach(attributes::remove);
            // The enabled state is kept on the resource references; the resource itself stays enabled
            attributes.put("enabled", "true");
            saveResource(attributes);
            if (onlyServer) {
                String references = ResourceTargets.load(rest).referencesUrl("server");
                if (rest.attributes(rest.child(references, name)).isEmpty()) {
                    rest.create(references, Map.of("id", name, "enabled", String.valueOf(enabled)), List.of("enabled"));
                } else {
                    rest.create(rest.child(references, name), Map.of("enabled", String.valueOf(enabled)), List.of("enabled"));
                }
            }
            rest.postJson(propertiesResource() + "/property.json", propertiesToSend);
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /**
     * Replaces the values that have a default with the default, as the Load Defaults button of the JSFTemplating pages
     * does ({@code gf.getDefaultValues} with the current values).
     */
    public void loadDefaults() {
        Map<String, String> defaults = rest.defaults(rest.url("resources", childType()));
        for (String key : values.keySet()) {
            String value = defaults.get(key);
            if (value != null) {
                values.put(key, value);
            } else if (key.equals("deploymentOrder")) {
                values.put(key, DEFAULT_DEPLOYMENT_ORDER);
            }
        }
    }

    /** The page of the Target tab (JSFTemplating), which returns to this page with its General tab. */
    public String getTargetPage() {
        String contextPath = FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath();
        String generalPage = contextPath + editPage() + "?name=" + encode(name);
        return contextPath + "/common/resourceNode/resourceEditTargets.jsf?name=" + encode(name) + "&generalPage=" + encode(generalPage);
    }

    public boolean isFound() {
        return found;
    }

    public boolean isOnlyServer() {
        return onlyServer;
    }

    public String getName() {
        return name;
    }

    /** The attribute values, bound by the page, for example {@code #{view.values['description']}}. */
    public Map<String, Object> getValues() {
        return values;
    }

    public String getLogicalJndiName() {
        return logicalJndiName;
    }

    /** The enabled state of the server's resource reference, when the server is the only target. */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** "Enabled on n of m Target(s)", when there is more than one target. */
    public String getStatus() {
        return status;
    }

    public PropertyRows getProperties() {
        return properties;
    }

    private String selfUrl() {
        return rest.url("resources", childType(), name);
    }

    private String logicalJndiName(FacesContext context) {
        String requested = context.getExternalContext().getRequestParameterMap().get("logicalJndiName");
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        return ResourceLookups.logicalJndiNames(rest, logicalNamesCommand(), logicalNamesKey()).getOrDefault(name, "");
    }

    private String status(ResourceTargets targets) {
        int referenced = 0;
        int enabledReferences = 0;
        for (String target : targets.names()) {
            String references = targets.referencesUrl(target);
            if (rest.childNames(references).contains(name)) {
                referenced++;
                if ("true".equals(ResourceListView.text(rest.attributes(rest.child(references, name)).get("enabled")))) {
                    enabledReferences++;
                }
            }
        }
        return referenced == 0 ? ConsoleMessages.core("deploy.noTarget")
                : ConsoleMessages.core("deploy.someEnabled", enabledReferences, referenced);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
