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
import jakarta.servlet.ServletContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.glassfish.admingui.common.plugin.ConsoleClassLoader;
import org.glassfish.admingui.connector.IntegrationPoint;
import org.glassfish.admingui.plugin.ConsolePluginService;
import org.glassfish.hk2.api.ServiceLocator;

/**
 * Integration points for Facelets pages.
 *
 * <p>
 * Prototype (docs/試作計画.md P-7). The integration points keep their type names and their registration in
 * {@code console-config.xml}. A Facelets page includes, for each point of a type, the Facelets view that has the name
 * of the point's content with {@code .xhtml} instead of {@code .inc}, when the plugin provides one:
 * {@code <c:forEach items="#{consoleIntegrationPoints.faceletsViews('org.glassfish.admingui:TargetSectionForResource')}" var="view"><ui:include src="#{view}"/></c:forEach>}.
 */
@Named
@ApplicationScoped
public class ConsoleIntegrationPoints {

    private static final String TEMPLATING_SUFFIX = ".inc";
    private static final String FACELETS_SUFFIX = ".xhtml";

    /** The view ids of the Facelets content of the integration points of the given type, by priority. */
    public List<String> faceletsViews(String type) {
        FacesContext context = FacesContext.getCurrentInstance();
        List<IntegrationPoint> points = new ArrayList<>(pluginService(context).getIntegrationPoints(type));
        points.sort(Comparator.comparingInt(IntegrationPoint::getPriority));
        List<String> views = new ArrayList<>();
        for (IntegrationPoint point : points) {
            String content = point.getContent();
            if (content == null || content.contains("://") || !content.endsWith(TEMPLATING_SUFFIX)) {
                continue;
            }
            while (content.startsWith("/")) {
                content = content.substring(1);
            }
            String view = "/" + point.getConsoleConfigId() + "/"
                    + content.substring(0, content.length() - TEMPLATING_SUFFIX.length()) + FACELETS_SUFFIX;
            if (context.getApplication().getResourceHandler().createViewResource(context, view) != null) {
                views.add(view);
            }
        }
        return views;
    }

    private static ConsolePluginService pluginService(FacesContext context) {
        ServletContext servletContext = (ServletContext) context.getExternalContext().getContext();
        ServiceLocator locator = (ServiceLocator) servletContext.getAttribute(ConsoleClassLoader.HABITAT_ATTRIBUTE);
        return locator.getService(ConsolePluginService.class);
    }
}
