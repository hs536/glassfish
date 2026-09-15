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

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ResourceListView;

/**
 * The JMS destination resources list page (prototype, adr/0008): the admin object resources that are JMS queues or
 * topics, as {@code list-jms-resources} returns them.
 *
 * <p>
 * The Facelets counterpart of {@code jms/jmsDestinations.jsf} and the handler {@code gfr.getJmsDestResources}.
 */
@Named
@ViewScoped
public class JmsDestinationsView extends ResourceListView {

    private static final long serialVersionUID = 1L;

    static final String CHILD_TYPE = "admin-object-resource";

    /** The target sent with an admin object resource itself when it is created or deleted, as the JSFTemplating pages send it. */
    static final String RESOURCE_TARGET = "server-config";

    static final List<String> TYPES = List.of("jakarta.jms.Topic", "jakarta.jms.Queue");

    @Override
    protected String childType() {
        return CHILD_TYPE;
    }

    @Override
    protected String deleteTarget() {
        return RESOURCE_TARGET;
    }

    @Override
    protected List<String> resourceNames(String collection) {
        List<String> names = new ArrayList<>();
        for (String type : List.of("jakarta.jms.Queue", "jakarta.jms.Topic")) {
            Map<String, Object> response = rest().get(rest().url("resources", "list-jms-resources"), Map.of("target", "domain", "resType", type));
            if (AdminRestService.extraProperties(response).get("jmsResources") instanceof List<?> resources) {
                for (Object resource : resources) {
                    if (resource instanceof Map<?, ?> map && map.get("name") != null) {
                        names.add(map.get("name").toString());
                    }
                }
            }
        }
        return names;
    }
}
