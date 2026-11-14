package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame763;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayTranslator} と {@link ExtraPlayTranslator} の762（1.19.4）⇔763
 * （1.20／1.20.1）向け変換テスト。
 *
 * <p>762と763のペイロード差（minecraft-data protocol.json で確認）：
 * Join Game・Respawn の末尾の portalCooldown、Death Combat Event・
 * End Combat Event の entityId、Advancements の sendsTelemetryData のみ。
 * チャット（署名付き）は762・763で同一のため変換しない。
 */
class PlayTranslator762Test {

    // ---- ヘルパー ----

    /** スロット1個（762／763形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
    private static void writeSlot(Writer writer, int itemId, int count) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeByte(0); // NBT 無し
    }

    /** スロットの先頭（個数＋アイテムID）を読む。 */
    private static int[] readSlotHead(Reader reader) {
        assertTrue(reader.readBoolean());
        int itemId = reader.readVarInt();
        int count = reader.readByte();
        assertEquals(0, reader.readByte()); // NBT 無し
        return new int[]{itemId, count};
    }

    /** 763形式の Join Game ペイロード。codec は小さなコンパウンド。 */
    private static byte[] joinGamePayload763(boolean hasDeath, int portalCooldown) {
        Writer writer = new Writer();
        writer.writeInt(7); // entityId
        writer.writeBoolean(false); // hardcore
        writer.writeByte(0); // gameMode
        writer.writeByte(-1); // previousGameMode
        PacketIO.writeStringList(writer, List.of("minecraft:overworld", "minecraft:the_nether"));
        Tag.CompoundTag codec = new Tag.CompoundTag(Map.of(
                "minecraft:dimension_type", new Tag.CompoundTag(Map.of(
                        "type", new Tag.StringTag("minecraft:dimension_type"),
                        "value", new Tag.ListTag(Nbt.COMPOUND, List.of())))));
        writer.writeNamedNbt("", codec);
        writer.writeString("minecraft:overworld"); // worldType
        writer.writeString("minecraft:overworld"); // worldName
        writer.writeLong(12345L); // hashedSeed
        writer.writeVarInt(20); // maxPlayers
        writer.writeVarInt(10); // viewDistance
        writer.writeVarInt(10); // simulationDistance
        writer.writeBoolean(false); // reducedDebug
        writer.writeBoolean(true); // enableRespawnScreen
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        writer.writeBoolean(hasDeath);
        if (hasDeath) {
            writer.writeString("minecraft:the_nether");
            writer.writePositionRaw(0x123456789AL);
        }
        writer.writeVarInt(portalCooldown); // portalCooldown（763のみ）
        return writer.bytes();
    }

    /** 762形式の Join Game ペイロード（portalCooldown 無し）。 */
    private static byte[] joinGamePayload762(boolean hasDeath) {
        Writer writer = new Writer();
        writer.writeInt(7);
        writer.writeBoolean(false);
        writer.writeByte(0);
        writer.writeByte(-1);
        PacketIO.writeStringList(writer, List.of("minecraft:overworld", "minecraft:the_nether"));
        Tag.CompoundTag codec = new Tag.CompoundTag(Map.of(
                "minecraft:dimension_type", new Tag.CompoundTag(Map.of(
                        "type", new Tag.StringTag("minecraft:dimension_type"),
                        "value", new Tag.ListTag(Nbt.COMPOUND, List.of())))));
        writer.writeNamedNbt("", codec);
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(12345L);
        writer.writeVarInt(20);
        writer.writeVarInt(10);
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(hasDeath);
        if (hasDeath) {
            writer.writeString("minecraft:the_nether");
            writer.writePositionRaw(0x123456789AL);
        }
        return writer.bytes();
    }

    // ---- Join Game ----

    @Test
    void joinGame763To762DropsPortalCooldown() {
        JoinGame763 join = PlayTranslator.parseJoinGame763(joinGamePayload763(false, 30));
        assertEquals(30, join.portalCooldown());
        assertEquals(7, join.entityId());
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"), join.worldNames());

        byte[] translated = PlayTranslator.translateJoinGame763To762(join);
        JoinGame763 back = PlayTranslator.parseJoinGame762(translated);
        assertEquals(0, back.portalCooldown()); // 762には portalCooldown が無い
        assertEquals(7, back.entityId());
        assertEquals("minecraft:overworld", back.worldName());
        assertEquals(12345L, back.hashedSeed());
        assertEquals(20, back.maxPlayers());
        assertFalse(back.hasDeath());
        // codec はそのまま写る。
        assertEquals(join.codec(), back.codec());
    }

