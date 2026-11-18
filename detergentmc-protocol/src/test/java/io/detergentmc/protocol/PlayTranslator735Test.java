package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PacketIds.PacketName;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayTranslator} の735（1.16）⇔736（1.16.1）向け変換テスト。
 *
 * <p>根拠：packet-ids-735.json（736↔735のパケットID差分は変更0件・新規0件・削除0件）と
 * STAGE-A-735-NOTES.md §8・§11.6、実バニラ1.16 jar と実バニラ1.16.1 jar の javap 実測。
 * 両jarの全21,480クラスを sha1 比較したところ、異なるのは o.class
 * （net.minecraft.DetectedVersion。バージョン文字列・world 2566／2567・protocol 735／736
 * のみ）1件だけで、パケット関連を含む他の全クラスはバイト同一。Respawn は
 * ClientboundRespawnPacket（pp）#write の javap で8フィールド・dimension は文字列と確認。
 * Join Game は14フィールドで hardcore が gameMode バイトの bit 3、dimension は文字列、
 * maxPlayers は u8、codec は {"dimension": [...]} だけ。Client Information・Position・
 * combat_event・title・world_border・ping／pong は両版とも同一形式で恒等写像。
 * displayed_recipe は両版とも -1 のため変換器は無い。
 * 生キャプチャはテストリソースにある（cap735-joingame-1.16.bin は実バニラ1.16の
 * Join Game 1,159バイト、entityId 352。cap736-joingame-1-16-1.bin は実バニラ1.16.1の
 * Join Game 1,159バイト、entityId 138）。
 * 実ゲームクライアントでの確認は未実施。
 */
class PlayTranslator735Test {

    private static byte[] readResource(String name) {
        try (InputStream in = PlayTranslator735Test.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new AssertionError("テストリソースがありません: " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError("テストリソースが読めません: " + name, e);
        }
    }

    /** 合成の735 Respawn ペイロード（nether）。736の read／write をそのまま使う。 */
    private static byte[] syntheticRespawn735() {
        Writer writer = new Writer();
        PlayTranslator.writeSpawnInfo736(writer, new PlayTranslator.SpawnInfo736(
                "minecraft:the_nether", "minecraft:the_nether",
                123456789L, 0, 255, false, false, true));
        return writer.bytes();
    }

    /** 合成の735 Client Information ペイロード（6フィールド。736と同一）。 */
    private static byte[] clientInformation735() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(0x7f);
        writer.writeVarInt(1);
        return writer.bytes();
    }

    /** 合成の735 Synchronize Player Position ペイロード（7フィールド。736と同一）。 */
    private static byte[] position735() {
        Writer writer = new Writer();
        writer.writeDouble(100.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-200.5);
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        writer.writeByte(0);
        writer.writeVarInt(7);
        return writer.bytes();
    }

    /** 合成の Combat Event ペイロード（event 0〜2）。 */
    private static byte[] combatEvent735(int event) {
        Writer writer = new Writer();
        writer.writeVarInt(event);
        if (event == 1) {
            writer.writeVarInt(100);
            writer.writeInt(5);
        } else if (event == 2) {
            writer.writeVarInt(7);
            writer.writeInt(9);
            writer.writeString("{\"text\":\"x\"}");
        }
        return writer.bytes();
    }

    /** 合成の Title ペイロード（action 0＝text／3＝times）。 */
    private static byte[] title735(int action) {
        Writer writer = new Writer();
        writer.writeVarInt(action);
        if (action == 0) {
            writer.writeString("{\"text\":\"hi\"}");
        } else if (action == 3) {
            writer.writeInt(10);
            writer.writeInt(70);
            writer.writeInt(20);
        }
        return writer.bytes();
    }

    /** 合成の World Border ペイロード（action 0＝size／3＝initialize）。 */
    private static byte[] worldBorder735(int action) {
        Writer writer = new Writer();
        writer.writeVarInt(action);
        if (action == 0) {
            writer.writeDouble(60000000.0);
        } else if (action == 3) {
            writer.writeDouble(0.0);
            writer.writeDouble(0.0);
            writer.writeDouble(60000000.0);
            writer.writeDouble(60000000.0);
            writer.writeVarLong(0L);
            writer.writeVarInt(29999984);
            writer.writeVarInt(5);
            writer.writeVarInt(15);
        }
        return writer.bytes();
    }

