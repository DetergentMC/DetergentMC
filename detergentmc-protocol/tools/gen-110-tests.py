"""110（1.9.4／1.9.3）の単体テストを生成する。

210 のテストを写して番号を差し替える。レジストリ・PacketIds・
ClientAdapter・ProtocolVersion は 110 固有の内容（実 jar 抽出の
数値、近似方針、37プロトコルの固定）で置き換える。
ChunkTranslator には形式差分（音の pitch の u8⇔f32 換算）の
検証を足し、ProxySession には resource_pack_receive の hash
付け外しの検証を足す。

使い方：
  python3 tools/gen-110-tests.py
"""

from pathlib import Path

TEST = (Path(__file__).parent.parent / "src" / "test" / "java"
        / "io" / "detergentmc" / "protocol")


def rename_210_to_110(text):
    text = text.replace("V1_10_2", "V1_9_4")
    text = text.replace("1.10.2", "1.9.4")
    text = text.replace("210", "110")
    return text


def copy_test(src_name, dst_name, transform=None):
    text = (TEST / src_name).read_text(encoding="utf-8")
    text = rename_210_to_110(text)
    if transform is not None:
        text = transform(text)
    (TEST / dst_name).write_text(text, encoding="utf-8")
    print(f"{dst_name} を生成した")


