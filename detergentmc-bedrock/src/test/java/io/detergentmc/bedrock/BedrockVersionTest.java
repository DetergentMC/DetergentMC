package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Bedrock バージョン表（プロトコル番号・名称・形式ゲート）のテスト。 */
class BedrockVersionTest {

    @Test
    void allThirtySupportedProtocolsResolve() {
        int[] protocols = {589, 594, 618, 622, 630, 649, 662, 671, 685, 686,
                712, 729, 748, 766, 776, 786, 800, 818, 819, 827,
                844, 859, 898, 924, 944, 975, 1001, 2168, 2169, 2193};
        String[] names = {"1.20.0", "1.20.10", "1.20.30", "1.20.40", "1.20.50",
                "1.20.60", "1.20.70", "1.20.80", "1.21.0", "1.21.2",
                "1.21.20", "1.21.30", "1.21.40", "1.21.50", "1.21.60", "1.21.70",
                "1.21.80", "1.21.90", "1.21.93", "1.21.100",
                "1.21.111", "1.21.120", "1.21.130", "1.26.0", "1.26.10", "1.26.20",
                "1.26.30", "1.26.40", "1.26.45", "1.26.50"};
        assertEquals(30, protocols.length, "対応プロトコルは 30 個");
        assertEquals(30, names.length);
        for (int i = 0; i < protocols.length; i++) {
            BedrockVersion version = BedrockVersion.fromProtocol(protocols[i]);
            assertEquals(names[i], version.displayName(),
                    "プロトコル " + protocols[i] + " の表示名");
            assertTrue(BedrockVersion.isSupportedProtocol(protocols[i]));
            assertEquals(protocols[i], version.protocol());
        }
    }

    @Test
    void unknownAndNetherNetProtocolsAreNotSupported() {
        assertNull(BedrockVersion.fromProtocol(0));
        assertNull(BedrockVersion.fromProtocol(588));
        assertNull(BedrockVersion.fromProtocol(631));
        assertNull(BedrockVersion.fromProtocol(2194));
        assertFalse(BedrockVersion.isSupportedProtocol(2225), "26.60 は NetherNet で対象外");
        assertFalse(BedrockVersion.isSupportedProtocol(0));
        assertTrue(BedrockVersion.isNetherNetProtocol(2225));
        assertFalse(BedrockVersion.isNetherNetProtocol(2193));
        assertEquals(2225, BedrockVersion.NETHERNET_PROTOCOL);
    }

    @Test
    void startGameGatesMatchVerifiedShapes() {
        // Shape A（1.20.0〜1.20.70）：hardcore・識別子・編集者項目なし。
        assertFalse(BedrockVersion.V1_20_0.hasHardcore());
        assertFalse(BedrockVersion.V1_20_0.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_20_0.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_20_0.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_20_0.hasItemstates());
        // Shape A2（1.20.80）：hardcore が追加。
        assertTrue(BedrockVersion.V1_20_80.hasHardcore());
        assertFalse(BedrockVersion.V1_20_80.hasPreLevelIdentifiers());
        // Shape B（1.21.0〜1.21.40）：level_id 前に識別子 3 つ。
        assertTrue(BedrockVersion.V1_21_0.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_21_0.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_21_0.hasItemstates());
        // Shape B2（1.21.50〜1.21.80）：itemstates が無くなる。
        assertFalse(BedrockVersion.V1_21_50.hasItemstates());
        assertTrue(BedrockVersion.V1_21_50.hasMovementAuthority());
        // Shape C（1.21.90〜1.21.130）：owner 識別子が追加、movement_authority が無くなる。
        assertTrue(BedrockVersion.V1_21_90.hasOwnerIdentifier());
        assertFalse(BedrockVersion.V1_21_90.hasMovementAuthority());
        assertFalse(BedrockVersion.V1_21_90.hasTickDeathSystems());
        // Shape C2（1.21.100〜1.21.120）：tick_death_systems だけ追加。
        assertTrue(BedrockVersion.V1_21_100.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_21_130.hasTickDeathSystems());
        // Shape D（26.0 以降）：識別子は末尾、has_server_join_info が付く。
        assertFalse(BedrockVersion.V1_26_0.hasPreLevelIdentifiers());
        assertTrue(BedrockVersion.V1_26_0.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_26_0.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_26_0.hasChatLogging());
        // Shape D2（26.30）：編集者項目と is_chat_logging が付く。
        assertTrue(BedrockVersion.V1_26_30.hasEditorPolicy());
        assertTrue(BedrockVersion.V1_26_30.hasChatLogging());
        // Shape D3（26.40 以降）：is_chat_logging が無くなる。
        assertFalse(BedrockVersion.V1_26_40.hasChatLogging());
        assertTrue(BedrockVersion.V1_26_40.hasEditorPolicy());
    }