    /** 合成の Container Ack（ping／pong）ペイロード（windowId・actionNumber・accepted）。 */
    private static byte[] containerAck735() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeShort(1);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    private static String stringField(Tag.CompoundTag compound, String name) {
        return compound.getString(name)
                .orElseThrow(() -> new AssertionError("string フィールドがありません: " + name));
    }

    @Test
    void joinGame735LiveCaptureParses() {
        byte[] capture = readResource("cap735-joingame-1.16.bin");
        assertEquals(1_159, capture.length);
        PlayTranslator.JoinGame736 join = PlayTranslator.parseJoinGame736(capture);
        assertEquals(352, join.entityId());
        assertFalse(join.hardcore());
        assertEquals(0, join.gameMode());
        assertEquals(-1, join.previousGameMode());
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                join.worldNames());
        assertEquals("minecraft:overworld", join.dimension());
        assertEquals("minecraft:overworld", join.worldName());
        assertEquals(-3_697_337_694_575_249_872L, join.hashedSeed());
        assertEquals(20, join.maxPlayers());
        assertEquals(10, join.viewDistance());
        assertFalse(join.reducedDebug());
        assertTrue(join.enableRespawnScreen());
        assertFalse(join.isDebug());
        assertFalse(join.isFlat());
        // codec は {"dimension": [...]} だけ。
        assertEquals(List.of("dimension"), List.copyOf(join.dimensionCodec().value().keySet()));
        List<Tag> dimensions = join.dimensionCodec().getList("dimension")
                .map(Tag.ListTag::value)
                .orElseThrow(() -> new AssertionError("dimension がありません"));
        assertEquals(4, dimensions.size());
        List<String> names = new ArrayList<>(dimensions.size());
        for (Tag d : dimensions) {
            names.add(stringField((Tag.CompoundTag) d, "name"));
        }
        assertEquals(List.of("minecraft:overworld", "minecraft:overworld_caves",
                "minecraft:the_nether", "minecraft:the_end"), names);
        // 735の element は736と同じく "name"／"shrunk" を持ち、"effects"／"coordinate_scale" は無い。
        Tag.CompoundTag overworld = (Tag.CompoundTag) dimensions.get(0);
        assertEquals("minecraft:overworld", stringField(overworld, "name"));
        assertTrue(overworld.get("shrunk").isPresent());
        assertFalse(overworld.get("effects").isPresent());
        assertFalse(overworld.get("coordinate_scale").isPresent());
        assertFalse(overworld.get("min_y").isPresent());
        assertFalse(overworld.get("height").isPresent());
        // 生キャプチャを write してもバイトが変わらない（パーサの検証）。
        assertArrayEquals(capture, PlayTranslator.writeJoinGame736(join));
    }

    @Test
    void joinGame735To736To735RoundTripBytesIdentical() {
        byte[] capture = readResource("cap735-joingame-1.16.bin");
        assertEquals(1_159, capture.length);
        byte[] via736 = PlayTranslator.translateJoinGame735To736(capture);
        assertEquals(1_159, via736.length);
        assertArrayEquals(capture, PlayTranslator.translateJoinGame736To735(via736));
    }

    @Test
    void joinGame736To735To736RoundTripBytesIdentical() {
        byte[] capture = readResource("cap736-joingame-1-16-1.bin");
        assertEquals(1_159, capture.length);
        byte[] via735 = PlayTranslator.translateJoinGame736To735(capture);
        assertEquals(1_159, via735.length);
        assertArrayEquals(capture, PlayTranslator.translateJoinGame735To736(via735));
    }

    @Test
    void joinGame735HardcoreBit() {
        PlayTranslator.JoinGame736 parsed =
                PlayTranslator.parseJoinGame736(readResource("cap735-joingame-1.16.bin"));
        PlayTranslator.JoinGame736 hardcore = new PlayTranslator.JoinGame736(
                parsed.entityId(), true, 1, parsed.previousGameMode(), parsed.worldNames(),
                parsed.dimensionCodec(), parsed.dimension(), parsed.worldName(),
                parsed.hashedSeed(), parsed.maxPlayers(), parsed.viewDistance(),
                parsed.reducedDebug(), parsed.enableRespawnScreen(),
                parsed.isDebug(), parsed.isFlat());
        byte[] bytes = PlayTranslator.writeJoinGame736(hardcore);
        // gameMode バイトは entityId（4バイト）の直後で、1 | 8 == 9。
        assertEquals(9, bytes[4]);
        PlayTranslator.JoinGame736 via736 = PlayTranslator.parseJoinGame736(
                PlayTranslator.translateJoinGame735To736(bytes));
        assertTrue(via736.hardcore());
        assertEquals(1, via736.gameMode());
    }

    @Test
    void joinGame735MaxPlayers200StaysU8() {
        PlayTranslator.JoinGame736 parsed =
                PlayTranslator.parseJoinGame736(readResource("cap735-joingame-1.16.bin"));
        PlayTranslator.JoinGame736 many = new PlayTranslator.JoinGame736(
                parsed.entityId(), false, 0, parsed.previousGameMode(), parsed.worldNames(),
                parsed.dimensionCodec(), parsed.dimension(), parsed.worldName(),
                parsed.hashedSeed(), 200, parsed.viewDistance(),
                parsed.reducedDebug(), parsed.enableRespawnScreen(),
                parsed.isDebug(), parsed.isFlat());
        byte[] bytes735 = PlayTranslator.writeJoinGame736(many);
        // 両版とも u8 のため、maxPlayers 20 のときと同じ長さ。
        assertEquals(1_159, bytes735.length);
        byte[] via736 = PlayTranslator.translateJoinGame735To736(bytes735);
        assertEquals(1_159, via736.length);
        assertEquals(200, PlayTranslator.parseJoinGame736(via736).maxPlayers());
        assertArrayEquals(bytes735, PlayTranslator.translateJoinGame736To735(via736));
    }

    @Test
    void respawn735To736To735RoundTripBytesIdentical() {
        byte[] original = syntheticRespawn735();
        byte[] via736 = PlayTranslator.translateRespawn735To736(original);
        assertArrayEquals(original, via736);
        assertArrayEquals(original, PlayTranslator.translateRespawn736To735(via736));
    }

    @Test
    void respawn735SyntheticFields() {
        PlayTranslator.SpawnInfo736 info =
                PlayTranslator.readSpawnInfo736(new Reader(syntheticRespawn735()));
        assertEquals("minecraft:the_nether", info.dimension());
        assertEquals("minecraft:the_nether", info.worldName());
        assertEquals(123456789L, info.hashedSeed());
        assertEquals(0, info.gameMode());
        // Respawn の previousGameMode は u8 で読むため、0xFF は 255 になる。
        assertEquals(255, info.previousGameMode());
        assertFalse(info.isDebug());
        assertFalse(info.isFlat());
        assertTrue(info.copyMetadata());
    }

    @Test
    void clientInformation735To736Identity() {
        assertArrayEquals(clientInformation735(),
                PlayTranslator.translateClientInformation735To736(clientInformation735()));
        assertArrayEquals(clientInformation735(),
                PlayTranslator.translateClientInformation736To735(clientInformation735()));
    }

    @Test
    void position735To736Identity() {
        assertArrayEquals(position735(),
                PlayTranslator.translatePosition735To736(position735()));
        assertArrayEquals(position735(),
                PlayTranslator.translatePosition736To735(position735()));
    }

    @Test
    void combatEvent735To736Identity() {
        for (int event = 0; event <= 2; event++) {
            assertArrayEquals(combatEvent735(event),
                    PlayTranslator.translateCombatEvent735To736(combatEvent735(event)),
                    "event=" + event);
            assertArrayEquals(combatEvent735(event),
                    PlayTranslator.translateCombatEvent736To735(combatEvent735(event)),
                    "event=" + event);
        }
    }

    @Test
    void title735To736Identity() {
        assertArrayEquals(title735(0),
                PlayTranslator.translateTitle735To736(title735(0)));
        assertArrayEquals(title735(0),
                PlayTranslator.translateTitle736To735(title735(0)));
        assertArrayEquals(title735(3),
                PlayTranslator.translateTitle735To736(title735(3)));
        assertArrayEquals(title735(3),
                PlayTranslator.translateTitle736To735(title735(3)));
    }

    @Test
    void worldBorder735To736Identity() {
        assertArrayEquals(worldBorder735(0),
                PlayTranslator.translateWorldBorder735To736(worldBorder735(0)));
        assertArrayEquals(worldBorder735(0),
                PlayTranslator.translateWorldBorder736To735(worldBorder735(0)));
        assertArrayEquals(worldBorder735(3),
                PlayTranslator.translateWorldBorder735To736(worldBorder735(3)));
        assertArrayEquals(worldBorder735(3),
                PlayTranslator.translateWorldBorder736To735(worldBorder735(3)));
    }

    @Test
    void ping735To736Identity() {
        assertArrayEquals(containerAck735(),
                PlayTranslator.translatePing735To736(containerAck735()));
        assertArrayEquals(containerAck735(),
                PlayTranslator.translatePing736To735(containerAck735()));
    }

    @Test
    void pong735To736Identity() {
        assertArrayEquals(containerAck735(),
                PlayTranslator.translatePong735To736(containerAck735()));
        assertArrayEquals(containerAck735(),
                PlayTranslator.translatePong736To735(containerAck735()));
    }

    @Test
    void displayedRecipeAbsentInBothAndIdsIdentical() {
        assertEquals(-1, PacketName.PLAY_DISPLAYED_RECIPE.id735());
        assertEquals(-1, PacketName.PLAY_DISPLAYED_RECIPE.id736());
        assertEquals(0x25, PacketName.PLAY_JOIN_GAME.id735());
        assertEquals(PacketName.PLAY_JOIN_GAME.id736(), PacketName.PLAY_JOIN_GAME.id735());
        assertEquals(0x3a, PacketName.PLAY_RESPAWN.id735());
        assertEquals(PacketName.PLAY_RESPAWN.id736(), PacketName.PLAY_RESPAWN.id735());
        assertEquals(0x12, PacketName.PLAY_PING.id735());
        assertEquals(PacketName.PLAY_PING.id736(), PacketName.PLAY_PING.id735());
        assertEquals(0x07, PacketName.PLAY_PONG.id735());
        assertEquals(PacketName.PLAY_PONG.id736(), PacketName.PLAY_PONG.id735());
    }

    @Test
    void joinGame735CodecMatches736Capture() {
        PlayTranslator.JoinGame736 join735 = PlayTranslator.parseJoinGame736(
                readResource("cap735-joingame-1.16.bin"));
        PlayTranslator.JoinGame736 join736 = PlayTranslator.parseJoinGame736(
                readResource("cap736-joingame-1-16-1.bin"));
        // codec のキー・dimension 名・element のキー集合は両版で同一。
        assertEquals(List.copyOf(join736.dimensionCodec().value().keySet()),
                List.copyOf(join735.dimensionCodec().value().keySet()));
        List<Tag> dims735 = join735.dimensionCodec().getList("dimension")
                .map(Tag.ListTag::value)
                .orElseThrow(() -> new AssertionError("dimension がありません"));
        List<Tag> dims736 = join736.dimensionCodec().getList("dimension")
                .map(Tag.ListTag::value)
                .orElseThrow(() -> new AssertionError("dimension がありません"));
        assertEquals(dims736.size(), dims735.size());
        for (int i = 0; i < dims735.size(); i++) {
            Tag.CompoundTag e735 = (Tag.CompoundTag) dims735.get(i);
            Tag.CompoundTag e736 = (Tag.CompoundTag) dims736.get(i);
            assertEquals(stringField(e736, "name"), stringField(e735, "name"));
            assertEquals(e736.value().keySet(), e735.value().keySet());
        }
    }
}
