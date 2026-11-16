// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

/**
 * 775（26.1）のプロキシ受け入れ（新ビッグコンストラクタの検証と
 * pack775 の保持）。pack775 は新ビッグコンストラクタ（pack759-first）にしか
 * 無いため、ここではそのコンストラクタを直接使う。
 * 新ビッグコンストラクタは26引数：ポート類4＋pack759〜764（6）＋authConfig＋
 * pack765〜775（11）＋pack757＋pack756＋pack755＋internalProtocol。
 */
class ClientProxy775Test {

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static ResourcePack pack775() {
        return new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-26.1.zip",
                "ffffffffffffffffffffffffffffffffffffffff", false, "");
    }

    @Test
    void internalProtocol775IsAccepted() throws Exception {
        ResourcePack pack = pack775();
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, pack, null, null, null, 775);
        assertEquals(775, proxy.internalProtocol());
        assertEquals(pack, proxy.pack775());
    }

    @Test
    void internalProtocol775WithNullPack775IsAccepted() throws Exception {
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, 775);
        assertEquals(775, proxy.internalProtocol());
        assertNull(proxy.pack775());
    }

    @Test
    void pack775IsKeptForNon775InternalProtocols() throws Exception {
        ResourcePack pack = pack775();
        ClientProxy proxy774 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, pack, null, null, null, 774);
        assertEquals(774, proxy774.internalProtocol());
        assertEquals(pack, proxy774.pack775());
    }

    @Test
    void internalProtocol776IsStillRejected() throws Exception {
        try {
            new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                    null, null, null, null, null, null,
                    ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, 776);
            throw new AssertionError("expected IllegalArgumentException for 776");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("775"), expected.getMessage());
        }
        // 中間コンストラクタ経由でも 776 は拒否される。
        assertThrows(IllegalArgumentException.class, () -> new ClientProxy(
                freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, 776));
    }
}