    @Test
    void resourcePackGatesMatchVerifiedShapes() {
        assertFalse(BedrockVersion.V1_20_0.hasAddons());
        assertFalse(BedrockVersion.V1_20_0.hasResourcePackLinks());
        assertTrue(BedrockVersion.V1_20_30.hasResourcePackLinks());
        assertTrue(BedrockVersion.V1_20_70.hasAddons());
        assertTrue(BedrockVersion.V1_20_70.hasForceServerPacks());
        assertTrue(BedrockVersion.V1_21_20.hasBehaviourPacks());
        assertFalse(BedrockVersion.V1_21_30.hasBehaviourPacks());
        assertTrue(BedrockVersion.V1_21_30.hasResourcePackLinks());
        assertFalse(BedrockVersion.V1_21_40.hasResourcePackLinks());
        assertTrue(BedrockVersion.V1_21_50.hasWorldTemplate());
        assertFalse(BedrockVersion.V1_21_50.hasBehaviourPacks());
        assertTrue(BedrockVersion.V1_21_90.hasDisableVibrantVisuals());
        assertTrue(BedrockVersion.V1_21_120.hasBehaviorPacksInStack());
        assertFalse(BedrockVersion.V1_21_130.hasBehaviorPacksInStack());
        assertTrue(BedrockVersion.V1_20_70.hasEditorPacksInStack());
        assertFalse(BedrockVersion.V1_20_60.hasEditorPacksInStack());
        // ResourcePacksInfo の件数は 26.40 以降だけ varint。
        assertFalse(BedrockVersion.V1_26_30.resourcePacksCountIsVarint());
        assertTrue(BedrockVersion.V1_26_40.resourcePacksCountIsVarint());
    }

    @Test
    void itemGatesMatchVerifiedShapes() {
        assertFalse(BedrockVersion.V1_20_50.mobEquipmentUsesItemV4());
        assertTrue(BedrockVersion.V1_26_20.mobEquipmentUsesItemV4());
        assertFalse(BedrockVersion.V1_26_20.mobArmorUsesItemV4());
        assertTrue(BedrockVersion.V1_26_30.mobArmorUsesItemV4());
        assertFalse(BedrockVersion.V1_26_30.addItemUsesItemV4());
        assertTrue(BedrockVersion.V1_26_40.addItemUsesItemV4());
        assertFalse(BedrockVersion.V1_21_2.mobArmorHasBodySlot());
        assertTrue(BedrockVersion.V1_21_20.mobArmorHasBodySlot());
        assertFalse(BedrockVersion.V1_20_50.transactionLegacyIsOption());
        assertTrue(BedrockVersion.V1_26_30.transactionLegacyIsOption());
        assertFalse(BedrockVersion.V1_26_30.transactionFieldsAreOptions());
        assertTrue(BedrockVersion.V1_26_40.transactionFieldsAreOptions());
    }

    @Test
    void itemTableTagsAreKnown() {
        assertEquals("1_20_0", BedrockVersion.V1_20_0.itemTableTag());
        assertEquals("1_20_50", BedrockVersion.V1_20_50.itemTableTag());
        assertEquals("1_21_0", BedrockVersion.V1_21_0.itemTableTag());
        assertEquals("1_21_0", BedrockVersion.V1_21_2.itemTableTag());
        assertEquals("1_21_93", BedrockVersion.V1_21_93.itemTableTag());
        assertEquals("1_21_93", BedrockVersion.V1_21_100.itemTableTag());
        assertEquals("1_26_30", BedrockVersion.V1_26_30.itemTableTag());
        assertEquals("1_26_30", BedrockVersion.V1_26_50.itemTableTag());
    }
}
