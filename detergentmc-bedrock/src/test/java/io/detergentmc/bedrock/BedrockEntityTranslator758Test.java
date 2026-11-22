package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bedrock → 内部 758（Java 1.18.2）サーバーの翻訳テスト。
 * 758 は Spawn Entity に headPitch が無く objectData が i32、モブは
 * Spawn Entity Living（0x02）、他プレイヤーは Named Entity Spawn（0x04）、
 * Entity Effect には factorCodec が無い。種別・アイテム・パーティクルは
 * 758→759→760→761→762→763 と辿って 765 に正規化する。エフェクトは
 * 763 と同じく 1 ベース（Speed=1）のため 1 引く。
 */
class BedrockEntityTranslator758Test {

    private static final UUID ENTITY_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void normalizeEntityType758PigTo765() {
        // 758 のブタ 64 → 765 のブタ 73。
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(64, 758));
        // 範囲外はブタ（73）。
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(999, 758));
    }

    @Test
    void normalizeItem758StoneTo765() {
        assertEquals(1, BedrockEntityTranslator.normalizeItem(1, 758));
        // 範囲外は石（1）。
        assertEquals(1, BedrockEntityTranslator.normalizeItem(9999, 758));
    }

    @Test
    void normalizeEffect758SpeedTo765() {
        // 758 の Speed=1 → 765 の Speed=0。33 番目（1.18.2 の末尾）→ 32。
        assertEquals(0, BedrockEntityTranslator.normalizeEffectTo765(1, 758));
        assertEquals(32, BedrockEntityTranslator.normalizeEffectTo765(33, 758));
        // 0・34 は範囲外のため Speed（0）。
        assertEquals(0, BedrockEntityTranslator.normalizeEffectTo765(0, 758));
        assertEquals(0, BedrockEntityTranslator.normalizeEffectTo765(34, 758));
    }

    /** 758 の Spawn Entity ペイロード（headPitch 無し、objectData は i32）。 */
    private static byte[] spawnEntity758Payload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeUuid(ENTITY_UUID);
        writer.writeVarInt(64); // type：758 のブタ
        writer.writeDouble(1.5);
        writer.writeDouble(2.5);
        writer.writeDouble(3.5);
        writer.writeByte(64); // pitch 90°
        writer.writeByte(0); // yaw 0°
        writer.writeInt(5); // objectData（i32）
        writer.writeShort(8000); // velX 1.0
        writer.writeShort(0); // velY
        writer.writeShort(-8000); // velZ -1.0
        return writer.bytes();
    }

    @Test
    void parseSpawnEntity758HasNoHeadPitchAndIntObjectData() {
        BedrockEntityTranslator.SpawnEntity spawn =
                BedrockEntityTranslator.parseSpawnEntity(spawnEntity758Payload(), 758);
        assertEquals(7, spawn.entityId());
        assertEquals(ENTITY_UUID, spawn.uuid());
        assertEquals(73, spawn.type765());
        assertEquals(1.5, spawn.x());
        assertEquals(2.5, spawn.y());
        assertEquals(3.5, spawn.z());
        assertEquals(90.0f, spawn.pitch());
        assertEquals(0.0f, spawn.yaw());
        // 758 には headPitch が無いため yaw で代用する。
        assertEquals(0.0f, spawn.headPitch());
        assertEquals(5, spawn.objectData());
        assertEquals(1.0, spawn.velX());
        assertEquals(0.0, spawn.velY());
        assertEquals(-1.0, spawn.velZ());
    }

    /** 758 の Spawn Entity Living ペイロード（yaw が pitch より先）。 */
    private static byte[] spawnEntityLiving758Payload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(8); // entityId
        writer.writeUuid(ENTITY_UUID);
        writer.writeVarInt(64); // type：758 のブタ
        writer.writeDouble(1.5);
        writer.writeDouble(2.5);
        writer.writeDouble(3.5);
        writer.writeByte(64); // yaw 90°
        writer.writeByte(0); // pitch 0°
        writer.writeByte(32); // headPitch 45°
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        return writer.bytes();
    }

    @Test
    void parseSpawnEntityLiving758YawBeforePitch() {
        BedrockEntityTranslator.SpawnEntity spawn =
                BedrockEntityTranslator.parseSpawnEntityLiving758(spawnEntityLiving758Payload());
        assertEquals(8, spawn.entityId());
        assertEquals(73, spawn.type765());
        assertEquals(90.0f, spawn.yaw());
        assertEquals(0.0f, spawn.pitch());
        assertEquals(45.0f, spawn.headPitch());
    }

    /** 758 の Named Entity Spawn ペイロード。 */
    private static byte[] namedEntitySpawn758Payload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(9); // entityId
        writer.writeUuid(ENTITY_UUID);
        writer.writeDouble(1.5);
        writer.writeDouble(2.5);
        writer.writeDouble(3.5);
        writer.writeByte(0); // yaw
        writer.writeByte(0); // pitch
        return writer.bytes();
    }

    @Test
    void parseNamedEntitySpawn758IsPlayer() {
        BedrockEntityTranslator.SpawnEntity spawn =
                BedrockEntityTranslator.parseNamedEntitySpawn758(namedEntitySpawn758Payload());
        assertEquals(9, spawn.entityId());
        // プレイヤー（765 で 124）。
        assertEquals(124, spawn.type765());
    }

    /** 758 の Entity Effect ペイロード（factorCodec 無し）。 */
    private static byte[] entityEffect758Payload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(3); // entityId
        writer.writeVarInt(1); // effect：758 の Speed
        writer.writeByte(2); // amplifier
        writer.writeVarInt(100); // duration
        writer.writeByte(2); // flags（showParticles）
        return writer.bytes();
    }

    @Test
    void parseEntityEffect758HasNoFactorCodec() {
        BedrockEntityTranslator.EntityEffect effect =
                BedrockEntityTranslator.parseEntityEffect(entityEffect758Payload(), 758);
        assertEquals(3, effect.entityId());
        assertEquals(0, effect.effect765());
        assertEquals(2, effect.amplifier());
        assertEquals(100, effect.duration());
        assertEquals(true, effect.showParticles());
    }
}
