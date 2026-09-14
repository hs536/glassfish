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

package org.glassfish.admingui.plugin.jmail;

import com.sun.jsftemplating.annotation.Handler;
import com.sun.jsftemplating.annotation.HandlerInput;
import com.sun.jsftemplating.layout.descriptors.handler.HandlerContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.GuiUtil;

/**
 * @author Vladimir Bychkov
 * */
public class MailHandlers {

    @Handler(id = "sendTestEmail",
    input = {
        @HandlerInput(name = "host", type = String.class, required = true),
        @HandlerInput(name = "defaultUser", type = String.class, required = true),
        @HandlerInput(name = "from", type = String.class, required = true),
        @HandlerInput(name = "storeProtocol", type = String.class, required = false),
        @HandlerInput(name = "storeProtocolClass", type = String.class, required = false),
        @HandlerInput(name = "transportProtocol", type = String.class, required = false),
        @HandlerInput(name = "transportProtocolClass", type = String.class, required = false),
        @HandlerInput(name = "debug", type = String.class, required = false),
        @HandlerInput(name = "properties", type = List.class, required = false)})
    public static void sendTestEmail(HandlerContext handlerCtx) {
        try {
            Map<String, Object> settings = new HashMap<>();
            settings.put("host", handlerCtx.getInputValue("host"));
            settings.put("user", handlerCtx.getInputValue("defaultUser"));
            settings.put("from", handlerCtx.getInputValue("from"));
            for (String name : List.of("debug", "storeProtocol", "storeProtocolClass", "transportProtocol", "transportProtocolClass")) {
                Object value = handlerCtx.getInputValue(name);
                if (value != null) {
                    settings.put(name, value);
                }
            }
            @SuppressWarnings("unchecked")
            List<Map<String, String>> properties = (List<Map<String, String>>) handlerCtx.getInputValue("properties");
            MailTestMessage.send(settings, properties);

            GuiUtil.prepareAlert("success", GuiUtil.getMessage("org.glassfish.jmail.admingui.Strings", "msg.SendSucceed"), null);
        } catch (Exception ex) {
            GuiUtil.prepareAlert("error", GuiUtil.getMessage("msg.Error"), ex.getMessage());
        }
    }
}
