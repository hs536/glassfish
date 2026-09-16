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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;

/**
 * The URLs, the choices and the deletion of the HTTP listener pages.
 *
 * <p>
 * Prototype (adr/0008): an HTTP listener of these pages is a network listener with a protocol of its own, named after
 * the listener, and the HTTP settings of that protocol.
 */
final class HttpListeners {

    /** The protocol of a listener created by these pages is named after it ({@code grizzly.protocolExtension}). */
    static final String PROTOCOL_SUFFIX = "-protocol";
    static final String DEFAULT_CONFIG = "server-config";
    /** The listener of the admin console, whose security cannot be changed here. */
    static final String ADMIN_LISTENER = "admin-listener";

    private HttpListeners() {
    }

    static String listenersUrl(AdminRestService rest, String configName) {
        return rest.url("configs", "config", configName, "network-config", "network-listeners", "network-listener");
    }

    static String protocolsUrl(AdminRestService rest, String configName) {
        return rest.url("configs", "config", configName, "network-config", "protocols", "protocol");
    }

    static String virtualServersUrl(AdminRestService rest, String configName) {
        return rest.url("configs", "config", configName, "http-service", "virtual-server");
    }

    static List<String> virtualServers(AdminRestService rest, String configName) {
        return rest.childNames(virtualServersUrl(rest, configName));
    }

    static List<String> threadPools(AdminRestService rest, String configName) {
        return rest.childNames(rest.url("configs", "config", configName, "thread-pools", "thread-pool"));
    }

    static List<String> transports(AdminRestService rest, String configName) {
        return rest.childNames(rest.url("configs", "config", configName, "network-config", "transports", "transport"));
    }

    /** The protocol of a listener, or an empty text when the listener is not there. */
    static String protocolOf(AdminRestService rest, String configName, String listener) {
        return text(rest.attributes(rest.child(listenersUrl(rest, configName), listener)).get("protocol"));
    }

    /**
     * Deletes a listener as the JSFTemplating page does: the listener is first removed from the virtual server the
     * protocol serves, and the protocol is deleted too when these pages created it and no other listener uses it.
     */
    static void delete(AdminRestService rest, String configName, String listener) {
        String listenerUrl = rest.child(listenersUrl(rest, configName), listener);
        String protocol = text(rest.attributes(listenerUrl).get("protocol"));
        String protocolUrl = rest.child(protocolsUrl(rest, configName), protocol);
        Map<String, Object> http = attributesOrEmpty(rest, rest.child(protocolUrl, "http"));
        if (!http.isEmpty()) {
            removeFromVirtualServer(rest, configName, text(http.get("defaultVirtualServer")), listener);
        }
        boolean ownProtocol = protocol.equals(listener + PROTOCOL_SUFFIX) && usedBy(rest, configName, protocol).size() == 1;
        rest.delete(listenerUrl, Map.of("target", configName));
        if (ownProtocol) {
            rest.delete(protocolUrl, Map.of("target", configName));
        }
    }

    /** The listeners that use the protocol. */
    private static List<String> usedBy(AdminRestService rest, String configName, String protocol) {
        List<String> listeners = new ArrayList<>();
        String url = listenersUrl(rest, configName);
        for (String name : rest.childNames(url)) {
            if (protocol.equals(text(rest.attributes(rest.child(url, name)).get("protocol")))) {
                listeners.add(name);
            }
        }
        return listeners;
    }

    private static void removeFromVirtualServer(AdminRestService rest, String configName, String virtualServer, String listener) {
        if (virtualServer.isEmpty()) {
            return;
        }
        String url = rest.child(virtualServersUrl(rest, configName), virtualServer);
        Map<String, Object> attributes = new LinkedHashMap<>(rest.attributes(url));
        if (attributes.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (String name : text(attributes.get("networkListeners")).split(",")) {
            if (!name.isBlank() && !name.strip().equals(listener)) {
                names.add(name.strip());
            }
        }
        attributes.put("networkListeners", String.join(",", names));
        rest.post(url, attributes);
    }

    /** The attributes of a resource that may not be there, such as the HTTP settings of a protocol (see X-33). */
    static Map<String, Object> attributesOrEmpty(AdminRestService rest, String url) {
        try {
            return rest.attributes(url);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
