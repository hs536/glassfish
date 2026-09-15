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

package org.glassfish.admingui.common;

import jakarta.faces.context.FacesContext;
import jakarta.faces.context.PartialViewContext;
import jakarta.faces.context.PartialViewContextFactory;
import jakarta.faces.context.PartialViewContextWrapper;
import jakarta.servlet.http.HttpServletRequest;

import org.glassfish.admingui.common.AdminGuiViewHandler.Route;

/**
 * Renders a Facelets page as a whole when it is requested with GET, even if the request carries the Ajax header.
 *
 * <p>
 * Prototype (adr/0006, X-30): a button or tab of a JSFTemplating page posts back with Ajax, and its handler
 * {@code gf.redirect} answers with an HTTP redirect. The browser follows the redirect with a GET that keeps the Ajax
 * header {@code Faces-Request: partial/ajax}. When the target is a JSFTemplating page, the partial response it renders
 * has the update {@code jakarta.faces.ViewRoot}, which the page frame ({@code admingui.ajax.handleResponse}) puts into
 * the content area. A Facelets page would render a partial response without that update, and the frame would show the
 * XML. A GET is never a Faces Ajax postback, so the Facelets page renders its content as HTML, which the frame also puts
 * into the content area.
 */
public class AdminGuiPartialViewContextFactory extends PartialViewContextFactory {

    public AdminGuiPartialViewContextFactory(PartialViewContextFactory wrapped) {
        super(wrapped);
    }

    @Override
    public PartialViewContext getPartialViewContext(FacesContext context) {
        PartialViewContext partialViewContext = getWrapped().getPartialViewContext(context);
        Object request = context.getExternalContext().getRequest();
        String method = request instanceof HttpServletRequest http ? http.getMethod() : null;
        if (rendersWholeView(method, AdminGuiViewHandler.route(context))) {
            return new WholeViewContext(partialViewContext);
        }
        return partialViewContext;
    }

    /** A GET request routed to a Facelets page renders the whole view; other requests are left as they are. */
    static boolean rendersWholeView(String method, Route route) {
        return "GET".equalsIgnoreCase(method) && route == Route.FACELETS;
    }

    private static final class WholeViewContext extends PartialViewContextWrapper {

        WholeViewContext(PartialViewContext wrapped) {
            super(wrapped);
        }

        @Override
        public boolean isAjaxRequest() {
            return false;
        }

        @Override
        public boolean isPartialRequest() {
            return false;
        }
    }
}
