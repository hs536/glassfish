/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.examples.jersey_cdi.resources;

import javax.annotation.ManagedBean;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * Shows constructor injection of a path parameter in a managed bean.
 *
 * @author robc
 */
@ManagedBean
@RequestScoped
@Path("echoconstructor/{a}")
public class EchoParamConstructorResource {

    public EchoParamConstructorResource() {}

    @Inject
    public EchoParamConstructorResource(@PathParam("a") String a) {
        this.a = a;
    }

    @GET
    @Produces("text/plain")
    public String get() {
        return "ECHO " + a;
    }

    String a;
}
