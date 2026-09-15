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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonTextTest {

    @Test
    public void propertiesKeepColonsAndEqualSigns() {
        Map<String, String> property = new LinkedHashMap<>();
        property.put("name", "AddressList");
        property.put("value", "localhost:7676;a=b/(c)");
        property.put("description", "");
        assertEquals("[{\"name\":\"AddressList\",\"value\":\"localhost:7676;a=b/(c)\",\"description\":\"\"}]", JsonText.of(List.of(property)));
    }

    @Test
    public void quotesBackslashesAndControlCharactersAreEscaped() {
        String quote = String.valueOf((char) 34);
        String backslash = String.valueOf((char) 92);
        String text = "a" + quote + "b" + backslash + "c" + (char) 10 + "d" + (char) 9 + "e" + (char) 1 + "f";
        String json = quote + "a" + backslash + quote + "b" + backslash + backslash + "c" + backslash + "nd" + backslash + "te"
            + backslash + "u0001f" + quote;
        assertEquals(json, JsonText.of(text));
    }

    @Test
    public void otherCharactersAreKept() {
        assertEquals("\"<é>&'\"", JsonText.of("<é>&'"));
    }

    @Test
    public void nullNumbersAndBooleans() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("none", null);
        values.put("count", 3);
        values.put("enabled", true);
        assertEquals("{\"none\":null,\"count\":3,\"enabled\":true}", JsonText.of(values));
    }

    @Test
    public void emptyList() {
        assertEquals("[]", JsonText.of(new ArrayList<>()));
    }
}
