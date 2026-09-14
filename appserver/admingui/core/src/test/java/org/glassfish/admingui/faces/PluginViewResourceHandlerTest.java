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

package org.glassfish.admingui.faces;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluginViewResourceHandlerTest {

    @Test
    public void pluginViewsAreServable() {
        assertTrue(PluginViewResourceHandler.isServableViewName("/jdbc/jdbcResources.xhtml"));
        assertTrue(PluginViewResourceHandler.isServableViewName("/common/shared/targetSectionForCreate.xhtml"));
    }

    @Test
    public void onlyFaceletsFilesAreServable() {
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc/jdbcResources.jsf"));
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc/org/glassfish/jdbc/admingui/Strings.properties"));
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc/org/glassfish/jdbc/admingui/JdbcConsolePlugin.class"));
    }

    @Test
    public void aPluginIdAndAPathAreRequired() {
        assertFalse(PluginViewResourceHandler.isServableViewName(null));
        assertFalse(PluginViewResourceHandler.isServableViewName("jdbc/jdbcResources.xhtml"));
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbcResources.xhtml"));
    }

    @Test
    public void metaInfAndWebInfAreNotServable() {
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc/META-INF/page.xhtml"));
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc/meta-inf/page.xhtml"));
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc/WEB-INF/page.xhtml"));
    }

    @Test
    public void unusualSegmentsAreNotServable() {
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc/../common/page.xhtml"));
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc/./page.xhtml"));
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc/.hidden.xhtml"));
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc//page.xhtml"));
    }

    @Test
    public void pathChangingCharactersAreNotServable() {
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc/..%2Fcommon/page.xhtml"));
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc\\page.xhtml"));
        assertFalse(PluginViewResourceHandler.isServableViewName("/jdbc/file:page.xhtml"));
    }

    @Test
    public void rejectedNamesAreNotLookedUp() {
        // Rejected before the plugin class loader is looked up, so no Faces context is needed
        assertNull(PluginViewResourceHandler.findInPlugin("/jdbc/META-INF/page.xhtml"));
    }
}
