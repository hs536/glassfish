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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.Settings;

/**
 * The HTTP settings of a protocol, with the choices the page offers for them.
 *
 * <p>
 * Prototype (adr/0008): what the fragment {@code /web/grizzly/httpAttrs.xhtml} shows, on the HTTP tab as well as on the
 * page of a new protocol.
 */
public class HttpSettings extends Settings {

    private static final long serialVersionUID = 1L;

    /** The settings that are sent as false when they are not chosen. */
    static final List<String> BOOLEANS = List.of("uploadTimeoutEnabled", "cometSupportEnabled", "dnsLookupEnabled",
            "rcmSupportEnabled", "traceEnabled", "authPassThroughEnabled", "chunkingEnabled", "encodedSlashEnabled",
            "websocketsSupport", "xpoweredBy", "behindProxy");
    /** The settings the server does not take without a value, as the JSFTemplating pages also know. */
    static final List<String> NOT_SENT_WHEN_EMPTY = List.of("redirectPort", "noCompressionUserAgents");
    /** The choices of the compression setting, as on the JSFTemplating page. */
    private static final List<String> COMPRESSION = List.of("on", "off", "force");

    private List<String> virtualServers = List.of();

    public List<String> getVirtualServers() {
        return virtualServers;
    }

    public void setVirtualServers(List<String> virtualServers) {
        this.virtualServers = virtualServers;
    }

    public List<String> getCompressionChoices() {
        return COMPRESSION;
    }

    /** The values to send, without the ones the server does not take without a value. */
    Map<String, Object> toSend() {
        Map<String, Object> attributes = new HashMap<>(getValues());
        for (String name : NOT_SENT_WHEN_EMPTY) {
            if (text(name).isEmpty()) {
                attributes.remove(name);
            }
        }
        return attributes;
    }
}
