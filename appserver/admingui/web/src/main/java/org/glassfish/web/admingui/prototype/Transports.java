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

package org.glassfish.web.admingui.prototype;

import java.util.List;

import org.glassfish.admingui.common.util.AdminRestService;

/**
 * The URL and the choices of the transport pages.
 *
 * <p>
 * Prototype (adr/0008): shared by the list, the new and the edit page.
 */
final class Transports {

    static final String DEFAULT_CONFIG = "server-config";
    /**
     * The values of the byte buffer type, as a transport has them. The JSFTemplating page offers them in upper case,
     * which matches no value and rewrites the attribute when the page is saved (B-40).
     */
    static final List<String> BYTE_BUFFER_TYPES = List.of("heap", "direct");

    private Transports() {
    }

    static String url(AdminRestService rest, String configName) {
        return rest.url("configs", "config", configName, "network-config", "transports", "transport");
    }

    static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
