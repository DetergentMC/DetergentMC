package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame498;
import io.detergentmc.protocol.PlayTranslator.JoinGame758;
import org.junit.jupiter.api.Test;

/**
 * 477（1.14）の Play 変換。Stage A の実データ照合で、Join Game／
 * Respawn のレイアウトは 480 と完全一致することを確認済みのため、
 * 490 用の変換（{@link PlayTranslator} の *490 メソッド）を共用する。
 * ここでは 477 の実レイアウトのペイロードがそのまま読めることを固定する。
 */
class PlayTranslator477Test {

    /** 477 の Join Game ペイロード（実 1.14 のレイアウト。490 と同一）。 */
    private static byte[] joinGame477Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0);
        writer.writeInt(0);
        writer.writeByte(20);
        writer.writeString("default");
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    @Test
    void joinGame477ParsesWithShared490Parser() {
        JoinGame498 parsed = PlayTranslator.parseJoinGame498(joinGame477Payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.gameMode());
        assertEquals(0, parsed.dimension());
        assertEquals(20, parsed.maxPlayers());
        assertEquals("default", parsed.levelType());
        assertEquals(10, parsed.viewDistance());
    }

    @Test
    void joinGame477To758SynthesizesSeedAndRespawnScreen() {
        byte[] out = PlayTranslator.translateJoinGame498To758(joinGame477Payload());
        JoinGame758 parsed = PlayTranslator.parseJoinGame758(out);
        assertEquals(42, parsed.entityId());
        assertEquals(0L, parsed.hashedSeed());
        assertTrue(parsed.enableRespawnScreen());
    }

    @Test
    void joinGame477RoundTripsThrough758() {
        byte[] as758 = PlayTranslator.translateJoinGame498To758(joinGame477Payload());
        byte[] back = PlayTranslator.translateJoinGame758To498(as758);
        assertArrayEquals(joinGame477Payload(), back);
    }
}
