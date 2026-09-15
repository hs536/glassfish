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
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.validator.ValidatorException;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;

import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * The first page of the new connector connection pool wizard (prototype, adr/0008): the pool name, the resource
 * adapter and the connection definition. The Facelets counterpart of {@code jca/connectorConnectionPoolNew1.jsf}.
 */
@Named
@ViewScoped
public class ConnectorConnectionPoolNew1View implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private ConnectorConnectionPoolWizard wizard;

    /** Back from the second page ({@code fromStep2=true}), the wizard keeps its values; any other opening starts a new wizard. */
    @PostConstruct
    protected void open() {
        String fromStep2 = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("fromStep2");
        if (!"true".equals(fromStep2)) {
            wizard.begin();
        }
    }

    public ConnectorConnectionPoolWizard getWizard() {
        return wizard;
    }

    /** Choosing a resource adapter lists its connection definitions. */
    public void adapterChanged() {
        try {
            wizard.listDefinitions();
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** Reads the configuration properties for the choices and opens the second page. */
    public void next() {
        try {
            wizard.listProperties();
            loadPage("/jca/connectorConnectionPoolNew2.jsf");
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    public void cancel() {
        wizard.end();
        loadPage("/jca/connectorConnectionPools.jsf");
    }

    /** A pool name must not contain a backslash, as {@code checkForBackslash} checks on the JSFTemplating page. */
    public void validateName(FacesContext context, UIComponent component, Object value) {
        if (value != null && value.toString().contains("\\")) {
            String message = ConsoleMessages.core("msg.JS.resources.resName") + " " + JcaStrings.get("poolName");
            throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR, message, null));
        }
    }

    private static void loadPage(String page) {
        FacesContext context = FacesContext.getCurrentInstance();
        String url = context.getExternalContext().getRequestContextPath() + page;
        context.getPartialViewContext().getEvalScripts().add("admingui.ajax.loadPage({url: '" + url + "'});");
    }
}
