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

package org.glassfish.web.admingui.prototype;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import jakarta.faces.context.FacesContext;

/**
 * The texts of this plugin for the messages the Bean classes show.
 *
 * <p>
 * Prototype (adr/0008): the pages read the texts through {@code consoleBundles}; this is for the Bean classes.
 */
final class WebStrings {

    private static final String BUNDLE = "org.glassfish.web.admingui.Strings";

    private WebStrings() {
    }

    static String get(String key, Object... arguments) {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, FacesContext.getCurrentInstance().getViewRoot().getLocale(),
                WebStrings.class.getClassLoader());
        String text = bundle.getString(key);
        return arguments.length == 0 ? text : MessageFormat.format(text, arguments);
    }
}
