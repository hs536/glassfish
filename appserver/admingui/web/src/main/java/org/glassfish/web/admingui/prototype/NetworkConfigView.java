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

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.List;

/**
 * The page of the network configuration, which links to the pages below it.
 *
 * <p>
 * Prototype (adr/0008, adr/0009): the Facelets version of {@code web/grizzly/networkConfig.jsf}. The page has no
 * settings of its own; the tree of the console shows the same three pages.
 */
@Named
@ViewScoped
public class NetworkConfigView implements Serializable {

    private static final long serialVersionUID = 1L;

    private String configName;
    private List<Page> pages = List.of();

    @PostConstruct
    protected void open() {
        String config = GrizzlyTabs.parameter("configName");
        configName = config == null || config.isEmpty() ? HttpListeners.DEFAULT_CONFIG : config;
        pages = List.of(page("networkListeners.jsf", "tree.grizzly.networkListeners"),
                page("protocols.jsf", "tree.grizzly.protocols"),
                page("transports.jsf", "tree.grizzly.transports"));
    }

    public String getConfigName() {
        return configName;
    }

    /** The pages this one links to, in the order the JSFTemplating page has them. */
    public List<Page> getPages() {
        return pages;
    }

    private Page page(String name, String textKey) {
        return new Page(GrizzlyTabs.contextPath() + "/web/grizzly/" + name + "?configName=" + GrizzlyTabs.encode(configName),
                textKey);
    }

    /** One link: where it goes and the key of its text. */
    public record Page(String url, String textKey) implements Serializable {

        public String getUrl() {
            return url;
        }

        public String getTextKey() {
            return textKey;
        }
    }
}
