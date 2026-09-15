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

import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.ResourceNewView;

/**
 * The new JMS destination resource page (prototype, adr/0008): an admin object resource of the JMS resource adapter,
 * with the physical destination name kept as its property {@code Name}.
 */
@Named
@ViewScoped
public class JmsDestinationNewView extends ResourceNewView {

    private static final long serialVersionUID = 1L;

    private String physicalName = "";

    @Override
    protected String childType() {
        return JmsDestinationsView.CHILD_TYPE;
    }

    @Override
    protected String listPage() {
        return "/jms/jmsDestinations.jsf";
    }

    @Override
    protected String createTarget() {
        return JmsDestinationsView.RESOURCE_TARGET;
    }

    // An overriding method is a post construct method only when it is annotated again
    @PostConstruct
    @Override
    protected void loadDefaults() {
        super.loadDefaults();
        getValues().put("resAdapter", JmsDestinations.ADAPTER);
    }

    @Override
    protected List<Map<String, String>> propertiesToSend() {
        return JmsDestinations.withPhysicalName(super.propertiesToSend(), physicalName);
    }

    public List<String> getTypes() {
        return JmsDestinationsView.TYPES;
    }

    public String getPhysicalName() {
        return physicalName;
    }

    public void setPhysicalName(String physicalName) {
        this.physicalName = physicalName;
    }
}
