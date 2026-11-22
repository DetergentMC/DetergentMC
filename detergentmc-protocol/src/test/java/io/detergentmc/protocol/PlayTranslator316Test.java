package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame316;
import io.detergentmc.protocol.PlayTranslator.JoinGame758;
import org.junit.jupiter.api.Test;

/**
 * 316（1.11.2）の Join Game／Respawn 変換。ワイヤ形式は 393 と完全一致
 * （実バニラ 1.11.2 の Join Game 採取で 20/20 バイト消費を確認）
 * するため 393 の実装へ委譲していることを、316 の入口で固定する。
 * 316 の Join Game は difficulty（u8）を持ち viewDistance を持たない。
 */
class PlayTranslator316Test {

    /** 316 の Join Game ペイロード（実 1.11.2 のレイアウト＝393 と同一）。 */
    private static byte[] joinGame316Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0);
        writer.writeInt(0);
        writer.writeByte(1); // difficulty
        writer.writeByte(20);
        writer.writeString("flat");
        writer.writeBoolean(false);
        return writer.bytes();
    }

    @Test
    void joinGame316Parses() {
        JoinGame316 parsed = PlayTranslator.parseJoinGame316(joinGame316Payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.gameMode());
        assertEquals(0, parsed.dimension());
        assertEquals(1, parsed.difficulty());
        assertEquals(20, parsed.maxPlayers());
        assertEquals("flat", parsed.levelType());
        assertEquals(false, parsed.reducedDebug());
    }

    @Test
    void joinGame316To758DropsDifficultyAndSynthesizesViewDistance() {
        byte[] out = PlayTranslator.translateJoinGame316To758(joinGame316Payload());
        JoinGame758 parsed = PlayTranslator.parseJoinGame758(out);
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.gameMode());
        assertEquals(10, parsed.viewDistance());
    }

    @Test
    void joinGame758To316SynthesizesNormalDifficulty() {
        byte[] as758 = PlayTranslator.translateJoinGame316To758(joinGame316Payload());
        byte[] back = PlayTranslator.translateJoinGame758To316(as758);
        JoinGame316 parsed = PlayTranslator.parseJoinGame316(back);
        assertEquals(42, parsed.entityId());
        // 758 側に difficulty は無いため、316 へ戻すときは normal（2）を合成する。
        assertEquals(2, parsed.difficulty());
        assertEquals(20, parsed.maxPlayers());
        assertEquals("flat", parsed.levelType());
    }

    @Test
    void respawn316To758DropsDifficulty() {
        Writer writer = new Writer();
        writer.writeInt(-1); // dimension（nether）
        writer.writeByte(3); // difficulty（hard、758 では落ちる）
        writer.writeByte(1); // gamemode（creative）
        writer.writeString("default");
        byte[] as758 = PlayTranslator.translateRespawn316To758(writer.bytes(), null);
        byte[] back = PlayTranslator.translateRespawn758To316(as758, null);
        Writer expected = new Writer();
        expected.writeInt(-1);
        expected.writeByte(2);
        expected.writeByte(1);
        expected.writeString("default");
        assertArrayEquals(expected.bytes(), back);
    }
}
