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

package org.glassfish.admingui.common.util;

import jakarta.inject.Inject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The SSL settings of a listener, a protocol or another endpoint.
 *
 * <p>
 * Prototype (adr/0006, adr/0008): the Facelets counterpart of the fragments {@code shared/sslPrepare.inc},
 * {@code sslAttrs.inc}, {@code sslButtons.inc} and {@code sslValidationsJS.inc}, which the SSL pages of several plugins
 * share. A page of a plugin says where its {@code <ssl>} element is and how it is created; the settings themselves and
 * the four lists of cipher suites are the same everywhere.
 */
public abstract class SslEditView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The settings that are sent as false when they are not chosen. */
    private static final List<String> BOOLEANS = List.of("ssl3Enabled", "tlsEnabled", "tls11Enabled", "tls12Enabled",
            "tls13Enabled", "clientAuthEnabled");
    /** The server does not take this one of an {@code <ssl>} element, as the JSFTemplating page also knows. */
    private static final String NOT_SENT = "sslInactivityTimeout";

    @Inject
    protected AdminRestService rest;

    private final Map<String, Object> values = new HashMap<>();
    private final Flags flags = new Flags(values);
    private boolean edit;
    private AddRemoveList common = new AddRemoveList();
    private AddRemoveList ephemeral = new AddRemoveList();
    private AddRemoveList smallKey = new AddRemoveList();
    private AddRemoveList ecc = new AddRemoveList();
    private List<String> keptCiphers = List.of();

    /** The configuration the endpoint belongs to. */
    protected abstract String configName();

    /** The endpoint of the {@code <ssl>} element. */
    protected abstract String sslUrl();

    /** The endpoint of the command that creates the {@code <ssl>} element. */
    protected abstract String createSslUrl();

    /** What that command needs besides the certificate nickname. */
    protected abstract Map<String, Object> createParameters();

    /** Reads the settings, or the defaults when the endpoint has no SSL yet. */
    protected void load() {
        values.clear();
        Map<String, Object> attributes = rest.attributesOrEmpty(sslUrl());
        edit = !attributes.isEmpty();
        if (edit) {
            values.putAll(attributes);
        } else {
            // The server has no endpoint that tells the defaults of an <ssl> element, so they are the ones of the
            // JSFTemplating page
            values.put("ssl3Enabled", "true");
            values.put("tlsEnabled", "true");
            values.put("trustMaxCertLength", "5");
        }
        List<String> chosen = SslCiphers.parse(values.get("ssl3TlsCiphers"));
        List<String> supported = supportedCiphers();
        common = list(SslCiphers.common(supported), SslCiphers.common(chosen));
        ephemeral = list(SslCiphers.ephemeral(supported), SslCiphers.ephemeral(chosen));
        smallKey = list(SslCiphers.smallKey(supported), SslCiphers.smallKey(chosen));
        ecc = list(SslCiphers.ecc(supported), SslCiphers.ecc(chosen));
        keptCiphers = SslCiphers.ungrouped(chosen);
    }

    /** Saves the settings, creating the {@code <ssl>} element first when the endpoint has none. */
    public void save() {
        if (!isProtocolChosenForCiphers()) {
            ConsoleMessages.error(ConsoleMessages.core("msg.JS.ssl.errCiphersSelected"));
            return;
        }
        try {
            values.put("ssl3TlsCiphers", chosenCiphers());
            if (!edit) {
                // create-ssl does not take all the settings, so the element is created with the nickname alone
                Map<String, Object> parameters = new HashMap<>(createParameters());
                parameters.put("certNickname", values.get("certNickname"));
                rest.create(createSslUrl(), parameters, List.of());
            }
            Map<String, Object> attributes = new HashMap<>(values);
            attributes.remove(NOT_SENT);
            rest.create(sslUrl(), attributes, BOOLEANS);
            load();
            ConsoleMessages.info(ConsoleMessages.core("msg.saveSuccessful"));
        } catch (RuntimeException e) {
            ConsoleMessages.error(e.getMessage());
        }
    }

    /** True when the endpoint already has SSL settings, which the page saves instead of creating them. */
    public boolean isEdit() {
        return edit;
    }

    /** The settings the page shows as fields. */
    public Map<String, Object> getValues() {
        return values;
    }

    /** The settings that are shown as checkboxes. */
    public Flags getFlags() {
        return flags;
    }

    public AddRemoveList getCommonCiphers() {
        return common;
    }

    public AddRemoveList getEphemeralCiphers() {
        return ephemeral;
    }

    public AddRemoveList getSmallKeyCiphers() {
        return smallKey;
    }

    public AddRemoveList getEccCiphers() {
        return ecc;
    }

    /** The value of the cipher suites, in the order of the four lists of the page. */
    private String chosenCiphers() {
        List<String> chosen = new ArrayList<>(common.getSelected());
        chosen.addAll(ephemeral.getSelected());
        chosen.addAll(smallKey.getSelected());
        chosen.addAll(ecc.getSelected());
        // A suite that belongs to none of the four lists is kept, so that saving the page does not drop it (B-42)
        chosen.addAll(keptCiphers);
        return SslCiphers.format(chosen);
    }

    /**
     * Whether a version of the protocol is chosen when cipher suites are, which the server needs to use them. The
     * JSFTemplating page asks for SSL3 or TLS 1.0 only (issue B-43).
     */
    private boolean isProtocolChosenForCiphers() {
        if (common.getSelected().isEmpty() && ephemeral.getSelected().isEmpty() && smallKey.getSelected().isEmpty()
                && ecc.getSelected().isEmpty()) {
            return true;
        }
        return chosen("ssl3Enabled") || chosen("tlsEnabled") || chosen("tls11Enabled") || chosen("tls12Enabled")
                || chosen("tls13Enabled");
    }

    /** The cipher suites the Java runtime of the server supports. */
    private List<String> supportedCiphers() {
        Map<String, Object> response = rest.get(rest.url("configs", "config", configName(), "security-service",
                "list-supported-cipher-suites"), Map.of());
        List<String> names = new ArrayList<>();
        if (response.get("data") instanceof Map<?, ?> data && data.get("children") instanceof List<?> children) {
            for (Object child : children) {
                if (child instanceof Map<?, ?> map && map.get("message") != null) {
                    names.add(map.get("message").toString());
                }
            }
        }
        return names;
    }

    /** One of the lists of the page: what the runtime supports, with what is chosen moved over. */
    private static AddRemoveList list(List<String> supported, List<String> chosen) {
        LinkedHashSet<String> items = new LinkedHashSet<>(supported);
        // A suite that is chosen although the runtime no longer supports it stays on the page
        items.addAll(chosen);
        AddRemoveList list = new AddRemoveList(items);
        list.select(chosen);
        return list;
    }

    private boolean chosen(String key) {
        return flags.get(key);
    }
}
