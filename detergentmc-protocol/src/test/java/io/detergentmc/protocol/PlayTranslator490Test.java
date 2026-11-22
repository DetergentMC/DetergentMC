package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame498;
import io.detergentmc.protocol.PlayTranslator.JoinGame758;
import org.junit.jupiter.api.Test;

/**
 * 490（1.14.3）の Play 変換。Stage A の実データ照合で、Join Game／
 * Respawn のレイアウトは 498 と完全一致することを確認済みのため、
 * 498 用の変換（{@link PlayTranslator} の *498 メソッド）を共用する。
 * ここでは 490 の実レイアウトのペイロードがそのまま読めることを固定する。
 */
class PlayTranslator490Test {

    /** 490 の Join Game ペイロード（実 1.14.3 のレイアウト。498 と同一）。 */
    private static byte[] joinGame490Payload() {
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
    void joinGame490ParsesWithShared498Parser() {
        JoinGame498 parsed = PlayTranslator.parseJoinGame498(joinGame490Payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.gameMode());
        assertEquals(0, parsed.dimension());
        assertEquals(20, parsed.maxPlayers());
        assertEquals("default", parsed.levelType());
        assertEquals(10, parsed.viewDistance());
    }

    @Test
    void joinGame490To758SynthesizesSeedAndRespawnScreen() {
        byte[] out = PlayTranslator.translateJoinGame498To758(joinGame490Payload());
        JoinGame758 parsed = PlayTranslator.parseJoinGame758(out);
        assertEquals(42, parsed.entityId());
        assertEquals(0L, parsed.hashedSeed());
        assertTrue(parsed.enableRespawnScreen());
    }

    @Test
    void joinGame490RoundTripsThrough758() {
        byte[] as758 = PlayTranslator.translateJoinGame498To758(joinGame490Payload());
        byte[] back = PlayTranslator.translateJoinGame758To498(as758);
        assertArrayEquals(joinGame490Payload(), back);
    }
}
