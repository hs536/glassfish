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

import java.util.ArrayList;
import java.util.List;

import org.glassfish.admingui.common.AdminGuiViewHandler.Route;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AdminGuiViewHandlerTest {

    private static final String MIGRATED_PAGE = "/jdbc/jdbcResources.jsf";

    @Test
    public void requestWithoutServletPathGoesToTemplating() {
        assertEquals(Route.TEMPLATING, AdminGuiViewHandler.route(null, viewId -> true, "GET", null));
    }

    @Test
    public void faceletsViewGoesToFacelets() {
        assertEquals(Route.FACELETS, AdminGuiViewHandler.route("/jdbc/prototype/page.xhtml", viewId -> false, "GET", null));
    }

    @Test
    public void otherRequestsGoToTemplating() {
        assertEquals(Route.TEMPLATING, AdminGuiViewHandler.route("/resource/common/css/style.css", viewId -> true, "GET", null));
    }

    @Test
    public void pageWithoutFaceletsViewGoesToTemplating() {
        List<String> lookedUp = new ArrayList<>();
        Route route = AdminGuiViewHandler.route("/jdbc/jdbcConnectionPools.jsf", viewId -> lookedUp.add(viewId) && false, "POST", "bare=true");

        assertEquals(Route.TEMPLATING, route);
        assertEquals(List.of("/jdbc/jdbcConnectionPools.xhtml"), lookedUp);
    }

    @Test
    public void directRequestForMigratedPageGoesToShell() {
        assertEquals(Route.SHELL, AdminGuiViewHandler.route(MIGRATED_PAGE, viewId -> true, "GET", "name=jdbc%2Fx"));
    }

    @Test
    public void directRequestWithoutQueryGoesToShell() {
        assertEquals(Route.SHELL, AdminGuiViewHandler.route(MIGRATED_PAGE, viewId -> true, "GET", null));
    }

    @Test
    public void contentRequestForMigratedPageGoesToFacelets() {
        assertEquals(Route.FACELETS, AdminGuiViewHandler.route(MIGRATED_PAGE, viewId -> true, "GET", "name=jdbc%2Fx&bare=true"));
    }

    @Test
    public void encodedBareParameterIsDecoded() {
        assertEquals(Route.FACELETS, AdminGuiViewHandler.route(MIGRATED_PAGE, viewId -> true, "GET", "b%61re=tru%65"));
    }

    @Test
    public void bareOtherThanTrueGoesToShell() {
        assertEquals(Route.SHELL, AdminGuiViewHandler.route(MIGRATED_PAGE, viewId -> true, "GET", "bare=false"));
        assertEquals(Route.SHELL, AdminGuiViewHandler.route(MIGRATED_PAGE, viewId -> true, "GET", "bare=trueish"));
        assertEquals(Route.SHELL, AdminGuiViewHandler.route(MIGRATED_PAGE, viewId -> true, "GET", "notbare=true"));
    }

    @Test
    public void postToMigratedPageGoesToFacelets() {
        assertEquals(Route.FACELETS, AdminGuiViewHandler.route(MIGRATED_PAGE, viewId -> true, "POST", null));
    }
}
