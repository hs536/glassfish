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

package org.glassfish.admingui.plugin.jms.prototype;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ResourceListView;
import org.glassfish.admingui.common.util.ResourceRow;

/**
 * The JMS connection factories list page (prototype, adr/0008): the connector resources that
 * {@code list-jms-resources} returns as connection factories, with the resource type and the description of their
 * connector connection pools.
 *
 * <p>
 * The Facelets counterpart of {@code jms/jmsConnections.jsf}, the handlers {@code gfr.getJmsConnectionFactories} and
 * {@code getJmsResourcesInfo}, and the delete button of {@code jms/shared/tableButtons.inc}.
 */
@Named
@ViewScoped
public class JmsConnectionFactoriesView extends ResourceListView {

    private static final long serialVersionUID = 1L;

    static final String CHILD_TYPE = "connector-resource";
    static final String POOL_TYPE = "connector-connection-pool";
    static final String POOL_SUFFIX = "-Connection-Pool";
    static final String LIST_COMMAND = "list-jms-resources";
    static final String LIST_KEY = "jmsResources";

    /** The resource types, in the order of the JSFTemplating page. */
    static final List<String> TYPES = List.of("jakarta.jms.TopicConnectionFactory", "jakarta.jms.QueueConnectionFactory", "jakarta.jms.ConnectionFactory");

    private static final String STRINGS = "org.glassfish.jms.admingui.Strings";

    @Override
    protected String childType() {
        return CHILD_TYPE;
    }

    @Override
    protected String logicalNamesCommand() {
        return LIST_COMMAND;
    }

    @Override
    protected String logicalNamesKey() {
        return LIST_KEY;
    }

    @Override
    protected List<String> resourceNames(String collection) {
        List<String> names = new ArrayList<>();
        for (String type : List.of("jakarta.jms.ConnectionFactory", "jakarta.jms.TopicConnectionFactory", "jakarta.jms.QueueConnectionFactory")) {
            Map<String, Object> response = rest().get(rest().url("resources", LIST_COMMAND), Map.of("target", "domain", "resType", type));
            if (AdminRestService.extraProperties(response).get(LIST_KEY) instanceof List<?> resources) {
                for (Object resource : resources) {
                    if (resource instanceof Map<?, ?> map && map.get("name") != null) {
                        names.add(map.get("name").toString());
                    }
                }
            }
        }
        return names;
    }

    // An overriding method is a post construct method only when it is annotated again
    @PostConstruct
    @Override
    protected void load() {
        super.load();
        for (ResourceRow row : getRows()) {
            Map<String, Object> pool = rest().attributes(rest().url("resources", POOL_TYPE, text(row.getAttributes().get("poolName"))));
            row.getAttributes().put("resType", text(pool.get("connectionDefinitionName")));
            row.getAttributes().put("description", text(pool.get("description")));
        }
    }

    /** A connection factory that the system requires is not deleted, and then none of the selected ones is. */
    @Override
    public void delete() {
        for (ResourceRow row : getRows()) {
            if (row.isSelected() && "system-all-req".equals(text(row.getAttributes().get("objectType")))) {
                FacesContext context = FacesContext.getCurrentInstance();
                String message = ResourceBundle.getBundle(STRINGS, context.getViewRoot().getLocale(), getClass().getClassLoader())
                        .getString("msg.jms.cannotDeleteDefault");
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, MessageFormat.format(message, row.getName()), null));
                return;
            }
        }
        super.delete();
    }

    /** The connection factory is deleted with its pool by {@code delete-jms-resource}. */
    @Override
    protected void deleteResource(ResourceRow row) {
        rest().delete(rest().url("resources", "delete-jms-resource"), Map.of("id", row.getName(), "target", isOnlyServer() ? "server" : "domain"));
    }

    static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * A copy of the values in which a value without text is an empty string. An empty choice of a select menu is set as
     * {@code null} and would not be sent; the JSFTemplating pages send it as an empty value (X-29).
     */
    static Map<String, Object> withEmptyValues(Map<String, Object> values) {
        Map<String, Object> copy = new java.util.HashMap<>(values);
        copy.replaceAll((key, value) -> value == null ? "" : value);
        return copy;
    }
}
