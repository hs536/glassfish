/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.acme.ejb32.timer.opallowed;

import javax.ejb.Timer;
import javax.ejb.TimerHandle;

public class TimeoutHelper {
    // this is to test the APIs allowed to be invoked
    // the return values are not cared
    public static void cancelTimer(TimerHandle th)  {
        if(th == null) {
            return;
        }
        Timer t = th.getTimer();
        t.getHandle();
        t.getInfo();
        t.getNextTimeout();
        t.getSchedule();
        t.getTimeRemaining();
        t.isCalendarTimer();
        t.isPersistent();
        t.cancel();
    }
}
