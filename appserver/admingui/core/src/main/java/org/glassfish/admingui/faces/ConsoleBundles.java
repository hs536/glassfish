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
package org.glassfish.admingui.faces;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;

import java.util.Locale;
import java.util.ResourceBundle;

import org.glassfish.admingui.common.plugin.ConsoleClassLoader;

/**
 * Resource bundles of console plugins for Facelets pages, in the locale of the current view:
 * {@code #{consoleBundles.get('jdbc', 'org.glassfish.jdbc.admingui.Strings')['jdbcResources.pageTitle']}}.
 *
 * <p>
 * Prototype (docs/試作計画.md P-5, P-6). A plugin bundle is not visible to the class loader of the web application, so
 * the bundle is loaded with the class loader of the plugin; language packs attached to the plugin are found the same
 * way.
 */
@Named
@ApplicationScoped
public class ConsoleBundles {

    /**
     * The bundle {@code baseName} of the plugin {@code pluginId}; with an empty plugin id, a bundle of the web
     * application (for example {@code org.glassfish.admingui.core.Strings}).
     */
    public ResourceBundle get(String pluginId, String baseName) {
        ClassLoader loader = pluginId == null || pluginId.isEmpty()
                ? Thread.currentThread().getContextClassLoader()
                : ConsoleClassLoader.findModuleClassLoader(pluginId);
        if (loader == null) {
            throw new IllegalArgumentException("Unknown console plugin: " + pluginId);
        }
        return ResourceBundle.getBundle(baseName, locale(), loader);
    }

    private static Locale locale() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (context.getViewRoot() != null && context.getViewRoot().getLocale() != null) {
            return context.getViewRoot().getLocale();
        }
        return context.getApplication().getViewHandler().calculateLocale(context);
    }
}
