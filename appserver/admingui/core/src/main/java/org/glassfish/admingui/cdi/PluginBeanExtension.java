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

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.api.admingui.ConsoleProvider;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.internal.api.Globals;

/**
 * Registers CDI beans that live in console plugin bundles.
 *
 * <p>
 * Plugin bundles are not part of the console web application, so the CDI container does not scan them. A plugin lists
 * its bean classes, one fully qualified class name per line, in {@value #BEAN_LIST}; this extension loads each class
 * with the plugin's class loader and adds it to the console's bean discovery.
 */
public class PluginBeanExtension implements Extension {

    static final String BEAN_LIST = "META-INF/admingui/beans";

    private static final Logger LOG = Logger.getLogger(PluginBeanExtension.class.getName());

    void addPluginBeans(@Observes BeforeBeanDiscovery event) {
        ServiceLocator locator = Globals.getDefaultHabitat();
        if (locator == null) {
            return;
        }
        Set<URL> readLists = new HashSet<>();
        for (ConsoleProvider provider : locator.getAllServices(ConsoleProvider.class)) {
            ClassLoader loader = provider.getClass().getClassLoader();
            for (String className : readBeanLists(loader, readLists)) {
                try {
                    event.addAnnotatedType(loader.loadClass(className), PluginBeanExtension.class.getName() + ":" + className);
                } catch (ClassNotFoundException | LinkageError e) {
                    LOG.log(Level.WARNING, "Cannot load console plugin bean " + className, e);
                }
            }
        }
    }

    static List<String> readBeanLists(ClassLoader loader, Set<URL> readLists) {
        List<String> classNames = new ArrayList<>();
        try {
            for (URL list : Collections.list(loader.getResources(BEAN_LIST))) {
                if (readLists.add(list)) {
                    classNames.addAll(readBeanList(list));
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read " + BEAN_LIST, e);
        }
        return classNames;
    }

    static List<String> readBeanList(URL list) throws IOException {
        List<String> classNames = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(list.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    classNames.add(line);
                }
            }
        }
        return classNames;
    }
}