REGISTRY_110 = '''package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 110（1.9.4／1.9.3）のレジストリ写像。110 は 210 とパケット ID が
 * 完全同一だが、レジストリは 1.10 の追加分が無い：ブロックは
 * magma (213)・nether_wart_block (214)・red_nether_brick (215)・
 * bone_block (216)・structure_void (217) が無く214種、アイテムは
 * その5種の形＋structure_block の形 (255) が無く365種、
 * エンティティは polar_bear (102) が無い。音は 210 の部分集合
 * （444件、数値 ID は登録順が違うため名前で合成）。
 * ground truth は実バニラ 1.9.4 jar（SHA-1
 * edbb7b1758af33d365bf835eb9d13de005b1e274）からの抽出。
 * structure_block のアイテム形が無いことは実サーバーの /give で
 * 確認済み。近似方針は docs/PROXY-1.9.4.md。
 */
class RegistryRemapper110Test {

    @Test
    void maxConstantsAreThe194Values() {
        assertEquals(4095, RegistryRemapper.MAX_BLOCK_STATE_110);
        assertEquals(2267, RegistryRemapper.MAX_ITEM_110);
        assertEquals(200, RegistryRemapper.MAX_ENTITY_TYPE_110);
        assertEquals(443, RegistryRemapper.MAX_SOUND_110);
        assertEquals(45, RegistryRemapper.MAX_PARTICLE_110);
        assertEquals(255, RegistryRemapper.MAX_BLOCK_110);
        assertEquals(167, RegistryRemapper.MAX_BIOME_110);
        assertEquals(41, RegistryRemapper.MAX_DAMAGE_TYPE_110);
        assertEquals(22, RegistryRemapper.MAX_BLOCK_ENTITY_110);
        assertEquals(27, RegistryRemapper.MAX_EFFECT_110);
    }

    @Test
    void blockStateSemanticAnchors() {
        // 空気は 0、石は (1<<4)|0 = 16。共有ブロックは 210 と同一。
        assertEquals(0, RegistryRemapper.mapBlockState110To758(0));
        assertEquals(16, RegistryRemapper.mapBlockState758To110(1));
        assertEquals(16, RegistryRemapper.mapBlockState758To110(
                RegistryRemapper.mapBlockState110To758(16)));
        int whiteWool758 = RegistryRemapper.mapBlockState110To758(560);
        assertEquals(560, RegistryRemapper.mapBlockState758To110(whiteWool758));
        int logY = RegistryRemapper.mapBlockState110To758(17 << 4);
        int logX = RegistryRemapper.mapBlockState110To758((17 << 4) | 4);
        assertEquals(17 << 4, RegistryRemapper.mapBlockState758To110(logY));
        assertEquals((17 << 4) | 4, RegistryRemapper.mapBlockState758To110(logX));
    }

    @Test
    void newIn110BlocksApproximate() {
        // magma (213) → ネザーラック (87)。
        int magma758 = RegistryRemapper.mapBlockState210To758(213 << 4);
        assertEquals(87 << 4, RegistryRemapper.mapBlockState758To110(magma758));
        // nether_wart_block (214) → ネザーレンガ (112)。
        int wart758 = RegistryRemapper.mapBlockState210To758(214 << 4);
        assertEquals(112 << 4, RegistryRemapper.mapBlockState758To110(wart758));
        // red_nether_brick (215) → ネザーレンガ (112)。
        int red758 = RegistryRemapper.mapBlockState210To758(215 << 4);
        assertEquals(112 << 4, RegistryRemapper.mapBlockState758To110(red758));
        // bone_block (216) → クォーツブロック (155)。メタは保持。
        int bone758 = RegistryRemapper.mapBlockState210To758((216 << 4) | 4);
        assertEquals((155 << 4) | 4,
                RegistryRemapper.mapBlockState758To110(bone758));
        // structure_void (217) → バリア (166)。
        int void758 = RegistryRemapper.mapBlockState210To758(217 << 4);
        assertEquals(166 << 4, RegistryRemapper.mapBlockState758To110(void758));
        // 逆向き：110 に無い範囲 (213〜217) は石（758 の状態 1）へ。
        assertEquals(1, RegistryRemapper.mapBlockState110To758(213 << 4));
        assertEquals(1, RegistryRemapper.mapBlockState110To758(217 << 4));
    }

    @Test
    void newIn110ItemsApproximate() {
        // magma のアイテム形 (213) → ネザーラック 87。
        assertEquals(87, RegistryRemapper.mapItem758To110(
                RegistryRemapper.mapItem210To758(213)));
        // nether_wart_block (214)／red_nether_brick (215) → 112。
        assertEquals(112, RegistryRemapper.mapItem758To110(
                RegistryRemapper.mapItem210To758(214)));
        assertEquals(112, RegistryRemapper.mapItem758To110(
                RegistryRemapper.mapItem210To758(215)));
        // bone_block (216) → クォーツブロック 155。
        assertEquals(155, RegistryRemapper.mapItem758To110(
                RegistryRemapper.mapItem210To758(216)));
        // structure_void (217) → バリア 166。
        assertEquals(166, RegistryRemapper.mapItem758To110(
                RegistryRemapper.mapItem210To758(217)));
        // structure_block のアイテム形 (255) は 1.9.4 に無い
        // （実サーバーの /give で確認）→ バリア 166。
        assertEquals(166, RegistryRemapper.mapItem758To110(
                RegistryRemapper.mapItem210To758(255)));
    }

    @Test
    void itemsAbsentIn110AreNeverProduced() {
        // 110 に無い番号（213〜217・255）を 110 向けに決して
        // 出さないこと。
        int[] absent = {213, 214, 215, 216, 217, 255};
        for (int i = 0; i <= 3000; i++) {
            int mapped = RegistryRemapper.mapItem758To110(i);
            for (int a : absent) {
                assertTrue(mapped != a, "758 item " + i + " -> " + mapped);
            }
        }
    }

    @Test
    void entityAnchors() {
        // polar_bear (102) は 110 に無いためブタ (90) へ。
        assertEquals(90, RegistryRemapper.mapEntityType758To110(
                RegistryRemapper.mapEntityType210To758(102)));
        // 逆向き：102 は 758 のブタへ落ちる（110 からは出ない）。
        assertEquals(RegistryRemapper.mapEntityType110To758(90),
                RegistryRemapper.mapEntityType110To758(102));
        // 亜種の畳み込みは 210 と同じ：husk → zombie (54)。
        assertEquals(54, RegistryRemapper.mapEntityType758To110(
                RegistryRemapper.mapEntityType316To758(23))); // husk
        assertEquals(51, RegistryRemapper.mapEntityType758To110(
                RegistryRemapper.mapEntityType316To758(6))); // stray
        // 豚・クリーパーの基本アンカー。
        assertEquals(64, RegistryRemapper.mapEntityType110To758(90));
        assertEquals(90, RegistryRemapper.mapEntityType758To110(64));
    }

    @Test
    void soundAndParticleAnchors() {
        // block.stone.break：110 では 364、210 では 375。758 では同一。
        assertEquals(RegistryRemapper.mapSound210To758(375),
                RegistryRemapper.mapSound110To758(364));
        assertEquals(364, RegistryRemapper.mapSound758To110(
                RegistryRemapper.mapSound210To758(375)));
        // 1.10 追加音（entity.polar_bear.ambient、210 では 277）は
        // 110 に無いため汎用音 (364) へ。
        assertEquals(364, RegistryRemapper.mapSound758To110(
                RegistryRemapper.mapSound210To758(277)));
        // fallingdust (46) は 110 に無いため explode (0) へ。
        assertEquals(0, RegistryRemapper.mapParticle758To110(
                RegistryRemapper.mapParticle210To758(46)));
        // 共有パーティクルは同一（45 = 210 と同じ番号）。
        assertEquals(RegistryRemapper.mapParticle210To758(45),
                RegistryRemapper.mapParticle110To758(45));
        // 音名：110 の entity.experience_orb.touch は 210 と同じく
        // 758 では pickup。
        assertEquals("entity.experience_orb.pickup",
                RegistryRemapper.mapSoundName110To758("entity.experience_orb.touch"));
    }

    @Test
    void blockEntityAndBiomeAnchors() {
        // かまど (0) は両世代で 0。ブロックエンティティは 210 と同一。
        assertEquals(0, RegistryRemapper.mapBlockEntityType758To110(
                RegistryRemapper.mapBlockEntityType210To758(0)));
        // bed のブロックエンティティは 110 に無いため DROP。
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityType758To110(
                RegistryRemapper.mapBlockEntityType340To758(23)));
        assertEquals("plains", RegistryRemapper.biomeNames110().get(1));
        assertEquals(1, RegistryRemapper.mapBiome110To758(1));
        assertEquals(1, RegistryRemapper.mapBiome758To110(1));
    }

    @Test
    void itemRoundTripsHoldForRegistered() {
        int[] registered = {1, 4, 35, 256, 267, 276, 297, 319, 331, 349,
                351, 355, 371, 383, 397, 409, 425, 2256, 2267};
        for (int i : registered) {
            int as758 = RegistryRemapper.mapItem110To758(i);
            assertEquals(i, RegistryRemapper.mapItem758To110(as758),
                    "item round trip " + i);
        }
        // ダイヤ剣：110 の 276 は 758 の 719（210 と同一）。
        assertEquals(719, RegistryRemapper.mapItem110To758(276));
        assertEquals(276, RegistryRemapper.mapItem758To110(719));
    }

    @Test
    void outOfRangeFallsBackSafely() {
        assertTrue(RegistryRemapper.mapBlockState110To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapBlockState110To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapItem110To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapEntityType110To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapBiome110To758(99999) >= 0);
    }
}
'''

