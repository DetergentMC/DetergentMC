package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Direction;
import org.junit.jupiter.api.Test;

/**
 * クライアントと内部サーバーの全組み合わせ（763／764／765／766 × 763／764／765／766）の
 * 振り分けと、763↔764の直接対応表の検査。
 *
 * <p>763→764の表は763→765表と765→764表を合成したものである。二段変換
 * （763→765→764）はしていないが、合成の都合で「近いものへの代替」が
 * ずれる箇所がある（下記の既知の不一致を参照）。これは既存の
 * 763→765／765→764の表と同じ「近いブロックへの代替」の方針であり、
 * 誤読ではなく表示のずれに留まる。
 */
class ClientAdapterCrossVersionTest {

    // ------------------------------------------------------------------
    // 振り分け
    // ------------------------------------------------------------------

    @Test
    void directionForAllPairs() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(763, 763));
        assertEquals(Direction.NATIVE, ClientAdapter.direction(764, 764));
        assertEquals(Direction.NATIVE, ClientAdapter.direction(765, 765));
        assertEquals(Direction.NATIVE, ClientAdapter.direction(766, 766));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 764));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 765));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 766));
        assertEquals(Direction.UP, ClientAdapter.direction(764, 765));
        assertEquals(Direction.UP, ClientAdapter.direction(764, 766));
        assertEquals(Direction.UP, ClientAdapter.direction(765, 766));
        assertEquals(Direction.DOWN, ClientAdapter.direction(764, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(765, 764));
        assertEquals(Direction.DOWN, ClientAdapter.direction(765, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(766, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(766, 764));
        assertEquals(Direction.DOWN, ClientAdapter.direction(766, 765));
    }

    @Test
    void decideAcceptsAllSupportedPairs() {
        for (int client : new int[]{763, 764, 765, 766}) {
            for (int server : new int[]{763, 764, 765, 766}) {
                ClientAdapter.Handshake handshake = ClientAdapter.decide(client, server);
                assertTrue(handshake.accepted(),
                        "client=" + client + " server=" + server);
                if (client == server) {
                    assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                    assertFalse(handshake.needsTranslation());
                } else {
                    assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                    assertTrue(handshake.needsTranslation());
                }
            }
        }
    }

    @Test
    void decideRejectsOutOfRange() {
        assertFalse(ClientAdapter.decide(757, 765).accepted());
        assertFalse(ClientAdapter.decide(777, 765).accepted());
        assertFalse(ClientAdapter.decide(757, 764).accepted());
        assertFalse(ClientAdapter.decide(777, 764).accepted());
        assertFalse(ClientAdapter.decide(777, 766).accepted());
        assertFalse(ClientAdapter.decide(757, 766).accepted());
        assertFalse(ClientAdapter.decide(777, 768).accepted());
        assertFalse(ClientAdapter.decide(757, 768).accepted());
        assertTrue(ClientAdapter.decide(768, 765).accepted());
        assertTrue(ClientAdapter.decide(768, 764).accepted());
        assertTrue(ClientAdapter.decide(768, 766).accepted());
    }

    @Test
    void versionNameForAllProtocols() {
        assertEquals("1.20.1", ClientAdapter.versionName(763));
        assertEquals("1.20.2", ClientAdapter.versionName(764));
        assertEquals("1.20.4", ClientAdapter.versionName(765));
        assertEquals("1.20.5", ClientAdapter.versionName(766));
    }

    // ------------------------------------------------------------------
    // ステータス書き換え
    // ------------------------------------------------------------------

    @Test
    void rewriteStatusJsonForAllPairs() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String json764 = "{\"version\":{\"name\":\"1.20.2\",\"protocol\":764},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        // 内部765→763クライアント。
        String to763 = ClientAdapter.rewriteStatusJson(json765, 765, 763);
        assertTrue(to763.contains("\"protocol\":763"), to763);
        assertTrue(to763.contains("\"name\":\"1.20.1\""), to763);
        // 内部765→764クライアント。
        String to764 = ClientAdapter.rewriteStatusJson(json765, 765, 764);
        assertTrue(to764.contains("\"protocol\":764"), to764);
        assertTrue(to764.contains("\"name\":\"1.20.2\""), to764);
        // 内部764→765クライアント。
        String to765 = ClientAdapter.rewriteStatusJson(json764, 764, 765);
        assertTrue(to765.contains("\"protocol\":765"), to765);
        assertTrue(to765.contains("\"name\":\"1.20.4\""), to765);
        // 内部764→763クライアント。
        String to763From764 = ClientAdapter.rewriteStatusJson(json764, 764, 763);
        assertTrue(to763From764.contains("\"protocol\":763"), to763From764);
        assertTrue(to763From764.contains("\"name\":\"1.20.1\""), to763From764);
        // 同じプロトコルはそのまま。
        assertEquals(json764, ClientAdapter.rewriteStatusJson(json764, 764, 764));
        assertEquals(json765, ClientAdapter.rewriteStatusJson(json765, 765, 765));
    }

    // ------------------------------------------------------------------
    // 763↔764の直接対応表
    // ------------------------------------------------------------------

    /**
     * ブロック状態の合成表の不一致は141件（26644件中）。
     * すべて765＝8843〜9106（頭蓋骨系の {@code powered} 違いによる
     * 衝突）と765＝10366の1件に集まっている。合成表は「近いものへの
     * 代替」であり、誤読にはならない。
     */
    @Test
    void blockStateRoundTripHasKnownMismatchesOnly() {
        int mismatch = 0;
        for (int id765 = 0; id765 <= RegistryRemapper.MAX_BLOCK_STATE_765; id765++) {
            int id763 = RegistryRemapper.mapBlockState(id765);
            int via763 = ClientAdapter.mapBlockState763To764(id763);
            int direct = RegistryRemapper.mapBlockState764(id765);
            if (via763 != direct) {
                mismatch++;
                assertTrue((id765 >= 8843 && id765 <= 9106) || id765 == 10366,
                        "unexpected block state mismatch at 765=" + id765);
            }
        }
        assertEquals(141, mismatch);
    }

    /** 音の合成表の不一致は11件（1539件中）。 */
    @Test
    void soundRoundTripHasKnownMismatchesOnly() {
        int mismatch = 0;
        for (int id765 = 0; id765 <= RegistryRemapper.MAX_SOUND_765; id765++) {
            int id763 = RegistryRemapper.mapSound(id765);
            int via763 = ClientAdapter.mapSound763To764(id763);
            int direct = RegistryRemapper.mapSound764(id765);
            if (via763 != direct) {
                mismatch++;
            }
        }
        assertEquals(11, mismatch);
    }

    /** パーティクルの合成は完全（101件中0件の不一致）。 */
    @Test
    void particleRoundTripIsExact() {
        for (int id765 = 0; id765 <= RegistryRemapper.MAX_PARTICLE_765; id765++) {
            int id763 = RegistryRemapper.mapParticle(id765);
            assertEquals(RegistryRemapper.mapParticle764(id765),
                    ClientAdapter.mapParticle763To764(id763),
                    "particle 765=" + id765);
        }
    }

    /** アイテムの合成は完全（逆写像で作ったため）。 */
    @Test
    void itemRoundTripIsExact() {
        for (int id765 = 0; id765 <= RegistryRemapper.MAX_ITEM_765; id765++) {
            int id763 = RegistryRemapper.mapItem(id765);
            assertEquals(RegistryRemapper.mapItem764(id765),
                    ClientAdapter.mapItem763To764(id763),
                    "item 765=" + id765);
        }
    }

    @Test
    void identityMapsForEntityBlockAndMenu() {
        for (int id = 0; id <= RegistryRemapper.MAX_ENTITY_TYPE_764; id++) {
            assertEquals(id, ClientAdapter.mapEntityType763To764(id));
            assertEquals(id, ClientAdapter.mapEntityType764To763(id));
        }
        for (int id = 0; id <= RegistryRemapper.MAX_BLOCK_764; id++) {
            assertEquals(id, ClientAdapter.mapBlock763To764(id));
            assertEquals(id, ClientAdapter.mapBlock764To763(id));
        }
        for (int id = 0; id <= RegistryRemapper.MAX_MENU_764; id++) {
            assertEquals(id, ClientAdapter.mapMenu763To764(id));
            assertEquals(id, ClientAdapter.mapMenu764To763(id));
        }
        // 範囲外は代替値（ブタ／石／作業台）。
        assertEquals(RegistryRemapper.PIG_ENTITY_TYPE_764,
                ClientAdapter.mapEntityType763To764(200));
        assertEquals(RegistryRemapper.STONE_BLOCK_764,
                ClientAdapter.mapBlock763To764(2000));
        assertEquals(RegistryRemapper.CRAFTING_MENU_764,
                ClientAdapter.mapMenu763To764(24));
    }

    @Test
    void effectMapsShiftByOne() {
        // 763は1始まり、764は0始まり。
        for (int id763 = 1; id763 <= RegistryRemapper.MAX_EFFECT_764 + 1; id763++) {
            assertEquals(id763 - 1, ClientAdapter.mapEffect763To764(id763));
            assertEquals(id763, ClientAdapter.mapEffect764To763(id763 - 1));
        }
        // 範囲外は Speed（763＝1、764＝0）。
        assertEquals(0, ClientAdapter.mapEffect763To764(0));
        assertEquals(0, ClientAdapter.mapEffect763To764(34));
        assertEquals(1, ClientAdapter.mapEffect764To763(33));
    }

    @Test
    void blockEntityTypeDropsNewTypes() {
        for (int id = 0; id <= 40; id++) {
            assertEquals(id, ClientAdapter.mapBlockEntityType763To764(id));
            assertEquals(id, ClientAdapter.mapBlockEntityType764To763(id));
        }
        // 41／42（crafter／trial_spawner）は763にも764にも無い。
        assertEquals(RegistryRemapper.DROP, ClientAdapter.mapBlockEntityType763To764(41));
        assertEquals(RegistryRemapper.DROP, ClientAdapter.mapBlockEntityType763To764(42));
        assertEquals(RegistryRemapper.DROP, ClientAdapter.mapBlockEntityType764To763(41));
        assertEquals(RegistryRemapper.DROP, ClientAdapter.mapBlockEntityType764To763(42));
    }

    @Test
    void differencesForDownAndDirectPairs() {
        // ネイティブは差分なし。
        assertTrue(ClientAdapter.differences(764, 764).isEmpty());
        assertTrue(ClientAdapter.differences(765, 765).isEmpty());
        // 下向き（765クライアント・内部764）は差分がある。
        assertFalse(ClientAdapter.differences(765, 764).isEmpty());
        // 763クライアント・内部764は直接の763→764の差分がある。
        assertFalse(ClientAdapter.differences(763, 764).isEmpty());
    }
}
