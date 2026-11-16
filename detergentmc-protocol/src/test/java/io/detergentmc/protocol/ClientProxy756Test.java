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
 * 756（1.17.1）のプロキシ受け入れ（新ビッグコンストラクタの検証と
 * pack756 の保持）。pack756 は新ビッグコンストラクタ（pack759-first）にしか
 * 無いため、ここではそのコンストラクタを直接使う。
 * 新ビッグコンストラクタは27引数：ポート類4＋pack759〜764（6）＋authConfig＋
 * pack765〜775（11）＋pack757＋pack756＋pack755＋pack754＋internalProtocol。
 */
class ClientProxy756Test {

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static ResourcePack pack756() {
        return new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.17.1.zip",
                "ffffffffffffffffffffffffffffffffffffffff", false, "");
    }

    @Test
    void internalProtocol756IsAccepted() throws Exception {
        ResourcePack pack = pack756();
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, pack, null, null, 756);
        assertEquals(756, proxy.internalProtocol());
        assertEquals(pack, proxy.pack756());
    }

    @Test
    void internalProtocol756WithNullPack756IsAccepted() throws Exception {
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, 756);
        assertEquals(756, proxy.internalProtocol());
        assertNull(proxy.pack756());
    }

    @Test
    void pack756IsKeptForNon756InternalProtocols() throws Exception {
        ResourcePack pack = pack756();
        ClientProxy proxy774 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, pack, null, null, 774);
        assertEquals(774, proxy774.internalProtocol());
        assertEquals(pack, proxy774.pack756());
    }

    @Test
    void internalProtocol758IsStillRejected() throws Exception {
        try {
            new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                    null, null, null, null, null, null,
                    ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, 758);
            throw new AssertionError("expected IllegalArgumentException for 758");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("756"), expected.getMessage());
        }
        // 中間コンストラクタ経由でも 758 は拒否される。
        assertThrows(IllegalArgumentException.class, () -> new ClientProxy(
                freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, 758));
    }
}
