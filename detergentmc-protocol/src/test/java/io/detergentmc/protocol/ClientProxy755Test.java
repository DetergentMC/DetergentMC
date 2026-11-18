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
 * 755（1.17）のプロキシ受け入れ（新ビッグコンストラクタの検証と
 * pack755 の保持）。pack755 は新ビッグコンストラクタ（pack759-first）にしか
 * 無いため、ここではそのコンストラクタを直接使う。
 * 新ビッグコンストラクタは31引数：ポート類4＋pack759〜764（6）＋authConfig＋
 * pack765〜775（11）＋pack757＋pack756＋pack755＋pack754＋pack753＋pack751＋pack736＋pack735＋internalProtocol。
 */
class ClientProxy755Test {

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static ResourcePack pack755() {
        return new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.17.zip",
                "ffffffffffffffffffffffffffffffffffffffff", false, "");
    }

    @Test
    void internalProtocol755IsAccepted() throws Exception {
        ResourcePack pack = pack755();
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, pack, null, null, null, null, null, 755);
        assertEquals(755, proxy.internalProtocol());
        assertEquals(pack, proxy.pack755());
    }

    @Test
    void internalProtocol755WithNullPack755IsAccepted() throws Exception {
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, 755);
        assertEquals(755, proxy.internalProtocol());
        assertNull(proxy.pack755());
    }

    @Test
    void pack755IsKeptForNon755InternalProtocols() throws Exception {
        ResourcePack pack = pack755();
        ClientProxy proxy774 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, pack, null, null, null, null, null, 774);
        assertEquals(774, proxy774.internalProtocol());
        assertEquals(pack, proxy774.pack755());
    }

    @Test
    void internalProtocol758IsStillRejected() throws Exception {
        try {
            new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                    null, null, null, null, null, null,
                    ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, 758);
            throw new AssertionError("expected IllegalArgumentException for 758");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("755"), expected.getMessage());
        }
        // 中間コンストラクタ経由でも 758 は拒否される。
        assertThrows(IllegalArgumentException.class, () -> new ClientProxy(
                freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, 758));
    }
}
