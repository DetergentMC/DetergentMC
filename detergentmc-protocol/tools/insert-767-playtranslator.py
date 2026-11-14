#!/usr/bin/env python3
"""PlayTranslator に 767（1.21）のエイリアスと Registry Data 並べ替えを挿入する。

- Login Success / Join Game / Respawn / Chat Command の 765↔767 は
  766↔765 と形式が同一のためエイリアス（766↔767 は素通し）。
- Registry Data は 766↔767 で順序が変わったレジストリ
  （enchantment / painting_variant / banner_pattern / trim_pattern /
  damage_type）を相手の順序に並べ替え、相手にだけあるエントリは
  hasData=false（名前だけ）で埋める。765↔767 は codec 段階で合成する
  （766のエントリ配列は id を持たないため、バイト列経由の合成は
  enchantment でずれる）。jukebox_song は 767→766/765 で落とす。
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PATH = ROOT / "src/main/java/io/detergentmc/protocol/PlayTranslator.java"

ENCHANTMENT_765 = ["protection", "fire_protection", "feather_falling", "blast_protection",
    "projectile_protection", "respiration", "aqua_affinity", "thorns", "depth_strider",
    "frost_walker", "binding_curse", "soul_speed", "swift_sneak", "sharpness", "smite",
    "bane_of_arthropods", "knockback", "fire_aspect", "looting", "sweeping", "efficiency",
    "silk_touch", "unbreaking", "fortune", "power", "punch", "flame", "infinity",
    "luck_of_the_sea", "lure", "loyalty", "impaling", "riptide", "channeling",
    "multishot", "quick_charge", "piercing", "mending", "vanishing_curse"]
ENCHANTMENT_766 = ["protection", "fire_protection", "feather_falling", "blast_protection",
    "projectile_protection", "respiration", "aqua_affinity", "thorns", "depth_strider",
    "frost_walker", "binding_curse", "soul_speed", "swift_sneak", "sharpness", "smite",
    "bane_of_arthropods", "knockback", "fire_aspect", "looting", "sweeping_edge",
    "efficiency", "silk_touch", "unbreaking", "fortune", "power", "punch", "flame",
    "infinity", "luck_of_the_sea", "lure", "loyalty", "impaling", "riptide", "channeling",
    "multishot", "quick_charge", "piercing", "density", "breach", "wind_burst",
    "mending", "vanishing_curse"]
ENCHANTMENT_767 = ["aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection",
    "breach", "channeling", "density", "depth_strider", "efficiency", "feather_falling",
    "fire_aspect", "fire_protection", "flame", "fortune", "frost_walker", "impaling",
    "infinity", "knockback", "looting", "loyalty", "luck_of_the_sea", "lure", "mending",
    "multishot", "piercing", "power", "projectile_protection", "protection", "punch",
    "quick_charge", "respiration", "riptide", "sharpness", "silk_touch", "smite",
    "soul_speed", "sweeping_edge", "swift_sneak", "thorns", "unbreaking",
    "vanishing_curse", "wind_burst"]
PAINTING_766 = ["kebab", "aztec", "alban", "aztec2", "bomb", "plant", "wasteland", "pool",
    "courbet", "sea", "sunset", "creebet", "wanderer", "graham", "match", "bust",
    "stage", "void", "skull_and_roses", "wither", "fighters", "pointer", "pigscene",
    "burning_skull", "skeleton", "donkey_kong"]
PAINTING_767 = ["alban", "aztec", "aztec2", "backyard", "baroque", "bomb", "bouquet",
    "burning_skull", "bust", "cavebird", "changing", "cotan", "courbet", "creebet",
    "donkey_kong", "earth", "endboss", "fern", "fighters", "finding", "fire", "graham",
    "humble", "kebab", "lowmist", "match", "meditative", "orb", "owlemons", "passage",
    "pigscene", "plant", "pointer", "pond", "pool", "prairie_ride", "sea", "skeleton",
    "skull_and_roses", "stage", "sunflowers", "sunset", "tides", "unpacked", "void",
    "wanderer", "wasteland", "water", "wind", "wither"]
BANNER_766 = ["base", "border", "bricks", "circle", "creeper", "cross", "curly_border",
    "diagonal_left", "diagonal_right", "diagonal_up_left", "diagonal_up_right", "flower",
    "globe", "gradient", "gradient_up", "half_horizontal", "half_horizontal_bottom",
    "half_vertical", "half_vertical_right", "mojang", "piglin", "rhombus", "skull",
    "small_stripes", "square_bottom_left", "square_bottom_right", "square_top_left",
    "square_top_right", "straight_cross", "stripe_bottom", "stripe_center",
    "stripe_downleft", "stripe_downright", "stripe_left", "stripe_middle", "stripe_right",
    "stripe_top", "triangle_bottom", "triangle_top", "triangles_bottom", "triangles_top"]
BANNER_767 = ["base", "border", "bricks", "circle", "creeper", "cross", "curly_border",
    "diagonal_left", "diagonal_right", "diagonal_up_left", "diagonal_up_right", "flow",
    "flower", "globe", "gradient", "gradient_up", "guster", "half_horizontal",
    "half_horizontal_bottom", "half_vertical", "half_vertical_right", "mojang", "piglin",
    "rhombus", "skull", "small_stripes", "square_bottom_left", "square_bottom_right",
    "square_top_left", "square_top_right", "straight_cross", "stripe_bottom",
    "stripe_center", "stripe_downleft", "stripe_downright", "stripe_left",
    "stripe_middle", "stripe_right", "stripe_top", "triangle_bottom", "triangle_top",
    "triangles_bottom", "triangles_top"]
TRIM_766 = ["coast", "dune", "eye", "host", "raiser", "rib", "sentry", "shaper",
    "silence", "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild"]
TRIM_767 = ["bolt", "coast", "dune", "eye", "flow", "host", "raiser", "rib", "sentry",
    "shaper", "silence", "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild"]
DAMAGE_766 = ["arrow", "bad_respawn_point", "cactus", "cramming", "dragon_breath", "drown",
    "dry_out", "explosion", "fall", "falling_anvil", "falling_block", "falling_stalactite",
    "fireball", "fireworks", "fly_into_wall", "freeze", "generic", "generic_kill",
    "hot_floor", "in_fire", "in_wall", "indirect_magic", "lava", "lightning_bolt",
    "magic", "mob_attack", "mob_attack_no_aggro", "mob_projectile", "on_fire",
    "out_of_world", "outside_border", "player_attack", "player_explosion", "sonic_boom",
    "spit", "stalagmite", "starve", "sting", "sweet_berry_bush", "thorns", "thrown",
    "trident", "unattributed_fireball", "wither", "wither_skull"]
DAMAGE_767 = ["arrow", "bad_respawn_point", "cactus", "campfire", "cramming",
    "dragon_breath", "drown", "dry_out", "explosion", "fall", "falling_anvil",
    "falling_block", "falling_stalactite", "fireball", "fireworks", "fly_into_wall",
    "freeze", "generic", "generic_kill", "hot_floor", "in_fire", "in_wall",
    "indirect_magic", "lava", "lightning_bolt", "magic", "mob_attack",
    "mob_attack_no_aggro", "mob_projectile", "on_fire", "out_of_world",
    "outside_border", "player_attack", "player_explosion", "sonic_boom", "spit",
    "stalagmite", "starve", "sting", "sweet_berry_bush", "thorns", "thrown",
    "trident", "unattributed_fireball", "wind_charge", "wither", "wither_skull"]


def java_array(name, values):
    lines = [f"    private static final String[] {name} = {{"]
    for i in range(0, len(values), 6):
        chunk = values[i:i + 6]
        lines.append("            " + ", ".join(f'"minecraft:{v}"' for v in chunk) + ",")
    lines.append("    };")
    return "\n".join(lines)


def insert_once(text, anchor, block, where="before"):
    if text.count(anchor) != 1:
        print(f"ANCHOR 失敗（{text.count(anchor)}件）: {anchor[:80]!r}")
        sys.exit(1)
    if where == "before":
        return text.replace(anchor, block + "\n" + anchor, 1)
    return text.replace(anchor, anchor + "\n" + block, 1)


def main():
    text = PATH.read_text(encoding="utf-8")
    if "translateLoginSuccess765To767" in text:
        print("既に挿入済みのため何もしません。")
        return

    # --- 1. Login Success エイリアス（Registry Data の javadoc の直前） ---
    login_block = """
    /**
     * 765の Login Success を767形式にする。767も766と同じく末尾に
     * strictErrorHandling（bool）が付くため {@link #translateLoginSuccess765To766}
     * と同じ（末尾に {@code 0} を1バイト足す）。
     */
    public static byte[] translateLoginSuccess765To767(byte[] payload) {
        return translateLoginSuccess765To766(payload);
    }

    /**
     * 767の Login Success を765形式にする。末尾の strictErrorHandling を落とす。
     * {@link #translateLoginSuccess766To765} と同じ。
     */
    public static byte[] translateLoginSuccess767To765(byte[] payload) {
        return translateLoginSuccess766To765(payload);
    }

    /**
     * 766の Login Success を767形式にする。両方とも末尾に strictErrorHandling
     * が付くため形式は同一で、そのまま返す。
     */
    public static byte[] translateLoginSuccess766To767(byte[] payload) {
        return payload;
    }

    /**
     * 767の Login Success を766形式にする。{@link #translateLoginSuccess766To767}
     * と同じく形式は同一で、そのまま返す。
     */
    public static byte[] translateLoginSuccess767To766(byte[] payload) {
        return payload;
    }
