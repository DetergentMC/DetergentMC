package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * 1.21.7／1.21.8（プロトコル772）の PlayTranslator 変換テスト。
 *
 * <p>771→772の変更（minecraft-data の表に準拠。実バニラ1.21.7／1.21.8の
 * Registry Data キャプチャで絵画・ジュークボックスの位置を確認する）：
 * Player Info／Set Held Item／Vehicle Move／Player Chat は形式が同一のため
 * 素通し。Chat Message／Chat Command Signed の末尾 checksum u8 は771・772とも
 * あるため、771⇔772では剥がしも付け足しもしない（strip／append は
 * 769⇔770／770⇔771／771⇔772の連鎖で使う道具として残る）。
 * Registry Data は jukebox_song が lava_chicken の挿入で 9番以降が+1ずれる。
 * minecraft:dialog は771・772ともにあるため、771⇔772では落とさず素通しする
 * （771段階で770向けに落としていたのとは違い、772では771向けに落とさない）。
 */
class PlayTranslator772Test {

    // ------------------------------------------------------------------
    // ヘルパー（PlayTranslator771Test と同形）
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

    /** 766／767／768／769／770／771／772形式の Registry Data 1パケット（レジストリ1個分）。 */
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

    /** 771／772のバイオーム65件（pale_garden は39、plains は40）。 */
    private static List<String> biomes771() {
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

    /** 771／772形式の Player Info（update_hat＋update_list_order 付き、プレイヤー2人）。 */
    private static byte[] playerInfo771Payload() {
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

    /** 771／772形式の Player Chat（先頭に globalIndex varint 付き）。 */
    private static byte[] playerChat771Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(3); // globalIndex
        writer.writeUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        writer.writeString("hello");
        writer.writeLong(123456789L);
        return writer.bytes();
    }

    /** 771のジュークボックスの歌20件（実バニラ1.21.6の並び）。 */
    private static List<String> jukeboxSongs771() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "mall", "mellohi", "otherside", "pigstep",
                "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }

    /** 772のジュークボックスの歌21件（lava_chicken を9番に挿入）。 */
    private static List<String> jukeboxSongs772() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "lava_chicken", "mall", "mellohi", "otherside",
                "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }

    // ------------------------------------------------------------------
    // Player Info／Set Held Item／Vehicle Move／Player Chat（素通し）
    // ------------------------------------------------------------------

    @Test
    void playerInfo771To772Identity() {
        byte[] payload = playerInfo771Payload();
        assertArrayEquals(payload, PlayTranslator.translatePlayerInfo771To772(payload));
    }

    @Test
    void playerInfo772To771Identity() {
        byte[] payload = playerInfo771Payload();
        assertArrayEquals(payload, PlayTranslator.translatePlayerInfo772To771(payload));
    }

    @Test
    void setHeldItem771To772Identity() {
        byte[] payload = new byte[]{0x05};
        assertArrayEquals(payload, PlayTranslator.translateSetHeldItem771To772(payload));
    }

    @Test
    void vehicleMove771To772IdentityWithOnGround() {
        // Vehicle Move は771と772で形式が同一（x/y/z＋yaw＋pitch＋onGround）。
        Writer writer = new Writer();
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.5f);
        writer.writeFloat(1.0f);
        writer.writeBoolean(true); // onGround
        byte[] payload = writer.bytes();
        assertArrayEquals(payload, PlayTranslator.translateVehicleMove771To772(payload));
        assertArrayEquals(payload, PlayTranslator.translateVehicleMove772To771(payload));
    }

    @Test
    void playerChat771To772Identity() {
        // Player Chat は771・772とも先頭に globalIndex があるため素通し。
        byte[] payload = playerChat771Payload();
        assertArrayEquals(payload, PlayTranslator.translatePlayerChat771To772(payload));
        assertArrayEquals(payload, PlayTranslator.translatePlayerChat772To771(payload));
    }

    // ------------------------------------------------------------------
    // チャットチェックサム（771⇔772では使わない道具）
    // ------------------------------------------------------------------

    @Test
    void stripChatChecksum772() {
        byte[] payload = new byte[]{0x01, 0x02, (byte) 0xAB};
        assertArrayEquals(new byte[]{0x01, 0x02},
                PlayTranslator.stripChatChecksum772(payload));
    }

