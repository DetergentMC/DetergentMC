package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 1.21.5（プロトコル770）のチャンク・スロット・パーティクル・メタデータ変換のテスト。
 *
 * <p>769→770の破壊的変更（Minecraft Wikiの1.21.5、新パケット／コンポーネント表に準拠）：
 * heightmapがNBTから直接の配列に変わった、データコンポーネント種別が67→96に増えた
 * （hide_tooltip／hide_additional_tooltipがtooltip_displayに統合、enchantments等の末尾
 * showTooltipが消えた、新種別weapon／blocks_attacks／break_sound等は770専用）、
 * エンティティメタデータのvariant系の種別番号がずれた（wolf_variantはholder→varint、
 * cow／pig／chicken／wolf_sound_variantは770のみ）、Spawn Entity Experience Orb
 * （769の0x02）が770では通常のSpawn Entity（type 47）に統合された、Window Clickは
 * HashedSlotのハッシュ算法が未検証のため変換できず落とす（ProxySession側で落とす）。
 */
class ChunkTranslator770Test {

    /** 高さマップ（MOTION_BLOCKING／WORLD_SURFACE 各2要素。実チャンクは37要素だが翻訳子は長さに依存しない）。 */
    private static Tag heightmaps() {
        return new Tag.CompoundTag(Map.of(
                "MOTION_BLOCKING", new Tag.LongArrayTag(new long[]{11L, 22L}),
                "WORLD_SURFACE", new Tag.LongArrayTag(new long[]{44L, 55L})));
    }

    /** 間接パレットのセクション1個（ブロック＋バイオームともに間接パレット）。769形式（Data Array に長さ varint 付き）。 */
    private static byte[] indirectSectionWithBiomePalette(int[] blockPalette, int[] biomePalette) {
        Writer writer = new Writer();
        writer.writeShort(1); // ブロック数
        writer.writeUnsignedByte(4); // BPE（間接）
        writer.writeVarInt(blockPalette.length);
        for (int value : blockPalette) {
            writer.writeVarInt(value);
        }
        writer.writeVarInt(256); // Data Array：4096エントリ×4bit＝256 long
        for (int i = 0; i < 256; i++) {
            writer.writeLong(0L);
        }
        writer.writeUnsignedByte(2); // バイオーム BPE（間接）
        writer.writeVarInt(biomePalette.length);
        for (int value : biomePalette) {
            writer.writeVarInt(value);
        }
        writer.writeVarInt(2); // Data Array：64エントリ×2bit＝128bit＝2 long
        for (int i = 0; i < 2; i++) {
            writer.writeLong(0L);
        }
        return writer.bytes();
    }

    /** 間接パレットのセクション1個。770形式（Data Array に長さ varint が無い。実バニラ1.21.5のチャンクで確認）。 */
    private static byte[] indirectSectionWithBiomePalette770(int[] blockPalette, int[] biomePalette) {
        Writer writer = new Writer();
        writer.writeShort(1); // ブロック数
        writer.writeUnsignedByte(4); // BPE（間接）
        writer.writeVarInt(blockPalette.length);
        for (int value : blockPalette) {
            writer.writeVarInt(value);
        }
        // 770には Data Array の長さ varint が無い。256 long をそのまま書く。
        for (int i = 0; i < 256; i++) {
            writer.writeLong(0L);
        }
        writer.writeUnsignedByte(2); // バイオーム BPE（間接）
        writer.writeVarInt(biomePalette.length);
        for (int value : biomePalette) {
            writer.writeVarInt(value);
        }
        // 770には Data Array の長さ varint が無い。2 long（64エントリ×2bit）をそのまま書く。
        for (int i = 0; i < 2; i++) {
            writer.writeLong(0L);
        }
        return writer.bytes();
    }

