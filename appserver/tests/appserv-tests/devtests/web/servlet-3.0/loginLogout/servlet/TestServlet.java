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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/myurl")
public class TestServlet extends HttpServlet {
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {

        StringBuilder b = new StringBuilder("g:Hello, ");
        b.append((req.getRemoteUser() == null) + ", ");
        b.append(req.isUserInRole("javaee") + ", ");

        req.login("javaee", "javaee");
        b.append(req.getRemoteUser() + ", ");
        b.append(req.isUserInRole("javaee") + ", ");
        req.logout();

        b.append((req.getRemoteUser() == null) + ", ");
        b.append(req.isUserInRole("javaee") + "\n");
        PrintWriter writer = res.getWriter();
        writer.write(b.toString());
    }
}
