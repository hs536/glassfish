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
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;
import org.glassfish.admingui.common.util.PropertyRows;

/**
 * The Additional Properties tab of a JDBC connection pool (prototype, adr/0008). The General and Advanced tabs are
 * other pages. Pools of an application are not covered (X-27).
 *
 * <p>
 * The Facelets counterpart of {@code jdbc/jdbcConnectionPoolProperty.jsf} with {@code resourceNode/poolNameSection.inc}
 * and {@code shared/propertyDescTable.inc}.
 */
@Named
@ViewScoped
public class JdbcConnectionPoolPropertyView implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private AdminRestService rest;

    private String name;
    private boolean found;
    private PropertyRows properties = new PropertyRows();

    @PostConstruct
    protected void load() {
        if (name == null) {
            name = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("name");
        }
        found = name != null && !name.isEmpty() && !rest.attributes(poolUrl()).isEmpty();
        properties = found ? PropertyRows.read(rest, poolUrl()) : new PropertyRows();
    }

    /** Saves the properties. Rows without a name or a value are left out, as on the JSFTemplating page. */
    public void save() {
        try {
            rest.postJson(poolUrl() + "/property.json", properties.toSend());
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    public String getName() {
        return name;
    }

    public boolean isFound() {
        return found;
    }

    public PropertyRows getProperties() {
        return properties;
    }

    public String getGeneralPage() {
        return tabPage("/jdbc/jdbcConnectionPoolEdit.jsf");
    }

    public String getAdvancedPage() {
        return tabPage("/jdbc/jdbcConnectionPoolAdvance.jsf");
    }

    private String tabPage(String page) {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + page + "?name="
                + URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    private String poolUrl() {
        return rest.url("resources", JdbcConnectionPoolEditView.CHILD_TYPE, name);
    }
}
