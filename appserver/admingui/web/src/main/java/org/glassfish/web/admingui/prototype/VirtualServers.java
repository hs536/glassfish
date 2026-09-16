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
 * The choices of the virtual server pages and the changes they make to the application references.
 *
 * <p>
 * Prototype (adr/0008): shared by the list, the new and the edit page. The two methods that change the application
 * references are the Facelets version of the handlers {@code checkVsOfAppRef} and {@code gf.ensureDefaultWebModule}.
 */
final class VirtualServers {

    static final String CHILD_TYPE = "virtual-server";
    static final String DEFAULT_CONFIG = "server-config";
    /** The values of the state, in the order of the JSFTemplating page. */
    static final List<String> STATES = List.of("on", "off", "disabled");
    /** The values of the single sign-on and the access logging. */
    static final List<String> INHERIT_CHOICES = List.of("inherit", "true", "false");

    private VirtualServers() {
    }

    static String url(AdminRestService rest, String configName) {
        return rest.url("configs", "config", configName, "http-service", CHILD_TYPE);
    }

    /**
     * The default values of a new virtual server, with the names the pages use. The create command names its parameters
     * in lower case ({@code logfile}), while the attributes of a virtual server are written as {@code logFile}.
     */
    static Map<String, Object> defaults(AdminRestService rest, String configName) {
        Map<String, String> defaults = rest.defaults(url(rest, configName));
        Map<String, Object> values = new LinkedHashMap<>(defaults);
        Map.of("logfile", "logFile", "defaultwebmodule", "defaultWebModule", "networklisteners", "networkListeners")
                .forEach((command, attribute) -> {
                    if (defaults.containsKey(command)) {
                        values.put(attribute, defaults.get(command));
                    }
                });
        return values;
    }

    /** The network listeners of the configuration. */
    static List<String> networkListeners(AdminRestService rest, String configName) {
        return rest.childNames(rest.url("configs", "config", configName, "network-config", "network-listeners", "network-listener"));
    }

    /** The web modules of the deployed applications, with an empty choice first. */
    static List<String> webModules(AdminRestService rest) {
        List<String> modules = new ArrayList<>();
        modules.add("");
        String applications = rest.url("applications", "application");
        for (String application : rest.childNames(applications)) {
            String applicationUrl = rest.child(applications, application);
            boolean enterpriseApplication = rest.childNames(rest.child(applicationUrl, "engine")).contains("ear");
            String moduleUrl = rest.child(applicationUrl, "module");
            for (String module : rest.childNames(moduleUrl)) {
                if (rest.childNames(rest.child(moduleUrl, module, "engine")).contains("web")) {
                    modules.add(enterpriseApplication ? application + "#" + module : application);
                }
            }
        }
        return modules;
    }

    /**
     * Removes the virtual servers that no longer exist from the application references of the targets that use the
     * configuration, as {@code checkVsOfAppRef} does after a virtual server is deleted.
     */
    static void removeDeletedFromReferences(AdminRestService rest, String configName) {
        List<String> names = rest.childNames(url(rest, configName));
        for (String target : targets(rest, configName)) {
            String references = rest.child(target, "application-ref");
            for (String application : rest.childNames(references)) {
                String referenceUrl = rest.child(references, application);
                Map<String, Object> attributes = new LinkedHashMap<>(rest.attributes(referenceUrl));
                List<String> kept = new ArrayList<>(split(attributes.get("virtualServers")));
                if (kept.removeIf(name -> !names.contains(name))) {
                    attributes.put("virtualServers", String.join(",", kept));
                    rest.post(referenceUrl, attributes);
                }
            }
        }
    }

    /**
     * Adds the virtual server to the application reference of its default web module and reloads the application, as
     * {@code gf.ensureDefaultWebModule} does after a virtual server is saved. Without a default web module nothing is
     * changed.
     */
    static void ensureDefaultWebModule(AdminRestService rest, String configName, String name, String webModule) {
        if (webModule == null || webModule.isEmpty()) {
            return;
        }
        String application = webModule.contains("#") ? webModule.substring(0, webModule.indexOf('#')) : webModule;
        for (String target : targets(rest, configName)) {
            String referenceUrl = rest.child(target, "application-ref", application);
            Map<String, Object> attributes = new LinkedHashMap<>(rest.attributes(referenceUrl));
            if (attributes.isEmpty()) {
                continue;
            }
            List<String> names = new ArrayList<>(split(attributes.get("virtualServers")));
            if (names.contains(name)) {
                continue;
            }
            names.add(name);
            attributes.put("virtualServers", String.join(",", names));
            rest.post(referenceUrl, attributes);
            reload(rest, referenceUrl);
        }
    }

    /** The servers and the clusters that use the configuration. */
    private static List<String> targets(AdminRestService rest, String configName) {
        List<String> targets = new ArrayList<>();
        for (String collection : List.of("servers", "clusters")) {
            String child = "servers".equals(collection) ? "server" : "cluster";
            String url = rest.url(collection, child);
            for (String name : rest.childNames(url)) {
                String targetUrl = rest.child(url, name);
                if (configName.equals(text(rest.attributes(targetUrl).get("configRef")))) {
                    targets.add(targetUrl);
                }
            }
        }
        return targets;
    }

    /** Disables and enables the application reference again, as {@code DeployUtil.reloadApplication} does. */
    private static void reload(AdminRestService rest, String referenceUrl) {
        if (!Boolean.parseBoolean(text(rest.attributes(referenceUrl).get("enabled")))) {
            return;
        }
        rest.post(referenceUrl, Map.of("enabled", "false"));
        rest.post(referenceUrl, Map.of("enabled", "true"));
    }

    private static List<String> split(Object value) {
        String text = text(value);
        List<String> names = new ArrayList<>();
        for (String name : text.split(",")) {
            if (!name.isBlank()) {
                names.add(name.strip());
            }
        }
        return names;
    }

    static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
