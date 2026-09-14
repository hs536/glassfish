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

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import java.util.List;

import org.glassfish.admingui.common.util.ResourceEditView;

/**
 * The edit JDBC resource page (prototype, adr/0008).
 */
@Named
@ViewScoped
public class JdbcResourceEditView extends ResourceEditView {

    private static final long serialVersionUID = 1L;

    private List<String> pools = List.of();

    @Override
    protected String childType() {
        return JdbcPages.CHILD_TYPE;
    }

    @Override
    protected String editPage() {
        return "/jdbc/jdbcResourceEdit.jsf";
    }

    @Override
    protected String logicalNamesCommand() {
        return JdbcPages.LIST_COMMAND;
    }

    @Override
    protected String logicalNamesKey() {
        return JdbcPages.LIST_KEY;
    }

    @PostConstruct
    protected void loadPools() {
        pools = JdbcPages.pools(rest());
    }

    public List<String> getPools() {
        return pools;
    }

    public String getPoolHelp() {
        return JdbcPages.poolHelp();
    }
}
