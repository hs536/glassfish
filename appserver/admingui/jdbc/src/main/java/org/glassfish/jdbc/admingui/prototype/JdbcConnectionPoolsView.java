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

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import org.glassfish.admingui.common.util.PoolListView;
import org.glassfish.admingui.common.util.ResourceRow;

/**
 * The JDBC connection pools list page (prototype, adr/0008). The class name column shows the driver class of a pool
 * of the resource type {@code java.sql.Driver} and the data source class of the others, as
 * {@code gf.addClassNameColumn} does.
 */
@Named
@ViewScoped
public class JdbcConnectionPoolsView extends PoolListView {

    private static final long serialVersionUID = 1L;

    @Override
    protected String childType() {
        return "jdbc-connection-pool";
    }

    @Override
    protected String newPage() {
        return "/jdbc/jdbcConnectionPoolNew1.jsf";
    }

    @Override
    protected void addAttributes(ResourceRow row) {
        String resType = text(row.getAttributes().get("resType"));
        String driverClassName = text(row.getAttributes().get("driverClassname"));
        String datasourceClassName = text(row.getAttributes().get("datasourceClassname"));
        String className = "";
        if (!resType.isEmpty()) {
            className = "java.sql.Driver".equals(resType) ? driverClassName : datasourceClassName;
        } else {
            if (!datasourceClassName.isEmpty()) {
                className = datasourceClassName;
            }
            if (!driverClassName.isEmpty()) {
                className = driverClassName;
            }
        }
        row.getAttributes().put("className", className);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
