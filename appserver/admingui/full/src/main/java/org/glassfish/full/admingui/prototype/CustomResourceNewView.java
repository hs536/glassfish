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

package org.glassfish.full.admingui.prototype;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import org.glassfish.admingui.common.util.ResourceNewView;

/**
 * The new custom resource page (prototype, adr/0008).
 */
@Named
@ViewScoped
public class CustomResourceNewView extends ResourceNewView {

    private static final long serialVersionUID = 1L;

    private final CustomResourceType type = new CustomResourceType();

    @Override
    protected String childType() {
        return CustomResourcePages.CHILD_TYPE;
    }

    @Override
    protected String listPage() {
        return "/full/customResources.jsf";
    }

    // An overriding method is a post construct method only when it is annotated again
    @PostConstruct
    @Override
    protected void loadDefaults() {
        super.loadDefaults();
        type.init(CustomResourcePages.builtInTypes(rest()), null);
    }

    public CustomResourceType getType() {
        return type;
    }

    /** Called when the built-in type or the choice between a built-in and a typed class changes. */
    public void typeChanged() {
        type.fillFactoryClass(getValues());
    }

    @Override
    public void create() {
        getValues().put("resType", type.resourceType());
        super.create();
    }
}
