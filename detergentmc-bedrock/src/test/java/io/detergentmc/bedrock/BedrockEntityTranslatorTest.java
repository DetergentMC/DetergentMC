package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt;
import io.detergentmc.protocol.PacketIO;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * BedrockEntityTranslator（Java エンティティパケットの読み取り・
 * 種別対応表・メタデータ翻訳）のテスト。
 */
class BedrockEntityTranslatorTest {

    /** Java 1.20.4（765）のエンティティ種別名（id 順。ViaVersion mapping-1.20.3.json）。 */
    private static final String[] JAVA_NAMES_765 = {
        "allay", "area_effect_cloud", "armor_stand", "arrow", "axolotl",
        "bat", "bee", "blaze", "block_display", "boat",
        "breeze", "camel", "cat", "cave_spider", "chest_boat",
        "chest_minecart", "chicken", "cod", "command_block_minecart", "cow",
        "creeper", "dolphin", "donkey", "dragon_fireball", "drowned",
        "egg", "elder_guardian", "end_crystal", "ender_dragon", "ender_pearl",
        "enderman", "endermite", "evoker", "evoker_fangs", "experience_bottle",
        "experience_orb", "eye_of_ender", "falling_block", "firework_rocket", "fox",
        "frog", "furnace_minecart", "ghast", "giant", "glow_item_frame",
        "glow_squid", "goat", "guardian", "hoglin", "hopper_minecart",
        "horse", "husk", "illusioner", "interaction", "iron_golem",
        "item", "item_display", "item_frame", "fireball", "leash_knot",
        "lightning_bolt", "llama", "llama_spit", "magma_cube", "marker",
        "minecart", "mooshroom", "mule", "ocelot", "painting",
        "panda", "parrot", "phantom", "pig", "piglin",
        "piglin_brute", "pillager", "polar_bear", "potion", "pufferfish",
        "rabbit", "ravager", "salmon", "sheep", "shulker",
        "shulker_bullet", "silverfish", "skeleton", "skeleton_horse", "slime",
        "small_fireball", "sniffer", "snow_golem", "snowball", "spawner_minecart",
        "spectral_arrow", "spider", "squid", "stray", "strider",
        "tadpole", "text_display", "tnt", "tnt_minecart", "trader_llama",
        "trident", "tropical_fish", "turtle", "vex", "villager",
        "vindicator", "wandering_trader", "warden", "wind_charge", "witch",
        "wither", "wither_skeleton", "wither_skull", "wolf", "zoglin",
        "zombie", "zombie_horse", "zombie_villager", "zombified_piglin", "player",
        "fishing_bobber",
    };

    /** 改名で対応する 14 種（Java 名→Bedrock 名）。 */
    private static final Map<String, String> RENAMED = Map.ofEntries(
            Map.entry("zombified_piglin", "minecraft:zombie_pigman"),
            Map.entry("evoker", "minecraft:evocation_illager"),
            Map.entry("end_crystal", "minecraft:ender_crystal"),
            Map.entry("eye_of_ender", "minecraft:eye_of_ender_signal"),
            Map.entry("experience_bottle", "minecraft:xp_bottle"),
            Map.entry("experience_orb", "minecraft:xp_orb"),
            Map.entry("fishing_bobber", "minecraft:fishing_hook"),
            Map.entry("trident", "minecraft:thrown_trident"),
            Map.entry("firework_rocket", "minecraft:fireworks_rocket"),
            Map.entry("tropical_fish", "minecraft:tropicalfish"),
            Map.entry("villager", "minecraft:villager_v2"),
            Map.entry("zombie_villager", "minecraft:zombie_villager_v2"),
            Map.entry("evoker_fangs", "minecraft:evocation_fang"),
            Map.entry("potion", "minecraft:splash_potion"));

    /** 代替で対応する 10 種（Java 名→Bedrock 名）。 */
    private static final Map<String, String> SUBSTITUTED = Map.ofEntries(
            Map.entry("spectral_arrow", "minecraft:arrow"),
            Map.entry("furnace_minecart", "minecraft:minecart"),
            Map.entry("spawner_minecart", "minecraft:minecart"),
            Map.entry("giant", "minecraft:zombie"),
            Map.entry("illusioner", "minecraft:evocation_illager"),
            Map.entry("breeze", "minecraft:blaze"),
            Map.entry("wind_charge", "minecraft:small_fireball"),
            Map.entry("block_display", "minecraft:armor_stand"),
            Map.entry("item_display", "minecraft:armor_stand"),
            Map.entry("text_display", "minecraft:armor_stand"));

