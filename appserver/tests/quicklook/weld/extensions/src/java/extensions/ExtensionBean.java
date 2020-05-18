/*
 * Copyright (c) 2009, 2018 Oracle and/or its affiliates. All rights reserved.
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

package extensions;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.enterprise.inject.spi.ProcessManagedBean;

public class ExtensionBean implements Extension {

    public static List<ProcessInjectionTarget> l = new ArrayList<ProcessInjectionTarget>();

    public static boolean bbd = false;
    public static boolean abd = false;
    public static boolean adv = false;
    public static boolean pat = false;
    public static boolean pit = false;
    public static boolean pmb = false;

    void beforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        bbd = true;
    }

    void afterBeanDiscovery(@Observes AfterBeanDiscovery event, BeanManager manager) {
        abd = true;
    }

    void afterDeploymentValidation(@Observes AfterDeploymentValidation event, BeanManager manager) {
        adv = true;
    }

    void processAnnotatedType(@Observes ProcessAnnotatedType<?> type) {
        pat = true;
    }

    void processInjectionTarget(@Observes ProcessInjectionTarget<?> _pit) {
        pit = true;
        l.add(_pit);
    }

    void processBean(@Observes ProcessManagedBean<?> pb) {
        pmb = true;
    }
}
