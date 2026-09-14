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
import java.util.Map;
import java.util.function.Supplier;

import org.glassfish.admingui.common.AdminGuiViewHandler.Route;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdminGuiViewHandlerTest {

    private static final String VIEW_STATE = "jakarta.faces.ViewState";

    @Test
    public void requestWithoutServletPathGoesToTemplating() {
        assertEquals(Route.TEMPLATING, AdminGuiViewHandler.route(null, viewId -> true, failingParameters()));
    }

    @Test
    public void faceletsViewGoesToFacelets() {
        assertEquals(Route.FACELETS, AdminGuiViewHandler.route("/jdbc/prototype/page.xhtml", viewId -> false, failingParameters()));
    }

    @Test
    public void otherRequestsGoToTemplating() {
        assertEquals(Route.TEMPLATING, AdminGuiViewHandler.route("/resource/common/css/style.css", viewId -> true, failingParameters()));
    }

    @Test
    public void pageWithoutFaceletsViewGoesToTemplatingWithoutReadingParameters() {
        List<String> lookedUp = new ArrayList<>();
        Route route = AdminGuiViewHandler.route("/jdbc/jdbcConnectionPools.jsf", viewId -> lookedUp.add(viewId) && false, failingParameters());

        assertEquals(Route.TEMPLATING, route);
        assertEquals(List.of("/jdbc/jdbcConnectionPools.xhtml"), lookedUp);
    }

    @Test
    public void directRequestForMigratedPageGoesToShell() {
        assertEquals(Route.SHELL, AdminGuiViewHandler.route("/jdbc/jdbcResources.jsf", viewId -> true, () -> Map.of("name", "jdbc/x")));
    }

    @Test
    public void contentRequestForMigratedPageGoesToFacelets() {
        assertEquals(Route.FACELETS, AdminGuiViewHandler.route("/jdbc/jdbcResources.jsf", viewId -> true, () -> Map.of("bare", "true")));
    }

    @Test
    public void bareOtherThanTrueGoesToShell() {
        assertEquals(Route.SHELL, AdminGuiViewHandler.route("/jdbc/jdbcResources.jsf", viewId -> true, () -> Map.of("bare", "false")));
    }

    @Test
    public void postbackToMigratedPageGoesToFacelets() {
        assertEquals(Route.FACELETS, AdminGuiViewHandler.route("/jdbc/jdbcResources.jsf", viewId -> true, () -> Map.of(VIEW_STATE, "x")));
    }

    @Test
    public void viewStateParameterNameIsTheFacesConstant() {
        assertTrue(VIEW_STATE.equals(jakarta.faces.render.ResponseStateManager.VIEW_STATE_PARAM));
    }

    private static Supplier<Map<String, String>> failingParameters() {
        return () -> {
            throw new AssertionError("the request parameters must not be read");
        };
    }
}
