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

package org.glassfish.jca.admingui.prototype;

import java.util.ArrayList;
import java.util.List;

import org.glassfish.admingui.common.util.AdminRestService;

/**
 * The user groups and principals of a connector security map, which the pages show as comma separated text
 * (prototype, adr/0008).
 */
final class SecurityMapNames {

    /** The option of a map that maps user groups. */
    static final String USERS = "users";

    /** The option of a map that maps principals. */
    static final String PRINCIPALS = "principals";

    private SecurityMapNames() {
    }

    /** The names in comma separated text, without the spaces around them (B-36). */
    static List<String> split(String text) {
        List<String> names = new ArrayList<>();
        if (text != null) {
            for (String name : text.split(",")) {
                String trimmed = name.strip();
                if (!trimmed.isEmpty()) {
                    names.add(trimmed);
                }
            }
        }
        return names;
    }

    static String join(List<String> names) {
        return String.join(",", names);
    }

    /** The names of a leaf list of a security map, such as its {@code user-group} or {@code principal}. */
    static List<String> read(AdminRestService rest, String url) {
        Object names = AdminRestService.extraProperties(rest.get(url, null)).get("leafList");
        return names instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }
}
