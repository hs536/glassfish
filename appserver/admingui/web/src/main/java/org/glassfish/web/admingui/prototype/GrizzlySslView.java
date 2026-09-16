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

import org.glassfish.admingui.common.util.SslEditView;

/**
 * The SSL settings of a protocol of the network configuration.
 *
 * <p>
 * Prototype (adr/0006, adr/0008): what the SSL pages of a network listener and of a protocol have in common, which is
 * everything but the way the {@code <ssl>} element is created.
 */
public abstract class GrizzlySslView extends SslEditView {

    private static final long serialVersionUID = 1L;

    private GrizzlyTabs tabs;

    /** Reads the page parameters and then the settings. */
    protected void open(String defaultCancelTo) {
        tabs = GrizzlyTabs.fromRequest(defaultCancelTo);
        load();
    }

    /** The pages of the other tabs and the page to go back to. */
    public GrizzlyTabs getTabs() {
        return tabs;
    }

    @Override
    protected String configName() {
        return tabs.getConfigName();
    }

    @Override
    protected String sslUrl() {
        return rest.child(protocolUrl(), "ssl");
    }

    public String getConfigName() {
        return tabs.getConfigName();
    }

    protected String protocolUrl() {
        return rest.child(HttpListeners.protocolsUrl(rest, tabs.getConfigName()), tabs.getProtocolName());
    }
}
