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

import org.glassfish.admingui.common.AdminGuiViewHandler.Route;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdminGuiPartialViewContextFactoryTest {

    @Test
    public void getForFaceletsPageRendersWholeView() {
        assertTrue(AdminGuiPartialViewContextFactory.rendersWholeView("GET", Route.FACELETS));
        assertTrue(AdminGuiPartialViewContextFactory.rendersWholeView("get", Route.FACELETS));
    }

    @Test
    public void postbackOfFaceletsPageStaysPartial() {
        assertFalse(AdminGuiPartialViewContextFactory.rendersWholeView("POST", Route.FACELETS));
    }

    @Test
    public void requestsForOtherRoutesStayAsTheyAre() {
        assertFalse(AdminGuiPartialViewContextFactory.rendersWholeView("GET", Route.TEMPLATING));
        assertFalse(AdminGuiPartialViewContextFactory.rendersWholeView("GET", Route.SHELL));
    }

    @Test
    public void requestWithoutMethodStaysAsItIs() {
        assertFalse(AdminGuiPartialViewContextFactory.rendersWholeView(null, Route.FACELETS));
    }
}
