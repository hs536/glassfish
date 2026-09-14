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

import java.util.List;
import java.util.ResourceBundle;

import org.glassfish.admingui.common.util.AdminRestService;

/**
 * Values shared by the JDBC resource pages (prototype, adr/0008).
 */
final class JdbcPages {

    static final String CHILD_TYPE = "jdbc-resource";
    static final String LIST_COMMAND = "list-jdbc-resources";
    static final String LIST_KEY = "jdbcResources";

    private static final String STRINGS = "org.glassfish.jdbc.admingui.Strings";

    private JdbcPages() {
    }

    static List<String> pools(AdminRestService rest) {
        return rest.childNames(rest.url("resources", "jdbc-connection-pool"));
    }

    /** The help text of the pool field. The message contains markup and an expression for the context path (X-21). */
    static String poolHelp() {
        FacesContext context = FacesContext.getCurrentInstance();
        String text = ResourceBundle.getBundle(STRINGS, context.getViewRoot().getLocale(), JdbcPages.class.getClassLoader())
                .getString("jdbcResource.poolHelp");
        return context.getApplication().evaluateExpressionGet(context, text, String.class);
    }
}
