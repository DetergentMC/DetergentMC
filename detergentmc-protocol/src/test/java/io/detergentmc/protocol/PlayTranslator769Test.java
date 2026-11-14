// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 768（1.21.2／1.21.3）⇔769（1.21.4）の変換テスト。
 * 期待値は minecraft-data の protocol.json・レジストリから計算したもの（推測ではない）。
 * Login Success／Join Game／Respawn／Client Information は768と769で形式が同一のため
 * PlayTranslator に768⇔769の翻訳メソッドは無く、プロキシは素通しする
 * （ProxySession769Test で扱う）。ここでは変わる4つを扱う。
 */
class PlayTranslator769Test {

    // ------------------------------------------------------------------
    // ヘルパー
    // ------------------------------------------------------------------

    /** レジストリ1個分の codec（エントリは名前順のリスト、element は空）。 */
    private static Tag.CompoundTag registryCodec(String registryId, List<String> names) {
        List<Tag> entries = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            entries.add(new Tag.CompoundTag(Map.of(
                    "name", new Tag.StringTag("minecraft:" + names.get(i)),
                    "id", new Tag.IntTag(i),
                    "element", new Tag.CompoundTag(Map.of()))));
        }
        return new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag(registryId),
                "value", new Tag.ListTag(Nbt.COMPOUND, entries)));
    }

    /** 766／767／768／769形式の Registry Data 1パケット（レジストリ1個分）。 */
    private static byte[] registryPacket(String registryId, List<String> names) {
        return PlayTranslator.buildRegistryData766(
                Map.of(registryId, registryCodec(registryId, names))).get(0);
    }

    /** Registry Data パケットを読み、名前列と hasData 列を返す。 */
    private record RegistryPacket(List<String> names, List<Boolean> hasData) {
    }

    private static RegistryPacket readRegistryPacket(byte[] payload) {
        Reader reader = new Reader(payload);
        String registryId = reader.readString();
        assertTrue(registryId.startsWith("minecraft:"), registryId);
        int count = reader.readVarInt();
        List<String> names = new ArrayList<>(count);
        List<Boolean> hasData = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(reader.readString());
            boolean has = reader.readBoolean();
            hasData.add(has);
            if (has) {
                reader.readAnonymousNbt();
            }
        }
        assertEquals(0, reader.remaining());
        return new RegistryPacket(names, hasData);
    }

    private static List<String> shortNames(List<String> fullNames) {
        return fullNames.stream()
                .map(n -> n.startsWith("minecraft:") ? n.substring("minecraft:".length()) : n)
                .toList();
    }

    /** 768のバイオーム64件（plains は39、old_growth_spruce_taiga は38）。 */
    private static List<String> biomes768() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if (i == 38) {
                names.add("old_growth_spruce_taiga");
            } else if (i == 39) {
                names.add("plains");
            } else {
                names.add("biome_" + i);
            }
        }
        return names;
    }

    /** 769のバイオーム65件（pale_garden は39、plains は40）。 */
    private static List<String> biomes769() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            if (i == 38) {
                names.add("old_growth_spruce_taiga");
            } else if (i == 39) {
                names.add("pale_garden");
            } else if (i == 40) {
                names.add("plains");
            } else if (i < 38) {
                names.add("biome_" + i);
            } else {
                names.add("biome_" + (i - 1));
            }
        }
        return names;
    }

    /** 768形式の Player Info（update_list_order 付き、プレイヤー2人）。 */
    private static byte[] playerInfo768Payload() {
        Writer writer = new Writer();
        writer.writeByte(0x7F); // 全アクション（list_order 含む）
        writer.writeVarInt(2); // count 2
        // 1人目：全アクションのデータあり。
        writer.writeUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        writer.writeString("Alice"); // add_player：名前
        writer.writeVarInt(1); // properties 1件
        writer.writeString("textures");
        writer.writeString("texvalue");
        writer.writeBoolean(true); // signature 有り
        writer.writeString("sigvalue");
        writer.writeBoolean(true); // initialize_chat：セッション有り
        writer.writeUuid(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        writer.writeLong(123456789L); // 有効期限
        writer.writeByteArray(new byte[]{0x01, 0x02}); // 公開鍵
        writer.writeByteArray(new byte[]{0x03}); // 署名
        writer.writeVarInt(1); // update_game_mode（creative）
        writer.writeVarInt(1); // listed（varint）
        writer.writeVarInt(42); // latency
        writer.writeBoolean(true); // displayName 有り
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of())); // 空の NBT
        writer.writeVarInt(5); // listPriority
        // 2人目：add_player と latency だけ。
        writer.writeUuid(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        writer.writeString("Bob");
        writer.writeVarInt(0); // properties 0件
        writer.writeBoolean(false); // initialize_chat：セッション無し
        writer.writeVarInt(0); // update_game_mode（survival）
        writer.writeVarInt(1); // listed
        writer.writeVarInt(7); // latency
        writer.writeBoolean(false); // displayName 無し
        writer.writeVarInt(9); // listPriority
        return writer.bytes();
    }

    /** 769形式の Player Info（update_hat＋update_list_order 付き、プレイヤー2人）。 */
    private static byte[] playerInfo769Payload() {
        Writer writer = new Writer();
        writer.writeByte(0xFF); // 全アクション（hat＋list_order 含む）
        writer.writeVarInt(2); // count 2
        // 1人目：全アクションのデータあり。listPriority の後に showHat。
        writer.writeUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        writer.writeString("Alice"); // add_player：名前
        writer.writeVarInt(1); // properties 1件
        writer.writeString("textures");
        writer.writeString("texvalue");
        writer.writeBoolean(true); // signature 有り
        writer.writeString("sigvalue");
        writer.writeBoolean(true); // initialize_chat：セッション有り
        writer.writeUuid(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        writer.writeLong(123456789L); // 有効期限
        writer.writeByteArray(new byte[]{0x01, 0x02}); // 公開鍵
        writer.writeByteArray(new byte[]{0x03}); // 署名
        writer.writeVarInt(1); // update_game_mode（creative）
        writer.writeVarInt(1); // listed（varint）
        writer.writeVarInt(42); // latency
        writer.writeBoolean(true); // displayName 有り
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of())); // 空の NBT
        writer.writeVarInt(5); // listPriority（bit 7）
        writer.writeBoolean(true); // showHat（bit 6）
        // 2人目：add_player と latency だけ。
        writer.writeUuid(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        writer.writeString("Bob");
        writer.writeVarInt(0); // properties 0件
        writer.writeBoolean(false); // initialize_chat：セッション無し
        writer.writeVarInt(0); // update_game_mode（survival）
        writer.writeVarInt(1); // listed
        writer.writeVarInt(7); // latency
        writer.writeBoolean(false); // displayName 無し
        writer.writeVarInt(9); // listPriority（bit 7）
        writer.writeBoolean(false); // showHat（bit 6）
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // Player Info
    // ------------------------------------------------------------------

    @Test
    void playerInfo768To769MovesListOrderBit() {
        // 768の bit 6（list_order）は769では bit 7 に移る。hat は送らない。
        byte[] out = PlayTranslator.translatePlayerInfo768To769(playerInfo768Payload());
        Reader reader = new Reader(out);
        assertEquals(0xBF, reader.readUnsignedByte()); // 0x7F の bit 6→bit 7
        assertEquals(2, reader.readVarInt());
        // 1人目：768と同じ並び、listPriority 5 が末尾。
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), reader.readUuid());
        assertEquals("Alice", reader.readString());
        assertEquals(1, reader.readVarInt());
        assertEquals("textures", reader.readString());
        assertEquals("texvalue", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals("sigvalue", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), reader.readUuid());
        assertEquals(123456789L, reader.readLong());
        assertArrayEquals(new byte[]{0x01, 0x02}, reader.readByteArray());
        assertArrayEquals(new byte[]{0x03}, reader.readByteArray());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(42, reader.readVarInt());
        assertTrue(reader.readBoolean());
        reader.readAnonymousNbt();
        assertEquals(5, reader.readVarInt()); // listPriority
        // 2人目。
        assertEquals(UUID.fromString("33333333-3333-3333-3333-333333333333"), reader.readUuid());
        assertEquals("Bob", reader.readString());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
        assertEquals(9, reader.readVarInt()); // listPriority
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerInfo769To768DropsHatMovesListOrderBit() {
        // 769の bit 7（list_order）は768では bit 6 に移る。
        // 769の bit 6（hat）の showHat bool は落とす。
        // 結果は768の Player Info とバイト単位で一致する。
        byte[] out = PlayTranslator.translatePlayerInfo769To768(playerInfo769Payload());
        assertArrayEquals(playerInfo768Payload(), out);
    }

    @Test
    void playerInfo769To768HatOnly() {
        // hat だけのアクション（bit 6）は768では空のアクションになる。
        Writer writer = new Writer();
        writer.writeByte(0x41); // add_player＋hat
        writer.writeVarInt(1); // count 1
        writer.writeUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        writer.writeString("Alex");
        writer.writeVarInt(0); // properties 0件
        writer.writeBoolean(true); // showHat
        byte[] out = PlayTranslator.translatePlayerInfo769To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x01, reader.readUnsignedByte()); // add_player のみ
        assertEquals(1, reader.readVarInt());
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), reader.readUuid());
        assertEquals("Alex", reader.readString());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerInfoRoundTripWithoutHat() {
        // hat が絡まなければ往復で元に戻る。
        byte[] original = playerInfo768Payload();
        assertArrayEquals(original, PlayTranslator.translatePlayerInfo769To768(
                PlayTranslator.translatePlayerInfo768To769(original)));
    }

    @Test
    void playerInfo768To769GamemodeOnly() {
        // list_order・hat が無ければアクションバイトは変わらない。
        Writer writer = new Writer();
        writer.writeByte(0x04); // update_game_mode のみ
        writer.writeVarInt(1); // count 1
        writer.writeUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        writer.writeVarInt(1); // creative
        byte[] out = PlayTranslator.translatePlayerInfo768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x04, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), reader.readUuid());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // Set Held Item（クライアント行き）
    // ------------------------------------------------------------------

    @Test
    void setHeldItem768To769ByteToVarint() {
        // 768は i8、769は varint。スロット 3 は1バイトのまま。
        byte[] out = PlayTranslator.translateSetHeldItem768To769(new byte[]{3});
        assertArrayEquals(new byte[]{3}, out);
    }

    @Test
    void setHeldItem769To768VarintToByte() {
        byte[] out = PlayTranslator.translateSetHeldItem769To768(new byte[]{3});
        assertArrayEquals(new byte[]{3}, out);
    }

    @Test
    void setHeldItem768To769SlotEight() {
        // スロット 8（ホットバーの末尾）。
        byte[] out = PlayTranslator.translateSetHeldItem768To769(new byte[]{8});
        assertArrayEquals(new byte[]{8}, out);
    }

    // ------------------------------------------------------------------
    // Vehicle Move（サーバー行き）
    // ------------------------------------------------------------------

    @Test
    void vehicleMove768To769AppendsOnGroundFalse() {
        // 769は末尾に onGround bool が増えた。768→769は false を補う。
        Writer writer = new Writer();
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(2.5);
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        byte[] out = PlayTranslator.translateVehicleMove768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.5, reader.readDouble(), 0.0001);
        assertEquals(64.0, reader.readDouble(), 0.0001);
        assertEquals(2.5, reader.readDouble(), 0.0001);
        assertEquals(90.0f, reader.readFloat(), 0.0001f);
        assertEquals(0.0f, reader.readFloat(), 0.0001f);
        assertEquals(false, reader.readBoolean()); // onGround（補った既定値）
        assertEquals(0, reader.remaining());
    }

    @Test
    void vehicleMove769To768StripsOnGround() {
        Writer writer = new Writer();
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(2.5);
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        writer.writeBoolean(true); // onGround
        byte[] out = PlayTranslator.translateVehicleMove769To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.5, reader.readDouble(), 0.0001);
        assertEquals(64.0, reader.readDouble(), 0.0001);
        assertEquals(2.5, reader.readDouble(), 0.0001);
        assertEquals(90.0f, reader.readFloat(), 0.0001f);
        assertEquals(0.0f, reader.readFloat(), 0.0001f);
        assertEquals(0, reader.remaining());
    }

    @Test
    void vehicleMoveRoundTrip() {
        Writer writer = new Writer();
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(2.5);
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        byte[] original = writer.bytes();
        assertArrayEquals(original, PlayTranslator.translateVehicleMove769To768(
                PlayTranslator.translateVehicleMove768To769(original)));
    }

    // ------------------------------------------------------------------
    // Registry Data
    // ------------------------------------------------------------------

    @Test
    void registryData768To769InsertsPaleGarden() {
        // 768のバイオーム64件（plains は39）に pale_garden を39番に挿入し、
        // plains は40に移る。pale_garden は名前だけのエントリ（hasData=false）。
        byte[] packet = registryPacket("minecraft:worldgen/biome", biomes768());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData768To769(packet));
        assertEquals(65, out.names().size());
        assertEquals("minecraft:pale_garden", out.names().get(39));
        assertEquals(false, out.hasData().get(39)); // 名前だけ
        assertEquals("minecraft:plains", out.names().get(40));
        assertEquals(true, out.hasData().get(40));
        assertEquals("minecraft:old_growth_spruce_taiga", out.names().get(38));
        assertEquals(List.of("pale_garden", "plains"),
                shortNames(out.names().subList(39, 41)));
    }

    @Test
    void registryData769To768DropsPaleGarden() {
        // 769のバイオーム65件（pale_garden は39、plains は40）から
        // pale_garden を落とし、plains は39に戻る。
        byte[] packet = registryPacket("minecraft:worldgen/biome", biomes769());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData769To768(packet));
        assertEquals(64, out.names().size());
        assertEquals("minecraft:plains", out.names().get(39));
        assertEquals(true, out.hasData().get(39));
        assertEquals("minecraft:old_growth_spruce_taiga", out.names().get(38));
        assertEquals("minecraft:biome_40", out.names().get(40));
        assertEquals(shortNames(biomes768()), shortNames(out.names()));
    }

    @Test
    void registryData768To769PaleGardenAlreadyPresent() {
        // winter_drop 有効時の768のように pale_garden が既にある場合は
        // 挿入せずそのまま（65件のまま）。
        byte[] packet = registryPacket("minecraft:worldgen/biome", biomes769());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData768To769(packet));
        assertEquals(65, out.names().size());
        assertEquals(shortNames(biomes769()), shortNames(out.names()));
    }

    @Test
    void registryData769To768JukeboxSongKept() {
        // jukebox_song は768にもあるため落とさない。
        byte[] packet = registryPacket("minecraft:jukebox_song",
                List.of("13", "cat", "blocks", "chirp", "far", "mall", "mellohi",
                        "stal", "strad", "ward", "11", "wait", "pigstep", "otherside",
                        "5", "relic", "creator", "precipice", "5"));
        byte[] out = PlayTranslator.translateRegistryData769To768(packet);
        assertNotNull(out);
        RegistryPacket read = readRegistryPacket(out);
        assertEquals(19, read.names().size());
        assertEquals("minecraft:13", read.names().get(0));
    }

    @Test
    void registryData768To769EnchantmentsIdentity() {
        // エンチャントは42種とも同一。
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            names.add("enchantment_" + i);
        }
        names.set(22, "mending");
        byte[] packet = registryPacket("minecraft:enchantment", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData768To769(packet));
        assertEquals(42, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
    }

    @Test
    void registryData769To768EnchantmentsIdentity() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            names.add("enchantment_" + i);
        }
        names.set(22, "mending");
        byte[] packet = registryPacket("minecraft:enchantment", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData769To768(packet));
        assertEquals(42, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
    }

    @Test
    void registryData768To769NonBiomeUnchanged() {
        // バイオーム以外のレジストリはそのまま。
        byte[] packet = registryPacket("minecraft:dimension_type",
                List.of("overworld", "overworld_caves", "the_nether", "the_end"));
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData768To769(packet));
        assertEquals(List.of("overworld", "overworld_caves", "the_nether", "the_end"),
                shortNames(out.names()));
    }
}