"""
    text = insert_once(text,
        "    /**\n     * 765／764の Registry Data（1個の codec NBT）を766形式に変換する。",
        login_block)

    # --- 2. Chat Command エイリアス（クライアント行きチャットの区切りの直前） ---
    chat_block = """
    /**
     * 767の Chat Command（符号なし、コマンド文字列のみ）を765形式
     * （符号付き）に変換する。766と同じ形のため
     * {@link #translateChatCommand766To765} と同じ。
     */
    public static byte[] translateChatCommand767To765(byte[] payload) {
        return translateChatCommand766To765(payload);
    }
"""
    text = insert_once(text,
        "    // ------------------------------------------------------------------\n"
        "    // チャット（クライアント行き）",
        chat_block)

    # --- 3. Join Game エイリアス（763の Join Game の javadoc の直前） ---
    join_block = """
    /**
     * 764／765の Join Game を767形式にする。767の Join Game は766と
     * 同一形式のため {@link #translateJoinGame765To766} と同じ。
     */
    public static byte[] translateJoinGame765To767(JoinGame765 join,
            Map<String, Tag.CompoundTag> registries) {
        return translateJoinGame765To766(join, registries);
    }

    /** 764／765の Join Game を767形式にする（{@link #translateJoinGame765To767} と同じ）。 */
    public static byte[] translateJoinGame764To767(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateJoinGame765To766(parseJoinGame765(payload), registries);
    }

    /**
     * 766の Join Game を767形式にする。Join Game（dimension の番号を
     * 含む SpawnInfo を持つ）は766と767で同一形式のため、そのまま返す
     * （dimension_type・biome のレジストリも766と767で同一）。
     */
    public static byte[] translateJoinGame766To767(byte[] payload) {
        return payload;
    }

    /**
     * 767の Join Game を766形式にする。{@link #translateJoinGame766To767}
     * と同じく同一形式のため、そのまま返す。
     */
    public static byte[] translateJoinGame767To766(byte[] payload) {
        return payload;
    }
