package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

/**
 * 767（1.21／1.21.1）のプロキシ受け入れ（コンストラクタの検証と
 * pack767 の保持）。767クライアントの実際の参加フロー（ルーティング）は
 * ProxySession 側の実装待ちのため、ここでは起動しない。
 */
class ClientProxy767Test {

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void internalProtocol767IsAccepted() throws Exception {
        ResourcePack pack767 = new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.21.zip",
                "dddddddddddddddddddddddddddddddddddddddd", false, "");
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, pack767, 767);
        assertEquals(767, proxy.internalProtocol());
        // 内部767のときは pack767 を渡す意味がないため、呼び出し元は
        // null を渡す（DetergentServer.createClientProxy と同じ決まり）。
        // ここでは渡した値がそのまま保持されることを確認する。
        assertEquals(pack767, proxy.pack767());
    }

    @Test
    void internalProtocol767WithNullPack767IsAccepted() throws Exception {
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, 767);
        assertEquals(767, proxy.internalProtocol());
        assertNull(proxy.pack767());
    }

    @Test
    void pack767IsKeptForNon767InternalProtocols() throws Exception {
        ResourcePack pack767 = new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.21.zip",
                "dddddddddddddddddddddddddddddddddddddddd", false, "");
        ClientProxy proxy765 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, pack767, 765);
        assertEquals(765, proxy765.internalProtocol());
        assertEquals(pack767, proxy765.pack767());
    }

    @Test
    void internalProtocol768IsAccepted() throws Exception {
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, null, 768);
        assertEquals(768, proxy.internalProtocol());
    }

    @Test
    void internalProtocol776IsStillRejected() throws Exception {
        try {
            new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                    null, null, null, ProxyAuthConfig.defaults(), null, null, null, 776);
            throw new AssertionError("expected IllegalArgumentException for 776");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("775"), expected.getMessage());
        }
    }

    @Test
    void tenArgConstructorStillWorksWithoutPack767() throws Exception {
        // 767用パックなしの従来のコンストラクタは pack767()==null で作れる。
        ClientProxy proxy = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, 765);
        assertEquals(765, proxy.internalProtocol());
        assertNull(proxy.pack767());
        assertThrows(IllegalArgumentException.class, () -> new ClientProxy(
                freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, 776));
    }
}
