"""210（1.10／1.10.1／1.10.2）の単体テストを生成する。

315 のテストを写して番号を差し替える。レジストリ・PacketIds・
ClientAdapter・ProtocolVersion は 210 固有の内容（実 jar 抽出の
数値、近似方針、36プロトコルの固定）で置き換える。
ChunkTranslator には形式差分4件（spawn type の u8、title の
action 番号、collect、block_place のカーソル）の検証を足す。

使い方：
  python3 tools/gen-210-tests.py
"""

from pathlib import Path

TEST = (Path(__file__).parent.parent / "src" / "test" / "java"
        / "io" / "detergentmc" / "protocol")


def rename_315_to_210(text):
    text = text.replace("V1_11", "V1_10_2")
    text = text.replace("1.11", "1.10.2")
    text = text.replace("315", "210")
    return text


def copy_test(src_name, dst_name, transform=None):
    text = (TEST / src_name).read_text(encoding="utf-8")
    text = rename_315_to_210(text)
    if transform is not None:
        text = transform(text)
    (TEST / dst_name).write_text(text, encoding="utf-8")
    print(f"{dst_name} を生成した")


REGISTRY_210 = '''package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 210（1.10／1.10.1／1.10.2）のレジストリ写像。210 は 315 と
 * パケット ID が完全同一だが、レジストリは 1.11 の追加分が無い：
 * ブロックは observer (218) とシュルカーボックス16種 (219〜234)
 * が無く219種、アイテムは371種（totem 449・shulker_shell 450・
 * iron_nugget 452 も無い）、エンティティは 1.11 新規モブ4種が
 * 無く、亜種9種は基本種＋メタデータだったため独立 ID が無い。
 * ground truth は実バニラ 1.10.2 jar（SHA-1
 * 3d501b23df53c548254f5e3f66492d178a48db63）からの抽出。
 * 近似方針は docs/PROXY-1.10.2.md。
 */
class RegistryRemapper210Test {

    @Test
    void maxConstantsAreThe1102Values() {
        assertEquals(4095, RegistryRemapper.MAX_BLOCK_STATE_210);
        assertEquals(2267, RegistryRemapper.MAX_ITEM_210);
        assertEquals(200, RegistryRemapper.MAX_ENTITY_TYPE_210);
        assertEquals(462, RegistryRemapper.MAX_SOUND_210);
        assertEquals(46, RegistryRemapper.MAX_PARTICLE_210);
        assertEquals(255, RegistryRemapper.MAX_BLOCK_210);
        assertEquals(167, RegistryRemapper.MAX_BIOME_210);
        assertEquals(41, RegistryRemapper.MAX_DAMAGE_TYPE_210);
        assertEquals(22, RegistryRemapper.MAX_BLOCK_ENTITY_210);
        assertEquals(27, RegistryRemapper.MAX_EFFECT_210);
    }

    @Test
    void blockStateSemanticAnchors() {
        // 空気は 0、石は (1<<4)|0 = 16。共有ブロックは 315 と同一。
        assertEquals(0, RegistryRemapper.mapBlockState210To758(0));
        assertEquals(16, RegistryRemapper.mapBlockState758To210(1));
        assertEquals(16, RegistryRemapper.mapBlockState758To210(
                RegistryRemapper.mapBlockState210To758(16)));
        int whiteWool758 = RegistryRemapper.mapBlockState210To758(560);
        assertEquals(560, RegistryRemapper.mapBlockState758To210(whiteWool758));
        int logY = RegistryRemapper.mapBlockState210To758(17 << 4);
        int logX = RegistryRemapper.mapBlockState210To758((17 << 4) | 4);
        assertEquals(17 << 4, RegistryRemapper.mapBlockState758To210(logY));
        assertEquals((17 << 4) | 4, RegistryRemapper.mapBlockState758To210(logX));
    }

    @Test
    void newIn111BlocksApproximate() {
        // observer (218) → ドロッパー (158)。メタデータは同一
        // レイアウト（向き＋電源）のため保持される。
        int observer758 = RegistryRemapper.mapBlockState316To758((218 << 4) | 2);
        assertEquals((158 << 4) | 2,
                RegistryRemapper.mapBlockState758To210(observer758));
        // シュルカーボックス (219〜234) → チェスト (54)。
        // 向き 0/1 はチェストの無効な向きのため 2（北）へ寄せる。
        int purple758 = RegistryRemapper.mapBlockState316To758(229 << 4);
        assertEquals((54 << 4) | 2,
                RegistryRemapper.mapBlockState758To210(purple758));
        int white758 = RegistryRemapper.mapBlockState316To758((219 << 4) | 5);
        assertEquals((54 << 4) | 2,
                RegistryRemapper.mapBlockState758To210(white758));
        // 注：シュルカーボックスの向きは 316 の表自体が全状態を
        // 1つの 758 状態へ畳むため、210 側でも向きは残らない
        // （316 から引き継いだ制限）。
        // 逆向き：210 に無い範囲 (218〜234) は石（758 の状態 1）へ。
        assertEquals(1, RegistryRemapper.mapBlockState210To758((218 << 4) | 2));
        assertEquals(1, RegistryRemapper.mapBlockState210To758(229 << 4));
    }

    @Test
    void newIn111ItemsApproximate() {
        // totem (449) → 金のリンゴ 322。
        assertEquals(322, RegistryRemapper.mapItem758To210(
                RegistryRemapper.mapItem316To758(449)));
        // shulker_shell (450) → プリズマリンシャード 409。
        assertEquals(409, RegistryRemapper.mapItem758To210(
                RegistryRemapper.mapItem316To758(450)));
        // iron_nugget (452。1.11.1 追加) → 金塊 371。
        assertEquals(371, RegistryRemapper.mapItem758To210(
                RegistryRemapper.mapItem316To758(452)));
        // observer のアイテム形 (218) → ドロッパー 158。
        assertEquals(158, RegistryRemapper.mapItem758To210(
                RegistryRemapper.mapItem316To758(218)));
        // シュルカーボックスのアイテム形 (219) → チェスト 54。
        assertEquals(54, RegistryRemapper.mapItem758To210(
                RegistryRemapper.mapItem316To758(219)));
    }

    @Test
    void itemsAbsentIn210AreNeverProduced() {
        // 210 に無い番号（218・219〜234・449・450・452）を
        // 210 向けに決して出さないこと。
        int[] absent = {218, 219, 220, 221, 222, 223, 224, 225, 226, 227,
                228, 229, 230, 231, 232, 233, 234, 449, 450, 452};
        for (int i = 0; i <= 3000; i++) {
            int mapped = RegistryRemapper.mapItem758To210(i);
            for (int a : absent) {
                assertTrue(mapped != a, "758 item " + i + " -> " + mapped);
            }
        }
    }

    @Test
    void entityVariantsFoldAndNewMobsApproximate() {
        // 亜種の畳み込み：husk → zombie (54)、stray → skeleton (51)、
        // wither_skeleton → skeleton (51)、elder_guardian → guardian (68)、
        // zombie_villager → zombie (54)、donkey/mule/skeleton_horse/
        // zombie_horse → horse (100)。
        assertEquals(54, RegistryRemapper.mapEntityType758To210(
                RegistryRemapper.mapEntityType316To758(23))); // husk
        assertEquals(51, RegistryRemapper.mapEntityType758To210(
                RegistryRemapper.mapEntityType316To758(6))); // stray
        assertEquals(51, RegistryRemapper.mapEntityType758To210(
                RegistryRemapper.mapEntityType316To758(5))); // wither_skeleton
        assertEquals(68, RegistryRemapper.mapEntityType758To210(
                RegistryRemapper.mapEntityType316To758(4))); // elder_guardian
        assertEquals(54, RegistryRemapper.mapEntityType758To210(
                RegistryRemapper.mapEntityType316To758(27))); // zombie_villager
        assertEquals(100, RegistryRemapper.mapEntityType758To210(
                RegistryRemapper.mapEntityType316To758(31))); // donkey
        assertEquals(100, RegistryRemapper.mapEntityType758To210(
                RegistryRemapper.mapEntityType316To758(32))); // mule
        // 1.11 新規モブ → 豚 (90)。
        assertEquals(90, RegistryRemapper.mapEntityType758To210(
                RegistryRemapper.mapEntityType316To758(34))); // evoker
        assertEquals(90, RegistryRemapper.mapEntityType758To210(
                RegistryRemapper.mapEntityType316To758(35))); // vex
        assertEquals(90, RegistryRemapper.mapEntityType758To210(
                RegistryRemapper.mapEntityType316To758(36))); // vindicator
        assertEquals(90, RegistryRemapper.mapEntityType758To210(
                RegistryRemapper.mapEntityType316To758(103))); // llama
        // polar_bear (102) は 1.10 で追加済みのため同一
        // （758 側の番号は 68）。
        assertEquals(102, RegistryRemapper.mapEntityType758To210(
                RegistryRemapper.mapEntityType316To758(102)));
        assertEquals(68, RegistryRemapper.mapEntityType210To758(102));
        // 豚・クリーパーの基本アンカー。
        assertEquals(64, RegistryRemapper.mapEntityType210To758(90));
        assertEquals(90, RegistryRemapper.mapEntityType758To210(64));
    }

    @Test
    void soundAndParticleAnchors() {
        // block.stone.break：210 では 375、316 では 397。758 では同一。
        assertEquals(RegistryRemapper.mapSound316To758(397),
                RegistryRemapper.mapSound210To758(375));
        assertEquals(375, RegistryRemapper.mapSound758To210(
                RegistryRemapper.mapSound316To758(397)));
        // 1.12 追加のパーティクル（totem 47・spit 48）は 210 に
        // 無いため explode (0) へ。
        assertEquals(0, RegistryRemapper.mapParticle758To210(
                RegistryRemapper.mapParticle316To758(47)));
        assertEquals(0, RegistryRemapper.mapParticle758To210(
                RegistryRemapper.mapParticle316To758(48)));
        // 音名：210 の entity.experience_orb.touch は 1.11 で
        // pickup に改名された。
        assertEquals("entity.experience_orb.pickup",
                RegistryRemapper.mapSoundName210To758("entity.experience_orb.touch"));
        assertEquals("block.slime_block.break",
                RegistryRemapper.mapSoundName210To758("block.slime.break"));
        assertEquals("block.slime.break",
                RegistryRemapper.mapSoundName758To210("block.slime_block.break"));
    }

    @Test
    void blockEntityAndBiomeAnchors() {
        // かまど (0) は両世代で 0。
        assertEquals(0, RegistryRemapper.mapBlockEntityType758To210(
                RegistryRemapper.mapBlockEntityType316To758(0)));
        // bed のブロックエンティティは 210 に無いため DROP。
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityType758To210(
                RegistryRemapper.mapBlockEntityType340To758(23)));
        assertEquals("plains", RegistryRemapper.biomeNames210().get(1));
        assertEquals(1, RegistryRemapper.mapBiome210To758(1));
        assertEquals(1, RegistryRemapper.mapBiome758To210(1));
    }

    @Test
    void itemRoundTripsHoldForRegistered() {
        int[] registered = {1, 4, 35, 256, 267, 276, 297, 319, 331, 349,
                351, 355, 371, 383, 397, 409, 425, 2256, 2267};
        for (int i : registered) {
            int as758 = RegistryRemapper.mapItem210To758(i);
            assertEquals(i, RegistryRemapper.mapItem758To210(as758),
                    "item round trip " + i);
        }
        // ダイヤ剣：210 の 276 は 758 の 719（315 と同一）。
        assertEquals(719, RegistryRemapper.mapItem210To758(276));
        assertEquals(276, RegistryRemapper.mapItem758To210(719));
    }

    @Test
    void outOfRangeFallsBackSafely() {
        assertTrue(RegistryRemapper.mapBlockState210To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapBlockState210To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapItem210To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapEntityType210To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapBiome210To758(99999) >= 0);
    }
}
'''

