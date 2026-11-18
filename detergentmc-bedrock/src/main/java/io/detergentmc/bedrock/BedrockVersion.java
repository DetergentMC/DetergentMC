package io.detergentmc.bedrock;

/**
 * Bedrock クライアントのバージョン（プロトコル番号ごとの形式差分）。
 *
 * <p>プロトコル番号は wiki.vg「Bedrock Protocol version numbers」と
 * PrismarineJS minecraft-data の bedrock protocol.json で 1 つずつ確認した。
 * 各バージョンの StartGame・Resource Packs・Item の形式も minecraft-data
 * の protocol.json（1.20.0 / 1.20.10 / 1.20.30 / 1.20.40 / 1.20.61 /
 * 1.20.71 / 1.20.80 / 1.21.0 / 1.21.2 / 1.21.20 / 1.21.30 / 1.21.42 /
 * 1.21.50 / 1.21.60 / 1.21.70 / 1.21.80 / 1.21.90 / 1.21.93 / 1.21.100 /
 * 1.21.111 / 1.21.120 / 1.21.130 / 1.26.0 / 1.26.10 / 1.26.20 / 1.26.30 /
 * 1.26.40 / 1.26.45 / 1.26.51 の 29 ファイル）から読み取った。
 * 形式が変わる境界はすべて実ファイルで確認済みである。
 *
 * <p>26.60（プロトコル 2225）は NetherNet 世代のため RakNet では繋がらない。
 * {@link #NETHERNET_PROTOCOL} を参照。
 */
public enum BedrockVersion {
    V1_20_0(589, "1.20.0"),
    V1_20_10(594, "1.20.10"),
    V1_20_30(618, "1.20.30"),
    V1_20_40(622, "1.20.40"),
    V1_20_50(630, "1.20.50"),
    V1_20_60(649, "1.20.60"),
    V1_20_70(662, "1.20.70"),
    V1_20_80(671, "1.20.80"),
    V1_21_0(685, "1.21.0"),
    V1_21_2(686, "1.21.2"),
    V1_21_20(712, "1.21.20"),
    V1_21_30(729, "1.21.30"),
    V1_21_40(748, "1.21.40"),
    V1_21_50(766, "1.21.50"),
    V1_21_60(776, "1.21.60"),
    V1_21_70(786, "1.21.70"),
    V1_21_80(800, "1.21.80"),
    V1_21_90(818, "1.21.90"),
    V1_21_93(819, "1.21.93"),
    V1_21_100(827, "1.21.100"),
    V1_21_111(844, "1.21.111"),
    V1_21_120(859, "1.21.120"),
    V1_21_130(898, "1.21.130"),
    V1_26_0(924, "1.26.0"),
    V1_26_10(944, "1.26.10"),
    V1_26_20(975, "1.26.20"),
    V1_26_30(1001, "1.26.30"),
    V1_26_40(2168, "1.26.40"),
    V1_26_45(2169, "1.26.45"),
    V1_26_50(2193, "1.26.50");

    /** NetherNet 世代のプロトコル番号（26.60 / 26.62）。RakNet では繋がらない。 */
    public static final int NETHERNET_PROTOCOL = 2225;

    private final int protocol;
    private final String displayName;

    BedrockVersion(int protocol, String displayName) {
        this.protocol = protocol;
        this.displayName = displayName;
    }

    /** プロトコル番号。 */
    public int protocol() {
        return protocol;
    }

    /** 最初にそのプロトコルを使った Bedrock のバージョン名。 */
    public String displayName() {
        return displayName;
    }

    /** プロトコル番号からバージョンを引く。対応外なら null。 */
    public static BedrockVersion fromProtocol(int protocol) {
        for (BedrockVersion v : values()) {
            if (v.protocol == protocol) {
                return v;
            }
        }
        return null;
    }

    /** 対応しているプロトコル番号か。 */
    public static boolean isSupportedProtocol(int protocol) {
        return fromProtocol(protocol) != null;
    }

    /** NetherNet 世代（26.60 / 26.62）のプロトコルか。 */
    public static boolean isNetherNetProtocol(int protocol) {
        return protocol == NETHERNET_PROTOCOL;
    }

    // ---- StartGame の形式 ----

    /** hardcore フィールドがある（1.20.80 以降）。 */
    public boolean hasHardcore() {
        return protocol >= 671;
    }

    /**
     * level_id の前に server/world/scenario の識別子文字列がある
     * （1.21.0〜1.21.80）。1.21.90 以降は owner_identifier が加わる。
     */
    public boolean hasPreLevelIdentifiers() {
        return protocol >= 685 && protocol <= 800;
    }

    /** level_id の前に owner_identifier もある（1.21.90〜1.21.130）。 */
    public boolean hasOwnerIdentifier() {
        return protocol >= 818 && protocol <= 898;
    }

    /** movement_authority フィールドがある（1.21.80 まで）。 */
    public boolean hasMovementAuthority() {
        return protocol <= 800;
    }

    /** itemstates フィールドがある（1.21.40 まで）。 */
    public boolean hasItemstates() {
        return protocol <= 748;
    }

