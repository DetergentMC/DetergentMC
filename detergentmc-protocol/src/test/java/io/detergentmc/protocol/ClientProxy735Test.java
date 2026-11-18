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
 * 735（1.16）のプロキシ受け入れ（新ビッグコンストラクタの検証と
 * pack735 の保持）。pack735 は新ビッグコンストラクタ（pack759-first）にしか
 * 無いため、ここではそのコンストラクタを直接使う。
 * 新ビッグコンストラクタは31引数：ポート類4＋pack759〜764（6）＋authConfig＋
 * pack765〜775（11）＋pack757＋pack756＋pack755＋pack754＋pack753＋pack751＋pack736＋pack735＋internalProtocol。
 */
class ClientProxy735Test {

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static ResourcePack pack735() {
        return new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.16.zip",
                "cccccccccccccccccccccccccccccccccccccccc", false, "");
    }

    @Test
    void internalProtocol735IsAccepted() throws Exception {
        ResourcePack pack = pack735();
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, pack, 735);
        assertEquals(735, proxy.internalProtocol());
        assertEquals(pack, proxy.pack735());
    }

    @Test
    void internalProtocol735WithNullPack735IsAccepted() throws Exception {
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, 735);
        assertEquals(735, proxy.internalProtocol());
        assertNull(proxy.pack735());
    }

    @Test
    void pack735IsKeptForNon735InternalProtocols() throws Exception {
        ResourcePack pack = pack735();
        ClientProxy proxy774 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, pack, 774);
        assertEquals(774, proxy774.internalProtocol());
        assertEquals(pack, proxy774.pack735());
    }

    @Test
    void internalProtocol758IsStillRejected() throws Exception {
        try {
            new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                    null, null, null, null, null, null,
                    ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, 758);
            throw new AssertionError("expected IllegalArgumentException for 758");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("735"), expected.getMessage());
        }
        // 中間コンストラクタ経由でも 758 は拒否される。
        assertThrows(IllegalArgumentException.class, () -> new ClientProxy(
                freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, 758));
    }
}