PACKETIDS_210 = '''package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 210（1.10／1.10.1／1.10.2）のパケット ID 列。315 と完全同一
 * （Stage A：minecraft-data の pc/1.10 と pc/1.11 の protocol.json
 * 全状態比較で ID 差分 0、実バニラ 1.10.2 のワイヤ採取でも確認）。
 * 形式差分は4件だけ（spawn type の u8、title の action 番号、
 * collect、block_place のカーソル）で、ID には現れない。
 * 201 という製品版プロトコルは存在しない（1.10 系は全て 210）。
 */
class PacketIds210Test {

    private static final int V210 = ProtocolVersion.V1_10_2;

    @Test
    void tableKnows210() {
        assertTrue(PacketIds.isSupported(V210));
        assertEquals(List.of(210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void allIdsEqual315() {
        // Stage A の確定事項：210⇔315 のパケットID差分は全エントリで 0 件。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id315(), name.id210(), name.name());
        }
    }

    @Test
    void presentCount210Is113() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id210() != -1) {
                count++;
            }
        }
        assertEquals(113, count);
    }

    @Test
    void playIds210() {
        assertEquals(0x0f, PacketIds.id(PacketName.PLAY_CHAT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V210).orElseThrow());
        assertEquals(0x45, PacketIds.id(PacketName.PLAY_TITLE,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V210).orElseThrow());
        assertEquals(0x48, PacketIds.id(PacketName.PLAY_COLLECT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V210).orElseThrow());
    }
}
'''

