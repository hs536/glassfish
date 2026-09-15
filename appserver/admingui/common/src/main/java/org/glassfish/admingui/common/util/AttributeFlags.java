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

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The boolean attributes of a resource as check boxes, for example {@code #{view.flags['failAllConnections']}}: a
 * view of the attribute values, which keep {@code "true"} or {@code "false"}.
 *
 * <p>
 * Prototype (adr/0008): the Facelets counterpart of {@code sun:checkbox} with {@code selectedValue="true"} on an
 * attribute value.
 */
public final class AttributeFlags extends AbstractMap<String, Boolean> {

    private final Map<String, Object> values;

    public AttributeFlags(Map<String, Object> values) {
        this.values = values;
    }

    @Override
    public Boolean get(Object key) {
        return "true".equals(String.valueOf(values.get(key)));
    }

    @Override
    public Boolean put(String key, Boolean value) {
        Boolean previous = get(key);
        values.put(key, String.valueOf(Boolean.TRUE.equals(value)));
        return previous;
    }

    @Override
    public Set<Entry<String, Boolean>> entrySet() {
        return values.keySet().stream().collect(Collectors.toMap(key -> key, this::get)).entrySet();
    }
}
