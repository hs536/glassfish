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

import jakarta.faces.application.ResourceHandler;
import jakarta.faces.application.ResourceHandlerWrapper;
import jakarta.faces.application.ViewResource;
import jakarta.faces.context.FacesContext;

import java.net.URL;

import org.glassfish.admingui.common.plugin.ConsoleClassLoader;

/**
 * Lets Facelets views live in console plugin bundles.
 *
 * <p>A view that the web application itself does not contain is looked up in the plugin whose console-config id
 * is the first segment of the view id: {@code /jdbc/resources.xhtml} is the resource {@code resources.xhtml} of the
 * plugin with id {@code jdbc}. This is the same convention the console already uses for JSFTemplating pages.
 */
public class PluginViewResourceHandler extends ResourceHandlerWrapper {

    public PluginViewResourceHandler(ResourceHandler wrapped) {
        super(wrapped);
    }

    @Override
    public ViewResource createViewResource(FacesContext context, String resourceName) {
        ViewResource resource = super.createViewResource(context, resourceName);
        if (resource != null) {
            return resource;
        }
        URL url = findInPlugin(resourceName);
        return url == null ? null : new PluginViewResource(url);
    }

    /** The URL of the resource in the plugin named by the first path segment, or null. */
    static URL findInPlugin(String resourceName) {
        if (!isServableViewName(resourceName)) {
            return null;
        }
        int slash = resourceName.indexOf('/', 1);
        ClassLoader pluginClassLoader = ConsoleClassLoader.findModuleClassLoader(resourceName.substring(1, slash));
        return pluginClassLoader == null ? null : pluginClassLoader.getResource(resourceName.substring(slash + 1));
    }

    /**
     * Whether a view id may be looked up in a plugin (X-18): a Facelets file ({@code .xhtml}) below a plugin id, outside
     * {@code META-INF} and {@code WEB-INF}, without hidden, empty or parent segments, and without characters that could
     * change the meaning of the path (backslash, percent, colon).
     */
    static boolean isServableViewName(String resourceName) {
        if (resourceName == null || !resourceName.startsWith("/") || !resourceName.endsWith(".xhtml")) {
            return false;
        }
        if (resourceName.indexOf('\\') >= 0 || resourceName.indexOf('%') >= 0 || resourceName.indexOf(':') >= 0) {
            return false;
        }
        String[] segments = resourceName.substring(1).split("/", -1);
        if (segments.length < 2) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isEmpty() || segment.startsWith(".") || segment.equalsIgnoreCase("META-INF") || segment.equalsIgnoreCase("WEB-INF")) {
                return false;
            }
        }
        return true;
    }

    private static final class PluginViewResource extends ViewResource {

        private final URL url;

        PluginViewResource(URL url) {
            this.url = url;
        }

        @Override
        public URL getURL() {
            return url;
        }
    }
}