PACKETIDS_110 = '''package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 110（1.9.4／1.9.3）のパケット ID 列。210 と完全同一
 * （Stage A：minecraft-data の pc/1.9.4 と pc/1.10 の protocol.json
 * 全状態比較で ID 差分 0、実バニラ 1.9.4 のワイヤ採取でも確認）。
 * 形式差分は3件だけ（音の pitch が u8、resource_pack_receive の
 * hash）で、ID には現れない。
 */
class PacketIds110Test {

    private static final int V110 = ProtocolVersion.V1_9_4;

    @Test
    void tableKnows110() {
        assertTrue(PacketIds.isSupported(V110));
        assertEquals(List.of(110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void allIdsEqual210() {
        // Stage A の確定事項：110⇔210 のパケットID差分は全エントリで 0 件。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id210(), name.id110(), name.name());
        }
    }

    @Test
    void presentCount110Is113() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id110() != -1) {
                count++;
            }
        }
        assertEquals(113, count);
    }

    @Test
    void playIds110() {
        assertEquals(0x0f, PacketIds.id(PacketName.PLAY_CHAT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V110).orElseThrow());
        assertEquals(0x45, PacketIds.id(PacketName.PLAY_TITLE,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V110).orElseThrow());
        assertEquals(0x48, PacketIds.id(PacketName.PLAY_COLLECT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V110).orElseThrow());
        assertEquals(0x16, PacketIds.id(PacketName.PLAY_RESOURCE_PACK_RESPONSE,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND,
                V110).orElseThrow());
    }
}
'''

