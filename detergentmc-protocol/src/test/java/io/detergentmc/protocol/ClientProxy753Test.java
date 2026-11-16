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
 * 753（1.16.3）のプロキシ受け入れ（新ビッグコンストラクタの検証と
 * pack753 の保持）。pack753 は新ビッグコンストラクタ（pack759-first）にしか
 * 無いため、ここではそのコンストラクタを直接使う。
 * 新ビッグコンストラクタは30引数：ポート類4＋pack759〜764（6）＋authConfig＋
 * pack765〜775（11）＋pack757＋pack756＋pack755＋pack754＋pack753＋pack751＋pack736＋internalProtocol。
 */
class ClientProxy753Test {

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static ResourcePack pack753() {
        return new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.16.3.zip",
                "ffffffffffffffffffffffffffffffffffffffff", false, "");
    }

    @Test
    void internalProtocol753IsAccepted() throws Exception {
        ResourcePack pack = pack753();
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, pack, null, null, 753);
        assertEquals(753, proxy.internalProtocol());
        assertEquals(pack, proxy.pack753());
    }

    @Test
    void internalProtocol753WithNullPack753IsAccepted() throws Exception {
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, 753);
        assertEquals(753, proxy.internalProtocol());
        assertNull(proxy.pack753());
    }

    @Test
    void pack753IsKeptForNon753InternalProtocols() throws Exception {
        ResourcePack pack = pack753();
        ClientProxy proxy774 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, pack, null, null, 774);
        assertEquals(774, proxy774.internalProtocol());
        assertEquals(pack, proxy774.pack753());
    }

    @Test
    void internalProtocol758IsStillRejected() throws Exception {
        try {
            new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                    null, null, null, null, null, null,
                    ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, 758);
            throw new AssertionError("expected IllegalArgumentException for 758");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("753"), expected.getMessage());
        }
        // 中間コンストラクタ経由でも 758 は拒否される。
        assertThrows(IllegalArgumentException.class, () -> new ClientProxy(
                freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, 758));
    }
}
