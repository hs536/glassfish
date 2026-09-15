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

package org.glassfish.concurrent.admingui.prototype;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import org.glassfish.admingui.common.util.ResourceListView;

/**
 * The context services list page (prototype, adr/0008).
 */
@Named
@ViewScoped
public class ContextServicesView extends ResourceListView {

    private static final long serialVersionUID = 1L;

    static final String CHILD_TYPE = "context-service";
    static final String LIST_COMMAND = "list-context-services";
    static final String LIST_KEY = "contextServices";

    @Override
    protected String childType() {
        return CHILD_TYPE;
    }

    @Override
    protected String logicalNamesCommand() {
        return LIST_COMMAND;
    }

    @Override
    protected String logicalNamesKey() {
        return LIST_KEY;
    }
}