CLIENTADAPTER_110 = '''package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 110（1.9.4／1.9.3）クライアントの受け入れ判断。
 * 最古が 110 に移っても 210・315・316・335・338・340 が一覧から
 * 脱落していないことを固定する（最古を下げた際に旧最古が
 * 脱落するバグが過去に起きている。210 が脱落すると 1.10 系、
 * 315 が脱落すると 1.11、316 が脱落すると 1.11.1／1.11.2 まで
 * 巻き添えになる）。110 は 1.9.3 と 1.9.4 の2リリース共通。
 */
class ClientAdapter110Test {

    @Test
    void decide110IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(110);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(110, handshake.clientProtocol());
        assertTrue(handshake.message().contains("110"), handshake.message());
    }

    @Test
    void decide110AgainstAllServerProtocols() {
        for (int server : new int[]{110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(110, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 110) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 109 以下（1.9.2 以前）と 777 はこのツリーでは未対応のため断る。
        assertFalse(ClientAdapter.decide(109, 110).accepted());
        assertFalse(ClientAdapter.decide(107, 110).accepted());
        assertFalse(ClientAdapter.decide(47, 110).accepted());
        assertFalse(ClientAdapter.decide(777, 110).accepted());
    }

    @Test
    void versionName110Is194() {
        assertEquals("1.9.4", ClientAdapter.versionName(110));
        assertEquals("1.10.2", ClientAdapter.versionName(210));
        assertEquals("1.11", ClientAdapter.versionName(315));
        assertEquals("1.11.2", ClientAdapter.versionName(316));
    }

    @Test
    void oldestSupportedIs110And210And315And316AreStillListed() {
        assertEquals(110, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(110, supported.get(0), supported.toString());
        // 最古を 110 に下げても 210・315・316・335・338・340 が
        // 一覧から脱落していない（最古脱落の罠の回帰防止）。
        assertEquals(210, supported.get(1), supported.toString());
        assertEquals(315, supported.get(2), supported.toString());
        assertEquals(316, supported.get(3), supported.toString());
        assertEquals(335, supported.get(4), supported.toString());
        assertEquals(338, supported.get(5), supported.toString());
        assertEquals(340, supported.get(6), supported.toString());
        assertTrue(supported.contains(393), supported.toString());
        assertTrue(supported.contains(776), supported.toString());
        assertEquals(37, supported.size(), supported.toString());
    }
}
'''

PROTOCOLVERSION_110 = '''package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 110 は 1.9.3 と 1.9.4 の2リリース共通プロトコル
 * （実バニラの Status 応答で両版とも protocol 110 を確認済み）。
 */
class ProtocolVersion110Test {

    @Test
    void both19ReleasesMapTo110() {
        assertEquals(ProtocolVersion.V1_9_4,
                ProtocolVersion.serverProtocolFor("1.9.4"));
        assertEquals(ProtocolVersion.V1_9_4,
                ProtocolVersion.serverProtocolFor("1.9.3"));
    }

    @Test
    void neighboursUnchanged() {
        assertEquals(ProtocolVersion.V1_10_2,
                ProtocolVersion.serverProtocolFor("1.10.2"));
        assertEquals(ProtocolVersion.V1_10_2,
                ProtocolVersion.serverProtocolFor("1.10"));
        assertEquals(ProtocolVersion.V1_11,
                ProtocolVersion.serverProtocolFor("1.11"));
    }
}
'''