    /** セクションのブロックパレットだけ読み、ブロック状態IDの配列を返す。 */
    private static int[] readIndirectPalette(byte[] sections) {
        Reader section = new Reader(sections);
        section.readShort();
        section.readUnsignedByte();
        int paletteSize = section.readVarInt();
        int[] palette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            palette[i] = section.readVarInt();
        }
        return palette;
    }

    /** セクションのバイオームパレットを読む（単一の場合はその1件を返す）。769形式（Data Array に長さ varint 付き）。 */
    private static int[] readBiomePalette(byte[] sections) {
        Reader section = new Reader(sections);
        section.readShort();
        section.readUnsignedByte();
        int paletteSize = section.readVarInt();
        for (int i = 0; i < paletteSize; i++) {
            section.readVarInt();
        }
        int dataLength = section.readVarInt();
        for (int i = 0; i < dataLength; i++) {
            section.readLong();
        }
        int bits = section.readUnsignedByte(); // バイオーム BPE
        if (bits == 0) {
            return new int[]{section.readVarInt()};
        }
        int biomeSize = section.readVarInt();
        int[] palette = new int[biomeSize];
        for (int i = 0; i < biomeSize; i++) {
            palette[i] = section.readVarInt();
        }
        return palette;
    }

    /** セクションのバイオームパレットを読む。770形式（Data Array に長さ varint が無い。長さは BPE から計算する）。 */
    private static int[] readBiomePalette770(byte[] sections) {
        Reader section = new Reader(sections);
        section.readShort();
        int blockBits = section.readUnsignedByte();
        int paletteSize = section.readVarInt();
        for (int i = 0; i < paletteSize; i++) {
            section.readVarInt();
        }
        int perLong = 64 / blockBits;
        int blockDataLength = (4096 + perLong - 1) / perLong;
        for (int i = 0; i < blockDataLength; i++) {
            section.readLong();
        }
        int bits = section.readUnsignedByte(); // バイオーム BPE
        if (bits == 0) {
            return new int[]{section.readVarInt()};
        }
        int biomeSize = section.readVarInt();
        int[] palette = new int[biomeSize];
        for (int i = 0; i < biomeSize; i++) {
            palette[i] = section.readVarInt();
        }
        return palette;
    }

    /** エンティティメタデータのパケット（entityId＋エントリ＋0xFF）。 */
    private static byte[] metadataPacket(int entityId, Writer entries) {
        Writer writer = new Writer();
        writer.writeVarInt(entityId);
        writer.writeBytes(entries.bytes());
        writer.writeUnsignedByte(0xFF);
        return writer.bytes();
    }

    /** World Particles ペイロード（769の形：longDistance の後に alwaysShow）。 */
    private static byte[] worldParticlesPacket769(boolean alwaysShow, int particleId, Writer particleData) {
        Writer writer = new Writer();
        writer.writeBoolean(false); // longDistance
        writer.writeBoolean(alwaysShow); // alwaysShow（769で追加）
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.5f); // offsetX
        writer.writeFloat(0.5f); // offsetY
        writer.writeFloat(0.5f); // offsetZ
        writer.writeFloat(1.0f); // speed
        writer.writeInt(4); // count
        writer.writeVarInt(particleId);
        writer.writeBytes(particleData.bytes());
        return writer.bytes();
    }

    /** World Particles ペイロード（770の形：769と同じ alwaysShow 付き）。 */
    private static byte[] worldParticlesPacket770(boolean alwaysShow, int particleId, Writer particleData) {
        return worldParticlesPacket769(alwaysShow, particleId, particleData);
    }

    /** World Particles ペイロードのヘッダを読み飛ばし、粒子IDを返す（769／770の形）。 */
    private static int readWorldParticlesId(Reader reader) {
        reader.readBoolean(); // longDistance
        reader.readBoolean(); // alwaysShow
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readInt();
        return reader.readVarInt();
    }

    /** Spawn Entity ペイロード（entityId＋UUID＋種別＋座標＋向き＋objectData＋速度）。 */
    private static byte[] spawnEntityPacket(int type, int objectData) {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeBytes(new byte[16]); // uuid
        writer.writeVarInt(type);
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeByte(0); // pitch
        writer.writeByte(0); // yaw
        writer.writeByte(0); // headPitch
        writer.writeVarInt(objectData);
        writer.writeShort(0); // velocityX
        writer.writeShort(0); // velocityY
        writer.writeShort(0); // velocityZ
        return writer.bytes();
    }

    /** Spawn Entity Experience Orb ペイロード（769の形：entityId＋座標＋count i16）。 */
    private static byte[] spawnEntityExperienceOrbPacket769(int count) {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeShort(count);
        return writer.bytes();
    }

    /** Set Slot ペイロード（windowId＋stateId＋slot＋アイテムID、成分なし）。 */
    private static byte[] setSlotPacket(int itemId) {
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1); // count
        writer.writeVarInt(itemId);
        writer.writeVarInt(0); // added 0
        writer.writeVarInt(0); // removed 0
        return writer.bytes();
    }

    /** スロットの先頭（個数＋アイテムID）を読み、追加コンポーネント数を返す。削除数も読み飛ばす。 */
    private static int[] readSlotHead(Reader reader) {
        int count = reader.readVarInt();
        int itemId = reader.readVarInt();
        int added = reader.readVarInt();
        int removed = reader.readVarInt();
        return new int[]{count, itemId, added, removed};
    }

    // ---- チャンク（heightmap） ----

    @Test
    void chunkData769To770HeightmapBecomesArray() {
        // 769の heightmap NBT（MOTION_BLOCKING＋WORLD_SURFACE）が
        // 770では直接の配列（type id＋long[]）になる。
        // type id はバニラ Heightmap.Types の宣言順を仮定
        // （0=WORLD_SURFACE_WG、1=WORLD_SURFACE、4=MOTION_BLOCKING）。
        Writer payload = new Writer();
        payload.writeInt(3);
        payload.writeInt(-2);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSectionWithBiomePalette(
                new int[]{0, 1, 27651, 27663}, new int[]{38, 39}));
        payload.writeVarInt(0);

        byte[] translated = ChunkTranslator.translateChunkData769To770(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        // 770の heightmap は配列。
        assertEquals(2, reader.readVarInt()); // 2件
        // type id 順（1=WORLD_SURFACE、4=MOTION_BLOCKING）で書かれる。
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(44L, reader.readLong());
        assertEquals(55L, reader.readLong());
        assertEquals(4, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(11L, reader.readLong());
        assertEquals(22L, reader.readLong());
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 27698, 27710}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette770(sections));
        assertEquals(0, reader.readVarInt()); // ブロックエンティティは0件
    }

    @Test
    void chunkData770To769HeightmapBecomesNbt() {
        // 770の heightmap 配列が769では NBT（compound、キーは種別名）に戻る。
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeVarInt(2); // heightmap は2件
        payload.writeVarInt(1); // WORLD_SURFACE
        payload.writeVarInt(2);
        payload.writeLong(44L);
        payload.writeLong(55L);
        payload.writeVarInt(4); // MOTION_BLOCKING
        payload.writeVarInt(2);
        payload.writeLong(11L);
        payload.writeLong(22L);
        payload.writeByteArray(indirectSectionWithBiomePalette770(
                new int[]{0, 1, 27698, 27710}, new int[]{38, 39}));
        payload.writeVarInt(0);

        byte[] translated = ChunkTranslator.translateChunkData770To769(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        Tag heightmaps = reader.readAnonymousNbt().tag();
        assertTrue(heightmaps instanceof Tag.CompoundTag);
        Map<String, Tag> entries = ((Tag.CompoundTag) heightmaps).value();
        assertArrayEquals(new long[]{44L, 55L},
                ((Tag.LongArrayTag) entries.get("WORLD_SURFACE")).value());
        assertArrayEquals(new long[]{11L, 22L},
                ((Tag.LongArrayTag) entries.get("MOTION_BLOCKING")).value());
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 27651, 27663}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette(sections));
        assertEquals(0, reader.readVarInt());
    }

    @Test
    void chunkData770To769TestBlockDropped() {
        // test_block（770の 45）と test_instance_block（770の 46）は769に無いため
        // ブロックエンティティごと落とす。trial_spawner（43）は残る。
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeVarInt(0); // heightmap は0件
        payload.writeByteArray(indirectSectionWithBiomePalette770(new int[]{0, 1}, new int[]{38}));
        payload.writeVarInt(3);
        payload.writeUnsignedByte(0x11);
        payload.writeShort(64);
        payload.writeVarInt(45); // test_block
        payload.writeByte(0); // NBT 無し
        payload.writeUnsignedByte(0x22);
        payload.writeShort(65);
        payload.writeVarInt(46); // test_instance_block
        payload.writeByte(0);
        payload.writeUnsignedByte(0x33);
        payload.writeShort(66);
        payload.writeVarInt(43); // trial_spawner
        payload.writeByte(0);

        byte[] translated = ChunkTranslator.translateChunkData770To769(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        // heightmap は0件だったため NBT は無し（0x00）。
        assertNull(reader.readAnonymousNbt().tag());
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1}, readIndirectPalette(sections));
        assertEquals(1, reader.readVarInt()); // ブロックエンティティは1件だけ残る
        assertEquals(0x33, reader.readUnsignedByte());
        assertEquals(66, reader.readShort());
        assertEquals(43, reader.readVarInt()); // trial_spawner（769）
        assertEquals(0, reader.readByte());
    }

    // ---- スロット（データコンポーネント） ----

    @Test
    void slot769To770EnchantmentDropsShowTooltip() {
        // enchantments は769も770も種別 10。769の末尾 showTooltip が770では消える。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(10); // enchantments
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(22); // mending
        writer.writeVarInt(3); // level
        writer.writeBoolean(true); // showTooltip（769）
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot769To770(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(10, reader.readVarInt()); // enchantments（770）
        assertEquals(1, reader.readVarInt());
        assertEquals(22, reader.readVarInt()); // mending（770）
        assertEquals(3, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // removed 0（showTooltip は無い）
    }

    @Test
    void slot770To769EnchantmentAddsShowTooltip() {
        // 逆向き：770には無い showTooltip を true で書き足す。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(10); // enchantments
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(22); // mending
        writer.writeVarInt(3); // level
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot770To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(10, reader.readVarInt()); // enchantments（769）
        assertEquals(1, reader.readVarInt());
        assertEquals(22, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertTrue(reader.readBoolean()); // showTooltip（769）
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot769To770HideTooltipBecomesTooltipDisplay() {
        // hide_tooltip（769の 16、void）が tooltip_display（770の 15、
        // {hideTooltip=true, hiddenComponents=[]}）になる。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(16); // hide_tooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot769To770(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(15, reader.readVarInt()); // tooltip_display（770）
        assertTrue(reader.readBoolean()); // hideTooltip
        assertEquals(0, reader.readVarInt()); // hiddenComponents 0件
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot770To769TooltipDisplayHideTooltip() {
        // tooltip_display（hideTooltip=true）は hide_tooltip（769の 16、void）に戻る。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(15); // tooltip_display
        writer.writeBoolean(true); // hideTooltip
        writer.writeVarInt(0); // hiddenComponents 0件
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot770To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(16, reader.readVarInt()); // hide_tooltip（769）
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot770To769TooltipDisplayHiddenComponents() {
        // tooltip_display（hideTooltip=false、hiddenComponents あり）は
        // hide_additional_tooltip（769の 15、void）に戻る。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(15); // tooltip_display
        writer.writeBoolean(false); // hideTooltip
        writer.writeVarInt(1); // hiddenComponents 1件
        writer.writeVarInt(10); // enchantments（770）
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot770To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(15, reader.readVarInt()); // hide_additional_tooltip（769）
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot770To769NewKindDropped() {
        // break_sound（770の新規種別 71、varint）は769に無いため成分ごと落とす。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(71); // break_sound
        writer.writeVarInt(42); // 音ID（varint）
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot770To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // added 0（落とした）
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot769To770NewItemsFallBackToStone() {
        // 770の新アイテム（blue_egg 970／brown_egg 971／bush 204）は
        // 769に無いため石（1）に落とす。
        byte[] out = ChunkTranslator.translateSetSlot770To769(setSlotPacket(970));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, readSlotHead(reader)[1]);

        byte[] out2 = ChunkTranslator.translateSetSlot770To769(setSlotPacket(971));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(1, readSlotHead(reader2)[1]);

        byte[] out3 = ChunkTranslator.translateSetSlot770To769(setSlotPacket(204));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(1, readSlotHead(reader3)[1]);
    }

    // ---- Set Creative Slot（UntrustedSlot） / Window Click ----

    @Test
    void setCreativeSlot769To770UntrustedSlot() {
        // 769の Slot（{count, itemId, added, removed}）が770では
        // UntrustedSlot（{count, itemId, added, removed, 成分は {kind, ByteArray}}）になる。
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(1); // stone
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(10); // enchantments
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(22); // mending
        writer.writeVarInt(3); // level
        writer.writeBoolean(true); // showTooltip（769）
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetCreativeSlot769To770(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1, reader.readVarInt()); // stone（770）
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(0, reader.readVarInt()); // removed 0
        assertEquals(10, reader.readVarInt()); // enchantments（770）
        byte[] componentBytes = reader.readByteArray();
        Reader component = new Reader(componentBytes);
        assertEquals(1, component.readVarInt());
        assertEquals(22, component.readVarInt());
        assertEquals(3, component.readVarInt());
    }

    @Test
    void setCreativeSlot770To769Slot() {
        // 770の UntrustedSlot が769の Slot に戻る。
        Writer component = new Writer();
        component.writeVarInt(1); // count 1
        component.writeVarInt(22); // mending
        component.writeVarInt(3); // level
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(1); // stone
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(0); // removed 0
        writer.writeVarInt(10); // enchantments
        writer.writeByteArray(component.bytes());
        byte[] out = ChunkTranslator.translateSetCreativeSlot770To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1, reader.readVarInt()); // stone（769）
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(10, reader.readVarInt()); // enchantments（769）
        assertEquals(1, reader.readVarInt());
        assertEquals(22, reader.readVarInt()); // mending
        assertEquals(3, reader.readVarInt());
        assertTrue(reader.readBoolean()); // showTooltip（769）
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void windowClick769To770Throws() {
        // Window Click は HashedSlot のハッシュ算法が未検証のため変換できない。
        // ProxySession はこのパケットを落とす。
        assertThrows(UnsupportedOperationException.class,
                () -> ChunkTranslator.translateWindowClick769To770(new byte[0]));
    }

    @Test
    void windowClick770To769Throws() {
        assertThrows(UnsupportedOperationException.class,
                () -> ChunkTranslator.translateWindowClick770To769(new byte[0]));
    }

    // ---- パーティクル ----

    @Test
    void worldParticles770To769TintedLeavesDropped() {
        // tinted_leaves（770の 35、データは i32）は769に無いためパケットごと落とす。
        // データ（i32）を読み飛ばさないと後続が壊れるので、落とす前に読む。
        Writer data = new Writer();
        data.writeInt(12345);
        byte[] out = ChunkTranslator.translateWorldParticles770To769(
                worldParticlesPacket770(false, 35, data));
        assertNull(out);
    }

    @Test
    void worldParticles770To769FireflyDropped() {
        // firefly（770の 113、データ無し）は769に無いためパケットごと落とす。
        byte[] out = ChunkTranslator.translateWorldParticles770To769(
                worldParticlesPacket770(false, 113, new Writer()));
        assertNull(out);
    }

    @Test
    void worldParticles770To769TrailIdentity() {
        // trail 48→47。データ（3×f64＋i32＋varint）はそのまま。
        Writer data = new Writer();
        data.writeDouble(1.0);
        data.writeDouble(2.0);
        data.writeDouble(3.0);
        data.writeInt(0x11223344);
        data.writeVarInt(7);
        byte[] out = ChunkTranslator.translateWorldParticles770To769(
                worldParticlesPacket770(false, 48, data));
        Reader reader = new Reader(out);
        assertEquals(47, readWorldParticlesId(reader));
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(0x11223344, reader.readInt());
        assertEquals(7, reader.readVarInt());
    }

    @Test
    void worldParticles770To769BlockCrumble() {
        // block_crumble 112→111。データはブロック状態（trial_spawner 27698→27651）。
        Writer data = new Writer();
        data.writeVarInt(27698); // trial_spawner（770）
        byte[] out = ChunkTranslator.translateWorldParticles770To769(
                worldParticlesPacket770(false, 112, data));
        Reader reader = new Reader(out);
        assertEquals(111, readWorldParticlesId(reader));
        assertEquals(27651, reader.readVarInt());
    }

    @Test
    void worldParticles769To770BlockCrumble() {
        // block_crumble 111→112。データはブロック状態（trial_spawner 27651→27698）。
        Writer data = new Writer();
        data.writeVarInt(27651); // trial_spawner（769）
        byte[] out = ChunkTranslator.translateWorldParticles769To770(
                worldParticlesPacket769(false, 111, data));
        Reader reader = new Reader(out);
        assertEquals(112, readWorldParticlesId(reader));
        assertEquals(27698, reader.readVarInt());
    }

    // ---- エンティティメタデータ ----

    @Test
    void entityMetadata769To770WolfVariantHolderToVarint() {
        // wolf_variant は769では holder（種別 23、参照は id+1）、
        // 770では素の varint（種別 24、参照は id そのまま）。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(23); // wolf_variant（769）
        entries.writeVarInt(7); // holder：参照 id 6
        byte[] out = ChunkTranslator.translateEntityMetadata769To770(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt()); // wolf_variant（770）
        assertEquals(6, reader.readVarInt()); // 参照 id そのまま
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata770To769CowVariantDropped() {
        // cow_variant（770の 23）は769に無いためエントリごと落とす。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(23); // cow_variant（770）
        entries.writeVarInt(3); // 参照 id
        byte[] out = ChunkTranslator.translateEntityMetadata770To769(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata769To770PaintingVariant() {
        // painting_variant は769の種別 26（holder）→770の種別 30（holder）。
        // 参照は id+1 のまま（7→7）。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(26); // painting_variant（769）
        entries.writeVarInt(7); // holder：参照 id 6
        byte[] out = ChunkTranslator.translateEntityMetadata769To770(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt()); // painting_variant（770）
        assertEquals(7, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata770To769PaintingVariant() {
        // painting_variant は770の種別 30（holder）→769の種別 26（holder）。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(30); // painting_variant（770）
        entries.writeVarInt(7); // holder：参照 id 6
        byte[] out = ChunkTranslator.translateEntityMetadata770To769(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(26, reader.readVarInt()); // painting_variant（769）
        assertEquals(7, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata769To770FrogVariant() {
        // frog_variant は769の種別 24（varint）→770の種別 26（varint）。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(24); // frog_variant（769）
        entries.writeVarInt(2); // 参照 id
        byte[] out = ChunkTranslator.translateEntityMetadata769To770(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(26, reader.readVarInt()); // frog_variant（770）
        assertEquals(2, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    // ---- Spawn Entity Experience Orb ----

    @Test
    void spawnEntityExperienceOrb769To770() {
        // 769の spawn_entity_experience_orb（0x02）が770では通常の
        // Spawn Entity（type 47 experience_orb）になる。
        byte[] out = ChunkTranslator.translateSpawnEntityExperienceOrb769To770(
                spawnEntityExperienceOrbPacket769(42));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid（新規に振る）
        assertEquals(47, reader.readVarInt()); // experience_orb（770）
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        reader.readByte(); // pitch
        reader.readByte(); // yaw
        reader.readByte(); // headYaw
        assertEquals(42, reader.readVarInt()); // data（経験値）
        assertEquals(0, reader.readShort()); // velocityX
        assertEquals(0, reader.readShort()); // velocityY
        assertEquals(0, reader.readShort()); // velocityZ
    }

    @Test
    void spawnEntityExperienceOrb770To769() {
        // 770の Spawn Entity（type 47）が769では spawn_entity_experience_orb になる。
        // data varint が経験値であることは仮定（実バニラキャプチャで未確認）。
        byte[] out = ChunkTranslator.translateSpawnEntityExperienceOrb770To769(
                spawnEntityPacket(47, 42));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(42, reader.readShort()); // count
    }
}