CLIENTADAPTER_210 = '''package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 210（1.10／1.10.1／1.10.2）クライアントの受け入れ判断。
 * 最古が 210 に移っても 315・316・335・338・340 が一覧から
 * 脱落していないことを固定する（最古を下げた際に旧最古が
 * 脱落するバグが過去に起きている。315 が脱落すると 1.11、
 * 316 が脱落すると 1.11.1／1.11.2 まで巻き添えになる）。
 * 210 は 1.10 系3リリース共通で、201 という製品版プロトコル
 * は存在しない。
 */
class ClientAdapter210Test {

    @Test
    void decide210IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(210);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(210, handshake.clientProtocol());
        assertTrue(handshake.message().contains("210"), handshake.message());
    }

    @Test
    void decide210AgainstAllServerProtocols() {
        for (int server : new int[]{210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(210, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 210) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 209 以下と 777 はこのツリーでは未対応のため断る。
        // 201 という製品版プロトコルは存在しないが、名乗る
        // クライアントがいても最古 (210) 未満として拒否する。
        assertFalse(ClientAdapter.decide(209, 210).accepted());
        assertFalse(ClientAdapter.decide(201, 210).accepted());
        assertFalse(ClientAdapter.decide(777, 210).accepted());
    }

    @Test
    void versionName210Is1102() {
        assertEquals("1.10.2", ClientAdapter.versionName(210));
        assertEquals("1.11", ClientAdapter.versionName(315));
        assertEquals("1.11.2", ClientAdapter.versionName(316));
    }

    @Test
    void oldestSupportedIs210And315And316AreStillListed() {
        assertEquals(210, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(210, supported.get(0), supported.toString());
        // 最古を 210 に下げても 315・316・335・338・340 が
        // 一覧から脱落していない（最古脱落の罠の回帰防止）。
        assertEquals(315, supported.get(1), supported.toString());
        assertEquals(316, supported.get(2), supported.toString());
        assertEquals(335, supported.get(3), supported.toString());
        assertEquals(338, supported.get(4), supported.toString());
        assertEquals(340, supported.get(5), supported.toString());
        assertTrue(supported.contains(393), supported.toString());
        assertTrue(supported.contains(776), supported.toString());
        assertEquals(36, supported.size(), supported.toString());
    }
}
'''

