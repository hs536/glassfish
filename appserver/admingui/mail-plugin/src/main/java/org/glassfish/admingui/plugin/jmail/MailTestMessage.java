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

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Sends a test message with the settings of a mail session that is being edited, to the sender address itself.
 *
 * <p>
 * Shared by the {@code sendTestEmail} handler of the JSFTemplating pages and the Facelets pages (prototype, adr/0008).
 */
public final class MailTestMessage {

    private MailTestMessage() {
    }

    /**
     * Sends the message.
     *
     * @param settings the values of the page by attribute name: {@code host}, {@code user}, {@code from}, and
     *            optionally {@code debug}, {@code storeProtocol}, {@code storeProtocolClass}, {@code transportProtocol},
     *            {@code transportProtocolClass}
     * @param properties the additional properties, as maps with {@code name} and {@code value}
     */
    public static void send(Map<String, ?> settings, List<Map<String, String>> properties) throws MessagingException {
        Properties mailProperties = mailProperties(settings, properties);
        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                String protocol = getRequestingProtocol();
                if (protocol != null) {
                    String password = mailProperties.getProperty("mail." + protocol + ".password");
                    String username = mailProperties.getProperty("mail." + protocol + ".user", mailProperties.getProperty("mail.user"));
                    if (password != null && username != null) {
                        return new PasswordAuthentication(username, password);
                    }
                }
                return null;
            }
        };
        InternetAddress from = InternetAddress.parse(text(settings.get("from")), false)[0];
        Message message = new MimeMessage(Session.getInstance(mailProperties, authenticator));
        message.setSubject("test message subject");
        message.setSentDate(new Date());
        message.setFrom(from);
        message.setRecipient(Message.RecipientType.TO, from);
        message.setText("test message body");
        Transport.send(message);
    }

    static Properties mailProperties(Map<String, ?> settings, List<Map<String, String>> properties) {
        Properties mailProperties = new Properties();
        mailProperties.put("mail.host", text(settings.get("host")));
        mailProperties.put("mail.user", text(settings.get("user")));
        mailProperties.put("mail.from", text(settings.get("from")));
        putIfPresent(mailProperties, "mail.debug", settings.get("debug"));
        putIfPresent(mailProperties, "mail.store.protocol", settings.get("storeProtocol"));
        putIfPresent(mailProperties, "mail.transport.protocol", settings.get("transportProtocol"));
        if (mailProperties.containsKey("mail.store.protocol")) {
            putIfPresent(mailProperties, "mail." + mailProperties.getProperty("mail.store.protocol") + ".class", settings.get("storeProtocolClass"));
        }
        if (mailProperties.containsKey("mail.transport.protocol")) {
            putIfPresent(mailProperties, "mail." + mailProperties.getProperty("mail.transport.protocol") + ".class",
                    settings.get("transportProtocolClass"));
        }
        if (properties != null) {
            for (Map<String, String> property : properties) {
                if (property.get("name") != null && property.get("value") != null) {
                    mailProperties.put(property.get("name"), property.get("value"));
                }
            }
        }
        return mailProperties;
    }

    private static void putIfPresent(Properties mailProperties, String key, Object value) {
        if (value != null) {
            mailProperties.put(key, value.toString());
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
