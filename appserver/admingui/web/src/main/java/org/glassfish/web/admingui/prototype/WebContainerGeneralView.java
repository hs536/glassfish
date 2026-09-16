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

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import java.util.List;

/**
 * The General tab of the web container: its additional properties.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/configuration/webContainerGeneral.jsf}.
 */
@Named
@ViewScoped
public class WebContainerGeneralView extends WebContainerView {

    private static final long serialVersionUID = 1L;

    @Override
    protected List<String> path() {
        return List.of("web-container");
    }

    /** The page shows only the additional properties of the web container. */
    @Override
    protected boolean hasAttributes() {
        return false;
    }
}
