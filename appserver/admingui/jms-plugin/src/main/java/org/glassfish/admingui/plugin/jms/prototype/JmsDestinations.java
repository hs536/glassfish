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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.PropertyRows;

/**
 * What the JMS destination pages share (prototype, adr/0008): the physical destination name is the property
 * {@code Name} of the admin object resource.
 *
 * <p>
 * The Facelets counterpart of the handling of {@code physDestName} in {@code jms/jmsDestinationEdit.jsf} and
 * {@code jms/jmsDestinationButtons.inc}.
 */
final class JmsDestinations {

    static final String ADAPTER = "jmsra";

    private static final String NAME_PROPERTY = "Name";

    private JmsDestinations() {
    }

    /** Removes the property {@code Name} from the rows and returns its value, or an empty string. */
    static String takePhysicalName(PropertyRows properties) {
        String physicalName = "";
        for (Iterator<PropertyRows.Row> rows = properties.getRows().iterator(); rows.hasNext();) {
            PropertyRows.Row row = rows.next();
            if (NAME_PROPERTY.equals(row.getName())) {
                physicalName = row.getValue();
                rows.remove();
            }
        }
        return physicalName;
    }

    /** The properties followed by the property {@code Name} with the physical destination name, when it has a value. */
    static List<Map<String, String>> withPhysicalName(List<Map<String, String>> properties, String physicalName) {
        List<Map<String, String>> all = new ArrayList<>(properties);
        if (physicalName != null && !physicalName.isEmpty()) {
            Map<String, String> property = new LinkedHashMap<>();
            property.put("name", NAME_PROPERTY);
            property.put("value", physicalName);
            all.add(property);
        }
        return all;
    }
}