    @Test
    void joinGame763To762KeepsDeathLocation() {
        JoinGame763 join = PlayTranslator.parseJoinGame763(joinGamePayload763(true, 0));
        byte[] translated = PlayTranslator.translateJoinGame763To762(join);
        JoinGame763 back = PlayTranslator.parseJoinGame762(translated);
        assertTrue(back.hasDeath());
        assertEquals("minecraft:the_nether", back.deathDimension());
        assertEquals(0x123456789AL, back.deathLocation());
    }

    @Test
    void joinGame762RoundTripsThrough763() {
        JoinGame763 parsed = PlayTranslator.parseJoinGame762(joinGamePayload762(false));
        assertEquals(0, parsed.portalCooldown());
        byte[] translated = PlayTranslator.translateJoinGame763To762(parsed);
        // 762形式のまま読める（portalCooldown は書かれない）。
        JoinGame763 back = PlayTranslator.parseJoinGame762(translated);
        assertEquals(0, back.portalCooldown());
        assertEquals(7, back.entityId());
        assertEquals(12345L, back.hashedSeed());
    }

    // ---- スロット ----

    @Test
    void slot763To762AndBack() {
        Writer writer = new Writer();
        writeSlot(writer, 1236, 2); // archer_pottery_sherd（763）
        Writer out = new Writer();
        PlayTranslator.translateSlot763To762(new Reader(writer.bytes()), out);
        assertArrayEquals(new int[]{1224, 2}, readSlotHead(new Reader(out.bytes())));

        Writer writer762 = new Writer();
        writeSlot(writer762, 1224, 2);
        Writer out762 = new Writer();
        PlayTranslator.translateSlot762To763(new Reader(writer762.bytes()), out762);
        assertArrayEquals(new int[]{1236, 2}, readSlotHead(new Reader(out762.bytes())));
    }

    @Test
    void slotAbsentPassesThrough() {
        Writer writer = new Writer();
        writer.writeBoolean(false); // スロット無し
        Writer out = new Writer();
        PlayTranslator.translateSlot763To762(new Reader(writer.bytes()), out);
        assertFalse(new Reader(out.bytes()).readBoolean());
    }

    // ---- Respawn ----

    @Test
    void respawn763To762DropsPortalCooldown() {
        Writer writer = new Writer();
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0); // gamemode
        writer.writeByte(-1); // previousGamemode
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        writer.writeBoolean(true); // copyMetadata
        writer.writeBoolean(true); // hasDeath
        writer.writeString("minecraft:the_nether");
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(15); // portalCooldown（763のみ）
        byte[] out = ExtraPlayTranslator.translateRespawn763To762(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals(99L, reader.readLong());
        assertEquals(0, reader.readByte());
        assertEquals(255, reader.readUnsignedByte()); // previousGamemode -1
        assertEquals(false, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        assertEquals("minecraft:the_nether", reader.readString());
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(0, reader.remaining()); // portalCooldown は落とす
    }

    @Test
    void respawn762To763AddsPortalCooldownZero() {
        Writer writer = new Writer();
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false); // hasDeath なし
        byte[] out = ExtraPlayTranslator.translateRespawn762To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals(99L, reader.readLong());
        assertEquals(0, reader.readByte());
        assertEquals(255, reader.readUnsignedByte());
        assertEquals(false, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readVarInt()); // portalCooldown=0 を足す
        assertEquals(0, reader.remaining());
    }

    // ---- 戦闘イベント ----

    @Test
    void deathCombatEvent() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // playerId
        writer.writeString("{\"text\":\"died\"}");
        byte[] out = ExtraPlayTranslator.translateDeathCombatEvent763To762(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(7, reader.readInt()); // entityId は playerId で代用
        assertEquals("{\"text\":\"died\"}", reader.readString());
        assertEquals(0, reader.remaining());

        byte[] back = ExtraPlayTranslator.translateDeathCombatEvent762To763(out);
        Reader reader762 = new Reader(back);
        assertEquals(7, reader762.readVarInt());
        assertEquals("{\"text\":\"died\"}", reader762.readString());
        assertEquals(0, reader762.remaining());
    }

