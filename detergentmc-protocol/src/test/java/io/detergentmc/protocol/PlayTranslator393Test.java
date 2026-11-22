package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame393;
import io.detergentmc.protocol.PlayTranslator.JoinGame758;
import org.junit.jupiter.api.Test;

/**
 * 393（1.13）の Join Game／Respawn 変換。ワイヤ形式は 404 と完全一致
 * （protocol.json 平坦化比較で確認）するため 404 の実装へ委譲している
 * ことを、393 の入口で固定する。393 の Join Game は difficulty（u8）を
 * 持ち viewDistance を持たない。
 */
class PlayTranslator393Test {

    /** 393 の Join Game ペイロード（実 1.13 のレイアウト＝404 と同一）。 */
    private static byte[] joinGame393Payload() {
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
    void joinGame393Parses() {
        JoinGame393 parsed = PlayTranslator.parseJoinGame393(joinGame393Payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.gameMode());
        assertEquals(0, parsed.dimension());
        assertEquals(1, parsed.difficulty());
        assertEquals(20, parsed.maxPlayers());
        assertEquals("flat", parsed.levelType());
        assertEquals(false, parsed.reducedDebug());
    }

    @Test
    void joinGame393To758DropsDifficultyAndSynthesizesViewDistance() {
        byte[] out = PlayTranslator.translateJoinGame393To758(joinGame393Payload());
        JoinGame758 parsed = PlayTranslator.parseJoinGame758(out);
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.gameMode());
        assertEquals(10, parsed.viewDistance());
    }

    @Test
    void joinGame758To393SynthesizesNormalDifficulty() {
        byte[] as758 = PlayTranslator.translateJoinGame393To758(joinGame393Payload());
        byte[] back = PlayTranslator.translateJoinGame758To393(as758);
        JoinGame393 parsed = PlayTranslator.parseJoinGame393(back);
        assertEquals(42, parsed.entityId());
        // 758 側に difficulty は無いため、393 へ戻すときは normal（2）を合成する。
        assertEquals(2, parsed.difficulty());
        assertEquals(20, parsed.maxPlayers());
        assertEquals("flat", parsed.levelType());
    }

    @Test
    void respawn393To758DropsDifficulty() {
        Writer writer = new Writer();
        writer.writeInt(-1); // dimension（nether）
        writer.writeByte(3); // difficulty（hard、758 では落ちる）
        writer.writeByte(1); // gamemode（creative）
        writer.writeString("default");
        byte[] as758 = PlayTranslator.translateRespawn393To758(writer.bytes(), null);
        byte[] back = PlayTranslator.translateRespawn758To393(as758, null);
        Writer expected = new Writer();
        expected.writeInt(-1);
        expected.writeByte(2);
        expected.writeByte(1);
        expected.writeString("default");
        assertArrayEquals(expected.bytes(), back);
    }
}
