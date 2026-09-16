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

import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.admingui.common.util.AdminRestService;
import org.glassfish.admingui.common.util.ConsoleMessages;

/**
 * An existing transport.
 *
 * <p>
 * Prototype (adr/0006, adr/0008, adr/0009): the Facelets version of {@code web/grizzly/transportEdit.jsf}.
 */
@Named
@ViewScoped
public class TransportEditView implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final List<String> FLAGS = List.of("displayConfiguration", "enableSnoop", "tcpNoDelay");

    @Inject
    private AdminRestService rest;

    private String configName;
    private String name;
    private boolean found;
    private final Map<String, Object> values = new HashMap<>();

    @PostConstruct
    protected void load() {
        if (name == null) {
            Map<String, String> parameters = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
            String config = parameters.get("configName");
            configName = config == null || config.isEmpty() ? Transports.DEFAULT_CONFIG : config;
            name = parameters.get("name");
        }
        values.clear();
        found = name != null && !name.isEmpty();
        if (!found) {
            return;
        }
        Map<String, Object> attributes = rest.attributes(selfUrl());
        found = !attributes.isEmpty();
        if (found) {
            values.putAll(attributes);
        }
    }

    public void save() {
        try {
            rest.create(selfUrl(), new HashMap<>(values), FLAGS);
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    public String getConfigName() {
        return configName;
    }

    public String getName() {
        return name;
    }

    public boolean isFound() {
        return found;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public List<String> getByteBufferTypes() {
        return Transports.BYTE_BUFFER_TYPES;
    }

    /** The check boxes of the attributes, which are sent as the text {@code true} or {@code false}. */
    public boolean isDisplayConfiguration() {
        return flag("displayConfiguration");
    }

    public void setDisplayConfiguration(boolean value) {
        values.put("displayConfiguration", String.valueOf(value));
    }

    public boolean isEnableSnoop() {
        return flag("enableSnoop");
    }

    public void setEnableSnoop(boolean value) {
        values.put("enableSnoop", String.valueOf(value));
    }

    public boolean isTcpNoDelay() {
        return flag("tcpNoDelay");
    }

    public void setTcpNoDelay(boolean value) {
        values.put("tcpNoDelay", String.valueOf(value));
    }

    public String getListPage() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                + "/web/grizzly/transports.jsf?configName=" + URLEncoder.encode(configName, StandardCharsets.UTF_8);
    }

    private boolean flag(String key) {
        return Boolean.parseBoolean(Transports.text(values.get(key)));
    }

    private String selfUrl() {
        return rest.child(Transports.url(rest, configName), name);
    }
}
