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
package org.glassfish.admingui.common.util;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Texts of the console core bundle and page messages for Facelets pages.
 *
 * <p>
 * Prototype (docs/試作計画.md, adr/0008). The core bundle is part of the web application, so it is loaded with the
 * context class loader of the request.
 */
public final class ConsoleMessages {

    private static final String CORE_STRINGS = "org.glassfish.admingui.core.Strings";

    private ConsoleMessages() {
    }

    /** The text of a key of the core bundle in the locale of the current view, formatted with the arguments. */
    public static String core(String key, Object... arguments) {
        String text = ResourceBundle.getBundle(CORE_STRINGS, locale(), Thread.currentThread().getContextClassLoader()).getString(key);
        return arguments.length == 0 ? text : MessageFormat.format(text, arguments);
    }

    public static void info(String text) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, text, null));
    }

    public static void error(String text) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, text, null));
    }

    private static java.util.Locale locale() {
        FacesContext context = FacesContext.getCurrentInstance();
        return context.getViewRoot() != null ? context.getViewRoot().getLocale()
                : context.getApplication().getViewHandler().calculateLocale(context);
    }
}
