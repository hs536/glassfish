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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rows of an additional properties table (name, value, description) that the user edits.
 *
 * <p>
 * Prototype (docs/試作計画.md P-5): the model of the {@code adm:propertyTable} component, the Facelets counterpart of
 * {@code shared/propertyDescTable.inc}.
 */
public class PropertyRows implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The value the admin REST interface uses for a property that is set to an empty string. */
    private static final String EMPTY_VALUE_TOKEN = "()";

    private final List<Row> rows = new ArrayList<>();

    public List<Row> getRows() {
        return rows;
    }

    public void add() {
        rows.add(new Row());
    }

    public void deleteSelected() {
        rows.removeIf(Row::isSelected);
    }

    public boolean isAnySelected() {
        return rows.stream().anyMatch(Row::isSelected);
    }

    /**
     * The properties to send: rows without a name or without a value are left out, and the value {@code ()} stands for
     * an empty value (as {@code removeEmptyProps} does for the JSFTemplating pages).
     */
    public List<Map<String, String>> toSend() {
        List<Map<String, String>> properties = new ArrayList<>();
        for (Row row : rows) {
            if (isEmpty(row.name) || isEmpty(row.value)) {
                continue;
            }
            Map<String, String> property = new LinkedHashMap<>();
            property.put("name", row.name);
            property.put("value", EMPTY_VALUE_TOKEN.equals(row.value) ? "" : row.value);
            property.put("description", row.description == null ? "" : row.description);
            properties.add(property);
        }
        return properties;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /** One property. */
    public static class Row implements Serializable {

        private static final long serialVersionUID = 1L;

        private String name = "";
        private String value = "";
        private String description = "";
        private boolean selected;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }
    }
}
