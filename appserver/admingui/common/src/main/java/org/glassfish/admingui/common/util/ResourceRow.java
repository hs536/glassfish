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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One resource in a resources list: its attributes, its logical JNDI name, and the targets that reference it.
 *
 * <p>
 * Prototype (adr/0008).
 */
public class ResourceRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final Map<String, String> attributes = new TreeMap<>();
    private final List<String> targets = new ArrayList<>();
    private String logicalJndiName = "";
    private boolean enabled;
    private int enabledTargets;
    private boolean selected;

    public ResourceRow(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** The attributes of the resource as text; a missing attribute reads as an empty text. */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String attribute(String key) {
        return attributes.getOrDefault(key, "");
    }

    public String getLogicalJndiName() {
        return logicalJndiName;
    }

    void setLogicalJndiName(String logicalJndiName) {
        this.logicalJndiName = logicalJndiName;
    }

    /** The enabled attribute of the resource itself. */
    public boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** The names of the targets that reference the resource. */
    public List<String> getTargets() {
        return targets;
    }

    public int getEnabledTargets() {
        return enabledTargets;
    }

    void addTarget(String target, boolean referenceEnabled) {
        targets.add(target);
        if (referenceEnabled) {
            enabledTargets++;
        }
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