CHUNK_EXTRA_110 = '''
    // ---- 110 固有の形式差分（音の pitch）の検証 ----

    @Test
    void soundEffectPitchConvertsBetweenU8AndF32() {
        // 758 → 110：pitch f32 1.0 → u8 64（round(1.0 × 63.5)）。
        int stoneBreak758 = RegistryRemapper.mapSound110To758(364);
        PacketIO.Writer snd758 = new PacketIO.Writer();
        snd758.writeVarInt(stoneBreak758);
        snd758.writeVarInt(0); // category
        snd758.writeInt(8);
        snd758.writeInt(64);
        snd758.writeInt(8);
        snd758.writeFloat(1.0f); // volume
        snd758.writeFloat(1.0f); // pitch
        byte[] as110 = ChunkTranslator.translateSoundEffect758To110(snd758.bytes());
        PacketIO.Reader r = new PacketIO.Reader(as110);
        assertEquals(364, r.readVarInt());
        assertEquals(0, r.readVarInt());
        assertEquals(8, r.readInt());
        assertEquals(64, r.readInt());
        assertEquals(8, r.readInt());
        assertEquals(1.0f, r.readFloat());
        assertEquals(64, r.readUnsignedByte()); // pitch は u8
        assertEquals(0, r.remaining());
        // 110 → 758：pitch u8 64 → f32 64/63.5。
        byte[] back758 = ChunkTranslator.translateSoundEffect110To758(as110);
        PacketIO.Reader r2 = new PacketIO.Reader(back758);
        assertEquals(stoneBreak758, r2.readVarInt());
        assertEquals(0, r2.readVarInt());
        assertEquals(8, r2.readInt());
        assertEquals(64, r2.readInt());
        assertEquals(8, r2.readInt());
        assertEquals(1.0f, r2.readFloat());
        assertEquals(64 / 63.5f, r2.readFloat());
        assertEquals(0, r2.remaining());
        // 端数：pitch 0.5 → u8 32（round(31.75)）、2.0 → u8 127。
        PacketIO.Writer half758 = new PacketIO.Writer();
        half758.writeVarInt(stoneBreak758);
        half758.writeVarInt(0);
        half758.writeInt(0);
        half758.writeInt(0);
        half758.writeInt(0);
        half758.writeFloat(0.5f);
        half758.writeFloat(0.5f);
        PacketIO.Reader r3 = new PacketIO.Reader(
                ChunkTranslator.translateSoundEffect758To110(half758.bytes()));
        r3.readVarInt();
        r3.readVarInt();
        r3.readInt();
        r3.readInt();
        r3.readInt();
        r3.readFloat();
        assertEquals(32, r3.readUnsignedByte());
    }

    @Test
    void namedSoundEffectPitchConvertsBetweenU8AndF32() {
        // 758 → 110：名前はそのまま、pitch は u8 化する。
        PacketIO.Writer snd758 = new PacketIO.Writer();
        snd758.writeString("block.stone.break");
        snd758.writeVarInt(0); // category
        snd758.writeInt(1);
        snd758.writeInt(2);
        snd758.writeInt(3);
        snd758.writeFloat(0.75f); // volume
        snd758.writeFloat(2.0f); // pitch → u8 127
        byte[] as110 = ChunkTranslator.translateNamedSoundEffect758To110(snd758.bytes());
        PacketIO.Reader r = new PacketIO.Reader(as110);
        assertEquals("block.stone.break", r.readString());
        assertEquals(0, r.readVarInt());
        assertEquals(1, r.readInt());
        assertEquals(2, r.readInt());
        assertEquals(3, r.readInt());
        assertEquals(0.75f, r.readFloat());
        assertEquals(127, r.readUnsignedByte());
        assertEquals(0, r.remaining());
        // 110 → 758：pitch u8 127 → f32 2.0。
        byte[] back758 = ChunkTranslator.translateNamedSoundEffect110To758(as110);
        PacketIO.Reader r2 = new PacketIO.Reader(back758);
        assertEquals("block.stone.break", r2.readString());
        assertEquals(0, r2.readVarInt());
        assertEquals(1, r2.readInt());
        assertEquals(2, r2.readInt());
        assertEquals(3, r2.readInt());
        assertEquals(0.75f, r2.readFloat());
        assertEquals(2.0f, r2.readFloat());
        assertEquals(0, r2.remaining());
    }
'''

