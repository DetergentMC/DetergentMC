package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 担当3：Respawn／Boss Bar／Teams／Tab Complete／Explosion／
 * Advancements の765→763変換の検証。765／763のフィールド順は
 * minecraft-data の protocol.json（1.20.3／1.20）から取った。
 */
class ExtraPlayTranslatorTest {

    private static Tag text(String value) {
        return new Tag.CompoundTag(Map.of("text", new Tag.StringTag(value)));
    }

    // ------------------------------------------------------------------
    // Respawn
    // ------------------------------------------------------------------

    /** 765の Respawn ペイロードを作る（copyMetadata は末尾）。 */
    private static byte[] respawn765(String dimension, String worldName, long seed,
            int gamemode, int previousGamemode, boolean debug, boolean flat,
            boolean hasDeath, String deathDimension, long deathLocation,
            int portalCooldown, boolean copyMetadata) {
        Writer writer = new Writer();
        writer.writeString(dimension);
        writer.writeString(worldName);
        writer.writeLong(seed);
        writer.writeByte(gamemode);
        writer.writeByte(previousGamemode);
        writer.writeBoolean(debug);
        writer.writeBoolean(flat);
        writer.writeBoolean(hasDeath);
        if (hasDeath) {
            writer.writeString(deathDimension);
            writer.writePositionRaw(deathLocation);
        }
        writer.writeVarInt(portalCooldown);
        writer.writeBoolean(copyMetadata);
        return writer.bytes();
    }

    /** 763の Respawn ペイロードを読む（copyMetadata は death の前）。 */
    private record Respawn763(String dimension, String worldName, long hashedSeed,
            int gamemode, int previousGamemode, boolean isDebug, boolean isFlat,
            boolean copyMetadata, boolean hasDeath, String deathDimension,
            long deathLocation, int portalCooldown) {
    }

    private static Respawn763 parseRespawn763(byte[] payload) {
        Reader reader = new Reader(payload);
        String dimension = reader.readString();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gamemode = reader.readByte();
        int previousGamemode = reader.readUnsignedByte();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        boolean copyMetadata = reader.readBoolean();
        boolean hasDeath = reader.readBoolean();
        String deathDimension = null;
        long deathLocation = 0;
        if (hasDeath) {
            deathDimension = reader.readString();
            deathLocation = reader.readPositionRaw();
        }
        int portalCooldown = reader.readVarInt();
        assertEquals(0, reader.remaining(), "763の Respawn に余分なバイトがある");
        return new Respawn763(dimension, worldName, hashedSeed, gamemode,
                previousGamemode, isDebug, isFlat, copyMetadata, hasDeath,
                deathDimension, deathLocation, portalCooldown);
    }

    @Test
    void respawnMovesCopyMetadataBeforeDeath() {
        byte[] payload765 = respawn765("minecraft:the_nether", "minecraft:the_nether",
                12345L, 0, -1, false, false,
                true, "minecraft:overworld", 987654321L, 40, true);
        Respawn763 parsed = parseRespawn763(
                ExtraPlayTranslator.translateRespawn765To763(payload765));
        assertEquals("minecraft:the_nether", parsed.dimension());
        assertEquals("minecraft:the_nether", parsed.worldName());
        assertEquals(12345L, parsed.hashedSeed());
        assertEquals(0, parsed.gamemode());
        assertEquals(255, parsed.previousGamemode()); // -1 は u8 で 255
        assertFalse(parsed.isDebug());
        assertFalse(parsed.isFlat());
        assertTrue(parsed.copyMetadata());
        assertTrue(parsed.hasDeath());
        assertEquals("minecraft:overworld", parsed.deathDimension());
        assertEquals(987654321L, parsed.deathLocation());
        assertEquals(40, parsed.portalCooldown());
    }

    @Test
    void respawnWithoutDeathAndWithoutCopyMetadata() {
        byte[] payload765 = respawn765("minecraft:overworld", "minecraft:overworld",
                99L, 1, 0, true, true,
                false, null, 0, 0, false);
        Respawn763 parsed = parseRespawn763(
                ExtraPlayTranslator.translateRespawn765To763(payload765));
        assertEquals("minecraft:overworld", parsed.dimension());
        assertEquals(99L, parsed.hashedSeed());
        assertEquals(1, parsed.gamemode());
        assertEquals(0, parsed.previousGamemode());
        assertTrue(parsed.isDebug());
        assertTrue(parsed.isFlat());
        assertFalse(parsed.copyMetadata());
        assertFalse(parsed.hasDeath());
        assertNull(parsed.deathDimension());
        assertEquals(0, parsed.portalCooldown());
    }

