/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
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

/*
 * PrincipalGroupFactory.java
 *
 * Created on October 28, 2004, 12:34 PM
 */

package com.sun.enterprise.security.web.integration;

import java.lang.ref.WeakReference;

import org.glassfish.internal.api.Globals;
import org.glassfish.security.common.Group;
import org.glassfish.security.common.PrincipalImpl;
import org.jvnet.hk2.annotations.Service;

import com.sun.enterprise.security.PrincipalGroupFactory;

/**
 *
 * @author Harpreet Singh
 */
@Service
public class PrincipalGroupFactoryImpl implements PrincipalGroupFactory {

    /** Creates a new instance of PrincipalGroupFactory */

    private static WeakReference<WebSecurityManagerFactory> webSecurityManagerFactory = new WeakReference<>(null);

    private static synchronized WebSecurityManagerFactory _getWebSecurityManagerFactory() {
        if (webSecurityManagerFactory.get() == null) {
            webSecurityManagerFactory = new WeakReference<>(Globals.get(WebSecurityManagerFactory.class));
        }
        return webSecurityManagerFactory.get();
    }

    private static WebSecurityManagerFactory getWebSecurityManagerFactory() {
        if (webSecurityManagerFactory.get() != null) {
            return webSecurityManagerFactory.get();
        }
        return _getWebSecurityManagerFactory();
    }

    @Override
    public PrincipalImpl getPrincipalInstance(String name, String realm) {
        WebSecurityManagerFactory fact = getWebSecurityManagerFactory();
        PrincipalImpl p = (PrincipalImpl) fact.getAdminPrincipal(name, realm);
        if (p == null) {
            p = new PrincipalImpl(name);
        }
        return p;
    }

    @Override
    public Group getGroupInstance(String name, String realm) {
        WebSecurityManagerFactory fact = getWebSecurityManagerFactory();
        Group g = (Group) fact.getAdminGroup(name, realm);
        if (g == null) {
            g = new Group(name);
        }
        return g;
    }
}