"""
    text = insert_once(text,
        "    /**\n     * 763の Join Game を764／765形式（0x29）に変換する。",
        join_block)

    # --- 4. Respawn エイリアス（ファイル末尾の直前） ---
    respawn_block = """
    /**
     * Respawn（764／765 → 767）。767の Respawn は766と同一形式のため
     * {@link #translateRespawn765To766} と同じ。
     */
    public static byte[] translateRespawn765To767(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn765To766(payload, registries);
    }

    /** Respawn（764 → 767）。765→767と同じ形式。 */
    public static byte[] translateRespawn764To767(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn765To767(payload, registries);
    }

    /**
     * Respawn（767 → 764／765）。766→765と同じ形式。
     */
    public static byte[] translateRespawn767To765(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn766To765(payload, registries);
    }

    /** Respawn（767 → 764）。767→765と同じ形式。 */
    public static byte[] translateRespawn767To764(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn767To765(payload, registries);
    }

    /**
     * Respawn（766 → 767）。766と767で同一形式のため、そのまま返す
     * （変換に失敗したとき translateRespawn766To765 は素通しを返すが、
     * ここでは元をそのまま返す点も同じ）。
     */
    public static byte[] translateRespawn766To767(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return payload;
    }

    /**
     * Respawn（767 → 766）。{@link #translateRespawn766To767} と同じく
     * 同一形式のため、そのまま返す。
     */
    public static byte[] translateRespawn767To766(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return payload;
    }
