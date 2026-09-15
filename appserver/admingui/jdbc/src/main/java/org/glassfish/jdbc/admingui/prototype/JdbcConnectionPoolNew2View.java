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

package org.glassfish.jdbc.admingui.prototype;

import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * The second page of the new JDBC connection pool wizard (prototype, adr/0008): the class names, the pool settings and
 * the properties. The Facelets counterpart of {@code jdbc/jdbcConnectionPoolNew2.jsf},
 * {@code jdbc/poolPropertyNew.inc} and {@code jdbc/jdbcConnectionPoolNew2Buttons.inc}.
 */
@Named
@ViewScoped
public class JdbcConnectionPoolNew2View implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String LIST_PAGE = "/jdbc/jdbcConnectionPools.jsf";

    @Inject
    private JdbcConnectionPoolWizard wizard;

    public JdbcConnectionPoolWizard getWizard() {
        return wizard;
    }

    /** Opens the first page, which keeps the values of the wizard. */
    public void previous() {
        loadPage("/jdbc/jdbcConnectionPoolNew1.jsf?fromStep2=true");
    }

    /** Choosing another data source class replaces the properties with those of the class. */
    public void datasourceChanged() {
        try {
            wizard.listProperties();
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /**
     * Creates the pool and opens the pools list. When Ping is chosen, the pool is pinged, and the list shows the result
     * from the alert parameters, as after the JSFTemplating wizard (X-35): a failed ping is a warning, since the pool is
     * created.
     */
    public void finish() {
        try {
            wizard.create();
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
            return;
        }
        String page = LIST_PAGE;
        if (wizard.isPing()) {
            try {
                wizard.ping();
                page = LIST_PAGE + alert("success", ConsoleMessages.core("msg.PingSucceed"), null);
            } catch (RuntimeException e) {
                page = LIST_PAGE + alert("warning", JdbcStrings.get("msg.warning.poolCreatedPingFailed"), e.getMessage());
            }
        }
        wizard.end();
        loadPage(page);
    }

    public void cancel() {
        wizard.end();
        loadPage(LIST_PAGE);
    }

    private static String alert(String type, String summary, String detail) {
        return "?alertType=" + type + "&alertSummary=" + URLEncoder.encode(summary, StandardCharsets.UTF_8)
                + "&alertDetail=" + URLEncoder.encode(detail == null ? "" : detail, StandardCharsets.UTF_8);
    }

    private static void loadPage(String page) {
        FacesContext context = FacesContext.getCurrentInstance();
        String url = context.getExternalContext().getRequestContextPath() + page;
        context.getPartialViewContext().getEvalScripts().add("admingui.ajax.loadPage({url: '" + url + "'});");
    }
}
