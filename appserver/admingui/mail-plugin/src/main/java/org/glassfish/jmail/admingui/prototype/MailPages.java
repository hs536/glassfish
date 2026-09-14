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
package org.glassfish.jmail.admingui.prototype;

import jakarta.faces.context.FacesContext;

import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.glassfish.admingui.common.util.ConsoleMessages;
import org.glassfish.admingui.common.util.PropertyRows;
import org.glassfish.admingui.plugin.jmail.MailTestMessage;

/**
 * Values and actions shared by the Jakarta Mail session pages (prototype, adr/0008).
 */
final class MailPages {

    static final String CHILD_TYPE = "mail-resource";
    static final List<String> CONVERT_TO_FALSE = List.of("enabled", "debug");

    private static final String STRINGS = "org.glassfish.jmail.admingui.Strings";

    private MailPages() {
    }

    static boolean isDebug(Map<String, Object> values) {
        return "true".equals(String.valueOf(values.get("debug")));
    }

    static void setDebug(Map<String, Object> values, boolean debug) {
        values.put("debug", String.valueOf(debug));
    }

    /** Sends a test message with the values of the page, as the Send test email button of the JSFTemplating pages. */
    static void sendTestEmail(Map<String, Object> values, PropertyRows properties) {
        try {
            MailTestMessage.send(values, properties.toSend());
            FacesContext context = FacesContext.getCurrentInstance();
            ConsoleMessages.info(ResourceBundle.getBundle(STRINGS, context.getViewRoot().getLocale(), MailPages.class.getClassLoader())
                    .getString("msg.SendSucceed"));
        } catch (Exception e) {
            ConsoleMessages.error(ConsoleMessages.core("msg.Error") + " " + e.getMessage());
        }
    }
}
