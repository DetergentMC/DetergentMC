package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame404;
import io.detergentmc.protocol.PlayTranslator.JoinGame758;
import org.junit.jupiter.api.Test;

/**
 * 404（1.13.2）の Join Game／Respawn 変換。404 の Join Game は
 * 477 と違い difficulty（u8）を持ち viewDistance を持たない
 * （実バニラ 1.13.2 サーバーへのプローブでバイト列まで確認済み：
 * eid 0・gamemode 0・dimension 0・difficulty 1・maxPlayers 20・
 * "flat"・false）。
 */
class PlayTranslator404Test {

    /** 404 の Join Game ペイロード（実 1.13.2 のレイアウト）。 */
    private static byte[] joinGame404Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0);
        writer.writeInt(0);
        writer.writeByte(1); // difficulty（peaceful ではない 1）
        writer.writeByte(20);
        writer.writeString("flat");
        writer.writeBoolean(false);
        return writer.bytes();
    }

    @Test
    void joinGame404Parses() {
        JoinGame404 parsed = PlayTranslator.parseJoinGame404(joinGame404Payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.gameMode());
        assertEquals(0, parsed.dimension());
        assertEquals(1, parsed.difficulty());
        assertEquals(20, parsed.maxPlayers());
        assertEquals("flat", parsed.levelType());
        assertEquals(false, parsed.reducedDebug());
    }

    @Test
    void joinGame404To758DropsDifficultyAndSynthesizesViewDistance() {
        byte[] out = PlayTranslator.translateJoinGame404To758(joinGame404Payload());
        JoinGame758 parsed = PlayTranslator.parseJoinGame758(out);
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.gameMode());
        assertEquals(10, parsed.viewDistance());
    }

    @Test
    void joinGame758To404SynthesizesNormalDifficulty() {
        byte[] as758 = PlayTranslator.translateJoinGame404To758(joinGame404Payload());
        byte[] back = PlayTranslator.translateJoinGame758To404(as758);
        JoinGame404 parsed = PlayTranslator.parseJoinGame404(back);
        assertEquals(42, parsed.entityId());
        // 758 側に difficulty は無いため、404 へ戻すときは normal（2）を合成する
        // （本物の難易度は Server Difficulty パケットが続く）。
        assertEquals(2, parsed.difficulty());
        assertEquals(20, parsed.maxPlayers());
        assertEquals("flat", parsed.levelType());
    }

    @Test
    void respawn404To758DropsDifficulty() {
        Writer writer = new Writer();
        writer.writeInt(-1); // dimension（nether）
        writer.writeByte(3); // difficulty（hard、758 では落ちる）
        writer.writeByte(1); // gamemode（creative）
        writer.writeString("default");
        byte[] as758 = PlayTranslator.translateRespawn404To758(writer.bytes(), null);
        // 758 形式の先頭は次元 NBT。次元名が the_nether であることを読む代わりに、
        // 404 へ戻した結果で確認する（difficulty は合成値 2 になる）。
        byte[] back = PlayTranslator.translateRespawn758To404(as758, null);
        Writer expected = new Writer();
        expected.writeInt(-1);
        expected.writeByte(2);
        expected.writeByte(1);
        expected.writeString("default");
        assertArrayEquals(expected.bytes(), back);
    }
}
