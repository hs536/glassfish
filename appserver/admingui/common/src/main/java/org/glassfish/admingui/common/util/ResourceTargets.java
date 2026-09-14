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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The targets a resource can be referenced from: the server, the standalone instances and the clusters, in this
 * order, each with its admin REST URL.
 *
 * <p>
 * Prototype (adr/0008): the lookup of {@code shared/targetsList.inc} and {@code gfr.getResourcesTableData}.
 */
public final class ResourceTargets implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String SERVER = "server";

    /** Target name to its REST URL. */
    private final Map<String, String> urls = new LinkedHashMap<>();
    private final List<String> clusters = new ArrayList<>();

    private ResourceTargets() {
    }

    public static ResourceTargets load(AdminRestService rest) {
        ResourceTargets targets = new ResourceTargets();
        targets.urls.put(SERVER, rest.url("servers", "server", SERVER));
        Map<String, Object> instances = rest.get(rest.url("list-instances"), Map.of("standaloneonly", "true", "nostatus", "true"));
        if (AdminRestService.extraProperties(instances).get("instanceList") instanceof List<?> list) {
            for (Object instance : list) {
                if (instance instanceof Map<?, ?> map && map.get("name") != null) {
                    String name = map.get("name").toString();
                    targets.urls.put(name, rest.url("servers", "server", name));
                }
            }
        }
        for (String cluster : rest.childNames(rest.url("clusters", "cluster"))) {
            targets.urls.put(cluster, rest.url("clusters", "cluster", cluster));
            targets.clusters.add(cluster);
        }
        return targets;
    }

    /** The target names, the server first. */
    public List<String> names() {
        return List.copyOf(urls.keySet());
    }

    public boolean isCluster(String target) {
        return clusters.contains(target);
    }

    /** True when the domain has only the server as target. */
    public boolean onlyServer() {
        return urls.size() == 1;
    }

    /** The REST URL of the resource references of the target. */
    public String referencesUrl(String target) {
        String url = urls.get(target);
        if (url == null) {
            throw new IllegalArgumentException("Unknown target: " + target);
        }
        return url + "/resource-ref";
    }

    /** For each target, the names of the resources it references. */
    public Map<String, List<String>> references(AdminRestService rest) {
        Map<String, List<String>> references = new LinkedHashMap<>();
        for (String target : urls.keySet()) {
            references.put(target, rest.childNames(referencesUrl(target)));
        }
        return Collections.unmodifiableMap(references);
    }
}
