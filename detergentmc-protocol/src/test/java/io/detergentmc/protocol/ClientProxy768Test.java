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
 * 768（1.21.2／1.21.3）のプロキシ受け入れ（コンストラクタの検証と
 * pack768 の保持）。
 */
class ClientProxy768Test {

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void internalProtocol768IsAccepted() throws Exception {
        ResourcePack pack768 = new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.21.2.zip",
                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", false, "");
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, pack768, 768);
        assertEquals(768, proxy.internalProtocol());
        // 内部768のときは pack768 を渡す意味がないため、呼び出し元は
        // null を渡す（DetergentServer.createClientProxy と同じ決まり）。
        // ここでは渡した値がそのまま保持されることを確認する。
        assertEquals(pack768, proxy.pack768());
    }

    @Test
    void internalProtocol768WithNullPack768IsAccepted() throws Exception {
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, null, 768);
        assertEquals(768, proxy.internalProtocol());
        assertNull(proxy.pack768());
    }

    @Test
    void pack768IsKeptForNon768InternalProtocols() throws Exception {
        ResourcePack pack768 = new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.21.2.zip",
                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", false, "");
        ClientProxy proxy767 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, pack768, 767);
        assertEquals(767, proxy767.internalProtocol());
        assertEquals(pack768, proxy767.pack768());
    }

    @Test
    void internalProtocol776IsStillRejected() throws Exception {
        try {
            new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                    null, null, null, ProxyAuthConfig.defaults(), null, null, null, null, 776);
            throw new AssertionError("expected IllegalArgumentException for 776");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("775"), expected.getMessage());
        }
    }

    @Test
    void elevenArgConstructorStillWorksWithoutPack768() throws Exception {
        // 768用パックなしの従来のコンストラクタは pack768()==null で作れる。
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, 767);
        assertEquals(767, proxy.internalProtocol());
        assertNull(proxy.pack768());
        assertThrows(IllegalArgumentException.class, () -> new ClientProxy(
                freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, 776));
    }
}
