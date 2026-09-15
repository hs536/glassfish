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

    enum Route {
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
    static Route route(FacesContext context) {
        ExternalContext externalContext = context.getExternalContext();
        String servletPath = externalContext.getRequestServletPath();
        if (servletPath == null || !servletPath.endsWith(TEMPLATING_SUFFIX)) {
            // No view lookup and no request map write; also the case during application startup, where the request
            // map cannot be written
            return route(servletPath, viewId -> false, "GET", null);
        }
        Map<String, Object> requestMap = externalContext.getRequestMap();
        Route route = (Route) requestMap.get(ROUTE);
        if (route == null) {
            Object request = externalContext.getRequest();
            String method = request instanceof jakarta.servlet.http.HttpServletRequest http ? http.getMethod() : "GET";
            String queryString = request instanceof jakarta.servlet.http.HttpServletRequest http ? http.getQueryString() : null;
            route = route(servletPath,
                    viewId -> context.getApplication().getResourceHandler().createViewResource(context, viewId) != null,
                    method, queryString);
            requestMap.put(ROUTE, route);
        }
        return route;
    }

    /**
     * The routing rule. It reads neither the request parameters nor the request body, so that the body of a post is
     * decoded later with the character encoding of the request (X-19): every post to a page that has a Facelets view is a
     * postback of that view, and {@code bare=true} is looked up in the query string.
     *
     * @param servletPath the servlet path of the request, or {@code null}
     * @param hasFaceletsView tells whether a Facelets view with the given view id exists
     * @param method the HTTP method of the request
     * @param queryString the query string of the request, or {@code null}
     */
    static Route route(String servletPath, java.util.function.Predicate<String> hasFaceletsView, String method, String queryString) {
        if (servletPath == null) {
            return Route.TEMPLATING;
        }
        if (servletPath.endsWith(FACELETS_SUFFIX)) {
            return Route.FACELETS;
        }
        if (!servletPath.endsWith(TEMPLATING_SUFFIX)) {
            return Route.TEMPLATING;
        }
        String viewId = servletPath.substring(0, servletPath.length() - TEMPLATING_SUFFIX.length()) + FACELETS_SUFFIX;
        if (!hasFaceletsView.test(viewId)) {
            return Route.TEMPLATING;
        }
        if ("POST".equalsIgnoreCase(method) || isQueryParameterTrue(queryString, BARE_PARAMETER)) {
            return Route.FACELETS;
        }
        return Route.SHELL;
    }

    private static boolean isQueryParameterTrue(String queryString, String name) {
        if (queryString == null) {
            return false;
        }
        for (String parameter : queryString.split("&")) {
            int equals = parameter.indexOf('=');
            if (equals > 0
                    && name.equals(java.net.URLDecoder.decode(parameter.substring(0, equals), java.nio.charset.StandardCharsets.UTF_8))
                    && "true".equals(java.net.URLDecoder.decode(parameter.substring(equals + 1), java.nio.charset.StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }
}
