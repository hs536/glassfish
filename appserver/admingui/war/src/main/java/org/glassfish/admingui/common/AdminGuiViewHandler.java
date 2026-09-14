/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.glassfish.admingui.common;

import jakarta.faces.application.Application;
import jakarta.faces.application.ViewHandler;
import jakarta.faces.application.ViewHandlerWrapper;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.render.ResponseStateManager;

import java.util.Map;

/**
 *
 * @author Ondro Mihalyi
 */
public class AdminGuiViewHandler extends ViewHandlerWrapper {

    private static final String FACELETS_SUFFIX = ".xhtml";
    private static final String TEMPLATING_SUFFIX = ".jsf";
    private static final String BARE_PARAMETER = "bare";
    private static final String SHELL_VIEW_ID = "/facesShell.jsf";
    private static final String ROUTE = AdminGuiViewHandler.class.getName() + ".route";

    private ViewHandler defaultViewHandler;

    public AdminGuiViewHandler(ViewHandler wrapped) {
        super(wrapped);
        Application app = FacesContext.getCurrentInstance().getApplication();
        if (app instanceof AdminGuiApplication) {
            defaultViewHandler = ((AdminGuiApplication) app).getDefaultViewHandler();
        } else {
            defaultViewHandler = wrapped;
        }
    }

    @Override
    public ViewHandler getWrapped() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (defaultViewHandler != null && context != null && route(context) == Route.FACELETS) {
            return defaultViewHandler;
        }
        return super.getWrapped();
    }

    @Override
    public UIViewRoot createView(FacesContext context, String viewId) {
        if (route(context) == Route.SHELL) {
            return super.getWrapped().createView(context, SHELL_VIEW_ID);
        }
        return getWrapped().createView(context, viewId);
    }

    private enum Route {
        /** The request is handled by the default Faces view handler (Facelets). */
        FACELETS,
        /** A Facelets page opened directly: JSFTemplating renders the page frame, which then loads the page. */
        SHELL,
        /** The request is handled by JSFTemplating. */
        TEMPLATING
    }

    /**
     * Requests for {@code .xhtml} views go to Facelets. A request for a {@code .jsf} page that has a Facelets view with
     * the same name goes to Facelets when it asks for the page content only ({@code bare=true}) or posts back to the
     * view, and to the page frame otherwise. Other requests go to JSFTemplating.
     */
    private static Route route(FacesContext context) {
        ExternalContext externalContext = context.getExternalContext();
        String servletPath = externalContext.getRequestServletPath();
        if (servletPath == null || !servletPath.endsWith(TEMPLATING_SUFFIX)) {
            // Also the case during application startup, where the request map cannot be written
            return servletPath != null && servletPath.endsWith(FACELETS_SUFFIX) ? Route.FACELETS : Route.TEMPLATING;
        }
        Map<String, Object> requestMap = externalContext.getRequestMap();
        Route route = (Route) requestMap.get(ROUTE);
        if (route == null) {
            route = findRoute(context, servletPath);
            requestMap.put(ROUTE, route);
        }
        return route;
    }

    private static Route findRoute(FacesContext context, String servletPath) {
        String viewId = servletPath.substring(0, servletPath.length() - TEMPLATING_SUFFIX.length()) + FACELETS_SUFFIX;
        if (context.getApplication().getResourceHandler().createViewResource(context, viewId) == null) {
            return Route.TEMPLATING;
        }
        Map<String, String> parameters = context.getExternalContext().getRequestParameterMap();
        if ("true".equals(parameters.get(BARE_PARAMETER)) || parameters.containsKey(ResponseStateManager.VIEW_STATE_PARAM)) {
            return Route.FACELETS;
        }
        return Route.SHELL;
    }
}
