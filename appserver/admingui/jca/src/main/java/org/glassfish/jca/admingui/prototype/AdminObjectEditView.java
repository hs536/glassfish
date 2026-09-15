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

package org.glassfish.jca.admingui.prototype;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import org.glassfish.admingui.common.util.ResourceEditView;

/**
 * The edit admin object resource page (prototype, adr/0008). The additional properties are those of the resource;
 * the configuration properties of the adapter and type only tell which of them are confidential.
 */
@Named
@ViewScoped
public class AdminObjectEditView extends ResourceEditView {

    private static final long serialVersionUID = 1L;

    private final AdminObjectChoices choices = new AdminObjectChoices();

    @Override
    protected String childType() {
        return AdminObjectChoices.CHILD_TYPE;
    }

    @Override
    protected String editPage() {
        return "/jca/adminObjectEdit.jsf";
    }

    // An overriding method is a post construct method only when it is annotated again
    @PostConstruct
    @Override
    protected void load() {
        super.load();
        if (isFound()) {
            choices.loadAdapters(rest(), getValues());
            typeUpdated();
        }
    }

    public AdminObjectChoices getChoices() {
        return choices;
    }

    /** Called when the resource adapter changes: the first resource type and class name of the adapter are chosen. */
    public void adapterChanged() {
        getValues().remove("resType");
        typeChanged();
    }

    /** Called when the resource type changes: the first class name of the type is chosen. */
    public void typeChanged() {
        getValues().remove("className");
        typeUpdated();
    }

    private void typeUpdated() {
        choices.update(rest(), getValues());
        getProperties().markConfidential(choices.configProperties(rest(), getValues()).confidential());
    }
}
