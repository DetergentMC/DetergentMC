package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.bedrock.BedrockEntityTranslator.SpawnEntity;
import io.detergentmc.protocol.PacketIO;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 4（1.7.2〜1.7.5）のスポーン系パケットのパーステスト。Living／Object
 * は 5 と同形式・同種別表のため 5 用へ委譲されること、Named は
 * UUID がダッシュ無し文字列でプロパティ配列が無い 4 独自形式で
 * あることを固定する。
 */
class BedrockEntityTranslator4Test {

    @Test
    void spawnLiving4Matches5Parse() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(42);
        w.writeByte(90); // pig（1.7 系のモブ ID）
        w.writeInt(32); // x = 1.0
        w.writeInt(64); // y = 2.0
        w.writeInt(-32); // z = -1.0
        w.writeByte(0); // yaw
        w.writeByte(0); // pitch
        w.writeByte(0); // headPitch
        w.writeShort(0);
        w.writeShort(0);
        w.writeShort(0);
        w.writeByte(0x7F); // メタデータ終端
        SpawnEntity spawn = BedrockEntityTranslator.parseSpawnEntityLiving4(w.bytes());
        assertEquals(42, spawn.entityId());
        assertEquals(1.0, spawn.x());
        assertEquals(2.0, spawn.y());
        assertEquals(-1.0, spawn.z());
        SpawnEntity spawn5 = BedrockEntityTranslator.parseSpawnEntityLiving5(w.bytes());
        assertEquals(spawn5.type765(), spawn.type765());
    }

    @Test
    void namedSpawn4ReadsDashlessUuidWithoutProperties() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(7);
        // 4 の UUID はダッシュ無し32文字。プロパティ配列は無く、
        // 名前の直後が座標。
        w.writeString("123e4567e89b12d3a456426614174000");
        w.writeString("Steve");
        w.writeInt(320); // x = 10.0
        w.writeInt(128); // y = 4.0
        w.writeInt(0); // z = 0.0
        w.writeByte(64); // yaw
        w.writeByte(0); // pitch
        w.writeShort(0); // currentItem
        w.writeByte(0x7F); // メタデータ終端
        SpawnEntity spawn = BedrockEntityTranslator.parseNamedEntitySpawn4(w.bytes());
        assertEquals(7, spawn.entityId());
        assertEquals(uuid, spawn.uuid());
        assertEquals(10.0, spawn.x());
        assertEquals(4.0, spawn.y());
    }

    @Test
    void spawnEntity4Matches5Parse() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(9);
        w.writeByte(60); // arrow（1.7 系のオブジェクト ID）
        w.writeInt(0);
        w.writeInt(32);
        w.writeInt(0);
        w.writeByte(0); // pitch
        w.writeByte(0); // yaw
        w.writeInt(0); // data = 0 → 速度なし
        SpawnEntity spawn = BedrockEntityTranslator.parseSpawnEntity4(w.bytes());
        assertEquals(9, spawn.entityId());
        assertEquals(0.0, spawn.velX());
        SpawnEntity spawn5 = BedrockEntityTranslator.parseSpawnEntity5(w.bytes());
        assertEquals(spawn5.type765(), spawn.type765());
    }
}
