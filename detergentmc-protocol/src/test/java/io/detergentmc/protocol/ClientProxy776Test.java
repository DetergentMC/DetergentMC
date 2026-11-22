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
 * 776（26.2）のプロキシ受け入れ（新ビッグコンストラクタの検証と
 * pack776 の保持）。pack776 は新ビッグコンストラクタ（pack759-first）にしか
 * 無いため、ここではそのコンストラクタを直接使う。
 * 新ビッグコンストラクタは24引数：ポート類4＋pack759〜764（6）＋authConfig＋
 * pack765〜775（11）＋pack776＋internalProtocol。
 */
class ClientProxy776Test {

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static ResourcePack pack776() {
        return new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-26.2.zip",
                "ffffffffffffffffffffffffffffffffffffffff", false, "");
    }

    @Test
    void internalProtocol776IsAccepted() throws Exception {
        ResourcePack pack = pack776();
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null, null,
                null, pack, 776);
        assertEquals(776, proxy.internalProtocol());
        assertEquals(pack, proxy.pack776());
    }

    @Test
    void internalProtocol776WithNullPack776IsAccepted() throws Exception {
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null, null,
                null, null, 776);
        assertEquals(776, proxy.internalProtocol());
        assertNull(proxy.pack776());
    }

    @Test
    void pack776IsKeptForNon776InternalProtocols() throws Exception {
        ResourcePack pack = pack776();
        ClientProxy proxy775 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, null, null, null,
                ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null, null, null,
                null, pack, 775);
        assertEquals(775, proxy775.internalProtocol());
        assertEquals(pack, proxy775.pack776());
    }

    @Test
    void internalProtocol777IsStillRejected() throws Exception {
        try {
            new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                    null, null, null, null, null, null,
                    ProxyAuthConfig.defaults(), null, null, null, null, null, null, null, null,
                    null, null, null, null, 777);
            throw new AssertionError("expected IllegalArgumentException for 777");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("776"), expected.getMessage());
        }
        // 中間コンストラクタ経由でも 777 は拒否される。
        assertThrows(IllegalArgumentException.class, () -> new ClientProxy(
                freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, 777));
    }
}
