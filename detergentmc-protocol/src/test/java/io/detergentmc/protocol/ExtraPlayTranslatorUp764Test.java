package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link ExtraPlayTranslator} の逆向き（764→765）変換テスト。
 * 764形式のペイロードを合成して765形式に変換し、読み直して中身を確認する。
 */
class ExtraPlayTranslatorUp764Test {

    @Test
    void respawnIsIdentity() {
        Writer payload = new Writer();
        payload.writeString("minecraft:overworld");
        payload.writeString("world");
        payload.writeLong(12345L);
        payload.writeByte(0);
        payload.writeByte(1);
        payload.writeBoolean(false);
        payload.writeBoolean(false);
        payload.writeBoolean(false); // death 無し
        payload.writeVarInt(10);
        payload.writeBoolean(true); // copyMetadata（末尾。765と同じ位置）
        byte[] translated = ExtraPlayTranslator.translateRespawn764To765(payload.bytes());
        assertArrayEquals(payload.bytes(), translated);
    }

    @Test
    void startConfigurationIsIdentity() {
        byte[] empty = new byte[0];
        assertArrayEquals(empty, ExtraPlayTranslator.translateStartConfiguration764To765(empty));
    }

    @Test
    void bossBarUp() {
        UUID uuid = UUID.randomUUID();
        Writer payload = new Writer();
        payload.writeUuid(uuid);
        payload.writeVarInt(0); // 追加
        payload.writeString("{\"text\":\"Boss\",\"color\":\"red\"}");
        payload.writeFloat(0.5f);
        payload.writeVarInt(1);
        payload.writeVarInt(2);
        payload.writeByte(0);
        byte[] translated = ExtraPlayTranslator.translateBossBar764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(uuid, reader.readUuid());
        assertEquals(0, reader.readVarInt());
        assertEquals("{\"text\":\"Boss\",\"color\":\"red\"}",
                Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals(0.5f, reader.readFloat());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
    }

    @Test
    void bossBarUpTitleOnlyAction() {
        UUID uuid = UUID.randomUUID();
        Writer payload = new Writer();
        payload.writeUuid(uuid);
        payload.writeVarInt(3); // タイトル更新
        payload.writeString("\"New Title\"");
        byte[] translated = ExtraPlayTranslator.translateBossBar764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(uuid, reader.readUuid());
        assertEquals(3, reader.readVarInt());
        assertEquals("\"New Title\"", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
    }

    @Test
    void teamsUp() {
        Writer payload = new Writer();
        payload.writeString("red");
        payload.writeByte(0); // 作成
        payload.writeString("{\"text\":\"Red Team\"}");
        payload.writeByte(1);
        payload.writeString("always");
        payload.writeString("never");
        payload.writeVarInt(15);
        payload.writeString("{\"text\":\"[\"}");
        payload.writeString("{\"text\":\"]\"}");
        PacketIO.writeStringList(payload, java.util.List.of("Alice", "Bob"));
        byte[] translated = ExtraPlayTranslator.translateTeams764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("red", reader.readString());
        assertEquals(0, reader.readByte());
        assertEquals("{\"text\":\"Red Team\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals(1, reader.readByte());
        assertEquals("always", reader.readString());
        assertEquals("never", reader.readString());
        assertEquals(15, reader.readVarInt());
        assertEquals("{\"text\":\"[\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals("{\"text\":\"]\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals(java.util.List.of("Alice", "Bob"), PacketIO.readStringList(reader));
    }

    @Test
    void tabCompleteUp() {
        Writer payload = new Writer();
        payload.writeVarInt(7);
        payload.writeVarInt(1);
        payload.writeVarInt(3);
        payload.writeVarInt(2);
        payload.writeString("/help");
        payload.writeBoolean(true);
        payload.writeString("{\"text\":\"shows help\"}");
        payload.writeString("/hello");
        payload.writeBoolean(false);
        byte[] translated = ExtraPlayTranslator.translateTabComplete764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals("/help", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"shows help\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals("/hello", reader.readString());
        assertEquals(false, reader.readBoolean());
    }

    @Test
    void explosionUpSynthesizesTail() {
        Writer payload = new Writer();
        payload.writeDouble(10.0);
        payload.writeDouble(64.0);
        payload.writeDouble(-5.0);
        payload.writeFloat(3.0f);
        payload.writeVarInt(1);
        payload.writeByte(1);
        payload.writeByte(0);
        payload.writeByte(-1);
        payload.writeFloat(0.5f);
        payload.writeFloat(0.25f);
        payload.writeFloat(-0.5f);
        byte[] translated = ExtraPlayTranslator.translateExplosion764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(10.0, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-5.0, reader.readDouble());
        assertEquals(3.0f, reader.readFloat());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0.25f, reader.readFloat());
        assertEquals(-0.5f, reader.readFloat());
        // 合成した末尾。
        assertEquals(2, reader.readVarInt()); // block_interaction_type：破壊＋減衰あり
        assertEquals(23, reader.readVarInt()); // small：explosion
        assertEquals(22, reader.readVarInt()); // large：explosion_emitter
        assertEquals(0, reader.readVarInt()); // sound：直書き
        assertEquals("minecraft:entity.generic.explode", reader.readString());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void advancementsUp() {
        Writer payload = new Writer();
        payload.writeBoolean(true); // reset
        payload.writeVarInt(1);
        payload.writeString("minecraft:story/root");
        payload.writeBoolean(false); // parent 無し
        payload.writeBoolean(true); // display 有り
        payload.writeString("{\"text\":\"Title\"}");
        payload.writeString("{\"text\":\"Desc\"}");
        // icon：slot（764）。grass → short_grass。
        payload.writeBoolean(true);
        payload.writeVarInt(173);
        payload.writeByte(1);
        payload.writeByte(0); // NBT 無し
        payload.writeVarInt(0); // frameType
        payload.writeInt(0); // flags（背景テクスチャ無し）
        payload.writeFloat(0.0f);
        payload.writeFloat(0.0f);
        payload.writeVarInt(0); // requirements 無し
        payload.writeBoolean(false); // sendsTelemetryData
        payload.writeVarInt(0); // identifiers 無し
        payload.writeVarInt(0); // progressMapping 無し
        byte[] translated = ExtraPlayTranslator.translateAdvancements764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals("minecraft:story/root", reader.readString());
        assertEquals(false, reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"Title\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals("{\"text\":\"Desc\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertTrue(reader.readBoolean());
        assertEquals(194, reader.readVarInt()); // short_grass
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readInt());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }
}