    @Test
    void respawnWithTrailingBytesThrows() {
        byte[] payload765 = respawn765("minecraft:overworld", "minecraft:overworld",
                99L, 0, -1, false, false,
                false, null, 0, 0, false);
        byte[] broken = new byte[payload765.length + 1];
        System.arraycopy(payload765, 0, broken, 0, payload765.length);
        broken[broken.length - 1] = 0x7F;
        assertThrows(IllegalArgumentException.class,
                () -> ExtraPlayTranslator.translateRespawn765To763(broken));
    }

    // ------------------------------------------------------------------
    // Boss Bar
    // ------------------------------------------------------------------

    @Test
    void bossBarAddConvertsTitleToJson() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Writer writer = new Writer();
        writer.writeUuid(uuid);
        writer.writeVarInt(0); // 追加
        writer.writeAnonymousNbt(text("Ender Dragon"));
        writer.writeFloat(0.75f);
        writer.writeVarInt(5); // 色
        writer.writeVarInt(2); // 分割
        writer.writeByte(0x03); // フラグ
        byte[] converted = ExtraPlayTranslator.translateBossBar765To763(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals(uuid, reader.readUuid());
        assertEquals(0, reader.readVarInt());
        assertEquals("{\"text\":\"Ender Dragon\"}", reader.readString());
        assertEquals(0.75f, reader.readFloat());
        assertEquals(5, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(0x03, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void bossBarActionsOnlyCarryTheirOwnFields() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        // action 1＝削除：UUID＋action だけ。
        Writer remove = new Writer();
        remove.writeUuid(uuid);
        remove.writeVarInt(1);
        byte[] converted = ExtraPlayTranslator.translateBossBar765To763(remove.bytes());
        Reader reader = new Reader(converted);
        assertEquals(uuid, reader.readUuid());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.remaining());
        // action 2＝体力更新：UUID＋action＋体力。
        Writer health = new Writer();
        health.writeUuid(uuid);
        health.writeVarInt(2);
        health.writeFloat(0.25f);
        Reader healthReader = new Reader(
                ExtraPlayTranslator.translateBossBar765To763(health.bytes()));
        assertEquals(uuid, healthReader.readUuid());
        assertEquals(2, healthReader.readVarInt());
        assertEquals(0.25f, healthReader.readFloat());
        assertEquals(0, healthReader.remaining());
        // action 3＝タイトル更新：UUID＋action＋タイトル（JSON 文字列）。
        Writer title = new Writer();
        title.writeUuid(uuid);
        title.writeVarInt(3);
        title.writeAnonymousNbt(text("Wither"));
        Reader titleReader = new Reader(
                ExtraPlayTranslator.translateBossBar765To763(title.bytes()));
        assertEquals(uuid, titleReader.readUuid());
        assertEquals(3, titleReader.readVarInt());
        assertEquals("{\"text\":\"Wither\"}", titleReader.readString());
        assertEquals(0, titleReader.remaining());
    }

    // ------------------------------------------------------------------
    // Teams
    // ------------------------------------------------------------------

    @Test
    void teamsCreateConvertsNamePrefixSuffixToJson() {
        Writer writer = new Writer();
        writer.writeString("red");
        writer.writeByte(0); // 作成
        writer.writeAnonymousNbt(text("Red Team"));
        writer.writeByte(1); // friendlyFire（i8）
        writer.writeString("always");
        writer.writeString("always");
        writer.writeVarInt(12); // formatting
        writer.writeAnonymousNbt(text("[RED] "));
        writer.writeAnonymousNbt(text("!"));
        PacketIO.writeStringList(writer, List.of("Steve", "Alex"));
        byte[] converted = ExtraPlayTranslator.translateTeams765To763(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals("red", reader.readString());
        assertEquals(0, reader.readByte());
        assertEquals("{\"text\":\"Red Team\"}", reader.readString());
        assertEquals(1, reader.readByte());
        assertEquals("always", reader.readString());
        assertEquals("always", reader.readString());
        assertEquals(12, reader.readVarInt());
        assertEquals("{\"text\":\"[RED] \"}", reader.readString());
        assertEquals("{\"text\":\"!\"}", reader.readString());
        assertEquals(List.of("Steve", "Alex"), PacketIO.readStringList(reader));
        assertEquals(0, reader.remaining());
    }

    @Test
    void teamsRemoveCarriesNoInfo() {
        Writer writer = new Writer();
        writer.writeString("red");
        writer.writeByte(1); // 削除
        byte[] converted = ExtraPlayTranslator.translateTeams765To763(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals("red", reader.readString());
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void teamsAddPlayersCarriesOnlyPlayers() {
        Writer writer = new Writer();
        writer.writeString("blue");
        writer.writeByte(3); // プレイヤー追加
        PacketIO.writeStringList(writer, List.of("Steve"));
        Reader reader = new Reader(
                ExtraPlayTranslator.translateTeams765To763(writer.bytes()));
        assertEquals("blue", reader.readString());
        assertEquals(3, reader.readByte());
        assertEquals(List.of("Steve"), PacketIO.readStringList(reader));
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // Tab Complete
    // ------------------------------------------------------------------

    @Test
    void tabCompleteConvertsTooltipsToJson() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // transactionId
        writer.writeVarInt(2); // start
        writer.writeVarInt(3); // length
        writer.writeVarInt(2); // 候補2件
        writer.writeString("help");
        writer.writeBoolean(true);
        writer.writeAnonymousNbt(text("Shows help"));
        writer.writeString("hello");
        writer.writeBoolean(false);
        byte[] converted = ExtraPlayTranslator.translateTabComplete765To763(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals(7, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals("help", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"Shows help\"}", reader.readString());
        assertEquals("hello", reader.readString());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void tabCompleteWithNoMatches() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        byte[] converted = ExtraPlayTranslator.translateTabComplete765To763(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // Explosion
    // ------------------------------------------------------------------

    /** 765の Explosion の共通部分を作る。末尾は呼び出し側で足す。 */
    private static Writer explosionPrefix() {
        Writer writer = new Writer();
        writer.writeDouble(1.0); // x
        writer.writeDouble(2.0); // y
        writer.writeDouble(3.0); // z
        writer.writeFloat(4.0f); // radius
        writer.writeVarInt(2); // 影響ブロック2件
        writer.writeByte(1);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeByte(0);
        writer.writeByte(1);
        writer.writeByte(0);
        writer.writeFloat(0.1f); // playerMotionX
        writer.writeFloat(0.2f); // playerMotionY
        writer.writeFloat(0.3f); // playerMotionZ
        return writer;
    }

    /** 763の Explosion の期待ペイロード（共通部分だけ）。 */
    private static byte[] explosion763Expected() {
        return explosionPrefix().bytes();
    }

    @Test
    void explosionKeepsCommonPartAndDrops765OnlyTail() {
        Writer writer = explosionPrefix();
        writer.writeVarInt(1); // block_interaction_type＝destroy
        writer.writeVarInt(22); // small：explosion（データ無し）
        writer.writeVarInt(21); // large：explosion_emitter（データ無し）
        writer.writeVarInt(5); // sound：レジストリID（インライン無し）
        assertArrayEquals(explosion763Expected(),
                ExtraPlayTranslator.translateExplosion765To763(writer.bytes()));
    }

    @Test
    void explosionSkipsParticlesWithDataAndInlineSound() {
        Writer writer = explosionPrefix();
        writer.writeVarInt(0); // block_interaction_type＝keep
        writer.writeVarInt(14); // small：dust
        writer.writeFloat(1.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(1.0f);
        writer.writeVarInt(2); // large：block
        writer.writeVarInt(123); // blockState
        writer.writeVarInt(0); // sound：インライン定義
        writer.writeString("minecraft:entity.generic.explode");
        writer.writeBoolean(true);
        writer.writeFloat(2.0f);
        assertArrayEquals(explosion763Expected(),
                ExtraPlayTranslator.translateExplosion765To763(writer.bytes()));
    }

    @Test
    void explosionSkipsItemAndVibrationParticles() {
        Writer writer = explosionPrefix();
        writer.writeVarInt(2); // block_interaction_type＝destroy_with_decay
        writer.writeVarInt(42); // small：item（スロット付き）
        writer.writeBoolean(true);
        writer.writeVarInt(100); // itemId
        writer.writeByte(1); // count
        writer.writeBoolean(false); // NBT無し
        writer.writeVarInt(43); // large：vibration
        writer.writeString("minecraft:entity");
        writer.writeVarInt(9); // entityId
        writer.writeVarInt(10); // entityEyeHeight
        writer.writeVarInt(11); // destination entityId
        writer.writeVarInt(20); // ticks
        writer.writeVarInt(7); // sound：レジストリID
        assertArrayEquals(explosion763Expected(),
                ExtraPlayTranslator.translateExplosion765To763(writer.bytes()));
    }

    @Test
    void explosionTruncatedThrows() {
        Writer writer = explosionPrefix();
        writer.writeVarInt(1);
        writer.writeVarInt(22);
        // large particle と sound が欠けている。
        assertThrows(IllegalArgumentException.class,
                () -> ExtraPlayTranslator.translateExplosion765To763(writer.bytes()));
    }

    // ------------------------------------------------------------------
    // Advancements
    // ------------------------------------------------------------------

    /** 765の Advancements ペイロードを作る（表示あり1件＋進捗1件）。 */
    private static byte[] advancements765() {
        Writer icon = new Writer();
        icon.writeBoolean(true); // icon：あり
        icon.writeVarInt(1); // itemId
        icon.writeByte(1); // count
        icon.writeByte(0); // NBT無し（TAG_End。bool ではない）
        return advancements765WithIcon(icon.bytes());
    }

    /** 765の Advancements ペイロードを作る（アイコンを指定）。 */
    private static byte[] advancements765WithIcon(byte[] iconBytes) {
        Writer writer = new Writer();
        writer.writeBoolean(true); // reset
        writer.writeVarInt(1); // 進捗1件
        writer.writeString("minecraft:story/root");
        writer.writeBoolean(false); // parentId 無し
        writer.writeBoolean(true); // displayData あり
        writer.writeAnonymousNbt(text("Stone Age"));
        writer.writeAnonymousNbt(text("Mine stone with your new pickaxe"));
        writer.writeBytes(iconBytes); // icon：スロット
        writer.writeVarInt(0); // frameType
        writer.writeInt(0x01); // flags：背景テクスチャあり
        writer.writeString("minecraft:textures/gui/advancements/backgrounds/stone.png");
        writer.writeFloat(0.5f); // x
        writer.writeFloat(1.5f); // y
        writer.writeVarInt(1); // requirements 1組
        PacketIO.writeStringList(writer, List.of("minecraft:story/mine_stone"));
        writer.writeBoolean(true); // sendsTelemetryData
        PacketIO.writeStringList(writer, List.of("minecraft:story/old")); // identifiers
        writer.writeVarInt(1); // progress 1件
        writer.writeString("minecraft:story/root");
        writer.writeVarInt(1); // 基準1件
        writer.writeString("minecraft:story/mine_stone");
        writer.writeBoolean(true);
        writer.writeLong(12345L);
        return writer.bytes();
    }

    @Test
    void advancementsConvertsDisplayAndAddsEmptyCriteria() {
        Reader reader = new Reader(
                ExtraPlayTranslator.translateAdvancements765To763(advancements765()));
        assertTrue(reader.readBoolean()); // reset
        assertEquals(1, reader.readVarInt());
        assertEquals("minecraft:story/root", reader.readString());
        assertFalse(reader.readBoolean()); // parentId 無し
        assertTrue(reader.readBoolean()); // displayData あり
        assertEquals("{\"text\":\"Stone Age\"}", reader.readString());
        assertEquals("{\"text\":\"Mine stone with your new pickaxe\"}", reader.readString());
        // icon（スロットはそのまま）
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.readVarInt()); // frameType
        assertEquals(0x01, reader.readInt()); // flags
        assertEquals("minecraft:textures/gui/advancements/backgrounds/stone.png",
                reader.readString());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(1.5f, reader.readFloat());
        // 763では criteria が必須のため空配列が入る。
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // requirements 1組
        assertEquals(List.of("minecraft:story/mine_stone"),
                PacketIO.readStringList(reader));
        assertTrue(reader.readBoolean()); // sendsTelemetryData
        assertEquals(List.of("minecraft:story/old"), PacketIO.readStringList(reader));
        assertEquals(1, reader.readVarInt()); // progress 1件
        assertEquals("minecraft:story/root", reader.readString());
        assertEquals(1, reader.readVarInt());
        assertEquals("minecraft:story/mine_stone", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals(12345L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void advancementsWithoutDisplayAndEmpty() {
        Writer writer = new Writer();
        writer.writeBoolean(false); // reset
        writer.writeVarInt(1);
        writer.writeString("minecraft:story/root");
        writer.writeBoolean(true); // parentId あり
        writer.writeString("minecraft:story/parent");
        writer.writeBoolean(false); // displayData 無し
        writer.writeVarInt(0); // requirements 無し
        writer.writeBoolean(false); // sendsTelemetryData
        PacketIO.writeStringList(writer, List.of());
        writer.writeVarInt(0); // progress 無し
        Reader reader = new Reader(
                ExtraPlayTranslator.translateAdvancements765To763(writer.bytes()));
        assertFalse(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals("minecraft:story/root", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals("minecraft:story/parent", reader.readString());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.readVarInt()); // criteria（空）
        assertEquals(0, reader.readVarInt()); // requirements 無し
        assertFalse(reader.readBoolean());
        assertEquals(List.of(), PacketIO.readStringList(reader));
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // スロット（skipSlot／copySlotRaw）
    // ------------------------------------------------------------------

    /**
     * NBT 付きスロットを読み飛ばす。ワイヤ形式では NBT の前に bool は無く、
     * 型バイト（0x0A）から NBT blob が始まる。bool 付きとして読むと
     * 1バイトずれて後続が誤読される（修正前のバグ）。
     */
    @Test
    void skipSlotWithNbtConsumesExactlyOneSlot() {
        Writer writer = new Writer();
        writer.writeBoolean(true); // あり
        writer.writeVarInt(5); // itemId
        writer.writeByte(3); // count
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of(
                "Unbreakable", new Tag.IntTag(1)))); // NBT（bool なし）
        writer.writeVarInt(99); // 後続の目印
        Reader reader = new Reader(writer.bytes());
        ExtraPlayTranslator.skipSlot(reader);
        assertEquals(99, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    /** NBT 無し（0x00）と空スロットの読み飛ばし。 */
    @Test
    void skipSlotWithoutNbtAndEmptySlot() {
        Writer writer = new Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(1);
        writer.writeByte(1);
        writer.writeByte(0); // NBT 無し（TAG_End）
        writer.writeVarInt(7); // 後続の目印
        Reader reader = new Reader(writer.bytes());
        ExtraPlayTranslator.skipSlot(reader);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.remaining());

        Reader empty = new Reader(new byte[]{0, 42});
        ExtraPlayTranslator.skipSlot(empty);
        assertEquals(42, empty.readByte());
        assertEquals(0, empty.remaining());
    }

    /**
     * NBT 付きアイコンの Advancements：アイコン（スロット）はバイト列の
     * まま写され、後続（frameType 以降）がずれずに読めること。
     */
    @Test
    void advancementsIconWithNbtIsCopiedVerbatim() {
        Writer iconWriter = new Writer();
        iconWriter.writeBoolean(true);
        iconWriter.writeVarInt(1);
        iconWriter.writeByte(1);
        iconWriter.writeAnonymousNbt(new Tag.CompoundTag(Map.of(
                "display", new Tag.CompoundTag(Map.of(
                        "Name", new Tag.StringTag("{\"text\":\"Star\"}"))))));
        byte[] iconBytes = iconWriter.bytes();
        byte[] translated = ExtraPlayTranslator.translateAdvancements765To763(
                advancements765WithIcon(iconBytes));
        Reader reader = new Reader(translated);
        assertTrue(reader.readBoolean()); // reset
        assertEquals(1, reader.readVarInt());
        assertEquals("minecraft:story/root", reader.readString());
        assertFalse(reader.readBoolean()); // parentId 無し
        assertTrue(reader.readBoolean()); // displayData あり
        assertEquals("{\"text\":\"Stone Age\"}", reader.readString());
        assertEquals("{\"text\":\"Mine stone with your new pickaxe\"}", reader.readString());
        int iconStart = reader.position();
        ExtraPlayTranslator.skipSlot(reader);
        assertArrayEquals(iconBytes,
                Arrays.copyOfRange(translated, iconStart, reader.position()));
        assertEquals(0, reader.readVarInt()); // frameType（ずれていないこと）
        assertEquals(0x01, reader.readInt()); // flags
    }
}