    @Test
    void endCombatEvent() {
        Writer writer = new Writer();
        writer.writeVarInt(120); // duration
        byte[] out = ExtraPlayTranslator.translateEndCombatEvent763To762(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(120, reader.readVarInt());
        assertEquals(-1, reader.readInt()); // entityId は -1 で代用
        assertEquals(0, reader.remaining());

        byte[] back = ExtraPlayTranslator.translateEndCombatEvent762To763(out);
        Reader reader762 = new Reader(back);
        assertEquals(120, reader762.readVarInt());
        assertEquals(0, reader762.remaining());
    }

    // ---- Advancements ----

    /** 763形式の Advancements ペイロード（進捗1件・表示あり・sendsTelemetryData=true）。 */
    private static byte[] advancementsPayload763(boolean withDisplay, boolean sendsTelemetry) {
        Writer writer = new Writer();
        writer.writeBoolean(false); // reset
        writer.writeVarInt(1); // mappingCount
        writer.writeString("minecraft:story/root"); // key
        writer.writeBoolean(false); // hasParent なし
        writer.writeBoolean(withDisplay); // hasDisplay
        if (withDisplay) {
            writer.writeString("{\"text\":\"Title\"}"); // title
            writer.writeString("{\"text\":\"Desc\"}"); // description
            writeSlot(writer, 1236, 1); // icon（archer sherd）
            writer.writeVarInt(0); // frameType
            writer.writeInt(0x01); // flags（backgroundTexture あり）
            writer.writeString("minecraft:textures/gui/advancements/backgrounds/adventure.png");
            writer.writeFloat(0.5f); // xCord
            writer.writeFloat(0.25f); // yCord
        }
        writer.writeVarInt(2); // criteriaCount
        writer.writeString("a");
        writer.writeString("b");
        writer.writeVarInt(1); // requirementCount
        PacketIO.writeStringList(writer, List.of("a"));
        writer.writeBoolean(sendsTelemetry); // sendsTelemetryData（763のみ）
        PacketIO.writeStringList(writer, List.of("minecraft:story/removed")); // 削除一覧
        writer.writeVarInt(1); // progressCount
        writer.writeString("minecraft:story/root"); // key
        writer.writeVarInt(1); // criterionCount
        writer.writeString("a"); // criterionIdentifier
        writer.writeBoolean(true); // hasProgress
        writer.writeLong(1700000000000L); // criterionProgress
        return writer.bytes();
    }

    @Test
    void advancements763To762DropsSendsTelemetryData() {
        byte[] out = ExtraPlayTranslator.translateAdvancements763To762(
                advancementsPayload763(true, true));
        Reader reader = new Reader(out);
        assertEquals(false, reader.readBoolean()); // reset
        assertEquals(1, reader.readVarInt()); // mappingCount
        assertEquals("minecraft:story/root", reader.readString());
        assertEquals(false, reader.readBoolean()); // hasParent
        assertEquals(true, reader.readBoolean()); // hasDisplay
        assertEquals("{\"text\":\"Title\"}", reader.readString());
        assertEquals("{\"text\":\"Desc\"}", reader.readString());
        assertArrayEquals(new int[]{1224, 1}, readSlotHead(reader)); // icon は762向け
        assertEquals(0, reader.readVarInt()); // frameType
        assertEquals(0x01, reader.readInt()); // flags
        assertEquals("minecraft:textures/gui/advancements/backgrounds/adventure.png",
                reader.readString());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0.25f, reader.readFloat());
        assertEquals(2, reader.readVarInt()); // criteriaCount
        assertEquals("a", reader.readString());
        assertEquals("b", reader.readString());
        assertEquals(1, reader.readVarInt()); // requirementCount
        assertEquals(List.of("a"), PacketIO.readStringList(reader));
        // sendsTelemetryData は読んで落とされている（次は削除一覧）。
        assertEquals(List.of("minecraft:story/removed"), PacketIO.readStringList(reader));
        assertEquals(1, reader.readVarInt()); // progressCount
        assertEquals("minecraft:story/root", reader.readString());
        assertEquals(1, reader.readVarInt());
        assertEquals("a", reader.readString());
        assertEquals(true, reader.readBoolean());
        assertEquals(1700000000000L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void advancements762To763AddsSendsTelemetryDataFalse() {
        // 762形式のペイロードを作る（sendsTelemetryData 無し）。
        Writer writer = new Writer();
        writer.writeBoolean(false);
        writer.writeVarInt(1);
        writer.writeString("minecraft:story/root");
        writer.writeBoolean(false);
        writer.writeBoolean(false); // hasDisplay なし
        writer.writeVarInt(0); // criteriaCount
        writer.writeVarInt(0); // requirementCount
        PacketIO.writeStringList(writer, List.of());
        writer.writeVarInt(0); // progressCount
        byte[] out = ExtraPlayTranslator.translateAdvancements762To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(false, reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals("minecraft:story/root", reader.readString());
        assertEquals(false, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean()); // sendsTelemetryData=false を足す
        assertEquals(List.of(), PacketIO.readStringList(reader));
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }
}
