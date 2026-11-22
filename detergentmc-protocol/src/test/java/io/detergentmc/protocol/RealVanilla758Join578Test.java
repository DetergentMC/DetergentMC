package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 実バニラ 1.18.2 の Join Game（本物の codec）で 578 クライアントの流れを再現する。 */
class RealVanilla758Join578Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void joinGameWithRealCodecReaches578Client() {
        // ハンドシェイク＋ログイン。
        Writer hs = new Writer();
        hs.writeVarInt(578);
        hs.writeString("localhost");
        hs.writeUnsignedShort(25565);
        hs.writeVarInt(2);
        ProxySession.HandshakeResult result = ProxySession.resolveHandshake(
                hs.bytes(), false, null, null, ProxyAuthConfig.defaults(), null, 758);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        Writer login = new Writer();
        login.writeString("Steve");
        session.handleClientPacket(new MinecraftPacket(0x00, login.bytes()));

        // 実バニラ 1.18.2 の Login Success（UUID＋名前のみ。properties
        // フィールド自体が無い＝22バイト）。
        Writer success = new Writer();
        success.writeUuid(PLAYER_UUID);
        success.writeString("Steve");
        Outcome loginOutcome = session.handleServerPacket(
                new MinecraftPacket(0x02, success.bytes()));
        System.out.println("login outcome: toClient=" + loginOutcome.toClient().size()
                + " state=" + session.clientState() + "/" + session.serverState());

        // 実 codec を使った Join Game 758。
        Tag.CompoundTag codec = PlayTranslator.defaultCodec758();
        // dimension element は codec の overworld エントリから取る。
        Tag.CompoundTag dimRegistry = (Tag.CompoundTag) codec.value()
                .get("minecraft:dimension_type");
        Tag.ListTag values = (Tag.ListTag) dimRegistry.value().get("value");
        Tag.CompoundTag overworldEntry = (Tag.CompoundTag) values.value().get(0);
        Tag.CompoundTag element = (Tag.CompoundTag) overworldEntry.value().get("element");

        Writer join = new Writer();
        join.writeInt(42);
        join.writeBoolean(false);
        join.writeByte(0);
        join.writeByte(-1);
        PacketIO.writeStringList(join, List.of("minecraft:overworld"));
        join.writeNamedNbt("", codec);
        join.writeNamedNbt("", element);
        join.writeString("minecraft:overworld");
        join.writeLong(12345L);
        join.writeVarInt(20);
        join.writeVarInt(10);
        join.writeVarInt(10);
        join.writeBoolean(false);
        join.writeBoolean(true);
        join.writeBoolean(false);
        join.writeBoolean(false);
        Outcome joinOutcome = session.handleServerPacket(
                new MinecraftPacket(0x26, join.bytes()));
        System.out.println("join outcome: toClient=" + joinOutcome.toClient().size()
                + " toServer=" + joinOutcome.toServer().size());
        for (MinecraftPacket p : joinOutcome.toClient()) {
            System.out.println("  client packet id=0x" + Integer.toHexString(p.id())
                    + " len=" + p.payload().length);
        }
        assertEquals(1, joinOutcome.toClient().size());
        assertEquals(0x26, joinOutcome.toClient().get(0).id());
        PlayTranslator.JoinGame578 parsed = PlayTranslator.parseJoinGame578(
                joinOutcome.toClient().get(0).payload());
        assertEquals(0, parsed.dimension());
    }
}
