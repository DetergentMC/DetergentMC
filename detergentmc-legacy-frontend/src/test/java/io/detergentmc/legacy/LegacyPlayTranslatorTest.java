package io.detergentmc.legacy;

import io.detergentmc.legacy.LegacyPacketIO.LegacyPacket;
import io.detergentmc.legacy.LegacyWire.MetaEntry;
import io.detergentmc.legacy.LegacyWire.Slot;
import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.PacketIO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPlayTranslatorTest {

    private final LegacyPlayTranslator translator = new LegacyPlayTranslator();
    private final LegacyPlayTranslator.State state = new LegacyPlayTranslator.State();

    private static MinecraftPacket modern(int id, PacketIO.Writer w) {
        return new MinecraftPacket(id, w.bytes());
    }

    @Test
    void joinGameBecomesLegacyLogin() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(42);
        w.writeByte(1);      // gamemode creative
        w.writeByte(-1);     // dimension nether
        w.writeByte(2);      // difficulty
        w.writeByte(20);     // max players
        w.writeString("default");
        Optional<LegacyPacket> out = translator.toLegacy(modern(0x01, w), state);
        assertTrue(out.isPresent());
        LegacyPacket login = out.get();
        assertEquals(0x01, login.id());
        assertEquals(42, login.intField(0));
        assertEquals("default", login.field(1));
        assertEquals(1, login.intField(2));
        assertEquals(-1, login.intField(3));
        assertEquals(42, state.ownEntityId);
    }

    @Test
    void keepAliveBothWays() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(777);
        Optional<LegacyPacket> legacy = translator.toLegacy(modern(0x00, w), state);
        assertEquals(777, legacy.get().intField(0));

        Optional<MinecraftPacket> back = translator.toModern(
                new LegacyPacket(0x00, List.of(777)), state);
        assertEquals(0x00, back.get().id());
        assertEquals(777, new PacketIO.Reader(back.get().payload()).readInt());
    }

    @Test
    void chatJsonFlattensAndPlainGoesBack() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeString("{\"text\":\"<Steve> \",\"extra\":[{\"text\":\"hello\"}]}");
        Optional<LegacyPacket> legacy = translator.toLegacy(modern(0x02, w), state);
        assertEquals("<Steve> hello", legacy.get().field(0));

        Optional<MinecraftPacket> back = translator.toModern(
                new LegacyPacket(0x03, List.of("hi there")), state);
        assertEquals(0x01, back.get().id());
        assertEquals("hi there", new PacketIO.Reader(back.get().payload()).readString());
    }

    @Test
    void movementServerboundKeepsStanceSlot() {
        // Legacy wire order: x, stance, feetY, z (measured from the jar:
        // the server echoes stance = feet + 1.62 in slot 2).
        LegacyPacket pos = new LegacyPacket(0x0D,
                List.of(10.0, 65.62, 64.0, -20.0, 90.0f, 15.0f, true));
        Optional<MinecraftPacket> out = translator.toModern(pos, state);
        assertEquals(0x06, out.get().id());
        PacketIO.Reader r = new PacketIO.Reader(out.get().payload());
        assertEquals(10.0, r.readDouble());
        assertEquals(65.62, r.readDouble()); // stance passes through
        assertEquals(64.0, r.readDouble());  // feet Y
        assertEquals(-20.0, r.readDouble());
    }

    @Test
    void teleportAbsolutizesRelativeFlags() {
        state.lastX = 100;
        state.lastY = 64;
        state.lastZ = 100;
        state.positionKnown = true;
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeDouble(5.0);   // relative x
        w.writeDouble(64.0);  // absolute y
        w.writeDouble(-3.0);  // relative z
        w.writeFloat(0f);
        w.writeFloat(0f);
        w.writeByte(0x05);    // flags: x and z relative
        Optional<LegacyPacket> out = translator.toLegacy(modern(0x08, w), state);
        LegacyPacket teleport = out.get();
        assertEquals(0x0D, teleport.id());
        assertEquals(105.0, (Double) teleport.field(0));
        assertEquals(64.0 + 1.62, (Double) teleport.field(1)); // stance
        assertEquals(64.0, (Double) teleport.field(2));        // feet
        assertEquals(97.0, (Double) teleport.field(3));
    }

    @Test
    void metadataType7DroppedOthersKept() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(9);
        // entry: index 0, type 0 (byte) value 1
        w.writeByte((0 << 5) | 0);
        w.writeByte(1);
        // entry: index 1, type 7 (rotation) -> must be dropped
        w.writeByte((7 << 5) | 1);
        w.writeFloat(1f);
        w.writeFloat(2f);
        w.writeFloat(3f);
        // entry: index 6, type 3 (float) health
        w.writeByte((3 << 5) | 6);
        w.writeFloat(20f);
        w.writeByte(0x7F);
        Optional<LegacyPacket> out = translator.toLegacy(modern(0x1C, w), state);
        @SuppressWarnings("unchecked")
        List<MetaEntry> entries = (List<MetaEntry>) out.get().field(1);
        assertEquals(2, entries.size());
        assertEquals(3, entries.get(1).type());
        assertEquals(20.0f, entries.get(1).value());
    }

    @Test
    void resourcePackOfferBecomesTPack() {
        PacketIO.Writer w = new PacketIO.Writer();
        byte[] url = "http://127.0.0.1:25567/pack.zip".getBytes(StandardCharsets.UTF_8);
        w.writeString("MC|RPack");
        w.writeShort(url.length);
        w.writeBytes(url);
        Optional<LegacyPacket> out = translator.toLegacy(modern(0x3F, w), state);
        assertEquals("MC|TPack", out.get().field(0));
        byte[] data = (byte[]) out.get().field(1);
        assertEquals("http://127.0.0.1:25567/pack.zip\0" + "16",
                new String(data, StandardCharsets.UTF_8));
    }

    @Test
    void pluginMessagePassesThrough() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeString("MC|Brand");
        w.writeShort(7);
        w.writeBytes("vanilla".getBytes(StandardCharsets.UTF_8));
        Optional<LegacyPacket> out = translator.toLegacy(modern(0x3F, w), state);
        assertEquals("MC|Brand", out.get().field(0));

        Optional<MinecraftPacket> back = translator.toModern(
                new LegacyPacket(0xFA, List.of("MC|Brand",
                        "vanilla".getBytes(StandardCharsets.UTF_8))), state);
        assertEquals(0x17, back.get().id());
        PacketIO.Reader r = new PacketIO.Reader(back.get().payload());
        assertEquals("MC|Brand", r.readString());
        assertEquals(7, r.readShort());
    }

    @Test
    void spawnMobTranslates() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(100);
        w.writeByte(92); // cow
        w.writeInt(160); // x fixed
        w.writeInt(2048);
        w.writeInt(-160);
        w.writeByte(0);
        w.writeByte(0);
        w.writeByte(0);
        w.writeShort(0);
        w.writeShort(0);
        w.writeShort(0);
        w.writeByte((3 << 5) | 6); // health float
        w.writeFloat(10f);
        w.writeByte(0x7F);
        Optional<LegacyPacket> out = translator.toLegacy(modern(0x0F, w), state);
        LegacyPacket spawn = out.get();
        assertEquals(0x18, spawn.id());
        assertEquals(100, spawn.intField(0));
        assertEquals(92, spawn.intField(1));
    }

    @Test
    void signEditorKeptFor78DroppedFor73() {
        // Modern 0x36 (open sign editor) becomes legacy 0x85, which
        // does not exist in protocol 73 (arrived in 1.6.2).
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(1);
        w.writeInt(64);
        w.writeInt(2);
        Optional<LegacyPacket> for78 = translator.toLegacy(modern(0x36, w), state);
        assertTrue(for78.isPresent());
        assertEquals(0x85, for78.get().id());

        LegacyPlayTranslator translator73 = new LegacyPlayTranslator(LegacyProtocol.V1_6_1);
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeInt(1);
        w2.writeInt(64);
        w2.writeInt(2);
        assertTrue(translator73.toLegacy(modern(0x36, w2), state).isEmpty(),
                "0x85 must be dropped for a 1.6.1 client");
    }

    @Test
    void mapAndStatisticsAreDropped() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(0);
        assertTrue(translator.toLegacy(modern(0x34, w), state).isEmpty());
        assertTrue(translator.toLegacy(modern(0x37, w), state).isEmpty());
        assertTrue(translator.toLegacy(modern(0x7E, w), state).isEmpty());
    }

    @Test
    void windowItemsRemapSlotItems() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(0);
        w.writeShort(1);
        w.writeShort(95); // stained glass -> glass (20)
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        Optional<LegacyPacket> out = translator.toLegacy(modern(0x30, w), state);
        @SuppressWarnings("unchecked")
        List<Slot> slots = (List<Slot>) out.get().field(1);
        assertEquals(20, slots.get(0).itemId());
    }

    @Test
    void tabCompleteJoinsWithNul() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(2);
        w.writeString("alpha");
        w.writeString("beta");
        Optional<LegacyPacket> out = translator.toLegacy(modern(0x3A, w), state);
        assertEquals("alpha\0beta\0", out.get().field(0));
    }

    @Test
    void disconnectFlattens() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeString("{\"text\":\"Kicked for flying\"}");
        Optional<LegacyPacket> out = translator.toLegacy(modern(0x40, w), state);
        assertEquals(0xFF, out.get().id());
        assertEquals("Kicked for flying", out.get().field(0));
    }

    @Test
    void clientSettingsTranslate() {
        LegacyPacket settings = new LegacyPacket(0xCC,
                List.of("ja_JP", 10, 0, 0, true));
        Optional<MinecraftPacket> out = translator.toModern(settings, state);
        assertEquals(0x15, out.get().id());
        PacketIO.Reader r = new PacketIO.Reader(out.get().payload());
        assertEquals("ja_JP", r.readString());
        assertEquals(10, r.readByte());
    }

    @Test
    void armAnimationBecomesEmptyModernPacket() {
        Optional<MinecraftPacket> out = translator.toModern(
                new LegacyPacket(0x12, List.of(123, 1)), state);
        assertEquals(0x0A, out.get().id());
        assertEquals(0, out.get().payload().length);
    }

    // ------------------------------------------------------------------
    // Protocol 61 (1.5.2) specifics (docs/PROXY-1.5.2.md)
    // ------------------------------------------------------------------

    private final LegacyPlayTranslator translator61 =
            new LegacyPlayTranslator(LegacyProtocol.V1_5_2);

    @Test
    void protocol60GetsTheSame15GenerationConversions() {
        // The 60 translator must behave exactly like the 61 one: the
        // generation branches are keyed on is1_5Generation, not on
        // protocol 61 alone.
        LegacyPlayTranslator translator60 =
                new LegacyPlayTranslator(LegacyProtocol.V1_5_1);
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeFloat(19.6f);
        w.writeShort(18);
        w.writeFloat(0.5f);
        Optional<LegacyPacket> out = translator60.toLegacy(modern(0x06, w), state);
        LegacyPacket health = out.get();
        assertEquals(0x08, health.id());
        assertEquals(20, health.intField(0), "health rounds to the I16 for 60 too");
        assertEquals(18, health.intField(1));

        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeByte(3);
        w2.writeFloat(0.05f);
        w2.writeFloat(0.1f);
        LegacyPacket abilities = translator60.toLegacy(modern(0x39, w2), state).get();
        assertEquals(0xCA, abilities.id());
        assertEquals(13, abilities.intField(1), "fly speed converts for 60 too");
    }

    @Test
    void healthBecomesShortFor61() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeFloat(19.6f);
        w.writeShort(18);
        w.writeFloat(0.5f);
        Optional<LegacyPacket> out = translator61.toLegacy(modern(0x06, w), state);
        LegacyPacket health = out.get();
        assertEquals(0x08, health.id());
        assertEquals(20, health.intField(0), "health rounds to the 61 I16");
        assertEquals(18, health.intField(1));
        // The same packet stays a float for 78.
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeFloat(19.6f);
        w2.writeShort(18);
        w2.writeFloat(0.5f);
        assertEquals(19.6f, (Float) translator.toLegacy(modern(0x06, w2), state)
                .get().field(0));
    }

    @Test
    void abilitiesConvertSpeedsFor61() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(3);
        w.writeFloat(0.05f);
        w.writeFloat(0.1f);
        Optional<LegacyPacket> out = translator61.toLegacy(modern(0x39, w), state);
        LegacyPacket abilities = out.get();
        assertEquals(0xCA, abilities.id());
        assertEquals(3, abilities.intField(0));
        assertEquals(13, abilities.intField(1), "fly speed 0.05 -> byte 13");
        assertEquals(26, abilities.intField(2), "walk speed 0.1 -> byte 26");
        // And back: a 61 client's byte speeds become floats again.
        Optional<MinecraftPacket> back = translator61.toModern(
                new LegacyPacket(0xCA, List.of(3, 13, 26)), state);
        assertEquals(0x13, back.get().id());
        PacketIO.Reader r = new PacketIO.Reader(back.get().payload());
        assertEquals(3, r.readByte());
        assertEquals(13 / 255f, r.readFloat());
        assertEquals(26 / 255f, r.readFloat());
    }

    @Test
    void attachDropsLeashFlagFor61() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(7);
        w.writeInt(9);
        w.writeByte(1);
        Optional<LegacyPacket> out = translator61.toLegacy(modern(0x1B, w), state);
        assertEquals(0x27, out.get().id());
        assertEquals(2, out.get().fields().size());
    }

    @Test
    void attributesDroppedFor61() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(7);
        w.writeInt(0); // no properties
        assertTrue(translator61.toLegacy(modern(0x20, w), state).isEmpty(),
                "0x2C does not exist in protocol 61");
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeInt(7);
        w2.writeInt(0);
        assertEquals(0x2C, translator.toLegacy(modern(0x20, w2), state).get().id());
    }

    @Test
    void horseSpawnsAsPigFor61() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(100);
        w.writeByte(100); // horse (protocol-5 mob type)
        w.writeInt(160);
        w.writeInt(2048);
        w.writeInt(-160);
        w.writeByte(0);
        w.writeByte(0);
        w.writeByte(0);
        w.writeShort(0);
        w.writeShort(0);
        w.writeShort(0);
        w.writeByte(0x7F);
        Optional<LegacyPacket> out = translator61.toLegacy(modern(0x0F, w), state);
        assertEquals(0x18, out.get().id());
        assertEquals(90, out.get().intField(1), "horse -> pig for a 61 client");
    }

    @Test
    void entityAction61GetsSyntheticAux() {
        Optional<MinecraftPacket> out = translator61.toModern(
                new LegacyPacket(0x13, List.of(5, 1)), state);
        assertEquals(0x0B, out.get().id());
        PacketIO.Reader r = new PacketIO.Reader(out.get().payload());
        assertEquals(5, r.readInt());
        assertEquals(1, r.readByte());
        assertEquals(0, r.readInt(), "the missing aux field is synthesized as 0");
    }

    @Test
    void windowItemsRemap61Items() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(0);
        w.writeShort(1);
        w.writeShort(420); // lead (1.6) -> string (287) for 61
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        Optional<LegacyPacket> out = translator61.toLegacy(modern(0x30, w), state);
        @SuppressWarnings("unchecked")
        List<Slot> slots = (List<Slot>) out.get().field(1);
        assertEquals(287, slots.get(0).itemId());
    }

    // ------------------------------------------------------------------
    // Protocol 51 (1.4.7 / 1.4.6) specifics (docs/PROXY-1.4.7.md)
    // ------------------------------------------------------------------

    private final LegacyPlayTranslator translator51 =
            new LegacyPlayTranslator(LegacyProtocol.V1_4_7);

    @Test
    void healthBecomesShortFor51() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeFloat(19.6f);
        w.writeShort(18);
        w.writeFloat(0.5f);
        LegacyPacket health = translator51.toLegacy(modern(0x06, w), state).get();
        assertEquals(0x08, health.id());
        assertEquals(20, health.intField(0), "health rounds to the 51 I16");
        assertEquals(18, health.intField(1));
    }

    @Test
    void abilitiesConvertSpeedsFor51() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(3);
        w.writeFloat(0.05f);
        w.writeFloat(0.1f);
        LegacyPacket abilities = translator51.toLegacy(modern(0x39, w), state).get();
        assertEquals(0xCA, abilities.id());
        assertEquals(13, abilities.intField(1), "fly speed 0.05 -> byte 13 for 51");
        assertEquals(26, abilities.intField(2), "walk speed 0.1 -> byte 26 for 51");
        Optional<MinecraftPacket> back = translator51.toModern(
                new LegacyPacket(0xCA, List.of(3, 13, 26)), state);
        assertEquals(0x13, back.get().id());
        PacketIO.Reader r = new PacketIO.Reader(back.get().payload());
        assertEquals(3, r.readByte());
        assertEquals(13 / 255f, r.readFloat());
        assertEquals(26 / 255f, r.readFloat());
    }

    @Test
    void attachDropsLeashFlagFor51() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(7);
        w.writeInt(9);
        w.writeByte(1);
        Optional<LegacyPacket> out = translator51.toLegacy(modern(0x1B, w), state);
        assertEquals(0x27, out.get().id());
        assertEquals(2, out.get().fields().size());
    }

    @Test
    void horseSpawnsAsPigFor51() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(100);
        w.writeByte(100); // horse (protocol-5 mob type)
        w.writeInt(160);
        w.writeInt(2048);
        w.writeInt(-160);
        w.writeByte(0);
        w.writeByte(0);
        w.writeByte(0);
        w.writeShort(0);
        w.writeShort(0);
        w.writeShort(0);
        w.writeByte(0x7F);
        Optional<LegacyPacket> out = translator51.toLegacy(modern(0x0F, w), state);
        assertEquals(0x18, out.get().id());
        assertEquals(90, out.get().intField(1), "horse -> pig for a 51 client");
    }

    @Test
    void scoreboardAndParticlesDroppedFor51() {
        // Named particles (modern 0x2A -> legacy 0x3F) arrived in 1.5.
        PacketIO.Writer wp = new PacketIO.Writer();
        wp.writeString("note");
        for (int i = 0; i < 7; i++) {
            wp.writeFloat(0f);
        }
        wp.writeInt(1);
        assertTrue(translator51.toLegacy(modern(0x2A, wp), state).isEmpty(),
                "0x3F does not exist in protocol 51");
        PacketIO.Writer wp2 = new PacketIO.Writer();
        wp2.writeString("note");
        for (int i = 0; i < 7; i++) {
            wp2.writeFloat(0f);
        }
        wp2.writeInt(1);
        assertEquals(0x3F, translator61.toLegacy(modern(0x2A, wp2), state).get().id());

        // Scoreboard objective (modern 0x3B -> legacy 0xCE).
        PacketIO.Writer wo = new PacketIO.Writer();
        wo.writeString("obj");
        wo.writeString("title");
        wo.writeByte(0);
        assertTrue(translator51.toLegacy(modern(0x3B, wo), state).isEmpty(),
                "0xCE does not exist in protocol 51");
        PacketIO.Writer wo2 = new PacketIO.Writer();
        wo2.writeString("obj");
        wo2.writeString("title");
        wo2.writeByte(0);
        assertEquals(0xCE, translator61.toLegacy(modern(0x3B, wo2), state).get().id());

        // Update score (modern 0x3C -> legacy 0xCF).
        PacketIO.Writer ws = new PacketIO.Writer();
        ws.writeString("Steve");
        ws.writeByte(0);
        ws.writeString("obj");
        ws.writeInt(42);
        assertTrue(translator51.toLegacy(modern(0x3C, ws), state).isEmpty(),
                "0xCF does not exist in protocol 51");

        // Display scoreboard (modern 0x3D -> legacy 0xD0).
        PacketIO.Writer wd = new PacketIO.Writer();
        wd.writeVarInt(1);
        wd.writeString("obj");
        assertTrue(translator51.toLegacy(modern(0x3D, wd), state).isEmpty(),
                "0xD0 does not exist in protocol 51");

        // Teams (modern 0x3E -> legacy 0xD1), remove form.
        PacketIO.Writer wt = new PacketIO.Writer();
        wt.writeString("team");
        wt.writeByte(1);
        assertTrue(translator51.toLegacy(modern(0x3E, wt), state).isEmpty(),
                "0xD1 does not exist in protocol 51");
    }

    @Test
    void minecartVariantDataDroppedFor51() {
        // spawn_object: varint id, u8 type, i32 x/y/z, i8 pitch,
        // i8 yaw, i32 data, then velocity shorts when data != 0.
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(50);
        w.writeByte(10); // minecart
        w.writeInt(160);
        w.writeInt(2048);
        w.writeInt(-160);
        w.writeByte(0);
        w.writeByte(0);
        w.writeInt(3); // TNT minecart (a 1.5 variant)
        w.writeShort(0);
        w.writeShort(0);
        w.writeShort(0);
        Optional<LegacyPacket> out = translator51.toLegacy(modern(0x0E, w), state);
        assertEquals(0x17, out.get().id());
        assertEquals(0, out.get().intField(7),
                "TNT minecart data falls back to a plain minecart for 51");
        // Chest minecarts exist in 1.4.7: data 1 is kept.
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeVarInt(51);
        w2.writeByte(10);
        w2.writeInt(160);
        w2.writeInt(2048);
        w2.writeInt(-160);
        w2.writeByte(0);
        w2.writeByte(0);
        w2.writeInt(1);
        w2.writeShort(0);
        w2.writeShort(0);
        w2.writeShort(0);
        assertEquals(1, translator51.toLegacy(modern(0x0E, w2), state)
                .get().intField(7));
        // And a 61 client keeps the TNT variant.
        PacketIO.Writer w3 = new PacketIO.Writer();
        w3.writeVarInt(50);
        w3.writeByte(10);
        w3.writeInt(160);
        w3.writeInt(2048);
        w3.writeInt(-160);
        w3.writeByte(0);
        w3.writeByte(0);
        w3.writeInt(3);
        w3.writeShort(0);
        w3.writeShort(0);
        w3.writeShort(0);
        assertEquals(3, translator61.toLegacy(modern(0x0E, w3), state)
                .get().intField(7));
    }

    @Test
    void windowItemsRemap51Items() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(0);
        w.writeShort(2);
        w.writeShort(406); // nether quartz (1.5) -> diamond (264) for 51
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        w.writeShort(154); // hopper (1.5) -> dispenser (23) for 51
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        Optional<LegacyPacket> out = translator51.toLegacy(modern(0x30, w), state);
        @SuppressWarnings("unchecked")
        List<Slot> slots = (List<Slot>) out.get().field(1);
        assertEquals(264, slots.get(0).itemId());
        assertEquals(23, slots.get(1).itemId());
    }

    // ------------------------------------------------------------------
    // Protocol 49 (1.4.5 / 1.4.4) specifics (docs/PROXY-1.4.5.md)
    // ------------------------------------------------------------------

    private final LegacyPlayTranslator translator49 =
            new LegacyPlayTranslator(LegacyProtocol.V1_4_5);

    @Test
    void fireworkItemsRemapFor49() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(0);
        w.writeShort(2);
        w.writeShort(401); // firework rocket (1.4.6) -> paper (339) for 49
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        w.writeShort(403); // enchanted book (1.4.6) -> book (340) for 49
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        Optional<LegacyPacket> out = translator49.toLegacy(modern(0x30, w), state);
        @SuppressWarnings("unchecked")
        List<Slot> slots = (List<Slot>) out.get().field(1);
        assertEquals(339, slots.get(0).itemId());
        assertEquals(340, slots.get(1).itemId());
        // A 51 client keeps both items as they are.
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeByte(0);
        w2.writeShort(1);
        w2.writeShort(401);
        w2.writeByte(1);
        w2.writeShort(0);
        w2.writeShort(-1);
        Optional<LegacyPacket> out51 = translator51.toLegacy(modern(0x30, w2), state);
        @SuppressWarnings("unchecked")
        List<Slot> slots51 = (List<Slot>) out51.get().field(1);
        assertEquals(401, slots51.get(0).itemId());
    }

    @Test
    void fireworkRocketSpawnDroppedFor49() {
        // spawn_object type 22 = firework rocket (arrived in 1.4.6).
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(60);
        w.writeByte(22);
        w.writeInt(160);
        w.writeInt(2048);
        w.writeInt(-160);
        w.writeByte(0);
        w.writeByte(0);
        w.writeInt(0);
        assertTrue(translator49.toLegacy(modern(0x0E, w), state).isEmpty(),
                "firework rockets do not exist in protocol 49");
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeVarInt(60);
        w2.writeByte(22);
        w2.writeInt(160);
        w2.writeInt(2048);
        w2.writeInt(-160);
        w2.writeByte(0);
        w2.writeByte(0);
        w2.writeInt(0);
        assertEquals(0x17, translator51.toLegacy(modern(0x0E, w2), state).get().id(),
                "a 51 client receives the firework rocket spawn");
    }

    // ------------------------------------------------------------------
    // Legacy protocol 47 (1.4.2) specifics (docs/PROXY-1.4.2.md)
    // ------------------------------------------------------------------

    private final LegacyPlayTranslator translator47Old =
            new LegacyPlayTranslator(LegacyProtocol.V1_4_2);

    @Test
    void waitRecordRemapsFor47Old() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(0);
        w.writeShort(2);
        w.writeShort(2267); // "wait" record (1.4.4) -> "11" record (2266) for legacy 47
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        w.writeShort(401); // firework rocket -> paper (339) via the 49 layer
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        Optional<LegacyPacket> out = translator47Old.toLegacy(modern(0x30, w), state);
        @SuppressWarnings("unchecked")
        List<Slot> slots = (List<Slot>) out.get().field(1);
        assertEquals(2266, slots.get(0).itemId());
        assertEquals(339, slots.get(1).itemId());
        // A 49 client keeps the "wait" record as it is.
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeByte(0);
        w2.writeShort(1);
        w2.writeShort(2267);
        w2.writeByte(1);
        w2.writeShort(0);
        w2.writeShort(-1);
        Optional<LegacyPacket> out49 = translator49.toLegacy(modern(0x30, w2), state);
        @SuppressWarnings("unchecked")
        List<Slot> slots49 = (List<Slot>) out49.get().field(1);
        assertEquals(2267, slots49.get(0).itemId());
    }

    @Test
    void fireworkRocketSpawnDroppedFor47Old() {
        // spawn_object type 22 = firework rocket (arrived in 1.4.6);
        // legacy 47 (1.4.2) has no such object type either.
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(60);
        w.writeByte(22);
        w.writeInt(160);
        w.writeInt(2048);
        w.writeInt(-160);
        w.writeByte(0);
        w.writeByte(0);
        w.writeInt(0);
        assertTrue(translator47Old.toLegacy(modern(0x0E, w), state).isEmpty(),
                "firework rockets do not exist in legacy protocol 47 (1.4.2)");
    }

    @Test
    void blockChangeRemapsFor51() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(10);
        w.writeByte(64);
        w.writeInt(-20);
        w.writeVarInt(154); // hopper
        w.writeByte(3);
        LegacyPacket out = translator51.toLegacy(modern(0x23, w), state).get();
        assertEquals(0x35, out.id());
        assertEquals(23, out.intField(3), "hopper -> dispenser for 51");
        assertEquals(3, out.intField(4), "facing meta is kept");

        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeInt(10);
        w2.writeByte(64);
        w2.writeInt(-20);
        w2.writeVarInt(44); // stone slab, meta 7 = quartz variant
        w2.writeByte(7);
        LegacyPacket out2 = translator51.toLegacy(modern(0x23, w2), state).get();
        assertEquals(44, out2.intField(3));
        assertEquals(1, out2.intField(4), "quartz slab -> sandstone slab for 51");
    }

    // ------------------------------------------------------------------
    // Protocol 39 (1.3.2 / 1.3.1) specifics (docs/PROXY-1.3.2.md)
    // ------------------------------------------------------------------

    private final LegacyPlayTranslator translator39 =
            new LegacyPlayTranslator(LegacyProtocol.V1_3_2);

    @Test
    void netherStarRemapsFor39() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(0);
        w.writeShort(2);
        w.writeShort(399); // nether star (1.4) -> emerald (388) for 39
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        w.writeShort(389); // item frame (1.4) -> painting (321) for 39
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        Optional<LegacyPacket> out = translator39.toLegacy(modern(0x30, w), state);
        @SuppressWarnings("unchecked")
        List<Slot> slots = (List<Slot>) out.get().field(1);
        assertEquals(388, slots.get(0).itemId());
        assertEquals(321, slots.get(1).itemId());
        // A legacy-47 client keeps both items as they are.
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeByte(0);
        w2.writeShort(1);
        w2.writeShort(399);
        w2.writeByte(1);
        w2.writeShort(0);
        w2.writeShort(-1);
        Optional<LegacyPacket> out47 = translator47Old.toLegacy(modern(0x30, w2), state);
        @SuppressWarnings("unchecked")
        List<Slot> slots47 = (List<Slot>) out47.get().field(1);
        assertEquals(399, slots47.get(0).itemId());
    }

    @Test
    void timeUpdate39CarriesOnlyTheTimeOfDay() {
        // The modern packet carries (worldAge, timeOfDay); 1.3.2
        // has a single time value, which is the time of day (the
        // world-age / day-time split arrived in 1.4, measured).
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeLong(999999L); // world age
        w.writeLong(6000L);   // time of day
        LegacyPacket out = translator39.toLegacy(modern(0x03, w), state).get();
        assertEquals(0x04, out.id());
        assertEquals(List.of(6000L), out.fields());
        // Legacy 47 keeps both values.
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeLong(999999L);
        w2.writeLong(6000L);
        LegacyPacket out47 = translator47Old.toLegacy(modern(0x03, w2), state).get();
        assertEquals(List.of(999999L, 6000L), out47.fields());
    }

    @Test
    void worldEvent39DropsTheGlobalFlag() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(1001);
        w.writeInt(10);
        w.writeByte(64);
        w.writeInt(-20);
        w.writeInt(5);
        w.writeBoolean(true);
        LegacyPacket out = translator39.toLegacy(modern(0x28, w), state).get();
        assertEquals(0x3D, out.id());
        assertEquals(5, out.fields().size(), "39 effect has no trailing bool");
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeInt(1001);
        w2.writeInt(10);
        w2.writeByte(64);
        w2.writeInt(-20);
        w2.writeInt(5);
        w2.writeBoolean(true);
        LegacyPacket out47 = translator47Old.toLegacy(modern(0x28, w2), state).get();
        assertEquals(6, out47.fields().size(), "legacy 47 effect keeps the bool");
    }

    @Test
    void witherBatWitchBecomePigsFor39() {
        for (int type : new int[]{64, 65, 66}) {
            PacketIO.Writer w = new PacketIO.Writer();
            w.writeVarInt(50);
            w.writeByte(type);
            w.writeInt(160);
            w.writeInt(2048);
            w.writeInt(-160);
            w.writeByte(0);
            w.writeByte(0);
            w.writeByte(0);
            w.writeShort(0);
            w.writeShort(0);
            w.writeShort(0);
            w.writeByte(0x7F); // empty metadata (bitfield terminator)
            LegacyPacket out = translator39.toLegacy(modern(0x0F, w), state).get();
            assertEquals(0x18, out.id());
            assertEquals(90, out.intField(1), "mob type " + type + " -> pig for 39");
        }
        // A legacy-47 client sees the witch as a witch.
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(50);
        w.writeByte(66);
        w.writeInt(160);
        w.writeInt(2048);
        w.writeInt(-160);
        w.writeByte(0);
        w.writeByte(0);
        w.writeByte(0);
        w.writeShort(0);
        w.writeShort(0);
        w.writeShort(0);
        w.writeByte(0x7F);
        LegacyPacket out47 = translator47Old.toLegacy(modern(0x0F, w), state).get();
        assertEquals(66, out47.intField(1));
    }

    @Test
    void fireworkRocketSpawnDroppedFor39() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(60);
        w.writeByte(22);
        w.writeInt(160);
        w.writeInt(2048);
        w.writeInt(-160);
        w.writeByte(0);
        w.writeByte(0);
        w.writeInt(0);
        assertTrue(translator39.toLegacy(modern(0x0E, w), state).isEmpty(),
                "firework rockets do not exist in protocol 39");
    }

    @Test
    void blockChangeRemapsFor39() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(10);
        w.writeByte(64);
        w.writeInt(-20);
        w.writeVarInt(137); // command block
        w.writeByte(3);
        LegacyPacket out = translator39.toLegacy(modern(0x23, w), state).get();
        assertEquals(0x35, out.id());
        assertEquals(25, out.intField(3), "command block -> note block for 39");
        assertEquals(3, out.intField(4), "meta is kept");
    }

    @Test
    void clientSettings39SynthesizesShowCape() {
        // A 39 client_settings packet has no showCape flag; the
        // modern side expects one, synthesized as true.
        LegacyPacket legacy = new LegacyPacket(0xCC, List.of("en_US", 0, 0, 0));
        Optional<MinecraftPacket> out = translator39.toModern(legacy, state);
        assertEquals(0x15, out.get().id());
        PacketIO.Reader r = new PacketIO.Reader(out.get().payload());
        // Internal modern layout: string locale, i8 view distance,
        // i8 chat flags, bool chat colors, i8 difficulty, bool
        // show cape.
        assertEquals("en_US", r.readString());
        assertEquals(0, r.readByte());
        assertEquals(0, r.readByte());
        assertTrue(r.readBoolean());
        assertEquals(0, r.readByte());
        assertTrue(r.readBoolean(), "showCape is synthesized as true for 39");
    }

    // ------------------------------------------------------------------
    // Protocol 29 (1.2.5) specifics (docs/PROXY-1.2.5.md)
    // ------------------------------------------------------------------

    private final LegacyPlayTranslator translator29 =
            new LegacyPlayTranslator(LegacyProtocol.V1_2_5);

    @Test
    void joinGame29BecomesEightFieldLogin() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(42);
        w.writeByte(1);      // gamemode creative
        w.writeByte(-1);     // dimension nether
        w.writeByte(2);      // difficulty
        w.writeByte(20);     // max players
        w.writeString("default");
        List<LegacyPacket> out = translator29.toLegacyAll(modern(0x01, w), state);
        assertEquals(1, out.size());
        LegacyPacket login = out.get(0);
        assertEquals(0x01, login.id());
        // [entityId, "", levelType, gamemode, dimension, difficulty,
        //  worldHeight(0), maxPlayers] — the 29 login-response form.
        assertEquals(List.of(42, "", "default", 1, -1, 2, 0, 20), login.fields());
        assertEquals(42, state.ownEntityId);
    }

    @Test
    void timeUpdate29CarriesOnlyTheTimeOfDay() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeLong(999999L);
        w.writeLong(6000L);
        LegacyPacket out = translator29.toLegacy(modern(0x03, w), state).get();
        assertEquals(0x04, out.id());
        assertEquals(List.of(6000L), out.fields());
    }

    @Test
    void chunk29BecomesPreChunkThenChunk() {
        int[] ids = new int[4096];
        ids[0] = 129; // emerald ore -> diamond ore (56) for 29
        byte[] raw = new byte[10496];
        for (int j = 0; j < 4096; j++) {
            raw[j] = (byte) ids[j];
        }
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(3);
        w.writeInt(-5);
        w.writeBoolean(true);
        w.writeShort(1);
        w.writeShort(0);
        byte[] data = LegacyChunkRemap.deflate(raw);
        w.writeInt(data.length);
        w.writeBytes(data);
        List<LegacyPacket> out = translator29.toLegacyAll(modern(0x21, w), state);
        assertEquals(2, out.size());
        assertEquals(0x32, out.get(0).id(), "the pre-chunk comes first");
        assertEquals(List.of(3, -5, 1), out.get(0).fields());
        assertEquals(0x33, out.get(1).id());
        assertEquals(3, out.get(1).intField(0));
        assertEquals(-5, out.get(1).intField(1));
        byte[] remapped = LegacyChunkRemap.inflate((byte[]) out.get(1).field(5));
        assertEquals(56, remapped[0] & 0xFF);
    }

    @Test
    void chunkUnload29BecomesPreChunkModeZero() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(7);
        w.writeInt(9);
        w.writeBoolean(true);
        w.writeShort(0); // primary bitmap 0 + ground-up = unload
        w.writeShort(0);
        w.writeInt(0);
        List<LegacyPacket> out = translator29.toLegacyAll(modern(0x21, w), state);
        assertEquals(1, out.size());
        assertEquals(0x32, out.get(0).id());
        assertEquals(List.of(7, 9, 0), out.get(0).fields());
    }

    @Test
    void bulk29SplitsIntoPreChunkPairs() {
        byte[] chunkA = new byte[10496];
        chunkA[0] = (byte) 129; // emerald ore -> 56
        byte[] chunkB = new byte[10496];
        chunkB[0] = (byte) 133; // emerald block -> 57
        byte[] both = new byte[chunkA.length + chunkB.length];
        System.arraycopy(chunkA, 0, both, 0, chunkA.length);
        System.arraycopy(chunkB, 0, both, chunkA.length, chunkB.length);
        byte[] data = LegacyChunkRemap.deflate(both);
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeShort(2);
        w.writeInt(data.length);
        w.writeBoolean(true);
        w.writeBytes(data);
        w.writeInt(1);
        w.writeInt(2);
        w.writeShort(1);
        w.writeShort(0);
        w.writeInt(3);
        w.writeInt(4);
        w.writeShort(1);
        w.writeShort(0);
        List<LegacyPacket> out = translator29.toLegacyAll(modern(0x26, w), state);
        assertEquals(4, out.size());
        assertEquals(List.of(1, 2, 1), out.get(0).fields());
        assertEquals(0x33, out.get(1).id());
        assertEquals(56, LegacyChunkRemap.inflate((byte[]) out.get(1).field(5))[0] & 0xFF);
        assertEquals(List.of(3, 4, 1), out.get(2).fields());
        assertEquals(0x33, out.get(3).id());
        assertEquals(57, LegacyChunkRemap.inflate((byte[]) out.get(3).field(5))[0] & 0xFF);
    }

    @Test
    void destroy29SplitsIntoSingleIdPackets() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(3);
        w.writeInt(11);
        w.writeInt(22);
        w.writeInt(33);
        List<LegacyPacket> out = translator29.toLegacyAll(modern(0x13, w), state);
        assertEquals(3, out.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(0x1D, out.get(i).id());
        }
        assertEquals(List.of(11), out.get(0).field(0));
        assertEquals(List.of(22), out.get(1).field(0));
        assertEquals(List.of(33), out.get(2).field(0));
        // The same packet toward 39 stays one multi-id packet.
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeByte(3);
        w2.writeInt(11);
        w2.writeInt(22);
        w2.writeInt(33);
        List<LegacyPacket> out39 = translator39.toLegacyAll(modern(0x13, w2), state);
        assertEquals(1, out39.size());
        assertEquals(List.of(11, 22, 33), out39.get(0).field(0));
    }

    @Test
    void abilities29AreFourBools() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(0b1101); // invulnerable + canFly + creative
        w.writeFloat(0.05f);
        w.writeFloat(0.1f);
        LegacyPacket out = translator29.toLegacy(modern(0x39, w), state).get();
        assertEquals(0xCA, out.id());
        assertEquals(List.of(true, false, true, true), out.fields());

        Optional<MinecraftPacket> back = translator29.toModern(
                new LegacyPacket(0xCA, List.of(true, false, true, false)), state);
        assertEquals(0x13, back.get().id());
        PacketIO.Reader r = new PacketIO.Reader(back.get().payload());
        assertEquals(0b0101, r.readUnsignedByte());
        assertEquals(0.05f, r.readFloat());
        assertEquals(0.1f, r.readFloat());
    }

    @Test
    void spawnPlayer29DropsTrailingMetadata() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(50);
        w.writeString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        w.writeString("Steve");
        w.writeVarInt(0); // no properties
        w.writeInt(160);
        w.writeInt(2048);
        w.writeInt(-160);
        w.writeByte(10);
        w.writeByte(20);
        w.writeShort(0);
        w.writeByte(0x7F); // empty metadata
        LegacyPacket out = translator29.toLegacy(modern(0x0C, w), state).get();
        assertEquals(0x14, out.id());
        assertEquals(8, out.fields().size(), "29 named spawn has no metadata field");
        assertEquals(50, out.intField(0));
        assertEquals("Steve", out.field(1));
    }

    @Test
    void spawnMob29DropsVelocitiesAndSubstitutesNewMobs() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(50);
        w.writeByte(66); // witch (1.4) -> pig for 29
        w.writeInt(160);
        w.writeInt(2048);
        w.writeInt(-160);
        w.writeByte(0);
        w.writeByte(0);
        w.writeByte(0);
        w.writeShort(100);
        w.writeShort(200);
        w.writeShort(300);
        w.writeByte(0x7F);
        LegacyPacket out = translator29.toLegacy(modern(0x0F, w), state).get();
        assertEquals(0x18, out.id());
        assertEquals(9, out.fields().size(), "29 mob spawn has no velocity fields");
        assertEquals(90, out.intField(1), "witch -> pig for 29");
    }

    @Test
    void blockPlace29WithoutCursorGetsZeroCursor() {
        LegacyPacket legacy = new LegacyPacket(0x0F, List.of(
                10, 64, -20, 1, new Slot(276, 1, 0, null)));
        Optional<MinecraftPacket> out = translator29.toModern(legacy, state);
        assertEquals(0x08, out.get().id());
        PacketIO.Reader r = new PacketIO.Reader(out.get().payload());
        r.readInt();
        r.readUnsignedByte();
        r.readInt();
        r.readUnsignedByte();
        // The modern slot: item, count, damage, NBT length (-1).
        assertEquals(276, r.readShort());
        r.readByte();
        r.readShort();
        assertEquals(-1, r.readShort());
        assertEquals(0, r.readByte(), "cursor x defaults to 0 for 29");
        assertEquals(0, r.readByte());
        assertEquals(0, r.readByte());
    }

    @Test
    void respawn29BecomesClientCommandBut39DropsIt() {
        LegacyPacket legacy = new LegacyPacket(0x09, List.of(0, 1, 0, 256, "default"));
        Optional<MinecraftPacket> out = translator29.toModern(legacy, state);
        assertEquals(0x16, out.get().id());
        assertEquals(0, new PacketIO.Reader(out.get().payload()).readByte(),
                "a 29 respawn request becomes client command 0 (respawn)");
        assertTrue(translator39.toModern(legacy, state).isEmpty(),
                "other generations keep the historical drop");
    }

    @Test
    void packetsMissingIn29AreDropped() {
        // Block entity data (0x84 does not carry NBT in 29).
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(10);
        w.writeShort(64);
        w.writeInt(-20);
        w.writeByte(1);
        w.writeShort(-1);
        assertTrue(translator29.toLegacy(modern(0x35, w), state).isEmpty(),
                "tile entity data is dropped for 29");
        // Named sound (no 0x3E in 29).
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeString("random.click");
        w2.writeInt(160);
        w2.writeInt(2048);
        w2.writeInt(-160);
        w2.writeFloat(1.0f);
        w2.writeByte(63);
        assertTrue(translator29.toLegacy(modern(0x29, w2), state).isEmpty());
        // Tab complete (no 0xCB in 29).
        PacketIO.Writer w3 = new PacketIO.Writer();
        w3.writeVarInt(1);
        w3.writeString("Steve");
        assertTrue(translator29.toLegacy(modern(0x3A, w3), state).isEmpty());
        // Block break animation (no 0x37 in 29).
        PacketIO.Writer w4 = new PacketIO.Writer();
        w4.writeVarInt(9);
        w4.writeInt(10);
        w4.writeInt(64);
        w4.writeInt(-20);
        w4.writeByte(3);
        assertTrue(translator29.toLegacy(modern(0x25, w4), state).isEmpty());
    }

    @Test
    void resourcePackOfferDroppedFor29() {
        // 1.2.5 has no texture-pack offer at all (the client jar
        // has no MC| strings, measured in Stage A).
        byte[] channel = "MC|RPack".getBytes(StandardCharsets.UTF_8);
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeShort(channel.length);
        w.writeBytes(channel);
        w.writeString("http://127.0.0.1/pack.zip");
        assertTrue(translator29.toLegacy(modern(0x3F, w), state).isEmpty(),
                "no texture-pack offer exists in 1.2.5");
    }

    @Test
    void windowItemsRemap29ItemsThroughTheChain() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(0);
        w.writeShort(2);
        w.writeShort(399); // nether star -> emerald (39) -> diamond (29)
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        w.writeShort(386); // book and quill -> book
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        Optional<LegacyPacket> out = translator29.toLegacy(modern(0x30, w), state);
        @SuppressWarnings("unchecked")
        List<Slot> slots = (List<Slot>) out.get().field(1);
        assertEquals(264, slots.get(0).itemId());
        assertEquals(340, slots.get(1).itemId());
    }

    @Test
    void blockChangeRemapsFor29() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(10);
        w.writeByte(64);
        w.writeInt(-20);
        w.writeVarInt(129); // emerald ore
        w.writeByte(0);
        LegacyPacket out = translator29.toLegacy(modern(0x23, w), state).get();
        assertEquals(0x35, out.id());
        assertEquals(56, out.intField(3), "emerald ore -> diamond ore for 29");
    }

    // ------------------------------------------------------------------
    // Protocols 28 (1.2.3), 23 (1.1) and 22 (1.0.0) specifics
    // (docs/PROXY-1.2.3.md, docs/PROXY-1.1.md, docs/PROXY-1.0.md)
    // ------------------------------------------------------------------

    private final LegacyPlayTranslator translator28 =
            new LegacyPlayTranslator(LegacyProtocol.V1_2_3);
    private final LegacyPlayTranslator translator23 =
            new LegacyPlayTranslator(LegacyProtocol.V1_1);
    private final LegacyPlayTranslator translator22 =
            new LegacyPlayTranslator(LegacyProtocol.V1_0);

    private static PacketIO.Writer joinGameWriter() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(42);
        w.writeByte(1);      // gamemode creative
        w.writeByte(-1);     // dimension nether
        w.writeByte(2);      // difficulty
        w.writeByte(20);     // max players
        w.writeString("default");
        return w;
    }

    @Test
    void joinGame28EqualsThe29Form() {
        // 28's login is byte-identical to 29's (measured): the
        // eight-field form.
        List<LegacyPacket> out = translator28.toLegacyAll(
                modern(0x01, joinGameWriter()), state);
        assertEquals(List.of(42, "", "default", 1, -1, 2, 0, 20),
                out.get(0).fields());
    }

    @Test
    void joinGame23BecomesTheSeedLogin() {
        List<LegacyPacket> out = translator23.toLegacyAll(
                modern(0x01, joinGameWriter()), state);
        assertEquals(1, out.size());
        // [entityId, "", seed(0 from the bridge), levelType,
        //  gamemode, dimension, difficulty, worldHeight(128),
        //  maxPlayers] — the transitional 1.1 form.
        assertEquals(List.of(42, "", 0L, "default", 1, -1, 2, 128, 20),
                out.get(0).fields());
    }

    @Test
    void joinGame22BecomesTheSeedLoginWithoutLevelType() {
        List<LegacyPacket> out = translator22.toLegacyAll(
                modern(0x01, joinGameWriter()), state);
        assertEquals(List.of(42, "", 0L, 1, -1, 2, 128, 20),
                out.get(0).fields());
    }

    @Test
    void respawn23And22CarryTheSeed() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(-1);      // dimension
        w.writeByte(1);      // difficulty
        w.writeByte(0);      // gamemode
        w.writeString("default");
        LegacyPacket out23 = translator23.toLegacy(modern(0x07, w), state).get();
        assertEquals(0x09, out23.id());
        assertEquals(List.of(-1, 1, 0, 128, 0L, "default"), out23.fields());

        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeInt(-1);
        w2.writeByte(1);
        w2.writeByte(0);
        w2.writeString("default");
        LegacyPacket out22 = translator22.toLegacy(modern(0x07, w2), state).get();
        assertEquals(List.of(-1, 1, 0, 128, 0L), out22.fields(),
                "the 22 respawn drops the level type");
    }

    @Test
    void spawnMob23HasTwoRotationBytesAndSubstitutes12Mobs() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(50);
        w.writeByte(98); // ocelot (1.2) -> pig for 23
        w.writeInt(160);
        w.writeInt(2048);
        w.writeInt(-160);
        w.writeByte(0);
        w.writeByte(0);
        w.writeByte(0);
        w.writeShort(100);
        w.writeShort(200);
        w.writeShort(300);
        w.writeByte(0x7F);
        LegacyPacket out = translator23.toLegacy(modern(0x0F, w), state).get();
        assertEquals(0x18, out.id());
        assertEquals(8, out.fields().size(),
                "23 mob spawn has two rotation bytes and no velocities");
        assertEquals(90, out.intField(1), "ocelot -> pig for 23");
        // The same spawn toward 29 keeps the ocelot (it exists in 1.2.5).
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeVarInt(50);
        w2.writeByte(98);
        w2.writeInt(160);
        w2.writeInt(2048);
        w2.writeInt(-160);
        w2.writeByte(0);
        w2.writeByte(0);
        w2.writeByte(0);
        w2.writeShort(100);
        w2.writeShort(200);
        w2.writeShort(300);
        w2.writeByte(0x7F);
        assertEquals(98, translator29.toLegacy(modern(0x0F, w2), state)
                .get().intField(1), "ocelot exists in 29 and passes through");
        // Iron golem (99) is substituted for 23 as well.
        PacketIO.Writer w3 = new PacketIO.Writer();
        w3.writeVarInt(51);
        w3.writeByte(99);
        w3.writeInt(160);
        w3.writeInt(2048);
        w3.writeInt(-160);
        w3.writeByte(0);
        w3.writeByte(0);
        w3.writeByte(0);
        w3.writeShort(0);
        w3.writeShort(0);
        w3.writeShort(0);
        w3.writeByte(0x7F);
        assertEquals(90, translator23.toLegacy(modern(0x0F, w3), state)
                .get().intField(1), "iron golem -> pig for 23");
    }

    private static PacketIO.Writer spawnObjectWriter(int type, int data) {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(50);
        w.writeByte(type);
        w.writeInt(160);
        w.writeInt(2048);
        w.writeInt(-160);
        w.writeByte(0);
        w.writeByte(0);
        w.writeInt(data);
        if (data != 0) {
            w.writeShort(0);
            w.writeShort(0);
            w.writeShort(0);
        }
        return w;
    }

    @Test
    void spawnObject23SplitsMinecartVariantsIntoTypes() {
        // The 1.1 / 1.0.0 trackers construct type 11 (chest) and
        // 12 (furnace) spawns with data 0 (measured in the jars).
        LegacyPacket chest = translator23.toLegacy(
                modern(0x0E, spawnObjectWriter(10, 1)), state).get();
        assertEquals(11, chest.intField(1), "chest minecart is type 11 for 23");
        assertEquals(0, chest.intField(7));
        LegacyPacket furnace = translator23.toLegacy(
                modern(0x0E, spawnObjectWriter(10, 2)), state).get();
        assertEquals(12, furnace.intField(1), "furnace minecart is type 12 for 23");
        assertEquals(0, furnace.intField(7));
        LegacyPacket tnt = translator23.toLegacy(
                modern(0x0E, spawnObjectWriter(10, 3)), state).get();
        assertEquals(10, tnt.intField(1), "TNT minecart falls back to plain for 23");
        assertEquals(0, tnt.intField(7));
        // The same split applies to 22.
        LegacyPacket chest22 = translator22.toLegacy(
                modern(0x0E, spawnObjectWriter(10, 1)), state).get();
        assertEquals(11, chest22.intField(1));
    }

    @Test
    void spawnObject23DropsExpBottleAndItemFrameButNotPotion() {
        assertTrue(translator23.toLegacy(
                        modern(0x0E, spawnObjectWriter(75, 0)), state).isEmpty(),
                "thrown exp bottles arrived in 1.2: dropped for 23");
        assertTrue(translator23.toLegacy(
                        modern(0x0E, spawnObjectWriter(71, 0)), state).isEmpty(),
                "item frames arrived in 1.4 (and type 71 means falling gravel in 1.1): dropped");
        // Thrown potions exist in 1.1 (the tracker constructs
        // type 73, measured): the spawn passes through.
        LegacyPacket potion = translator23.toLegacy(
                modern(0x0E, spawnObjectWriter(73, 8201)), state).get();
        assertEquals(73, potion.intField(1));
        assertEquals(8201, potion.intField(7));
    }

    @Test
    void abilitiesAreDroppedFor28And23And22() {
        for (LegacyPlayTranslator t : new LegacyPlayTranslator[]{
                translator28, translator23, translator22}) {
            PacketIO.Writer w = new PacketIO.Writer();
            w.writeByte(0b1101);
            w.writeFloat(0.05f);
            w.writeFloat(0.1f);
            assertTrue(t.toLegacy(modern(0x39, w), state).isEmpty(),
                    "no abilities packet exists before 1.2.4 (protocol 29)");
        }
    }

    @Test
    void headLookIsDroppedFor23And22() {
        for (LegacyPlayTranslator t : new LegacyPlayTranslator[]{
                translator23, translator22}) {
            PacketIO.Writer w = new PacketIO.Writer();
            w.writeInt(50);
            w.writeByte(30);
            assertTrue(t.toLegacy(modern(0x19, w), state).isEmpty(),
                    "entity_head_look arrived in 1.2");
        }
    }

    @Test
    void chunk23BecomesPreChunkThenCuboid() {
        byte[] raw = new byte[10496];
        raw[0] = (byte) 123; // redstone lamp -> glowstone (89) for 23
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(3);
        w.writeInt(-5);
        w.writeBoolean(true);
        w.writeShort(1);
        w.writeShort(0);
        byte[] data = LegacyChunkRemap.deflate(raw);
        w.writeInt(data.length);
        w.writeBytes(data);
        List<LegacyPacket> out = translator23.toLegacyAll(modern(0x21, w), state);
        assertEquals(2, out.size());
        assertEquals(0x32, out.get(0).id(), "the pre-chunk comes first");
        assertEquals(List.of(3, -5, 1), out.get(0).fields());
        LegacyPacket chunk = out.get(1);
        assertEquals(0x33, chunk.id());
        // Cuboid fields: [x*16, yBase, z*16, sizeX, sizeY, sizeZ, data].
        assertEquals(48, chunk.intField(0));
        assertEquals(0, chunk.intField(1));
        assertEquals(-80, chunk.intField(2));
        assertEquals(16, chunk.intField(3));
        assertEquals(16, chunk.intField(4), "one section -> a 16-high cuboid");
        assertEquals(16, chunk.intField(5));
        byte[] cuboid = LegacyChunkRemap.inflate((byte[]) chunk.field(6));
        // 4096 block ids + 3 x 2048 nibble arrays.
        assertEquals(4096 + 3 * 2048, cuboid.length);
        assertEquals(89, cuboid[0] & 0xFF, "the lamp became glowstone");
    }

    @Test
    void chunk23AboveY128ProducesOnlyThePreChunk() {
        // A chunk whose only section is section 9 (y 144..159)
        // has no cuboid home in a 128-high world.
        byte[] raw = new byte[10496];
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeInt(3);
        w.writeInt(-5);
        w.writeBoolean(true);
        w.writeShort(1 << 9);
        w.writeShort(0);
        byte[] data = LegacyChunkRemap.deflate(raw);
        w.writeInt(data.length);
        w.writeBytes(data);
        List<LegacyPacket> out = translator23.toLegacyAll(modern(0x21, w), state);
        assertEquals(1, out.size());
        assertEquals(0x32, out.get(0).id());
        // The unload form still works for 23/22.
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeInt(7);
        w2.writeInt(9);
        w2.writeBoolean(true);
        w2.writeShort(0);
        w2.writeShort(0);
        w2.writeInt(0);
        List<LegacyPacket> unload = translator22.toLegacyAll(modern(0x21, w2), state);
        assertEquals(List.of(7, 9, 0), unload.get(0).fields());
    }

    @Test
    void windowItemsRemap23And22ItemsThroughTheChain() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(0);
        w.writeShort(3);
        w.writeShort(384); // bottle o' enchanting -> potion for 23
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        w.writeShort(385); // fire charge -> flint and steel for 23
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        w.writeShort(383); // spawn egg: exists in 23, -> paper for 22
        w.writeByte(1);
        w.writeShort(0);
        w.writeShort(-1);
        @SuppressWarnings("unchecked")
        List<Slot> slots23 = (List<Slot>) translator23.toLegacy(
                modern(0x30, w), state).get().field(1);
        assertEquals(373, slots23.get(0).itemId());
        assertEquals(259, slots23.get(1).itemId());
        assertEquals(383, slots23.get(2).itemId(), "the spawn egg exists in 1.1");

        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeByte(0);
        w2.writeShort(3);
        w2.writeShort(384);
        w2.writeByte(1);
        w2.writeShort(0);
        w2.writeShort(-1);
        w2.writeShort(385);
        w2.writeByte(1);
        w2.writeShort(0);
        w2.writeShort(-1);
        w2.writeShort(383);
        w2.writeByte(1);
        w2.writeShort(0);
        w2.writeShort(-1);
        @SuppressWarnings("unchecked")
        List<Slot> slots22 = (List<Slot>) translator22.toLegacy(
                modern(0x30, w2), state).get().field(1);
        assertEquals(373, slots22.get(0).itemId());
        assertEquals(259, slots22.get(1).itemId());
        assertEquals(339, slots22.get(2).itemId(), "spawn egg -> paper for 22");
        // ... and the bottle o' enchanting passes 28 untouched.
        PacketIO.Writer w3 = new PacketIO.Writer();
        w3.writeByte(0);
        w3.writeShort(1);
        w3.writeShort(384);
        w3.writeByte(1);
        w3.writeShort(0);
        w3.writeShort(-1);
        @SuppressWarnings("unchecked")
        List<Slot> slots28 = (List<Slot>) translator28.toLegacy(
                modern(0x30, w3), state).get().field(1);
        assertEquals(384, slots28.get(0).itemId());
    }

    @Test
    void respawnRequest23BecomesClientCommand() {
        LegacyPacket legacy = new LegacyPacket(0x09, List.of(
                0, 1, 0, 128, 0L, "default"));
        Optional<MinecraftPacket> out = translator23.toModern(legacy, state);
        assertEquals(0x16, out.get().id());
        assertEquals(0, new PacketIO.Reader(out.get().payload()).readByte());
    }

    @Test
    void playerInput22IsDropped() {
        // 0x1B in protocol 22 is the raw movement input; it has
        // no modern counterpart and must not become a steer.
        LegacyPacket legacy = new LegacyPacket(0x1B, List.of(
                0.0f, 1.0f, 0.0f, 90.0f, true, false));
        assertTrue(translator22.toModern(legacy, state).isEmpty());
    }

    private static PacketIO.Writer pluginWriter(String channel, String payload) {
        // The modern plugin-message form: string channel, then a
        // short-length-prefixed payload.
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeString(channel);
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        w.writeShort(data.length);
        w.writeBytes(data);
        return w;
    }

    @Test
    void pluginMessageDroppedFor22ButPassesFor23() {
        assertTrue(translator22.toLegacy(
                        modern(0x3F, pluginWriter("MC|Brand", "detergent")), state)
                        .isEmpty(),
                "protocol 22 has no plugin-message packet");
        assertTrue(translator23.toLegacy(
                        modern(0x3F, pluginWriter("MC|Brand", "detergent")), state)
                        .isPresent(),
                "plugin messages exist in 23 (they arrived in 1.1)");
    }

    @Test
    void resourcePackOfferDroppedFor23() {
        assertTrue(translator23.toLegacy(
                        modern(0x3F, pluginWriter("MC|RPack", "http://127.0.0.1/pack.zip")),
                        state)
                        .isEmpty(),
                "no texture-pack offer exists in 1.1 either");
    }
}
