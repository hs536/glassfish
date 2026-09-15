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

package org.glassfish.concurrent.admingui.prototype;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A view of the attribute values of a concurrent resource for the page: the boolean attributes as check boxes, and
 * the context information as a list of chosen contexts, kept in the values as a comma separated string.
 *
 * <p>
 * Prototype (adr/0008): the Facelets counterpart of {@code concurrent/contextInfo.inc} and of the conversion of the
 * context information in {@code common/resourceNode/resourceEditPageButtons.inc} ({@code isConcurrent}).
 */
public class ConcurrentValues {

    /** The boolean attributes of a resource type, sent as {@code false} when they are not set. */
    static List<String> convertToFalse(String childType) {
        return switch (childType) {
            case ContextServicesView.CHILD_TYPE -> List.of("enabled", "contextInfoEnabled");
            case ManagedThreadFactoriesView.CHILD_TYPE -> List.of("enabled", "contextInfoEnabled", "useVirtualThreads");
            default -> List.of("enabled", "contextInfoEnabled", "useVirtualThreads", "longRunningTasks", "hungLoggerPrintOnce");
        };
    }

    private static final List<String> CONTEXTS = List.of("Classloader", "JNDI", "Security", "WorkArea");

    private final Map<String, Object> values;

    ConcurrentValues(Map<String, Object> values) {
        this.values = values;
    }

    /** The boolean attributes, for example {@code #{view.concurrent.flags['longRunningTasks']}}. */
    public Map<String, Boolean> getFlags() {
        return new AbstractMap<>() {
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
        };
    }

    /** The contexts that can be propagated. */
    public List<String> getContextOptions() {
        return CONTEXTS;
    }

    /** The chosen contexts, in the order of the values. */
    public List<String> getContextInfo() {
        List<String> contexts = new ArrayList<>();
        Object value = values.get("contextInfo");
        if (value != null) {
            for (String context : value.toString().split(",")) {
                if (!context.isBlank()) {
                    contexts.add(context.trim());
                }
            }
        }
        return contexts;
    }

    public void setContextInfo(List<String> contexts) {
        values.put("contextInfo", contexts == null ? "" : String.join(",", contexts));
    }
}
