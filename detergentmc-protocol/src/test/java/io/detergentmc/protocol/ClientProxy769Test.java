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
 * 769（1.21.4）のプロキシ受け入れ（コンストラクタの検証と
 * pack769 の保持）。
 */
class ClientProxy769Test {

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void internalProtocol769IsAccepted() throws Exception {
        ResourcePack pack769 = new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.21.4.zip",
                "ffffffffffffffffffffffffffffffffffffffff", false, "");
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, null, pack769, 769);
        assertEquals(769, proxy.internalProtocol());
        // 内部769のときは pack769 を渡す意味がないため、呼び出し元は
        // null を渡す（DetergentServer.createClientProxy と同じ決まり）。
        // ここでは渡した値がそのまま保持されることを確認する。
        assertEquals(pack769, proxy.pack769());
    }

    @Test
    void internalProtocol769WithNullPack769IsAccepted() throws Exception {
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, null, null, 769);
        assertEquals(769, proxy.internalProtocol());
        assertNull(proxy.pack769());
    }

    @Test
    void pack769IsKeptForNon769InternalProtocols() throws Exception {
        ResourcePack pack769 = new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.21.4.zip",
                "ffffffffffffffffffffffffffffffffffffffff", false, "");
        ClientProxy proxy768 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, null, pack769, 768);
        assertEquals(768, proxy768.internalProtocol());
        assertEquals(pack769, proxy768.pack769());
    }

    @Test
    void internalProtocol777IsStillRejected() throws Exception {
        try {
            new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                    null, null, null, ProxyAuthConfig.defaults(), null, null, null, null, null, 777);
            throw new AssertionError("expected IllegalArgumentException for 777");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("776"), expected.getMessage());
        }
    }

    @Test
    void elevenArgConstructorStillWorksWithoutPack769() throws Exception {
        // 769用パックなしの従来のコンストラクタは pack769()==null で作れる。
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, 768);
        assertEquals(768, proxy.internalProtocol());
        assertNull(proxy.pack769());
        assertThrows(IllegalArgumentException.class, () -> new ClientProxy(
                freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, 777));
    }
}
