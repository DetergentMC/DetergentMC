package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 108（1.9.1）のパケット ID 列。109（1.9.2）と全エントリ完全一致
 * （Stage A で実 jar 同士の全面比較とワイヤ採取により差分ゼロを
 * 確認。検証スクリプトは tools/packet-ids-108.py）。そのため
 * 110 との関係も 109 と同じになる：クライアント行き update_sign
 * が 108 にもあり（0x46、1.9.3 で削除）、その後ろの6件
 * （sound_effect〜entity_effect）が 110 より +1 ずれる。
 */
class PacketIds108Test {

    private static final int V108 = ProtocolVersion.V1_9_1;

    /** 108 でも ID が 110 より +1 ずれる6件（update_sign 削除の影響）。 */
    private static final Set<PacketName> SHIFTED = Set.of(
            PacketName.PLAY_SOUND_EFFECT,
            PacketName.PLAY_PLAYERLIST_HEADER,
            PacketName.PLAY_COLLECT,
            PacketName.PLAY_ENTITY_TELEPORT,
            PacketName.PLAY_ENTITY_UPDATE_ATTRIBUTES,
            PacketName.PLAY_ENTITY_EFFECT);

    @Test
    void tableKnows108() {
        assertTrue(PacketIds.isSupported(V108));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void idsEqual109AcrossAllEntries() {
        // Stage A の確定事項：108⇔109 の ID 差分はゼロ。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id109(), name.id108(), name.name());
        }
    }

    @Test
    void idsDifferFrom110OnlyByTheUpdateSignShift() {
        // 109 と同じ関係：update_sign の有無と後続6件の +1 ずれだけ。
        for (PacketName name : PacketName.values()) {
            if (name == PacketName.PLAY_UPDATE_SIGN_CLIENTBOUND) {
                assertEquals(0x46, name.id108(), name.name());
                assertEquals(-1, name.id110(), name.name());
            } else if (SHIFTED.contains(name)) {
                assertEquals(name.id110() + 1, name.id108(), name.name());
            } else {
                assertEquals(name.id110(), name.id108(), name.name());
            }
        }
    }

    @Test
    void presentCount108Is114() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id108() != -1) {
                count++;
            }
        }
        assertEquals(114, count);
    }

    @Test
    void playIds108() {
        assertEquals(0x0f, PacketIds.id(PacketName.PLAY_CHAT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V108).orElseThrow());
        assertEquals(0x45, PacketIds.id(PacketName.PLAY_TITLE,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V108).orElseThrow());
        // ずれる6件のうち代表2件と、108 にもある update_sign。
        assertEquals(0x47, PacketIds.id(PacketName.PLAY_SOUND_EFFECT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V108).orElseThrow());
        assertEquals(0x49, PacketIds.id(PacketName.PLAY_COLLECT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V108).orElseThrow());
        assertEquals(0x46, PacketIds.id(PacketName.PLAY_UPDATE_SIGN_CLIENTBOUND,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V108).orElseThrow());
        // サーバー行きは 110 と同一（update_sign サーバー行きは両方にある）。
        assertEquals(0x19, PacketIds.id(PacketName.PLAY_UPDATE_SIGN,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND,
                V108).orElseThrow());
        assertEquals(0x16, PacketIds.id(PacketName.PLAY_RESOURCE_PACK_RESPONSE,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND,
                V108).orElseThrow());
    }
}
