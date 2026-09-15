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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The rows of an additional properties table (name, value, description) that the user edits. A confidential row takes
 * its value twice, and the two values must match.
 *
 * <p>
 * Prototype (docs/試作計画.md P-5): the model of the {@code adm:propertyTable} component, the Facelets counterpart of
 * {@code shared/propertyDescTable.inc} and {@code resourceNode/confidentialPropsTable.inc}.
 */
public class PropertyRows implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The value the admin REST interface uses for a property that is set to an empty string. */
    private static final String EMPTY_VALUE_TOKEN = "()";

    private final List<Row> rows = new ArrayList<>();

    /** The additional properties of a resource, from its {@code property.json}. */
    public static PropertyRows read(AdminRestService rest, String resourceUrl) {
        PropertyRows rows = new PropertyRows();
        Map<String, Object> response = rest.get(resourceUrl + "/property.json", null);
        if (AdminRestService.extraProperties(response).get("properties") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Row row = new Row();
                    row.setName(ResourceListView.text(map.get("name")));
                    row.setValue(ResourceListView.text(map.get("value")));
                    row.setDescription(ResourceListView.text(map.get("description")));
                    rows.rows.add(row);
                }
            }
        }
        return rows;
    }

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

    public boolean isAnyConfidential() {
        return rows.stream().anyMatch(Row::isConfidential);
    }

    /** Replaces the rows with the given names and values, in their order, without descriptions. */
    public void replace(Map<String, String> values) {
        rows.clear();
        values.forEach((name, value) -> {
            Row row = new Row();
            row.setName(name);
            row.setValue(value == null ? "" : value);
            rows.add(row);
        });
    }

    /** Marks the rows with the given names as confidential, with the current value as the confirmation. */
    public void markConfidential(Collection<String> names) {
        for (Row row : rows) {
            if (names.contains(row.name)) {
                row.confidential = true;
                row.confirmValue = row.value;
            }
        }
    }

    /**
     * The properties to send: rows without a name or without a value are left out, and the value {@code ()} stands for
     * an empty value (as {@code removeEmptyProps} does for the JSFTemplating pages).
     *
     * @throws IllegalArgumentException when the two values of a confidential row differ (as {@code gf.combineProperties}
     *             reports)
     */
    public List<Map<String, String>> toSend() {
        List<Map<String, String>> properties = new ArrayList<>();
        for (Row row : rows) {
            if (!isEmpty(row.name) && row.confidential && !Objects.equals(row.value, row.confirmValue)) {
                throw new IllegalArgumentException("Confidential property '" + row.name + "' does not match.");
            }
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
        private boolean confidential;
        private String confirmValue = "";

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

        /** A confidential value is entered in password fields, twice. */
        public boolean isConfidential() {
            return confidential;
        }

        public String getConfirmValue() {
            return confirmValue;
        }

        public void setConfirmValue(String confirmValue) {
            this.confirmValue = confirmValue;
        }
    }
}