    /** Spawn Entity（765）のペイロードを作る。角度は 0〜255 のバイト値で渡す。 */
    private static byte[] spawnEntityPayload(int entityId, UUID uuid, int type,
            double x, double y, double z, int pitch, int yaw, int headPitch,
            int objectData, int velX, int velY, int velZ) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(entityId);
        writer.writeUuid(uuid);
        writer.writeVarInt(type);
        writer.writeDouble(x);
        writer.writeDouble(y);
        writer.writeDouble(z);
        writer.writeByte(pitch);
        writer.writeByte(yaw);
        writer.writeByte(headPitch);
        writer.writeVarInt(objectData);
        writer.writeShort(velX);
        writer.writeShort(velY);
        writer.writeShort(velZ);
        return writer.bytes();
    }

    /** メタデータのエントリ 1 個（index・type・値の書き方）を書く。 */
    private interface MetadataEntry {
        void write(PacketIO.Writer writer);
    }

    private static byte[] metadataPayload(int entityId, MetadataEntry... entries) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(entityId);
        for (MetadataEntry entry : entries) {
            entry.write(writer);
        }
        writer.writeByte(0xFF);
        return writer.bytes();
    }

    private static MetadataEntry byteEntry(int index, int value) {
        return w -> {
            w.writeByte(index);
            w.writeVarInt(0);
            w.writeByte(value);
        };
    }

    private static MetadataEntry varIntEntry(int index, int value) {
        return w -> {
            w.writeByte(index);
            w.writeVarInt(1);
            w.writeVarInt(value);
        };
    }

    private static MetadataEntry floatEntry(int index, float value) {
        return w -> {
            w.writeByte(index);
            w.writeVarInt(3);
            w.writeFloat(value);
        };
    }

    private static MetadataEntry booleanEntry(int index, boolean value) {
        return w -> {
            w.writeByte(index);
            w.writeVarInt(8);
            w.writeBoolean(value);
        };
    }

    private static MetadataEntry componentEntry(int index, String text) {
        return w -> {
            w.writeByte(index);
            w.writeVarInt(5);
            w.writeBytes(Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(
                    Map.of("text", new Nbt.Tag.StringTag(text)))));
        };
    }

    /** Java のスロット 1 個（765：NBT なしの空 compound 付き）。 */
    private static void writeSlot(PacketIO.Writer w, boolean present, int item, int count) {
        w.writeBoolean(present);
        if (!present) {
            return;
        }
        w.writeVarInt(item);
        w.writeByte(count);
        w.writeBytes(Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(Map.of())));
    }

    private static MetadataEntry slotEntry(int index, int item, int count) {
        return w -> {
            w.writeByte(index);
            w.writeVarInt(7);
            writeSlot(w, true, item, count);
        };
    }

    private static MetadataEntry villagerDataEntry(int index, int type, int profession, int level) {
        return w -> {
            w.writeByte(index);
            w.writeVarInt(19);
            w.writeVarInt(type);
            w.writeVarInt(profession);
            w.writeVarInt(level);
        };
    }

    private static MetadataEntry holderEntry(int index, int registryId, String assetId) {
        return w -> {
            w.writeByte(index);
            w.writeVarInt(26);
            w.writeVarInt(registryId);
            if (registryId == 0) {
                w.writeBytes(Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(
                        Map.of("asset_id", new Nbt.Tag.StringTag(assetId)))));
            }
        };
    }

    @Test
    void spawnZombieParses() {
        UUID uuid = UUID.randomUUID();
        byte[] payload = spawnEntityPayload(101, uuid, 120,
                10.5, 64.0, -3.25, 0, 64, 32, 0, 0, 0, 0);
        BedrockEntityTranslator.SpawnEntity spawn =
                BedrockEntityTranslator.parseSpawnEntity(payload, 765);
        assertEquals(101, spawn.entityId());
        assertEquals(uuid, spawn.uuid());
        assertEquals(120, spawn.type765());
        assertEquals(10.5, spawn.x());
        assertEquals(64.0, spawn.y());
        assertEquals(-3.25, spawn.z());
        assertEquals(90.0f, spawn.yaw(), 0.001f); // 64 * 360 / 256
        assertEquals(45.0f, spawn.headPitch(), 0.001f); // 32 * 360 / 256
        assertEquals("minecraft:zombie", BedrockEntityTypeIds.bedrockType(120));
    }

    @Test
    void spawnPigAndVillagerParse() {
        BedrockEntityTranslator.SpawnEntity pig = BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(102, UUID.randomUUID(), 73,
                        1.0, 64.0, 1.0, 0, 0, 0, 0, 0, 0, 0),
                765);
        assertEquals(73, pig.type765());
        assertEquals("minecraft:pig", BedrockEntityTypeIds.bedrockType(73));

        BedrockEntityTranslator.SpawnEntity villager =
                BedrockEntityTranslator.parseSpawnEntity(
                        spawnEntityPayload(103, UUID.randomUUID(), 109,
                                2.0, 64.0, 2.0, 0, 0, 0, 0, 0, 0, 0),
                        765);
        assertEquals(109, villager.type765());
        assertEquals("minecraft:villager_v2", BedrockEntityTypeIds.bedrockType(109));
    }

    @Test
    void spawnPlayerParses() {
        UUID uuid = UUID.randomUUID();
        BedrockEntityTranslator.SpawnEntity player =
                BedrockEntityTranslator.parseSpawnEntity(
                        spawnEntityPayload(104, uuid, 124,
                                8.5, 64.0, 8.5, 0, 0, 0, 0, 0, 0, 0),
                        765);
        assertEquals(124, player.type765());
        assertEquals(uuid, player.uuid());
        assertNull(BedrockEntityTypeIds.bedrockType(124), "プレイヤーは AddPlayer 経由のため表は null");
    }

    @Test
    void spawnItemMetadataHasSlot() {
        BedrockEntityTranslator.SpawnEntity item =
                BedrockEntityTranslator.parseSpawnEntity(
                        spawnEntityPayload(105, UUID.randomUUID(), 55,
                                3.0, 65.0, 3.0, 0, 0, 0, 0, 0, 0, 0),
                        765);
        assertEquals(55, item.type765());
        assertEquals("minecraft:item", BedrockEntityTypeIds.bedrockType(55));

        byte[] meta = metadataPayload(105, slotEntry(8, 1, 3));
        BedrockEntityTranslator.JavaMetadata parsed =
                BedrockEntityTranslator.parseMetadata(meta, 765, 55);
        assertTrue(parsed.complete());
        assertTrue(parsed.itemSlot().present());
        assertEquals(1, parsed.itemSlot().item765()); // stone
        assertEquals(3, parsed.itemSlot().count());
        BedrockEntityTranslator.BedrockItem bedrock =
                BedrockEntityTranslator.toBedrockItem(parsed.itemSlot());
        assertEquals(1, bedrock.networkId()); // Bedrock の石
        assertTrue(bedrock.blockRuntimeId() > 0);
    }

    @Test
    void spawnExperienceOrbParses() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(106);
        writer.writeDouble(5.0);
        writer.writeDouble(64.0);
        writer.writeDouble(5.0);
        writer.writeShort(7);
        BedrockEntityTranslator.SpawnExperienceOrb orb =
                BedrockEntityTranslator.parseSpawnExperienceOrb(writer.bytes());
        assertEquals(106, orb.entityId());
        assertEquals(5.0, orb.x());
        assertEquals(7, orb.count());
    }

    @Test
    void spawnArrowParses() {
        BedrockEntityTranslator.SpawnEntity arrow =
                BedrockEntityTranslator.parseSpawnEntity(
                        spawnEntityPayload(107, UUID.randomUUID(), 3,
                                0.0, 65.0, 0.0, 0, 0, 0, 1, 800, 0, 0),
                        765);
        assertEquals(3, arrow.type765());
        assertEquals(1, arrow.objectData());
        assertEquals(0.1, arrow.velX(), 0.0001); // 800 / 8000
        assertEquals("minecraft:arrow", BedrockEntityTypeIds.bedrockType(3));
    }

    @Test
    void entityTypeTableCoverage() {
        assertEquals(126, JAVA_NAMES_765.length);
        assertEquals(126, BedrockEntityTypeIds.COUNT);
        int direct = 0;
        int renamed = 0;
        int substituted = 0;
        int notSpawned = 0;
        int player = 0;
        Set<String> distinct = new HashSet<>();
        for (int id = 0; id < 126; id++) {
            String bedrock = BedrockEntityTypeIds.bedrockType(id);
            String javaName = JAVA_NAMES_765[id];
            if (bedrock == null) {
                if (id == 124) {
                    player++;
                } else {
                    notSpawned++;
                    assertTrue(id == 53 || id == 64,
                            "null は interaction（53）・marker（64）・player（124）のみのはず: " + id);
                }
                continue;
            }
            assertTrue(bedrock.startsWith("minecraft:"),
                    "Bedrock 識別子は minecraft: 始まりのはず: " + bedrock);
            distinct.add(bedrock);
            if (RENAMED.containsKey(javaName)) {
                assertEquals(RENAMED.get(javaName), bedrock);
                renamed++;
            } else if (SUBSTITUTED.containsKey(javaName)) {
                assertEquals(SUBSTITUTED.get(javaName), bedrock);
                substituted++;
            } else {
                assertEquals("minecraft:" + javaName, bedrock);
                direct++;
            }
        }
        assertEquals(99, direct, "直接対応の種別数");
        assertEquals(14, renamed, "改名で対応の種別数");
        assertEquals(10, substituted, "代替で対応の種別数");
        assertEquals(2, notSpawned, "出さない種別数（interaction・marker）");
        assertEquals(1, player, "プレイヤー（AddPlayer 経由）");
        assertEquals(123, direct + renamed + substituted, "Bedrock へ出す種別数");
        assertEquals(113, distinct.size(), "異なる Bedrock 識別子の数（代替の重複を除く）");
    }

    @Test
    void itemTableCoverage() {
        assertEquals(1312, BedrockItemIds.COUNT);
        assertEquals(1272, BedrockItemIds.MAPPED_COUNT, "Bedrock へ写せるアイテム数");
        assertEquals(1, BedrockItemIds.networkId(1), "石は 1");
        assertEquals(318, BedrockItemIds.networkId(834), "ダイヤの剣（765 の 834）は 318");
        assertEquals(1, BedrockItemIds.networkId(99999), "範囲外は石（1）に落ちる");
        assertEquals(1, BedrockItemIds.networkId(0), "空気は石（1）に落ちる（アイテムとして送らない）");
        assertTrue(BedrockItemIds.isMapped(1));
        assertFalse(BedrockItemIds.isMapped(0));
    }

    @Test
    void itemBlockRuntimeIdForStone() {
        // 石のアイテム（765 の 1）はブロック状態 1 のランタイム ID になる。
        assertEquals(1774, BedrockItemBlockIds.blockRuntimeId(1));
    }

    @Test
    void metadataZombieBabyAndHealth() {
        byte[] meta = metadataPayload(101,
                byteEntry(0, 0x00),
                floatEntry(9, 20.0f),
                booleanEntry(16, true));
        BedrockEntityTranslator.JavaMetadata parsed =
                BedrockEntityTranslator.parseMetadata(meta, 765, 120);
        assertTrue(parsed.complete());
        assertTrue(parsed.baby(), "ゾンビの赤ちゃん");
        assertEquals(20.0f, parsed.health(), 0.001f);
        BedrockEntityTranslator.BedrockEntityData data =
                BedrockEntityTranslator.toBedrockData(parsed, 120);
        long flags = (Long) data.entries().stream()
                .filter(e -> e.key() == BedrockEntityTranslator.BedrockEntityData.KEY_FLAGS)
                .findFirst().orElseThrow().value();
        assertTrue((flags & (1L << BedrockEntityTranslator.BedrockEntityData.FLAG_BABY)) != 0,
                "Bedrock の flags に BABY があること");
        assertEquals(20, data.entries().stream()
                .filter(e -> e.key() == BedrockEntityTranslator.BedrockEntityData.KEY_HEALTH)
                .findFirst().orElseThrow().value());
    }

    @Test
    void metadataPigSaddled() {
        byte[] meta = metadataPayload(102, booleanEntry(16, true));
        BedrockEntityTranslator.JavaMetadata parsed =
                BedrockEntityTranslator.parseMetadata(meta, 765, 73);
        assertTrue(parsed.saddled());
        BedrockEntityTranslator.BedrockEntityData data =
                BedrockEntityTranslator.toBedrockData(parsed, 73);
        long flags = (Long) data.entries().stream()
                .filter(e -> e.key() == BedrockEntityTranslator.BedrockEntityData.KEY_FLAGS)
                .findFirst().orElseThrow().value();
        assertTrue((flags & (1L << BedrockEntityTranslator.BedrockEntityData.FLAG_SADDLED)) != 0);
    }

    @Test
    void metadataVillagerProfession() {
        byte[] meta = metadataPayload(103, villagerDataEntry(16, 0, 2, 1));
        BedrockEntityTranslator.JavaMetadata parsed =
                BedrockEntityTranslator.parseMetadata(meta, 765, 109);
        assertEquals(2, parsed.profession());
        BedrockEntityTranslator.BedrockEntityData data =
                BedrockEntityTranslator.toBedrockData(parsed, 109);
        assertEquals(2, data.entries().stream()
                .filter(e -> e.key() == BedrockEntityTranslator.BedrockEntityData.KEY_VARIANT)
                .findFirst().orElseThrow().value());
    }

    @Test
    void metadataNametagAndFlags() {
        byte[] meta = metadataPayload(101,
                byteEntry(0, 0x02 | 0x10), // しゃがみ＋不可視
                componentEntry(2, "Boss"),
                booleanEntry(3, true));
        BedrockEntityTranslator.JavaMetadata parsed =
                BedrockEntityTranslator.parseMetadata(meta, 765, 120);
        assertEquals("Boss", parsed.customName());
        assertTrue(parsed.nameVisible());
        BedrockEntityTranslator.BedrockEntityData data =
                BedrockEntityTranslator.toBedrockData(parsed, 120);
        Map<Integer, Object> byKey = new HashMap<>();
        for (BedrockEntityTranslator.BedrockEntityData.Entry entry : data.entries()) {
            byKey.put(entry.key(), entry.value());
        }
        assertEquals("Boss", byKey.get(BedrockEntityTranslator.BedrockEntityData.KEY_NAMETAG));
        assertEquals(1, byKey.get(BedrockEntityTranslator.BedrockEntityData.KEY_ALWAYS_SHOW_NAMETAG));
        long flags = (Long) byKey.get(BedrockEntityTranslator.BedrockEntityData.KEY_FLAGS);
        assertTrue((flags & (1L << BedrockEntityTranslator.BedrockEntityData.FLAG_SNEAKING)) != 0);
        assertTrue((flags & (1L << BedrockEntityTranslator.BedrockEntityData.FLAG_INVISIBLE)) != 0);
    }

    @Test
    void metadataGoatHornCountOnlyFrom440() {
        // 431 の MetadataDictionary の key 123 は base_runtime_id。
        // 440 で key 123 が goat_horn_count になる。
        // ヤギ（type 46）の左右の角が両方あると goatHornCount = 2。
        byte[] meta = metadataPayload(105,
                booleanEntry(17, true),
                booleanEntry(18, true));
        BedrockEntityTranslator.JavaMetadata parsed =
                BedrockEntityTranslator.parseMetadata(meta, 765, 46);
        assertEquals(2, parsed.goatHornCount());
        BedrockEntityTranslator.BedrockEntityData data440 =
                BedrockEntityTranslator.toBedrockData(parsed, 46, BedrockVersion.V1_17_2);
        assertEquals(2, data440.entries().stream()
                .filter(e -> e.key() == BedrockEntityTranslator.BedrockEntityData.KEY_GOAT_HORN_COUNT)
                .findFirst().orElseThrow().value());
        BedrockEntityTranslator.BedrockEntityData data431 =
                BedrockEntityTranslator.toBedrockData(parsed, 46, BedrockVersion.V1_16_221);
        assertTrue(data431.entries().stream()
                .noneMatch(e -> e.key() == BedrockEntityTranslator.BedrockEntityData.KEY_GOAT_HORN_COUNT),
                "431 では key 123 を書かない（base_runtime_id の位置）");
        // 2 引数版（既定 V1_20_50）は従来通り key 123 を書く。
        BedrockEntityTranslator.BedrockEntityData dataDefault =
                BedrockEntityTranslator.toBedrockData(parsed, 46);
        assertTrue(dataDefault.entries().stream()
                .anyMatch(e -> e.key() == BedrockEntityTranslator.BedrockEntityData.KEY_GOAT_HORN_COUNT));
    }

    @Test
    void metadataPlayerExtra() {
        byte[] meta = metadataPayload(104,
                floatEntry(16, 4.0f),
                varIntEntry(17, 5),
                byteEntry(18, 0x7f),
                byteEntry(19, 1));
        BedrockEntityTranslator.JavaMetadata parsed =
                BedrockEntityTranslator.parseMetadata(meta, 765, 124);
        assertEquals(4.0f, parsed.playerExtraHearts(), 0.001f);
        assertEquals(5, parsed.playerScore());
        assertEquals((byte) 0x7f, parsed.playerSkinParts().byteValue());
        assertEquals((byte) 1, parsed.playerMainHand().byteValue());
    }

    @Test
    void paintingTitleMapping() {
        assertEquals("Kebab", BedrockEntityTranslator.paintingTitle(0));
        assertEquals("Fighters", BedrockEntityTranslator.paintingTitle(20));
        assertEquals("SkullAndRoses", BedrockEntityTranslator.paintingTitle(18));
        assertEquals("Wither", BedrockEntityTranslator.paintingTitle(19));
        assertEquals("Kebab", BedrockEntityTranslator.paintingTitle(25), "earth は 1.21 追加のため Kebab");
        assertEquals("Kebab", BedrockEntityTranslator.paintingTitle(999), "範囲外は Kebab");
        assertEquals("Kebab", BedrockEntityTranslator.paintingTitle("minecraft:kebab"));
        assertEquals("SkullAndRoses",
                BedrockEntityTranslator.paintingTitle("minecraft:skull_and_roses"));
        assertEquals("Fighters", BedrockEntityTranslator.paintingTitle("minecraft:fight"));
    }

    @Test
    void paintingMetadataHolder() {
        byte[] meta = metadataPayload(108, holderEntry(8, 0, "minecraft:kebab"));
        BedrockEntityTranslator.JavaMetadata parsed =
                BedrockEntityTranslator.parseMetadata(meta, 765, 69);
        assertEquals("minecraft:kebab", parsed.paintingVariant());
    }

    @Test
    void relativeMoveTeleportVelocityHeadRotation() {
        PacketIO.Writer move = new PacketIO.Writer();
        move.writeVarInt(101);
        move.writeShort(409); // +0.09985
        move.writeShort(-819); // -0.19995
        move.writeShort(0);
        move.writeBoolean(true);
        BedrockEntityTranslator.EntityMove parsedMove =
                BedrockEntityTranslator.parseRelativeMove(move.bytes());
        assertEquals(101, parsedMove.entityId());
        assertEquals(409 / 4096.0, parsedMove.dx(), 0.0001);
        assertEquals(-819 / 4096.0, parsedMove.dy(), 0.0001);
        assertTrue(parsedMove.onGround());

        PacketIO.Writer teleport = new PacketIO.Writer();
        teleport.writeVarInt(101);
        teleport.writeDouble(10.0);
        teleport.writeDouble(65.0);
        teleport.writeDouble(-5.0);
        teleport.writeByte(64); // yaw 90
        teleport.writeByte(0); // pitch 0
        teleport.writeBoolean(false);
        BedrockEntityTranslator.EntityTeleport parsedTeleport =
                BedrockEntityTranslator.parseTeleport(teleport.bytes());
        assertEquals(10.0, parsedTeleport.x());
        assertEquals(90.0f, parsedTeleport.yaw(), 0.001f);
        assertFalse(parsedTeleport.onGround());

        PacketIO.Writer velocity = new PacketIO.Writer();
        velocity.writeVarInt(101);
        velocity.writeShort(8000);
        velocity.writeShort(0);
        velocity.writeShort(-4000);
        BedrockEntityTranslator.EntityVelocity parsedVelocity =
                BedrockEntityTranslator.parseVelocity(velocity.bytes());
        assertEquals(1.0f, parsedVelocity.velX(), 0.001f);
        assertEquals(-0.5f, parsedVelocity.velZ(), 0.001f);

        PacketIO.Writer head = new PacketIO.Writer();
        head.writeVarInt(101);
        head.writeByte(128); // 180
        BedrockEntityTranslator.EntityHeadRotation parsedHead =
                BedrockEntityTranslator.parseHeadRotation(head.bytes());
        assertEquals(180.0f, parsedHead.headYaw(), 0.001f);
    }

    @Test
    void removeEntitiesParses() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(2);
        writer.writeVarInt(101);
        writer.writeVarInt(102);
        List<Integer> ids = BedrockEntityTranslator.parseRemoveEntities(writer.bytes());
        assertEquals(List.of(101, 102), ids);
    }

    @Test
    void equipmentParses() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(101);
        writer.writeByte(0x80 | 0); // mainhand、続きあり
        writeSlot(writer, true, 834, 1); // ダイヤの剣
        writer.writeByte(2); // feet、最後
        writeSlot(writer, true, 353, 1); // ダイヤのブーツ
        BedrockEntityTranslator.EntityEquipment equipment =
                BedrockEntityTranslator.parseEntityEquipment(writer.bytes(), 765);
        assertTrue(equipment.complete());
        assertEquals(2, equipment.entries().size());
        assertEquals(0, equipment.entries().get(0).slot());
        assertEquals(834, equipment.entries().get(0).item().item765());
        assertEquals(2, equipment.entries().get(1).slot());
        assertEquals(353, equipment.entries().get(1).item().item765());
    }

    @Test
    void effectParses() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(101);
        writer.writeVarInt(0); // speed（765 は 0 始まり）
        writer.writeByte(1); // amplifier
        writer.writeVarInt(200); // duration
        writer.writeByte(0x02); // show particles
        writer.writeBoolean(false); // factorCodec なし
        BedrockEntityTranslator.EntityEffect effect =
                BedrockEntityTranslator.parseEntityEffect(writer.bytes(), 765);
        assertEquals(0, effect.effect765());
        assertEquals(1, effect.amplifier());
        assertEquals(200, effect.duration());
        assertTrue(effect.showParticles());
        assertEquals(1, BedrockEntityTranslator.bedrockEffectId(0, 765), "Bedrock は 1 始まり");
        assertEquals(1, BedrockEntityTranslator.bedrockEffectId(1, 763), "763 は 1 始まりのまま");
    }

    @Test
    void updateAttributesParses() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(101);
        writer.writeVarInt(1);
        writer.writeString("minecraft:generic.max_health");
        writer.writeDouble(30.0);
        writer.writeVarInt(0); // modifiers なし
        BedrockEntityTranslator.UpdateAttributes attributes =
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 765);
        assertEquals(30.0, attributes.maxHealth(), 0.001);
    }

    @Test
    void passengersAndAttachParse() {
        PacketIO.Writer passengers = new PacketIO.Writer();
        passengers.writeVarInt(200); // vehicle
        passengers.writeVarInt(2);
        passengers.writeVarInt(201);
        passengers.writeVarInt(202);
        BedrockEntityTranslator.SetPassengers parsedPassengers =
                BedrockEntityTranslator.parseSetPassengers(passengers.bytes());
        assertEquals(200, parsedPassengers.vehicleId());
        assertEquals(List.of(201, 202), parsedPassengers.passengerIds());

        PacketIO.Writer attach = new PacketIO.Writer();
        attach.writeInt(201);
        attach.writeInt(200);
        BedrockEntityTranslator.AttachEntity parsedAttach =
                BedrockEntityTranslator.parseAttachEntity(attach.bytes());
        assertEquals(201, parsedAttach.entityId());
        assertEquals(200, parsedAttach.vehicleId());
    }

    @Test
    void normalizeEntityType() {
        assertEquals(120, BedrockEntityTranslator.normalizeEntityType(120, 765));
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(9999, 765), "範囲外はブタ");
        assertEquals(37, BedrockEntityTranslator.normalizeEntityType(36, 764),
                "764 の falling_block（36）は 765 の 37");
        assertEquals(11, BedrockEntityTranslator.normalizeEntityType(10, 764),
                "764 の camel（10）は 765 の 11");
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(9999, 764));
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(9999, 766));
    }
}