PROTOCOLVERSION_210 = '''package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 210 は 1.10／1.10.1／1.10.2 の3リリース共通プロトコル
 * （実バニラの Status 応答で3版とも protocol 210 を確認済み。
 * 201 という製品版プロトコルは存在しない）。
 */
class ProtocolVersion210Test {

    @Test
    void allThree110ReleasesMapTo210() {
        assertEquals(ProtocolVersion.V1_10_2,
                ProtocolVersion.serverProtocolFor("1.10.2"));
        assertEquals(ProtocolVersion.V1_10_2,
                ProtocolVersion.serverProtocolFor("1.10.1"));
        assertEquals(ProtocolVersion.V1_10_2,
                ProtocolVersion.serverProtocolFor("1.10"));
    }

    @Test
    void neighboursUnchanged() {
        assertEquals(ProtocolVersion.V1_11,
                ProtocolVersion.serverProtocolFor("1.11"));
        assertEquals(ProtocolVersion.V1_11_2,
                ProtocolVersion.serverProtocolFor("1.11.2"));
        assertEquals(ProtocolVersion.V1_11_2,
                ProtocolVersion.serverProtocolFor("1.11.1"));
    }
}
'''

CHUNK_EXTRA_210 = '''
    // ---- 210 固有の形式差分4件の検証 ----

    @Test
    void spawnLiving210TypeIsUnsignedByte() {
        // 210 側の type は u8。0xFF は varint として読むと後続を
        // 食って壊れるため、u8 で読めていることの固定になる。
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PacketIO.Writer living210 = new PacketIO.Writer();
        living210.writeVarInt(20);
        living210.writeUuid(uuid);
        living210.writeByte(0xFF); // 範囲外の種別（u8 で 255）
        living210.writeDouble(0);
        living210.writeDouble(0);
        living210.writeDouble(0);
        living210.writeByte(0);
        living210.writeByte(0);
        living210.writeByte(0);
        living210.writeShort(0);
        living210.writeShort(0);
        living210.writeShort(0);
        byte[] as758 = ChunkTranslator.spawnLiving210To758(living210.bytes())[0];
        assertNotNull(as758);
        assertTrue(!java.util.Arrays.equals(living210.bytes(), as758));
        PacketIO.Reader reader = new PacketIO.Reader(as758);
        assertEquals(20, reader.readVarInt());
        assertEquals(uuid, reader.readUuid());
        assertEquals(RegistryRemapper.mapEntityType210To758(255),
                reader.readVarInt());
        // 逆向き：758 の pig (64) → 210 では 1 バイトの 90。
        PacketIO.Writer living758 = new PacketIO.Writer();
        living758.writeVarInt(20);
        living758.writeUuid(uuid);
        living758.writeVarInt(64);
        living758.writeDouble(0);
        living758.writeDouble(0);
        living758.writeDouble(0);
        living758.writeByte(0);
        living758.writeByte(0);
        living758.writeByte(0);
        living758.writeShort(0);
        living758.writeShort(0);
        living758.writeShort(0);
        byte[] as210 = ChunkTranslator.translateSpawnLiving758To210(living758.bytes());
        PacketIO.Reader r2 = new PacketIO.Reader(as210);
        assertEquals(20, r2.readVarInt());
        assertEquals(uuid, r2.readUuid());
        assertEquals(90, r2.readUnsignedByte());
    }

    @Test
    void blockPlaceCursorConvertsBetweenI8AndF32() {
        // 758 → 210：カーソル f32 (0.0, 0.5, 1.0) → i8 (0, 8, 15)。
        PacketIO.Writer place758 = new PacketIO.Writer();
        place758.writeVarInt(0); // hand
        place758.writeLong((0L << 38) | (0L << 12) | 64L); // 758 位置 (0,64,0)
        place758.writeVarInt(1); // face
        place758.writeFloat(0.0f);
        place758.writeFloat(0.5f);
        place758.writeFloat(1.0f);
        place758.writeBoolean(false); // insideBlock
        byte[] as210 = ChunkTranslator.translateBlockPlace758To210(place758.bytes());
        PacketIO.Reader r = new PacketIO.Reader(as210);
        assertEquals(64L << 26, r.readLong()); // 210 位置 (0,64,0)
        assertEquals(1, r.readVarInt()); // face
        assertEquals(0, r.readVarInt()); // hand
        assertEquals(0, r.readByte());
        assertEquals(8, r.readByte());
        assertEquals(15, r.readByte());
        assertEquals(0, r.remaining());
        // 210 → 758：i8 (0, 8, 15) → f32 (0.0, 8/15, 1.0)。
        PacketIO.Writer place210 = new PacketIO.Writer();
        place210.writeLong(64L << 26);
        place210.writeVarInt(1);
        place210.writeVarInt(0);
        place210.writeByte(0);
        place210.writeByte(8);
        place210.writeByte(15);
        byte[] back758 = ChunkTranslator.translateBlockPlace210To758(place210.bytes());
        PacketIO.Reader r2 = new PacketIO.Reader(back758);
        assertEquals(0, r2.readVarInt()); // hand
        assertEquals(64L, r2.readLong()); // 758 位置 (0,64,0)
        assertEquals(1, r2.readVarInt()); // face
        assertEquals(0.0f, r2.readFloat());
        assertEquals(8 / 15.0f, r2.readFloat());
        assertEquals(1.0f, r2.readFloat());
        assertEquals(false, r2.readBoolean()); // insideBlock（補われる）
        assertEquals(0, r2.remaining());
    }

    @Test
    void collectCountFieldOnlyExistsOn758Side() {
        PacketIO.Writer collect758 = new PacketIO.Writer();
        collect758.writeVarInt(5);
        collect758.writeVarInt(7);
        collect758.writeVarInt(3); // pickupItemCount
        byte[] as210 = ChunkTranslator.translateCollect758To210(collect758.bytes());
        PacketIO.Reader r = new PacketIO.Reader(as210);
        assertEquals(5, r.readVarInt());
        assertEquals(7, r.readVarInt());
        assertEquals(0, r.remaining());
        PacketIO.Writer collect210 = new PacketIO.Writer();
        collect210.writeVarInt(5);
        collect210.writeVarInt(7);
        byte[] back758 = ChunkTranslator.translateCollect210To758(collect210.bytes());
        PacketIO.Reader r2 = new PacketIO.Reader(back758);
        assertEquals(5, r2.readVarInt());
        assertEquals(7, r2.readVarInt());
        assertEquals(1, r2.readVarInt()); // 補われる個数
        assertEquals(0, r2.remaining());
    }

    @Test
    void titleActionsUse210Numbering() {
        // 758 の Set Title Text → 210 の action 0。
        PacketIO.Writer text758 = new PacketIO.Writer();
        text758.writeString("{\\"text\\":\\"Hi\\"}");
        byte[] as210 = ChunkTranslator.titleText758To210(text758.bytes());
        PacketIO.Reader r = new PacketIO.Reader(as210);
        assertEquals(0, r.readVarInt());
        assertEquals("{\\"text\\":\\"Hi\\"}", r.readString());
        // 758 の Set Title Time → 210 の action 2（315 では 3）。
        PacketIO.Writer time758 = new PacketIO.Writer();
        time758.writeInt(10);
        time758.writeInt(70);
        time758.writeInt(20);
        byte[] time210 = ChunkTranslator.titleAnimation758To210(time758.bytes());
        PacketIO.Reader r2 = new PacketIO.Reader(time210);
        assertEquals(2, r2.readVarInt());
        assertEquals(10, r2.readInt());
        assertEquals(70, r2.readInt());
        assertEquals(20, r2.readInt());
        // 758 の Clear Titles → 210 の action 3（消去）／4（リセット）。
        PacketIO.Writer clear758 = new PacketIO.Writer();
        clear758.writeBoolean(false);
        PacketIO.Reader r3 = new PacketIO.Reader(
                ChunkTranslator.titleClear758To210(clear758.bytes()));
        assertEquals(3, r3.readVarInt());
        PacketIO.Writer reset758 = new PacketIO.Writer();
        reset758.writeBoolean(true);
        PacketIO.Reader r4 = new PacketIO.Reader(
                ChunkTranslator.titleClear758To210(reset758.bytes()));
        assertEquals(4, r4.readVarInt());
        // 逆向き：210 の action 2 は時間、3 は消去 (false)、4 はリセット (true)。
        assertEquals(2, ChunkTranslator.titleAction210(time210));
        byte[] timeBack = ChunkTranslator.translateTitle210To758(time210);
        PacketIO.Reader r5 = new PacketIO.Reader(timeBack);
        assertEquals(10, r5.readInt());
        assertEquals(70, r5.readInt());
        assertEquals(20, r5.readInt());
        PacketIO.Writer clear210 = new PacketIO.Writer();
        clear210.writeVarInt(3);
        PacketIO.Reader r6 = new PacketIO.Reader(
                ChunkTranslator.translateTitle210To758(clear210.bytes()));
        assertEquals(false, r6.readBoolean());
        PacketIO.Writer reset210 = new PacketIO.Writer();
        reset210.writeVarInt(4);
        PacketIO.Reader r7 = new PacketIO.Reader(
                ChunkTranslator.translateTitle210To758(reset210.bytes()));
        assertEquals(true, r7.readBoolean());
    }

    @Test
    void actionBarTravelsAsChatPosition2() {
        PacketIO.Writer bar758 = new PacketIO.Writer();
        bar758.writeString("{\\"text\\":\\"Bar\\"}");
        byte[] as210 = ChunkTranslator.actionBar758ToChat210(bar758.bytes());
        assertEquals(2, ChunkTranslator.chatPosition210(as210));
        PacketIO.Reader r = new PacketIO.Reader(as210);
        assertEquals("{\\"text\\":\\"Bar\\"}", r.readString());
        assertEquals(2, r.readByte());
        assertEquals(0, r.remaining());
        byte[] back = ChunkTranslator.chat210ToActionBar758(as210);
        PacketIO.Reader r2 = new PacketIO.Reader(back);
        assertEquals("{\\"text\\":\\"Bar\\"}", r2.readString());
        assertEquals(0, r2.remaining());
        // 通常のチャット（position 0）はアクションバーではない。
        PacketIO.Writer chat210 = new PacketIO.Writer();
        chat210.writeString("{\\"text\\":\\"Chat\\"}");
        chat210.writeByte(0);
        assertEquals(0, ChunkTranslator.chatPosition210(chat210.bytes()));
    }
'''


