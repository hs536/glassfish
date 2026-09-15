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

import org.glassfish.admingui.common.util.ResourceEditView;

/**
 * The edit JMS destination resource page (prototype, adr/0008). The property {@code Name} is edited as the physical
 * destination name and is not shown in the additional properties table.
 */
@Named
@ViewScoped
public class JmsDestinationEditView extends ResourceEditView {

    private static final long serialVersionUID = 1L;

    private String physicalName = "";

    @Override
    protected String childType() {
        return JmsDestinationsView.CHILD_TYPE;
    }

    @Override
    protected String editPage() {
        return "/jms/jmsDestinationEdit.jsf";
    }

    // An overriding method is a post construct method only when it is annotated again
    @PostConstruct
    @Override
    protected void load() {
        super.load();
        if (isFound()) {
            physicalName = JmsDestinations.takePhysicalName(getProperties());
        }
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
