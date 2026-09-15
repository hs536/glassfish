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
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AttributeFlags;
import org.glassfish.admingui.common.util.ResourceEditView;

/**
 * The edit JMS connection factory page (prototype, adr/0008). The page shows the connector resource (its name and
 * status) and edits its connector connection pool: the attributes and the additional properties.
 *
 * <p>
 * The Facelets counterpart of {@code jms/jmsConnectionEdit.jsf} and the save button of
 * {@code jms/jmsConnectionButtons.inc}.
 */
@Named
@ViewScoped
public class JmsConnectionFactoryEditView extends ResourceEditView {

    private static final long serialVersionUID = 1L;

    /** The boolean attributes of the pool, sent as {@code false} when they are not set. */
    static final List<String> POOL_FLAGS = List.of("failAllConnections", "isConnectionValidationRequired");

    private final Map<String, Object> poolValues = new HashMap<>();

    @Override
    protected String childType() {
        return JmsConnectionFactoriesView.CHILD_TYPE;
    }

    @Override
    protected String editPage() {
        return "/jms/jmsConnectionEdit.jsf";
    }

    @Override
    protected String logicalNamesCommand() {
        return JmsConnectionFactoriesView.LIST_COMMAND;
    }

    @Override
    protected String logicalNamesKey() {
        return JmsConnectionFactoriesView.LIST_KEY;
    }

    @Override
    protected List<String> convertToFalse() {
        return POOL_FLAGS;
    }

    // An overriding method is a post construct method only when it is annotated again
    @PostConstruct
    @Override
    protected void load() {
        super.load();
        poolValues.clear();
        if (isFound()) {
            rest().attributes(propertiesResource()).forEach((key, value) -> poolValues.put(key, JmsConnectionFactoriesView.text(value)));
        }
    }

    /** The pool of the connection factory. */
    @Override
    protected String propertiesResource() {
        return rest().url("resources", JmsConnectionFactoriesView.POOL_TYPE, JmsConnectionFactoriesView.text(getValues().get("poolName")));
    }

    @Override
    protected void saveResource(Map<String, Object> attributes) {
        rest().create(propertiesResource(), JmsConnectionFactoriesView.withEmptyValues(poolValues), convertToFalse());
    }

    /** Replaces the values of the pool that have a default with the default. */
    @Override
    public void loadDefaults() {
        Map<String, String> defaults = rest().defaults(rest().url("resources", JmsConnectionFactoriesView.POOL_TYPE));
        for (String key : poolValues.keySet()) {
            String value = defaults.get(key);
            if (value != null) {
                poolValues.put(key, value);
            }
        }
    }

    /** The attribute values of the pool, bound by the page. */
    public Map<String, Object> getPoolValues() {
        return poolValues;
    }

    public Map<String, Boolean> getFlags() {
        return new AttributeFlags(poolValues);
    }

    public List<String> getTypes() {
        return JmsConnectionFactoriesView.TYPES;
    }
}
