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

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import java.util.List;

import org.glassfish.admingui.common.util.ResourceNewView;

/**
 * The new Jakarta Mail session page (prototype, adr/0008).
 */
@Named
@ViewScoped
public class MailResourceNewView extends ResourceNewView {

    private static final long serialVersionUID = 1L;

    @Override
    protected String childType() {
        return MailPages.CHILD_TYPE;
    }

    @Override
    protected String listPage() {
        return "/jmail/jakartaMailResources.jsf";
    }

    @Override
    protected List<String> convertToFalse() {
        return MailPages.CONVERT_TO_FALSE;
    }

    public boolean isDebug() {
        return MailPages.isDebug(getValues());
    }

    public void setDebug(boolean debug) {
        MailPages.setDebug(getValues(), debug);
    }

    public void sendTestEmail() {
        MailPages.sendTestEmail(getValues(), getProperties());
    }
}
