package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.bedrock.BedrockEntityTranslator.SpawnEntity;
import io.detergentmc.protocol.PacketIO;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 5（1.7.10）のスポーン系パケットのパーステスト。Living／Object は
 * 47 と同形式だが種別表は 5 専用、Named は UUID 文字列＋名前＋
 * プロパティという 5 独自形式であることを固定する。
 */
class BedrockEntityTranslator5Test {

    @Test
    void spawnLiving5NormalizesWith5Table() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(42);
        w.writeByte(90); // pig（5 のモブ ID）
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
        SpawnEntity spawn = BedrockEntityTranslator.parseSpawnEntityLiving5(w.bytes());
        assertEquals(42, spawn.entityId());
        assertEquals(1.0, spawn.x());
        assertEquals(2.0, spawn.y());
        assertEquals(-1.0, spawn.z());
        // 47 の同じワイヤでも同じ 765 種別（ブタ）になる。
        SpawnEntity spawn47 = BedrockEntityTranslator.parseSpawnEntityLiving47(w.bytes());
        assertEquals(spawn47.type765(), spawn.type765());
    }

    @Test
    void namedSpawn5ReadsStringUuidNameAndProperties() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(7);
        w.writeString(uuid.toString());
        w.writeString("Steve");
        w.writeVarInt(1); // プロパティ1件
        w.writeString("textures");
        w.writeString("value");
        w.writeString("signature");
        w.writeInt(320); // x = 10.0
        w.writeInt(128); // y = 4.0
        w.writeInt(0); // z = 0.0
        w.writeByte(64); // yaw
        w.writeByte(0); // pitch
        w.writeShort(0); // currentItem
        w.writeByte(0x7F); // メタデータ終端
        SpawnEntity spawn = BedrockEntityTranslator.parseNamedEntitySpawn5(w.bytes());
        assertEquals(7, spawn.entityId());
        assertEquals(uuid, spawn.uuid());
        assertEquals(10.0, spawn.x());
        assertEquals(4.0, spawn.y());
    }

    @Test
    void spawnEntity5ArrowHasNoVelocityWhenDataZero() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(9);
        w.writeByte(60); // arrow（5 のオブジェクト ID）
        w.writeInt(0);
        w.writeInt(32);
        w.writeInt(0);
        w.writeByte(0); // pitch
        w.writeByte(0); // yaw
        w.writeInt(0); // data = 0 → 速度なし
        SpawnEntity spawn = BedrockEntityTranslator.parseSpawnEntity5(w.bytes());
        assertEquals(9, spawn.entityId());
        assertEquals(0.0, spawn.velX());
    }
}