"""
    text = insert_once(text,
        "    /** Respawn（764 → 766）。765→766と同じ形式。 */\n"
        "    public static byte[] translateRespawn764To766(byte[] payload,\n"
        "            Map<String, Tag.CompoundTag> registries) {\n"
        "        return translateRespawn765To766(payload, registries);\n"
        "    }\n}",
        respawn_block)

    # --- 5. Registry Data 並べ替え（766→765 変換の直後） ---
    registry_block = "\n" + java_array("ENCHANTMENT_765_NAMES", ENCHANTMENT_765) + "\n\n" \
        + java_array("ENCHANTMENT_766_NAMES", ENCHANTMENT_766) + "\n\n" \
        + java_array("ENCHANTMENT_767_NAMES", ENCHANTMENT_767) + "\n\n" \
        + java_array("PAINTING_766_NAMES", PAINTING_766) + "\n\n" \
        + java_array("PAINTING_767_NAMES", PAINTING_767) + "\n\n" \
        + java_array("BANNER_766_NAMES", BANNER_766) + "\n\n" \
        + java_array("BANNER_767_NAMES", BANNER_767) + "\n\n" \
        + java_array("TRIM_766_NAMES", TRIM_766) + "\n\n" \
        + java_array("TRIM_767_NAMES", TRIM_767) + "\n\n" \
        + java_array("DAMAGE_766_NAMES", DAMAGE_766) + "\n\n" \
        + java_array("DAMAGE_767_NAMES", DAMAGE_767) + """

    /**
     * レジストリの順序が変わった組み合わせで、送信側の数値 id から
     * 受信側の数値 id への写像を返す。順序が変わらない組み合わせは
     * {@code null}。763／764は765と同じ順序のため、ここでは765として扱う。
     * enchantment は765↔766でも順序が変わる（density・breach・wind_burst
     * の挿入で mending・vanishing_curse が後ろへずれる）。
     */
    private static java.util.function.IntUnaryOperator registryIdMapper(
            String registryId, int fromProtocol, int toProtocol) {
        int from = (fromProtocol == 763 || fromProtocol == 764) ? 765 : fromProtocol;
        int to = (toProtocol == 763 || toProtocol == 764) ? 765 : toProtocol;
        if (from == to) {
            return null;
        }
        return switch (registryId) {
            case "minecraft:enchantment" -> switch (from * 1000 + to) {
                case 765766 -> RegistryRemapper::mapEnchantment765To766;
                case 766765 -> RegistryRemapper::mapEnchantment766To765;
                case 766767 -> RegistryRemapper::mapEnchantment766To767;
                case 767766 -> RegistryRemapper::mapEnchantment767To766;
                case 765767 -> RegistryRemapper::mapEnchantment765To767;
                case 767765 -> RegistryRemapper::mapEnchantment767To765;
                default -> null;
            };
            case "minecraft:painting_variant" -> switch (from * 1000 + to) {
                case 766767 -> RegistryRemapper::mapPaintingVariant766To767;
                case 767766 -> RegistryRemapper::mapPaintingVariant767To766;
                case 765767 -> RegistryRemapper::mapPaintingVariant765To767;
                case 767765 -> RegistryRemapper::mapPaintingVariant767To765;
                default -> null;
            };
            case "minecraft:banner_pattern" -> switch (from * 1000 + to) {
                case 766767 -> RegistryRemapper::mapBannerPattern766To767;
                case 767766 -> RegistryRemapper::mapBannerPattern767To766;
                case 765767 -> RegistryRemapper::mapBannerPattern765To767;
                case 767765 -> RegistryRemapper::mapBannerPattern767To765;
                default -> null;
            };
            case "minecraft:trim_pattern" -> switch (from * 1000 + to) {
                case 766767 -> RegistryRemapper::mapTrimPattern766To767;
                case 767766 -> RegistryRemapper::mapTrimPattern767To766;
                case 765767 -> RegistryRemapper::mapTrimPattern765To767;
                case 767765 -> RegistryRemapper::mapTrimPattern767To765;
                default -> null;
            };
            case "minecraft:damage_type" -> switch (from * 1000 + to) {
                case 766767 -> RegistryRemapper::mapDamageType766To767;
                case 767766 -> RegistryRemapper::mapDamageType767To766;
                case 765767 -> RegistryRemapper::mapDamageType765To767;
                case 767765 -> RegistryRemapper::mapDamageType767To765;
                default -> null;
            };
            default -> null;
        };
    }

    /**
     * 受信側プロトコルでのレジストリの全エントリ名。順序が変わった
     * レジストリで、送信側に無いエントリ（767だけにある絵画など）を
     * hasData=false で埋めるときに使う。765は766と同じ表を使う
     * （enchantment だけが765固有の表）。
     */
    private static String[] registryTargetNames(String registryId, int toProtocol) {
        int to = (toProtocol == 763 || toProtocol == 764) ? 765 : toProtocol;
        return switch (registryId) {
            case "minecraft:enchantment" -> switch (to) {
                case 765 -> ENCHANTMENT_765_NAMES;
                case 766 -> ENCHANTMENT_766_NAMES;
                case 767 -> ENCHANTMENT_767_NAMES;
                default -> null;
            };
            case "minecraft:painting_variant" -> to == 767 ? PAINTING_767_NAMES : PAINTING_766_NAMES;
            case "minecraft:banner_pattern" -> to == 767 ? BANNER_767_NAMES : BANNER_766_NAMES;
            case "minecraft:trim_pattern" -> to == 767 ? TRIM_767_NAMES : TRIM_766_NAMES;
            case "minecraft:damage_type" -> to == 767 ? DAMAGE_767_NAMES : DAMAGE_766_NAMES;
            default -> null;
        };
    }

    /**
     * 1つのレジストリのエントリ列を、送信側の順序から受信側の順序へ
     * 並べ替える。受信側に無いエントリは落とし、送信側に無いエントリ
     * （受信側だけにあるもの）は名前だけのエントリ（hasData=false）
     * で埋める。埋めたエントリは数値 id の位置合わせのためで、実機
     * クライアントでの扱いは未確認。エントリの element（NBT）は
     * そのまま引き継ぐ。767→766/765 では sweeping→sweeping_edge の
     * 改名もここで吸収する（名前は受信側の表で置き換える）。
     */
    private static List<Tag> reorderRegistryEntries(String registryId,
            List<Tag> entries, int fromProtocol, int toProtocol) {
        java.util.function.IntUnaryOperator map =
                registryIdMapper(registryId, fromProtocol, toProtocol);
        if (map == null) {
            return entries;
        }
        String[] targetNames = registryTargetNames(registryId, toProtocol);
        List<Tag> out = new ArrayList<>(java.util.Collections.nCopies(targetNames.length, null));
        for (int i = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof Tag.CompoundTag compound)) {
                continue;
            }
            int srcId = compound.get("id")
                    .filter(t -> t instanceof Tag.IntTag)
                    .map(t -> ((Tag.IntTag) t).value())
                    .orElse(i);
            int targetId = map.applyAsInt(srcId);
            if (targetId < 0 || targetId >= targetNames.length) {
                continue;
            }
            Map<String, Tag> entry = new LinkedHashMap<>();
            entry.put("name", new Tag.StringTag(targetNames[targetId]));
            entry.put("id", new Tag.IntTag(targetId));
            compound.get("element").ifPresent(t -> entry.put("element", t));
            out.set(targetId, new Tag.CompoundTag(entry));
        }
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == null) {
                Map<String, Tag> entry = new LinkedHashMap<>();
                entry.put("name", new Tag.StringTag(targetNames[i]));
                entry.put("id", new Tag.IntTag(i));
                out.set(i, new Tag.CompoundTag(entry));
            }
        }
        return out;
    }

    /**
     * 保持したレジストリ（codec 形式）のうち、順序が変わったレジストリを
     * 受信側の順序に並べ替える。763／764は765として扱う。
     * {@code minecraft:jukebox_song} は766／765には存在しないため、
     * 受信側が766／765の場合はレジストリごと落とす。
     * 入力のマップは変更しない（新しいマップを返す）。
     */
    public static Map<String, Tag.CompoundTag> reorderRegistries(
            Map<String, Tag.CompoundTag> registries, int fromProtocol, int toProtocol) {
        Map<String, Tag.CompoundTag> out = new LinkedHashMap<>();
        for (Map.Entry<String, Tag.CompoundTag> registry : registries.entrySet()) {
            String registryId = registry.getKey();
            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21) {
                continue;
            }
            List<Tag> entries = registry.getValue().getList("value")
                    .map(Tag.ListTag::value)
                    .orElse(List.of());
            List<Tag> reordered = reorderRegistryEntries(
                    registryId, entries, fromProtocol, toProtocol);
            if (reordered == entries) {
                out.put(registryId, registry.getValue());
                continue;
            }
            Map<String, Tag> codec = new LinkedHashMap<>();
            codec.put("type", new Tag.StringTag(registryId));
            codec.put("value", new Tag.ListTag(Nbt.COMPOUND, reordered));
            out.put(registryId, new Tag.CompoundTag(codec));
        }
        return out;
    }

    /**
     * 766の Registry Data（レジストリごとのエントリ配列）を767形式にする。
     * 順序が変わったレジストリを並べ替え、767だけにあるエントリは
     * hasData=false で埋める。1レジストリ分のペイロードを受け取り、
     * 1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData766To767(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_20_5, ProtocolVersion.V1_21);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 767の Registry Data を766形式にする。{@code minecraft:jukebox_song}
     * は766に存在しないため {@code null} を返す（呼び出し側で落とす）。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData767To766(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21, ProtocolVersion.V1_20_5);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }

    /**
     * 765／764の Registry Data（1個の codec NBT）を767形式にする。
     * 766のエントリ配列は id を持たないためバイト列経由の合成はできない
     * （enchantment でずれる）ので、codec 段階で765→767へ直接並べ替えて
     * レジストリごとのペイロードのリストを返す。
     */
    public static List<byte[]> translateRegistryData765To767(byte[] payload) {
        return buildRegistryData766(reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_20_4, ProtocolVersion.V1_21));
    }

    /**
     * 767の Registry Data を765／764形式（1個の codec NBT）にする。
     * {@code minecraft:jukebox_song} の場合は {@code null} を返す
     * （呼び出し側で落とす）。
     */
    public static byte[] translateRegistryData767To765(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21, ProtocolVersion.V1_20_4);
        if (reordered.isEmpty()) {
            return null;
        }
        Map<String, Tag> root = new LinkedHashMap<>();
        for (Map.Entry<String, Tag.CompoundTag> registry : reordered.entrySet()) {
            root.put(registry.getKey(), registry.getValue());
        }
        return Nbt.writeAnonymous(new Tag.CompoundTag(root));
    }
"""
    text = insert_once(text,
        "    /** 763の Join Game の中身。codec は名前付き NBT（ルート名は空文字）。 */",
        registry_block)

    PATH.write_text(text, encoding="utf-8")
    print("PlayTranslator.java へ767を追加しました。")


if __name__ == "__main__":
    main()
