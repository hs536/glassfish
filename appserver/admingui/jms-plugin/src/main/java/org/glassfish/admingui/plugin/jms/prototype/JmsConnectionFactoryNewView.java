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
import org.glassfish.admingui.common.util.ResourceNewView;

/**
 * The new JMS connection factory page (prototype, adr/0008). The values are those of the connector connection pool,
 * which is created first under the name of the factory followed by {@code -Connection-Pool}; then the connector
 * resource, its references and the properties of the pool.
 *
 * <p>
 * The Facelets counterpart of {@code jms/jmsConnectionNew.jsf} and the new button of
 * {@code jms/jmsConnectionButtons.inc}.
 */
@Named
@ViewScoped
public class JmsConnectionFactoryNewView extends ResourceNewView {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> resourceDefaults = new HashMap<>();

    @Override
    protected String childType() {
        return JmsConnectionFactoriesView.POOL_TYPE;
    }

    @Override
    protected String listPage() {
        return "/jms/jmsConnections.jsf";
    }

    @Override
    protected List<String> convertToFalse() {
        return JmsConnectionFactoryEditView.POOL_FLAGS;
    }

    // An overriding method is a post construct method only when it is annotated again
    @PostConstruct
    @Override
    protected void loadDefaults() {
        super.loadDefaults();
        getValues().put("resourceAdapterName", JmsDestinations.ADAPTER);
        getValues().put("steadyPoolSize", "1");
        getValues().put("maxPoolSize", "250");
        resourceDefaults.clear();
        resourceDefaults.putAll(rest().defaults(rest().url("resources", JmsConnectionFactoriesView.CHILD_TYPE)));
        resourceDefaults.put("enabled", "true");
    }

    @Override
    protected void createResource(String name, Map<String, Object> attributes) {
        String poolName = name + JmsConnectionFactoriesView.POOL_SUFFIX;
        Map<String, Object> pool = JmsConnectionFactoriesView.withEmptyValues(getValues());
        pool.put("name", poolName);
        rest().create(rest().url("resources", JmsConnectionFactoriesView.POOL_TYPE), pool, convertToFalse());
        Map<String, Object> resource = new HashMap<>(resourceDefaults);
        resource.put("poolName", poolName);
        resource.put("name", name);
        resource.put("target", "domain");
        rest().create(rest().url("resources", JmsConnectionFactoriesView.CHILD_TYPE), resource, List.of());
    }

    @Override
    protected String propertiesUrl(String name) {
        return rest().url("resources", JmsConnectionFactoriesView.POOL_TYPE, name + JmsConnectionFactoriesView.POOL_SUFFIX, "property.json");
    }

    public Map<String, Boolean> getFlags() {
        return new AttributeFlags(getValues());
    }

    public List<String> getTypes() {
        return JmsConnectionFactoriesView.TYPES;
    }
}
