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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlagsTest {

    @Test
    public void textIsReadAsABoolean() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("traceEnabled", "true");
        values.put("dnsLookupEnabled", "false");
        Flags flags = new Flags(values);
        assertTrue(flags.get("traceEnabled"));
        assertFalse(flags.get("dnsLookupEnabled"));
    }

    @Test
    public void anAttributeThatIsNotThereIsFalse() {
        Flags flags = new Flags(new LinkedHashMap<>());
        assertFalse(flags.get("traceEnabled"));
    }

    @Test
    public void aBooleanIsWrittenAsTheTextTheServerTakes() {
        Map<String, Object> values = new LinkedHashMap<>();
        Flags flags = new Flags(values);
        flags.put("traceEnabled", true);
        flags.put("dnsLookupEnabled", false);
        flags.put("cometSupportEnabled", null);
        assertEquals("true", values.get("traceEnabled"));
        assertEquals("false", values.get("dnsLookupEnabled"));
        assertEquals("false", values.get("cometSupportEnabled"));
    }

    @Test
    public void everyAttributeOfTheResourceIsSeenAsABoolean() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("traceEnabled", "true");
        values.put("serverName", "");
        Flags flags = new Flags(values);
        assertEquals(Map.of("traceEnabled", true, "serverName", false), flags);
    }
}
