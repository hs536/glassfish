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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertyRowsTest {

    @Test
    public void addAndDeleteSelectedRows() {
        PropertyRows rows = new PropertyRows();
        rows.add();
        rows.add();
        assertEquals(2, rows.getRows().size());
        assertFalse(rows.isAnySelected());

        rows.getRows().get(0).setSelected(true);
        assertTrue(rows.isAnySelected());

        rows.deleteSelected();
        assertEquals(1, rows.getRows().size());
        assertFalse(rows.isAnySelected());
    }

    @Test
    public void rowsWithoutNameOrValueAreNotSent() {
        PropertyRows rows = rows(
                row("", "value", "no name"),
                row("noValue", "", "no value"),
                row("kept", "1", "kept row"));

        assertEquals(List.of(Map.of("name", "kept", "value", "1", "description", "kept row")), rows.toSend());
    }

    @Test
    public void emptyValueTokenIsSentAsEmptyValue() {
        PropertyRows rows = rows(row("empty", "()", ""));

        assertEquals(List.of(Map.of("name", "empty", "value", "", "description", "")), rows.toSend());
    }

    @Test
    public void missingDescriptionIsSentAsEmpty() {
        PropertyRows rows = rows(row("name", "value", null));

        assertEquals("", rows.toSend().get(0).get("description"));
    }

    @Test
    public void orderOfRowsIsKept() {
        PropertyRows rows = rows(row("b", "2", ""), row("a", "1", ""));

        assertEquals(List.of("b", "a"), rows.toSend().stream().map(property -> property.get("name")).toList());
    }

    @Test
    public void replaceKeepsTheOrderOfTheValues() {
        PropertyRows rows = rows(row("old", "1", ""));
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("b", "2");
        values.put("a", null);

        rows.replace(values);

        assertEquals(List.of("b", "a"), rows.getRows().stream().map(PropertyRows.Row::getName).toList());
        assertEquals("", rows.getRows().get(1).getValue());
    }

    @Test
    public void confidentialRowIsSentWhenBothValuesMatch() {
        PropertyRows rows = rows(row("password", "secret", ""), row("user", "admin", ""));
        rows.markConfidential(List.of("password"));

        assertTrue(rows.isAnyConfidential());
        assertTrue(rows.getRows().get(0).isConfidential());
        assertFalse(rows.getRows().get(1).isConfidential());
        assertEquals("secret", rows.getRows().get(0).getConfirmValue());
        assertEquals(2, rows.toSend().size());
    }

    @Test
    public void confidentialRowWithDifferentValuesIsRejected() {
        PropertyRows rows = rows(row("password", "secret", ""));
        rows.markConfidential(List.of("password"));
        rows.getRows().get(0).setConfirmValue("other");

        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, rows::toSend);
        assertEquals("Confidential property 'password' does not match.", e.getMessage());
    }

    private static PropertyRows rows(String[]... values) {
        PropertyRows rows = new PropertyRows();
        for (String[] value : values) {
            rows.add();
            PropertyRows.Row row = rows.getRows().get(rows.getRows().size() - 1);
            row.setName(value[0]);
            row.setValue(value[1]);
            row.setDescription(value[2]);
        }
        return rows;
    }

    private static String[] row(String name, String value, String description) {
        return new String[] {name, value, description};
    }
}
