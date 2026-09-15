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

import java.util.Map;

/**
 * The JSON text (RFC 8259) of plain Java values: maps, iterables, strings, numbers, booleans and {@code null}.
 *
 * <p>
 * Prototype (adr/0008, B-37): the pages send the properties of a resource to the admin REST interface as JSON.
 * {@code JSONUtil.javaToJSON} escapes characters such as {@code :} and {@code =} for JavaScript, which is not valid JSON,
 * so this class writes the escapes of JSON only.
 */
public final class JsonText {

    private JsonText() {
    }

    public static String of(Object value) {
        StringBuilder json = new StringBuilder();
        append(json, value);
        return json.toString();
    }

    private static void append(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Map<?, ?> map) {
            json.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                appendString(json, String.valueOf(entry.getKey()));
                json.append(':');
                append(json, entry.getValue());
            }
            json.append('}');
        } else if (value instanceof Iterable<?> items) {
            json.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                append(json, item);
            }
            json.append(']');
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else {
            appendString(json, value.toString());
        }
    }

    private static void appendString(StringBuilder json, String text) {
        json.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        json.append(String.format("\\u%04x", (int) ch));
                    } else {
                        json.append(ch);
                    }
                }
            }
        }
        json.append('"');
    }
}
