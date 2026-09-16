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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SslCiphersTest {

    @Test
    public void onlySuitesWithAPlusSignAreChosen() {
        assertEquals(List.of("TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_DHE_RSA_WITH_AES_128_CBC_SHA"),
                SslCiphers.parse("+TLS_RSA_WITH_AES_128_CBC_SHA,-SSL_RSA_WITH_RC4_128_MD5,+TLS_DHE_RSA_WITH_AES_128_CBC_SHA"));
        assertEquals(List.of(), SslCiphers.parse(null));
        assertEquals(List.of(), SslCiphers.parse(""));
    }

    @Test
    public void chosenSuitesAreWrittenWithAPlusSign() {
        assertEquals("+TLS_RSA_WITH_AES_128_CBC_SHA,+TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                SslCiphers.format(List.of("TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")));
        assertEquals("", SslCiphers.format(List.of()));
    }

    @Test
    public void suitesAreSplitIntoTheFourListsOfThePage() {
        List<String> names = List.of("TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                "TLS_DHE_RSA_WITH_AES_128_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA");
        assertEquals(List.of("TLS_RSA_WITH_AES_128_CBC_SHA"), SslCiphers.common(names));
        assertEquals(List.of("TLS_DHE_RSA_WITH_AES_128_CBC_SHA", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA"), SslCiphers.ephemeral(names));
        assertEquals(List.of("SSL_DHE_RSA_WITH_DES_CBC_SHA"), SslCiphers.smallKey(names));
        assertEquals(List.of("TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"), SslCiphers.ecc(names));
        assertEquals(List.of(), SslCiphers.ungrouped(names));
    }

    @Test
    public void suitesOfTls13BelongToNoList() {
        List<String> names = List.of("TLS_AES_128_GCM_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA");
        assertEquals(List.of("TLS_AES_128_GCM_SHA256"), SslCiphers.ungrouped(names));
    }
}
