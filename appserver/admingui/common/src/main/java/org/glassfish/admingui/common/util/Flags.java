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
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The attributes of a resource that are true or false, seen as booleans.
 *
 * <p>
 * Prototype (adr/0008): the admin REST interface has the attributes of a resource as text, and a checkbox
 * ({@code h:selectBooleanCheckbox}) needs a boolean. A page with many checkboxes binds them to this view of its
 * attributes ({@code #{bean.flags['traceEnabled']}}) instead of writing a pair of methods for each of them.
 */
public class Flags extends AbstractMap<String, Boolean> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> values;

    public Flags(Map<String, Object> values) {
        this.values = values;
    }

    /** False for an attribute that is not there, as an attribute that is not true is false. */
    @Override
    public Boolean get(Object key) {
        Object value = values.get(key);
        return value != null && Boolean.parseBoolean(value.toString());
    }

    /** Writes the attribute as the text the admin REST interface takes. */
    @Override
    public Boolean put(String key, Boolean value) {
        Boolean previous = get(key);
        values.put(key, String.valueOf(value != null && value));
        return previous;
    }

    @Override
    public Set<Entry<String, Boolean>> entrySet() {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (String key : values.keySet()) {
            flags.put(key, get(key));
        }
        return flags.entrySet();
    }
}