PROXY_EXTRA_110 = '''
    // ---- 110 固有の形式差分（resource_pack_receive の hash）の検証 ----

    @Test
    void resourcePackHashIsStrippedAndPrepended() throws Exception {
        var strip = ProxySession.class.getDeclaredMethod(
                "stripResourcePackHash110", byte[].class);
        strip.setAccessible(true);
        var prepend = ProxySession.class.getDeclaredMethod(
                "prependResourcePackHash110", byte[].class);
        prepend.setAccessible(true);
        // 110 形式（hash "abc" ＋ result 1）→ 758 形式（result 1 のみ）。
        PacketIO.Writer w110 = new PacketIO.Writer();
        w110.writeString("abc");
        w110.writeVarInt(1);
        byte[] stripped = (byte[]) strip.invoke(null, w110.bytes());
        PacketIO.Reader r = new PacketIO.Reader(stripped);
        assertEquals(1, r.readVarInt());
        assertEquals(0, r.remaining());
        // 758 形式（result 0 のみ）→ 110 形式（hash "" ＋ result 0）。
        PacketIO.Writer w758 = new PacketIO.Writer();
        w758.writeVarInt(0);
        byte[] prepended = (byte[]) prepend.invoke(null, w758.bytes());
        PacketIO.Reader r2 = new PacketIO.Reader(prepended);
        assertEquals("", r2.readString());
        assertEquals(0, r2.readVarInt());
        assertEquals(0, r2.remaining());
        // 往復：758 → 110 → 758 で result が保たれる。
        byte[] roundTrip = (byte[]) strip.invoke(null, prepended);
        assertTrue(java.util.Arrays.equals(w758.bytes(), roundTrip));
    }
'''


def fix_chunk_test(text):
    # 末尾の閉じ括弧の前に 110 固有テストを足す
    idx = text.rstrip().rfind("\n}")
    return text[:idx] + "\n" + CHUNK_EXTRA_110 + text[idx:]


def fix_proxy_test(text):
    idx = text.rstrip().rfind("\n}")
    return text[:idx] + "\n" + PROXY_EXTRA_110 + text[idx:]


def main():
    (TEST / "RegistryRemapper110Test.java").write_text(
        REGISTRY_110, encoding="utf-8")
    print("RegistryRemapper110Test を生成した")
    (TEST / "PacketIds110Test.java").write_text(
        PACKETIDS_110, encoding="utf-8")
    print("PacketIds110Test を生成した")
    (TEST / "ClientAdapter110Test.java").write_text(
        CLIENTADAPTER_110, encoding="utf-8")
    print("ClientAdapter110Test を生成した")
    (TEST / "ProtocolVersion110Test.java").write_text(
        PROTOCOLVERSION_110, encoding="utf-8")
    print("ProtocolVersion110Test を生成した")
    copy_test("ChunkTranslator210Test.java", "ChunkTranslator110Test.java",
              transform=fix_chunk_test)
    copy_test("SlotTranslator210Test.java", "SlotTranslator110Test.java")
    copy_test("PlayTranslator210Test.java", "PlayTranslator110Test.java")
    copy_test("ProxySession210Test.java", "ProxySession110Test.java",
              transform=fix_proxy_test)
    copy_test("ClientJoinFlow210Test.java", "ClientJoinFlow110Test.java")


if __name__ == "__main__":
    main()
