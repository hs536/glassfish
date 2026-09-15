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

import jakarta.faces.context.FacesContext;

import java.util.ArrayList;
import java.util.List;

import org.glassfish.admingui.common.util.AdminRestService;

/**
 * The resource adapters that the connector pages offer (prototype, adr/0008).
 */
final class ConnectorModules {

    /** The session attribute that the JMS plugin sets when it adds its tree nodes. */
    private static final String JMS_EXISTS = "_jms_exist";

    private ConnectorModules() {
    }

    /**
     * The connector modules of the deployed applications, as {@code gfr.getApplicationsBySnifferType} lists them: the
     * application name, or {@code application#module} for a module of an enterprise application.
     */
    static List<String> names(AdminRestService rest) {
        List<String> names = new ArrayList<>();
        String applications = rest.url("applications", "application");
        for (String application : rest.childNames(applications)) {
            String applicationUrl = rest.child(applications, application);
            boolean enterpriseApplication = rest.childNames(rest.child(applicationUrl, "engine")).contains("ear");
            String modules = rest.child(applicationUrl, "module");
            for (String module : rest.childNames(modules)) {
                if (rest.childNames(rest.child(modules, module, "engine")).contains("connector")) {
                    names.add(enterpriseApplication ? application + "#" + module : application);
                }
            }
        }
        return names;
    }

    /**
     * The resource adapters that a connector connection pool can use, as the JSFTemplating pool pages list them: the
     * system adapters that allow pools, when the JMS plugin is present, then the connector modules of the deployed
     * applications, a module of an enterprise application without its {@code .rar} extension
     * ({@code filterOutRarExtension}).
     */
    static List<String> poolAdapters(AdminRestService rest) {
        List<String> adapters = new ArrayList<>();
        if (jmsExists()) {
            Object rars = AdminRestService.extraProperties(rest.get(rest.url("resources", "get-system-rars-allowing-pool-creation"), null)).get("rarList");
            if (rars instanceof List<?> list) {
                list.forEach(rar -> adapters.add(String.valueOf(rar)));
            }
        }
        for (String module : names(rest)) {
            adapters.add(module.contains("#") && module.endsWith(".rar") ? module.substring(0, module.length() - ".rar".length()) : module);
        }
        return adapters;
    }

    /** True when the JMS plugin is present. */
    static boolean jmsExists() {
        return "true".equals(String.valueOf(FacesContext.getCurrentInstance().getExternalContext().getSessionMap().get(JMS_EXISTS)));
    }
}
