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

import jakarta.inject.Inject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;
import org.glassfish.admingui.common.util.Settings;

/**
 * The file cache settings of a protocol, shown as the File Cache tab.
 *
 * <p>
 * Prototype (adr/0008): the Facelets counterpart of {@code web/grizzly/fileCache.layout} and
 * {@code fileCacheAttrs.inc}, which the pages of a protocol and of a network listener share. A protocol without file
 * cache settings only shows them, as on the JSFTemplating page.
 */
public abstract class FileCacheEditView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The setting that is sent as false when it is not chosen. */
    static final List<String> BOOLEANS = List.of("enabled");

    @Inject
    protected AdminRestService rest;

    private GrizzlyTabs tabs;
    private final Settings fileCache = new Settings();
    private boolean found;

    /** Reads the page parameters and then the settings. */
    protected void open(String defaultCancelTo) {
        tabs = GrizzlyTabs.fromRequest(defaultCancelTo);
        load();
    }

    private void load() {
        Map<String, Object> attributes = rest.attributesOrEmpty(fileCacheUrl());
        found = !attributes.isEmpty();
        fileCache.replace(found ? attributes : rest.defaultsOrEmpty(fileCacheUrl()));
    }

    public void save() {
        try {
            rest.create(fileCacheUrl(), new HashMap<>(fileCache.getValues()), BOOLEANS);
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** Puts the values the server would use for a new file cache into the fields, without saving them. */
    public void loadDefaults() {
        fileCache.getValues().putAll(rest.defaultsOrEmpty(fileCacheUrl()));
    }

    /** The pages of the other tabs and the page to go back to. */
    public GrizzlyTabs getTabs() {
        return tabs;
    }

    public String getConfigName() {
        return tabs.getConfigName();
    }

    public String getProtocolName() {
        return tabs.getProtocolName();
    }

    /** True when the protocol has file cache settings, which are the only ones the page saves. */
    public boolean isFound() {
        return found;
    }

    /** The settings the page shows. */
    public Settings getSettings() {
        return fileCache;
    }

    private String fileCacheUrl() {
        return rest.child(HttpListeners.protocolsUrl(rest, tabs.getConfigName()), tabs.getProtocolName(), "http", "file-cache");
    }
}
