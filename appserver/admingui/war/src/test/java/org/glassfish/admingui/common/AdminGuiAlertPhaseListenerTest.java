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

import jakarta.faces.application.FacesMessage;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdminGuiAlertPhaseListenerTest {

    @Test
    public void noSummaryGivesNoMessage() {
        assertTrue(AdminGuiAlertPhaseListener.messages("warning", null, "detail").isEmpty());
        assertTrue(AdminGuiAlertPhaseListener.messages("warning", "", "detail").isEmpty());
    }

    @Test
    public void summaryAndDetailAreTwoMessages() {
        List<FacesMessage> messages = AdminGuiAlertPhaseListener.messages("warning", "Pool created", "Ping failed");
        assertEquals(2, messages.size());
        assertEquals("Pool created", messages.get(0).getSummary());
        assertEquals("Ping failed", messages.get(1).getSummary());
        assertEquals(FacesMessage.SEVERITY_WARN, messages.get(0).getSeverity());
        assertEquals(FacesMessage.SEVERITY_WARN, messages.get(1).getSeverity());
    }

    @Test
    public void summaryWithoutDetailIsOneMessage() {
        assertEquals(1, AdminGuiAlertPhaseListener.messages("success", "Ping Succeeded", "").size());
        assertEquals(1, AdminGuiAlertPhaseListener.messages("success", "Ping Succeeded", null).size());
    }

    @Test
    public void typesGiveSeverities() {
        assertEquals(FacesMessage.SEVERITY_ERROR, AdminGuiAlertPhaseListener.messages("error", "s", null).get(0).getSeverity());
        assertEquals(FacesMessage.SEVERITY_INFO, AdminGuiAlertPhaseListener.messages("success", "s", null).get(0).getSeverity());
        assertEquals(FacesMessage.SEVERITY_INFO, AdminGuiAlertPhaseListener.messages("information", "s", null).get(0).getSeverity());
        assertEquals(FacesMessage.SEVERITY_INFO, AdminGuiAlertPhaseListener.messages(null, "s", null).get(0).getSeverity());
    }
}
