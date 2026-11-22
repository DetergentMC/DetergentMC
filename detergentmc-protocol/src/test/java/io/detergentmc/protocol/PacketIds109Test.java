package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 109（1.9.2）のパケット ID 列。110 との差分は Stage A で実測確定：
 * クライアント行き update_sign が 109 にだけあり（0x46、1.9.3 で
 * 削除）、その後ろの6件（sound_effect〜entity_effect）が 109 では
 * +1 ずれる。それ以外は全エントリで 110 と同一。検証スクリプトは
 * tools/packet-ids-109.py（protocol.json 同士の全状態比較）。
 */
class PacketIds109Test {

    private static final int V109 = ProtocolVersion.V1_9_2;

    /** 109 で ID が 110 より +1 ずれる6件（update_sign 削除の影響）。 */
    private static final Set<PacketName> SHIFTED = Set.of(
            PacketName.PLAY_SOUND_EFFECT,
            PacketName.PLAY_PLAYERLIST_HEADER,
            PacketName.PLAY_COLLECT,
            PacketName.PLAY_ENTITY_TELEPORT,
            PacketName.PLAY_ENTITY_UPDATE_ATTRIBUTES,
            PacketName.PLAY_ENTITY_EFFECT);

    @Test
    void tableKnows109() {
        assertTrue(PacketIds.isSupported(V109));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void idsDifferFrom110OnlyByTheUpdateSignShift() {
        // Stage A の確定事項：109⇔110 の ID 差分は update_sign の
        // 有無と後続6件の +1 ずれだけ。
        for (PacketName name : PacketName.values()) {
            if (name == PacketName.PLAY_UPDATE_SIGN_CLIENTBOUND) {
                assertEquals(0x46, name.id109(), name.name());
                assertEquals(-1, name.id110(), name.name());
            } else if (SHIFTED.contains(name)) {
                assertEquals(name.id110() + 1, name.id109(), name.name());
            } else {
                assertEquals(name.id110(), name.id109(), name.name());
            }
        }
    }

    @Test
    void presentCount109Is114() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id109() != -1) {
                count++;
            }
        }
        assertEquals(114, count);
    }

    @Test
    void playIds109() {
        assertEquals(0x0f, PacketIds.id(PacketName.PLAY_CHAT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V109).orElseThrow());
        assertEquals(0x45, PacketIds.id(PacketName.PLAY_TITLE,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V109).orElseThrow());
        // ずれる6件のうち代表2件と、109 だけにある update_sign。
        assertEquals(0x47, PacketIds.id(PacketName.PLAY_SOUND_EFFECT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V109).orElseThrow());
        assertEquals(0x49, PacketIds.id(PacketName.PLAY_COLLECT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V109).orElseThrow());
        assertEquals(0x46, PacketIds.id(PacketName.PLAY_UPDATE_SIGN_CLIENTBOUND,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V109).orElseThrow());
        // サーバー行きは 110 と同一（update_sign サーバー行きは両方にある）。
        assertEquals(0x19, PacketIds.id(PacketName.PLAY_UPDATE_SIGN,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND,
                V109).orElseThrow());
        assertEquals(0x16, PacketIds.id(PacketName.PLAY_RESOURCE_PACK_RESPONSE,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND,
                V109).orElseThrow());
    }
}
