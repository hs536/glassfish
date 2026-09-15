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
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.PhaseEvent;
import jakarta.faces.event.PhaseId;
import jakarta.faces.event.PhaseListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.AdminGuiViewHandler.Route;

/**
 * Shows on a Facelets page the result of an operation that the previous page passes in the request parameters
 * {@code alertType}, {@code alertSummary} and {@code alertDetail}, as the JSFTemplating pages do
 * ({@code GuiUtil.prepareAlert} with {@code gf.redirect}, shown by {@code shared/alertMsg_1.inc}).
 *
 * <p>
 * Prototype (adr/0006, X-35): while JSFTemplating and Facelets pages coexist, a JSFTemplating page redirects to a
 * Facelets page with these parameters, and a Facelets page that replaces a JSFTemplating page passes its result to the
 * next page in the same way. The summary and the detail become messages, which the page escapes when it renders them.
 */
public class AdminGuiAlertPhaseListener implements PhaseListener {

    private static final long serialVersionUID = 1L;

    @Override
    public PhaseId getPhaseId() {
        return PhaseId.RENDER_RESPONSE;
    }

    @Override
    public void beforePhase(PhaseEvent event) {
        FacesContext context = event.getFacesContext();
        if (context.isPostback() || AdminGuiViewHandler.route(context) != Route.FACELETS) {
            return;
        }
        Map<String, String> parameters = context.getExternalContext().getRequestParameterMap();
        for (FacesMessage message : messages(parameters.get("alertType"), parameters.get("alertSummary"), parameters.get("alertDetail"))) {
            context.addMessage(null, message);
        }
    }

    @Override
    public void afterPhase(PhaseEvent event) {
    }

    /**
     * The messages of an alert: the summary, then the detail on its own line as the alert of the JSFTemplating pages
     * shows it. There is no message without a summary. The types are those of {@code GuiUtil.prepareAlert}.
     */
    static List<FacesMessage> messages(String type, String summary, String detail) {
        if (summary == null || summary.isEmpty()) {
            return List.of();
        }
        FacesMessage.Severity severity = switch (type == null ? "" : type) {
            case "error" -> FacesMessage.SEVERITY_ERROR;
            case "warning" -> FacesMessage.SEVERITY_WARN;
            default -> FacesMessage.SEVERITY_INFO;
        };
        List<FacesMessage> messages = new ArrayList<>();
        messages.add(new FacesMessage(severity, summary, null));
        if (detail != null && !detail.isEmpty()) {
            messages.add(new FacesMessage(severity, detail, null));
        }
        return messages;
    }
}
