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

package org.glassfish.admingui.cdi;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PluginBeanExtensionTest {

    @TempDir
    Path plugin;

    @Test
    public void commentsAndBlankLinesAreIgnored() throws IOException {
        URL list = writeBeanList("# a comment\n\n  org.example.First  \n\t\norg.example.Second\n");

        assertEquals(List.of("org.example.First", "org.example.Second"), PluginBeanExtension.readBeanList(list));
    }

    @Test
    public void beanListsOfAClassLoaderAreRead() throws IOException {
        writeBeanList("org.example.Bean\n");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {plugin.toUri().toURL()}, null)) {
            assertEquals(List.of("org.example.Bean"), PluginBeanExtension.readBeanLists(loader, new HashSet<>()));
        }
    }

    @Test
    public void aBeanListIsReadOnlyOnce() throws IOException {
        writeBeanList("org.example.Bean\n");
        Set<URL> readLists = new HashSet<>();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {plugin.toUri().toURL()}, null)) {
            PluginBeanExtension.readBeanLists(loader, readLists);

            assertEquals(List.of(), PluginBeanExtension.readBeanLists(loader, readLists));
        }
    }

    private URL writeBeanList(String content) throws IOException {
        Path list = plugin.resolve(PluginBeanExtension.BEAN_LIST);
        Files.createDirectories(list.getParent());
        Files.writeString(list, content, StandardCharsets.UTF_8);
        return list.toUri().toURL();
    }
}