    /** tick_death_systems フィールドがある（1.21.100〜1.21.120 のみ）。 */
    public boolean hasTickDeathSystems() {
        return protocol >= 827 && protocol <= 859;
    }

    /**
     * 末尾に has_server_join_info・server_join_info・識別子 4 つがある
     * （26.0 以降）。1.21.130 までは識別子は level_id の前にある。
     */
    public boolean hasJoinInfoTail() {
        return protocol >= 924;
    }

    /** server_editor_connection_policy 等の編集者向け項目がある（26.30 以降）。 */
    public boolean hasEditorPolicy() {
        return protocol >= 1001;
    }

    /** is_chat_logging フィールドがある（26.30 のみ。26.40 で消えた）。 */
    public boolean hasChatLogging() {
        return protocol == 1001;
    }

    // ---- Resource Packs Info の形式 ----

    /** has_addons フィールドがある（1.20.70 以降）。 */
    public boolean hasAddons() {
        return protocol >= 662;
    }

    /** force_server_packs フィールドがある（1.21.20 まで）。 */
    public boolean hasForceServerPacks() {
        return protocol <= 712;
    }

    /** behaviour_packs の一覧がある（1.21.20 まで）。 */
    public boolean hasBehaviourPacks() {
        return protocol <= 712;
    }

    /** resource_pack_links の一覧がある（1.20.30〜1.21.30）。 */
    public boolean hasResourcePackLinks() {
        return protocol >= 618 && protocol <= 729;
    }

    /** world_template（UUID＋バージョン）がある（1.21.50 以降）。 */
    public boolean hasWorldTemplate() {
        return protocol >= 766;
    }

    /** disable_vibrant_visuals フィールドがある（1.21.90 以降）。 */
    public boolean hasDisableVibrantVisuals() {
        return protocol >= 818;
    }

    /** texture_packs の件数が varint（26.40 以降）。それ以前は li16。 */
    public boolean resourcePacksCountIsVarint() {
        return protocol >= 2168;
    }

    // ---- Resource Pack Stack の形式 ----

    /** behavior_packs の一覧がある（1.21.120 まで）。 */
    public boolean hasBehaviorPacksInStack() {
        return protocol <= 859;
    }

    /** has_editor_packs フィールドがある（1.20.70 以降）。 */
    public boolean hasEditorPacksInStack() {
        return protocol >= 662;
    }

    // ---- Item の形式（各パケットごと） ----

    /** MobEquipment のアイテムが ItemV4（26.20 以降）。 */
    public boolean mobEquipmentUsesItemV4() {
        return protocol >= 975;
    }

    /** MobArmorEquipment のアイテムが ItemV4（26.30 以降）。 */
    public boolean mobArmorUsesItemV4() {
        return protocol >= 1001;
    }

    /** AddItemEntity・AddPlayer のアイテムが ItemV4（26.40 以降）。 */
    public boolean addItemUsesItemV4() {
        return protocol >= 2168;
    }

    /** MobArmorEquipment に body（5 個目）がある（1.21.20 以降）。 */
    public boolean mobArmorHasBodySlot() {
        return protocol >= 712;
    }

    // ---- InventoryTransaction（クライアント→サーバー）の形式 ----

    /** 取引の legacy_transactions が option 形式（26.30 以降）。 */
    public boolean transactionLegacyIsOption() {
        return protocol >= 1001;
    }

    /** 取引のアイテムが ItemV4（26.30 以降）。 */
    public boolean transactionUsesItemV4() {
        return protocol >= 1001;
    }

    /** transaction_type・actions が option 形式（26.40 以降）。 */
    public boolean transactionFieldsAreOptions() {
        return protocol >= 2168;
    }

    // ---- アイテム対応表の読み込み ----

    /**
     * バージョン別アイテム対応表（{@code bedrock_item_ids_<tag>.bin}）の
     * タグ。1.20.50 は BedrockItemIds.java の表と同じ世代のため
     * {@code 1_20_50} を返す。
     */
    public String itemTableTag() {
        return switch (protocol) {
            case 589 -> "1_20_0";
            case 594 -> "1_20_10";
            case 618 -> "1_20_30";
            case 622 -> "1_20_40";
            case 630 -> "1_20_50";
            case 649 -> "1_20_60";
            case 662 -> "1_20_70";
            case 671 -> "1_20_80";
            case 685, 686 -> "1_21_0";
            case 712 -> "1_21_20";
            case 729 -> "1_21_30";
            case 748 -> "1_21_40";
            case 766 -> "1_21_50";
            case 776 -> "1_21_60";
            case 786 -> "1_21_70";
            case 800 -> "1_21_80";
            case 818 -> "1_21_90";
            case 819, 827 -> "1_21_93";
            case 844 -> "1_21_111";
            case 859 -> "1_21_120";
            case 898 -> "1_21_130";
            case 924 -> "1_26_0";
            case 944 -> "1_26_10";
            case 975 -> "1_26_20";
            case 1001, 2168, 2169, 2193 -> "1_26_30";
            default -> null;
        };
    }
}
