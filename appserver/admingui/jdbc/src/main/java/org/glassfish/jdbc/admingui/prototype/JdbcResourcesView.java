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

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.List;

import org.glassfish.admingui.common.util.RestUtil;

/**
 * Prototype only (docs/試作計画.md P-3): the names of the JDBC resources, read from the admin REST interface, for the
 * Facelets list page. The REST access is replaced by the service designed for P-4.
 */
@Named
@RequestScoped
public class JdbcResourcesView {

    private List<String> names;

    public List<String> getNames() throws Exception {
        if (names == null) {
            String restUrl = (String) FacesContext.getCurrentInstance().getExternalContext().getSessionMap().get("REST_URL");
            names = new ArrayList<>(RestUtil.getChildMap(restUrl + "/resources/jdbc-resource").keySet());
        }
        return names;
    }
}