    @Test
    void stripChatChecksum772Empty() {
        assertArrayEquals(new byte[0], PlayTranslator.stripChatChecksum772(new byte[0]));
    }

    @Test
    void appendChatChecksum772() {
        // 末尾に checksum 0x00 を書き足す。0x00 が実バニラ1.21.7／1.21.8で
        // 受け入れられることは実バニラでの統合テストで確認する（プローブ未実施）。
        byte[] payload = new byte[]{0x01, 0x02};
        assertArrayEquals(new byte[]{0x01, 0x02, 0x00},
                PlayTranslator.appendChatChecksum772(payload));
    }

    // ------------------------------------------------------------------
    // Registry Data
    // ------------------------------------------------------------------

    @Test
    void registryData771To772EnchantmentsIdentity() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            names.add("enchantment_" + i);
        }
        names.set(22, "mending");
        byte[] packet = registryPacket("minecraft:enchantment", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData771To772(packet));
        assertEquals(42, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
    }

    @Test
    void registryData772To771EnchantmentsIdentity() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            names.add("enchantment_" + i);
        }
        names.set(22, "mending");
        byte[] packet = registryPacket("minecraft:enchantment", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To771(packet));
        assertEquals(42, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
    }

    @Test
    void registryData771To772BiomesIdentity() {
        byte[] packet = registryPacket("minecraft:worldgen/biome", biomes771());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData771To772(packet));
        assertEquals(65, out.names().size());
        assertEquals("minecraft:pale_garden", out.names().get(39));
        assertEquals("minecraft:plains", out.names().get(40));
        assertEquals(shortNames(biomes771()), shortNames(out.names()));
    }

    @Test
    void registryData771To772JukeboxSongInsertsLavaChicken() {
        // 771→772：lava_chicken を9番に挿入し、mall 以降が+1ずれる。
        // 挿入された lava_chicken は名前だけのエントリ（hasData=false）。
        byte[] packet = registryPacket("minecraft:jukebox_song", jukeboxSongs771());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData771To772(packet));
        assertEquals(21, out.names().size());
        assertEquals(shortNames(jukeboxSongs772()), shortNames(out.names()));
        assertEquals("lava_chicken", out.names().get(9));
        assertEquals("mall", out.names().get(10));
        assertEquals("tears", out.names().get(18));
        assertEquals("wait", out.names().get(19));
        assertEquals("ward", out.names().get(20));
        assertFalse(out.hasData().get(9));
    }

    @Test
    void registryData772To771JukeboxSongDropsLavaChicken() {
        // 772→771：lava_chicken を落とし、mall 以降が-1ずれる。
        byte[] packet = registryPacket("minecraft:jukebox_song", jukeboxSongs772());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To771(packet));
        assertEquals(20, out.names().size());
        assertEquals(shortNames(jukeboxSongs771()), shortNames(out.names()));
        assertEquals("mall", out.names().get(9));
        assertEquals("tears", out.names().get(17));
        assertEquals("wait", out.names().get(18));
        assertEquals("ward", out.names().get(19));
    }

    @Test
    void registryData771To772DialogPassesThrough() {
        // minecraft:dialog は771・772ともにあるため素通しする（落とさない）。
        byte[] packet = registryPacket("minecraft:dialog",
                List.of("custom_options", "quick_actions", "server_links"));
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData771To772(packet));
        assertEquals(3, out.names().size());
        assertEquals(List.of("custom_options", "quick_actions", "server_links"),
                shortNames(out.names()));
    }

    @Test
    void registryData772To771DialogPassesThrough() {
        // 逆向きも素通しする（771段階で770向けに落としていたのとは違う）。
        byte[] packet = registryPacket("minecraft:dialog",
                List.of("custom_options", "quick_actions", "server_links"));
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To771(packet));
        assertEquals(3, out.names().size());
        assertEquals(List.of("custom_options", "quick_actions", "server_links"),
                shortNames(out.names()));
    }

    @Test
    void registryData771To772NonBiomeUnchanged() {
        byte[] packet = registryPacket("minecraft:dimension_type",
                List.of("overworld", "overworld_caves", "the_nether", "the_end"));
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData771To772(packet));
        assertEquals(List.of("overworld", "overworld_caves", "the_nether", "the_end"),
                shortNames(out.names()));
    }
}
