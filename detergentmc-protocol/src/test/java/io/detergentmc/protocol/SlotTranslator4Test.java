package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 4（1.7.2〜1.7.5）のスロット層付け替え。5 との違いは
 * named_entity_spawn の1件だけ（プロパティ配列が無く、UUID 文字列が
 * ダッシュ無し32文字）。それ以外は 5 と同じ付け替えになることを
 * 固定する。
 */
class SlotTranslator4Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("cf72409d-f7b8-35ae-b9c2-e0373fd7c562");
    private static final int NAMED_SPAWN_ID = 0x0c;

    /** 4 の named_entity_spawn（プロパティ配列なし）。 */
    private static byte[] namedSpawn4Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(99);
        writer.writeString("cf72409df7b835aeb9c2e0373fd7c562");
        writer.writeString("Steve");
        writer.writeInt(256); // x = 8.0 × 32
        writer.writeInt(2048); // y = 64.0 × 32
        writer.writeInt(256); // z
        writer.writeByte(0); // yaw
        writer.writeByte(0); // pitch
        writer.writeShort(0); // currentItem
        writer.writeByte(0x7f); // メタデータ終端
        return writer.bytes();
    }

    @Test
    void namedSpawnFrom4HasNoPropertiesAndDashlessUuid() {
        MinecraftPacket hybrid = SlotTranslator.clientboundFrom4(
                new MinecraftPacket(NAMED_SPAWN_ID, namedSpawn4Payload()));
        Reader reader = new Reader(hybrid.payload());
        assertEquals(99, reader.readVarInt());
        assertEquals(PLAYER_UUID, reader.readUuid());
        // プロパティ配列が無いため、名前の直後の固定小数点座標が
        // そのまま f64 座標になる（5 形式として読むとずれる）。
        assertEquals(8.0, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(8.0, reader.readDouble());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readByte());
        // ハイブリッド形式のメタデータ終端は 0xFF（1.7 ワイヤの
        // 0x7F から付け替わる）。
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void namedSpawnFrom5DiffersFromFrom4() {
        // 5 形式（プロパティ1件付き）を 5 として読むと正しく変換され、
        // 4 として読むと座標がずれる＝弁別できることを固定する。
        Writer writer = new Writer();
        writer.writeVarInt(99);
        writer.writeString(PLAYER_UUID.toString());
        writer.writeString("Steve");
        writer.writeVarInt(1); // properties
        writer.writeString("textures");
        writer.writeString("abc");
        writer.writeString("sig");
        writer.writeInt(256);
        writer.writeInt(2048);
        writer.writeInt(256);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeShort(0);
        writer.writeByte(0x7f);
        byte[] payload5 = writer.bytes();

        MinecraftPacket hybrid5 = SlotTranslator.clientboundFrom5(
                new MinecraftPacket(NAMED_SPAWN_ID, payload5));
        Reader r5 = new Reader(hybrid5.payload());
        assertEquals(99, r5.readVarInt());
        assertEquals(PLAYER_UUID, r5.readUuid());
        assertEquals(8.0, r5.readDouble());

        MinecraftPacket hybrid4 = SlotTranslator.clientboundFrom4(
                new MinecraftPacket(NAMED_SPAWN_ID, payload5));
        // 4 読みではプロパティ件数・文字列を座標として読み進めるため
        // メタデータ解析が破綻し、変換器は例外時の約束どおり入力を
        // そのまま返す（＝正しく変換された 5 読みとは結果が食い違う
        // ことで弁別できる）。
        assertArrayEquals(payload5, hybrid4.payload());
        org.junit.jupiter.api.Assertions.assertFalse(
                java.util.Arrays.equals(hybrid5.payload(), hybrid4.payload()));
    }

    @Test
    void serverboundFrom4MatchesFrom5() {
        // サーバー行きは 4⇔5 で形式同一のため、付け替え結果が
        // バイト単位で一致する。
        Writer chat = new Writer();
        chat.writeString("hello");
        int chatId = PacketIds.id(PacketIds.PacketName.PLAY_CHAT_TO_SERVER,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND,
                ProtocolVersion.V1_7_2).orElseThrow();
        MinecraftPacket packet = new MinecraftPacket(chatId, chat.bytes());
        MinecraftPacket from4 = SlotTranslator.serverboundFrom4(packet);
        MinecraftPacket from5 = SlotTranslator.serverboundFrom5(packet);
        assertEquals(from5.id(), from4.id());
        assertArrayEquals(from5.payload(), from4.payload());
    }

    @Test
    void clientboundTo4PassesNamedSpawnThroughForSessionSynthesis() {
        // ハイブリッド→4 の named_entity_spawn は名前・UUID 文字列の
        // 合成が要るため、SlotTranslator は素通しし ProxySession が捌く
        // （5 と同じ約束）。
        MinecraftPacket packet = new MinecraftPacket(NAMED_SPAWN_ID,
                namedSpawn4Payload());
        MinecraftPacket out = SlotTranslator.clientboundTo4(packet);
        assertEquals(packet.id(), out.id());
        assertArrayEquals(packet.payload(), out.payload());
    }
}
