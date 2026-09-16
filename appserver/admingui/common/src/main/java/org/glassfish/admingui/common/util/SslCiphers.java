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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The cipher suites of an {@code <ssl>} element, as the SSL pages show them.
 *
 * <p>
 * The server keeps them in one attribute, {@code ssl3TlsCiphers}, as a comma separated list where a chosen suite is
 * written with a leading plus sign. The pages show them in four lists, and a suite that belongs to none of the four is
 * kept as it is (issue B-42).
 *
 * <p>
 * Prototype (adr/0006): the counterpart of the handlers {@code convertToDifferentCiphersGroup} and
 * {@code convertCiphersItemsToStr} of {@code NewSSLHandlers}.
 */
public final class SslCiphers {

    /** The suites of the first list. */
    public static final List<String> COMMON = List.of("SSL_RSA_WITH_RC4_128_MD5", "SSL_RSA_WITH_RC4_128_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_256_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA");

    /** The suites of the 40 bit and 56 bit list. */
    public static final List<String> SMALL_KEY = List.of("SSL_RSA_WITH_DES_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA",
            "SSL_DHE_DSS_WITH_DES_CBC_SHA", "SSL_RSA_EXPORT_WITH_RC4_40_MD5", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");

    private SslCiphers() {
    }

    /** The suites chosen in the value of {@code ssl3TlsCiphers}, which are those written with a plus sign. */
    public static List<String> parse(Object ciphers) {
        List<String> names = new ArrayList<>();
        if (ciphers != null) {
            for (String entry : ciphers.toString().split(",")) {
                if (entry.startsWith("+")) {
                    names.add(entry.substring(1));
                }
            }
        }
        return names;
    }

    /** The value of {@code ssl3TlsCiphers} for the chosen suites. */
    public static String format(Collection<String> names) {
        StringBuilder ciphers = new StringBuilder();
        for (String name : names) {
            if (ciphers.length() > 0) {
                ciphers.append(',');
            }
            ciphers.append('+').append(name);
        }
        return ciphers.toString();
    }

    /** The suites of the common list. */
    public static List<String> common(Collection<String> names) {
        return filter(names, COMMON);
    }

    /** The suites of the ephemeral Diffie-Hellman list, the Diffie-Hellman ones that are not of a small key. */
    public static List<String> ephemeral(Collection<String> names) {
        List<String> ephemeral = containing(names, "_DHE_RSA_");
        ephemeral.addAll(containing(names, "_DHE_DSS_"));
        return ephemeral;
    }

    /** The suites of the 40 bit and 56 bit list. */
    public static List<String> smallKey(Collection<String> names) {
        return filter(names, SMALL_KEY);
    }

    /** The suites of the elliptic curve list. */
    public static List<String> ecc(Collection<String> names) {
        List<String> ecc = containing(names, "_ECDH_");
        ecc.addAll(containing(names, "_ECDHE_"));
        return ecc;
    }

    /** The suites that belong to none of the four lists, such as the ones of TLS 1.3 (issue B-42). */
    public static List<String> ungrouped(Collection<String> names) {
        List<String> rest = new ArrayList<>(names);
        rest.removeAll(common(names));
        rest.removeAll(ephemeral(names));
        rest.removeAll(smallKey(names));
        rest.removeAll(ecc(names));
        return rest;
    }

    private static List<String> filter(Collection<String> names, List<String> wanted) {
        List<String> found = new ArrayList<>();
        for (String name : names) {
            if (wanted.contains(name)) {
                found.add(name);
            }
        }
        return found;
    }

    private static List<String> containing(Collection<String> names, String part) {
        List<String> found = new ArrayList<>();
        for (String name : names) {
            if (name.contains(part) && !SMALL_KEY.contains(name)) {
                found.add(name);
            }
        }
        return found;
    }
}
