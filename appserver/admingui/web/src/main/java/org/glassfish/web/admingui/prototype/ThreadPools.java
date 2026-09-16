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

import org.glassfish.admingui.common.util.AdminRestService;

/**
 * The URL of the thread pools of a configuration.
 *
 * <p>
 * Prototype (adr/0008): shared by the list, the new and the edit page.
 */
final class ThreadPools {

    static final String DEFAULT_CONFIG = "server-config";

    private ThreadPools() {
    }

    static String url(AdminRestService rest, String configName) {
        return rest.url("configs", "config", configName, "thread-pools", "thread-pool");
    }

    static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
