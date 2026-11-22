package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 5（1.7.6〜1.7.10）のパケット ID 列。Stage A で実バニラ 1.7.10 の
 * ワイヤ採取と minecraft-data の protocol.json 全面比較により確定
 * （検証スクリプトは tools/packet-ids-5.py）。47 との関係：
 * 両方に存在するパケットは95件すべて ID が一致（差分0件）。
 * 47 にだけ存在するパケットは1.8の追加分12件で、5 にだけ存在する
 * パケットは無い。形式差分は型の付け替えのみ（entityId の i32 化、
 * 座標のバラ数値化、Keep Alive の i32 化など）。
 */
class PacketIds5Test {

    private static final int V5 = ProtocolVersion.V1_7_10;

    @Test
    void tableKnows5() {
        assertTrue(PacketIds.isSupported(V5));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void presentCount5Is95() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id5() != -1) {
                count++;
            }
        }
        // 47 の 107件から、1.8 の追加分12件を引いた95件。
        assertEquals(95, count);
    }

    @Test
    void diffAgainst47IsZeroAndOnlyIn47AreThe12Additions() {
        int common = 0;
        int differ = 0;
        Set<PacketName> only47 = new java.util.HashSet<>();
        Set<PacketName> only5 = new java.util.HashSet<>();
        for (PacketName name : PacketName.values()) {
            if (name.id47() != -1 && name.id5() != -1) {
                common++;
                if (name.id47() != name.id5()) {
                    differ++;
                }
            } else if (name.id47() != -1) {
                only47.add(name);
            } else if (name.id5() != -1) {
                only5.add(name);
            }
        }
        assertEquals(95, common);
        assertEquals(0, differ);
        assertEquals(Set.of(
                PacketName.LOGIN_SET_COMPRESSION,
                PacketName.PLAY_CAMERA,
                PacketName.PLAY_COMBAT_EVENT,
                PacketName.PLAY_DIFFICULTY,
                PacketName.PLAY_PLAYERLIST_HEADER,
                PacketName.PLAY_RESOURCE_PACK,
                PacketName.PLAY_RESOURCE_PACK_RESPONSE,
                PacketName.PLAY_SET_COMPRESSION,
                PacketName.PLAY_SPECTATE,
                PacketName.PLAY_TITLE,
                PacketName.PLAY_UPDATE_ENTITY_NBT,
                PacketName.PLAY_WORLD_BORDER), only47);
        assertTrue(only5.isEmpty(), only5.toString());
    }

    @Test
    void playIds5() {
        assertEquals(0x01, PacketIds.id(PacketName.PLAY_JOIN_GAME,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V5).orElseThrow());
        assertEquals(0x02, PacketIds.id(PacketName.PLAY_CHAT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V5).orElseThrow());
        // プレイ状態の 0x03 は Update Time であり、Set Compression では
        // ない（1.7 には圧縮の仕組み自体が無い）。ログイン状態の生ID
        // 切断判定はログイン中の 0x03 のみのため誤爆しない。
        assertEquals(0x03, PacketIds.id(PacketName.PLAY_UPDATE_TIME,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V5).orElseThrow());
        assertTrue(PacketIds.id(PacketName.PLAY_SET_COMPRESSION,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V5).isEmpty());
        assertEquals(0x21, PacketIds.id(PacketName.PLAY_CHUNK_DATA,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V5).orElseThrow());
        assertEquals(0x26, PacketIds.id(PacketName.PLAY_MAP_CHUNK_BULK,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V5).orElseThrow());
        assertEquals(0x38, PacketIds.id(PacketName.PLAY_PLAYER_INFO_UPDATE,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V5).orElseThrow());
        assertEquals(0x0c, PacketIds.id(PacketName.PLAY_NAMED_ENTITY_SPAWN,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V5).orElseThrow());
        // Keep Alive は両方向とも i32 で、ID は 0x00 同士。
        assertEquals(0x00, PacketIds.id(PacketName.PLAY_KEEP_ALIVE,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V5).orElseThrow());
        assertEquals(0x00, PacketIds.id(PacketName.PLAY_KEEP_ALIVE_TO_SERVER,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND,
                V5).orElseThrow());
        // Teleport Confirm と Title は 5 に無い。
        assertTrue(PacketIds.id(PacketName.PLAY_TELEPORT_CONFIRM,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND,
                V5).isEmpty());
        assertTrue(PacketIds.id(PacketName.PLAY_TITLE,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V5).isEmpty());
    }
}
