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
import java.util.HashMap;
import java.util.Map;

/**
 * The attributes of one resource as a page shows them: the values, and the same values seen as booleans.
 *
 * <p>
 * Prototype (adr/0008): a page that shows more than one resource, such as a new protocol with its HTTP and file cache
 * settings, keeps one of these for each of them. A fragment of the page then takes the settings it shows as a
 * parameter, instead of knowing the names of the methods of the page Bean.
 */
public class Settings implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, Object> read = new HashMap<>();
    private final Flags flags = new Flags(values);

    /** The values as the admin REST interface has them. */
    public Map<String, Object> getValues() {
        return values;
    }

    /** The values that are true or false. */
    public Flags getFlags() {
        return flags;
    }

    /** Takes the given attributes as the values, forgetting the ones that were there. */
    public void replace(Map<String, ?> attributes) {
        values.clear();
        values.putAll(attributes);
        read.clear();
        read.putAll(attributes);
    }

    /**
     * The values to send to the server: an attribute that is empty and that the server had no value for is left out, as
     * the JSFTemplating pages leave it out (X-29). An attribute the user cleared is sent, so that it is cleared.
     */
    public Map<String, Object> toSend() {
        Map<String, Object> attributes = new HashMap<>(values);
        attributes.entrySet().removeIf(attribute -> isEmpty(attribute.getValue()) && read.get(attribute.getKey()) == null);
        return attributes;
    }

    private static boolean isEmpty(Object value) {
        return value == null || value.toString().isEmpty();
    }

    /** The value of one attribute as text, which is empty when the attribute is not there. */
    public String text(String name) {
        Object value = values.get(name);
        return value == null ? "" : value.toString();
    }
}