def fix_chunk_test(text):
    # 末尾の閉じ括弧の前に 210 固有テストを足す
    idx = text.rstrip().rfind("\n}")
    return text[:idx] + "\n" + CHUNK_EXTRA_210 + text[idx:]


def main():
    (TEST / "RegistryRemapper210Test.java").write_text(
        REGISTRY_210, encoding="utf-8")
    print("RegistryRemapper210Test を生成した")
    (TEST / "PacketIds210Test.java").write_text(
        PACKETIDS_210, encoding="utf-8")
    print("PacketIds210Test を生成した")
    (TEST / "ClientAdapter210Test.java").write_text(
        CLIENTADAPTER_210, encoding="utf-8")
    print("ClientAdapter210Test を生成した")
    (TEST / "ProtocolVersion210Test.java").write_text(
        PROTOCOLVERSION_210, encoding="utf-8")
    print("ProtocolVersion210Test を生成した")
    copy_test("ChunkTranslator315Test.java", "ChunkTranslator210Test.java",
              transform=fix_chunk_test)
    copy_test("SlotTranslator315Test.java", "SlotTranslator210Test.java")
    copy_test("PlayTranslator315Test.java", "PlayTranslator210Test.java")
    copy_test("ProxySession315Test.java", "ProxySession210Test.java")
    copy_test("ClientJoinFlow315Test.java", "ClientJoinFlow210Test.java")


if __name__ == "__main__":
    main()
