package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 1.21.5（プロトコル770）の PlayTranslator 変換テスト。
 *
 * <p>769→770の変更（minecraft-data の protocol.json に準拠）：
 * Player Info／Set Held Item／Vehicle Move は形式が同一のため素通し。
 * Player Chat（clientbound）は先頭に globalIndex varint が増えた。
 * Chat Message／Chat Command Signed（serverbound）は末尾に checksum u8 が増えた
 * （769→770では 0x00 を書き足すが、実バニラで受け入れられるかは未確認）。
 * Registry Data は順序が同一のため並べ替えは素通しだが、jukebox_song は
 * 770にもあるため落とさない（reorderRegistries の除外対象に 770 を追加済み）。
 */
class PlayTranslator770Test {

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

    /** 766／767／768／769／770形式の Registry Data 1パケット（レジストリ1個分）。 */
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

    /** 769／770のバイオーム65件（pale_garden は39、plains は40）。 */
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

    /** 769／770形式の Player Info（update_hat＋update_list_order 付き、プレイヤー2人）。 */
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

    /** 769形式の Player Chat（senderUuid＋メッセージ＋タイムスタンプ。翻訳子は中身を読まない）。 */
    private static byte[] playerChat769Payload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        writer.writeString("hello");
        writer.writeLong(123456789L);
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // Player Info／Set Held Item／Vehicle Move（素通し）
    // ------------------------------------------------------------------

    @Test
    void playerInfo769To770Identity() {
        // Player Info は769と770で形式が同一（8ビットとも変わらない）。
        byte[] payload = playerInfo769Payload();
        assertArrayEquals(payload, PlayTranslator.translatePlayerInfo769To770(payload));
    }

    @Test
    void playerInfo770To769Identity() {
        byte[] payload = playerInfo769Payload();
        assertArrayEquals(payload, PlayTranslator.translatePlayerInfo770To769(payload));
    }

    @Test
    void setHeldItem769To770Identity() {
        byte[] payload = new byte[]{0x05};
        assertArrayEquals(payload, PlayTranslator.translateSetHeldItem769To770(payload));
    }

    @Test
    void vehicleMove769To770Identity() {
        // Vehicle Move は769と770で形式が同一（x/y/z＋yaw＋pitch）。
        Writer writer = new Writer();
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.5f);
        writer.writeFloat(1.0f);
        byte[] payload = writer.bytes();
        assertArrayEquals(payload, PlayTranslator.translateVehicleMove769To770(payload));
        assertArrayEquals(payload, PlayTranslator.translateVehicleMove770To769(payload));
    }

    // ------------------------------------------------------------------
    // Player Chat（globalIndex）
    // ------------------------------------------------------------------

    @Test
    void playerChat769To770PrependsGlobalIndex() {
        // 769→770：先頭に globalIndex varint を書き足す。
        byte[] payload = playerChat769Payload();
        byte[] out = PlayTranslator.translatePlayerChat769To770(payload, 7);
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // globalIndex
        assertArrayEquals(payload, reader.rest());
    }

    @Test
    void playerChat770To769StripsGlobalIndex() {
        // 770→769：先頭の globalIndex varint を落とす。
        byte[] payload = playerChat769Payload();
        Writer writer = new Writer();
        writer.writeVarInt(3); // globalIndex
        writer.writeBytes(payload);
        byte[] out = PlayTranslator.translatePlayerChat770To769(writer.bytes());
        assertArrayEquals(payload, out);
    }

    @Test
    void playerChatRoundTrip() {
        byte[] payload = playerChat769Payload();
        byte[] forward = PlayTranslator.translatePlayerChat769To770(payload, 0);
        assertArrayEquals(payload, PlayTranslator.translatePlayerChat770To769(forward));
    }

    // ------------------------------------------------------------------
    // チャットチェックサム
    // ------------------------------------------------------------------

    @Test
    void stripChatChecksum770() {
        // 770→769：末尾の checksum u8 を落とす。
        byte[] payload = new byte[]{0x01, 0x02, (byte) 0xAB};
        assertArrayEquals(new byte[]{0x01, 0x02},
                PlayTranslator.stripChatChecksum770(payload));
    }

    @Test
    void stripChatChecksum770Empty() {
        // 空のパケットはそのまま（壊れない）。
        assertArrayEquals(new byte[0], PlayTranslator.stripChatChecksum770(new byte[0]));
    }

    @Test
    void appendChatChecksum770() {
        // 769→770：末尾に checksum 0x00 を書き足す。
        // 0x00 が実バニラで受け入れられるかは未確認（統合テストで確認する）。
        byte[] payload = new byte[]{0x01, 0x02};
        assertArrayEquals(new byte[]{0x01, 0x02, 0x00},
                PlayTranslator.appendChatChecksum770(payload));
    }

    // ------------------------------------------------------------------
    // Registry Data
    // ------------------------------------------------------------------

    @Test
    void registryData769To770EnchantmentsIdentity() {
        // エンチャントは42種とも同一。
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            names.add("enchantment_" + i);
        }
        names.set(22, "mending");
        byte[] packet = registryPacket("minecraft:enchantment", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData769To770(packet));
        assertEquals(42, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
    }

    @Test
    void registryData770To769EnchantmentsIdentity() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            names.add("enchantment_" + i);
        }
        names.set(22, "mending");
        byte[] packet = registryPacket("minecraft:enchantment", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData770To769(packet));
        assertEquals(42, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
    }

    @Test
    void registryData769To770BiomesIdentity() {
        // バイオームは65件とも同一（pale_garden は39、plains は40）。
        byte[] packet = registryPacket("minecraft:worldgen/biome", biomes769());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData769To770(packet));
        assertEquals(65, out.names().size());
        assertEquals("minecraft:pale_garden", out.names().get(39));
        assertEquals("minecraft:plains", out.names().get(40));
        assertEquals(shortNames(biomes769()), shortNames(out.names()));
    }

    @Test
    void registryData769To770JukeboxSongKept() {
        // jukebox_song は770にもあるため落とさない。
        byte[] packet = registryPacket("minecraft:jukebox_song",
                List.of("13", "cat", "blocks", "chirp", "far", "mall", "mellohi",
                        "stal", "strad", "ward", "11", "wait", "pigstep", "otherside",
                        "5", "relic", "creator", "precipice", "5"));
        byte[] out = PlayTranslator.translateRegistryData769To770(packet);
        RegistryPacket read = readRegistryPacket(out);
        assertEquals(19, read.names().size());
        assertEquals("minecraft:13", read.names().get(0));
    }

    @Test
    void registryData770To769JukeboxSongKept() {
        byte[] packet = registryPacket("minecraft:jukebox_song",
                List.of("13", "cat", "blocks", "chirp", "far", "mall", "mellohi",
                        "stal", "strad", "ward", "11", "wait", "pigstep", "otherside",
                        "5", "relic", "creator", "precipice", "5"));
        byte[] out = PlayTranslator.translateRegistryData770To769(packet);
        RegistryPacket read = readRegistryPacket(out);
        assertEquals(19, read.names().size());
        assertEquals("minecraft:13", read.names().get(0));
    }

    @Test
    void registryData769To770NonBiomeUnchanged() {
        // バイオーム以外のレジストリはそのまま。
        byte[] packet = registryPacket("minecraft:dimension_type",
                List.of("overworld", "overworld_caves", "the_nether", "the_end"));
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData769To770(packet));
        assertEquals(List.of("overworld", "overworld_caves", "the_nether", "the_end"),
                shortNames(out.names()));
    }
}
