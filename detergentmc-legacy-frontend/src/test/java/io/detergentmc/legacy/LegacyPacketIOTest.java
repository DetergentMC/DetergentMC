package io.detergentmc.legacy;

import io.detergentmc.legacy.LegacyPacketIO.LegacyPacket;
import io.detergentmc.legacy.LegacyPacketIO.Modifier;
import io.detergentmc.legacy.LegacyPacketIO.Property;
import io.detergentmc.legacy.LegacyPacketTable.Field;
import io.detergentmc.legacy.LegacyPacketTable.PacketDef;
import io.detergentmc.legacy.LegacyWire.MetaEntry;
import io.detergentmc.legacy.LegacyWire.Slot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LegacyPacketIOTest {

    private static Object sampleValue(Field f) {
        return switch (f) {
            case U8 -> 200;
            case I8 -> -5;
            case BOOL -> true;
            case I16 -> -3000;
            case U16 -> 60000;
            case I32 -> 123456;
            case I64 -> 9876543210L;
            case F32 -> 3.25f;
            case F64 -> -7.5;
            case STRING -> "テストabc";
            case BYTES16 -> new byte[]{1, 2, 3};
            case SLOT -> new Slot(35, 2, 3, null);
            case METADATA -> List.of(new MetaEntry(0, 0, (byte) 1),
                    new MetaEntry(6, 3, 20.0f));
            case NBTBYTES -> new byte[]{4, 5};
        };
    }

    private static void assertFieldsEqual(List<Object> expected, List<Object> actual,
                                          String context) {
        assertEquals(expected.size(), actual.size(), context + ": field count");
        for (int i = 0; i < expected.size(); i++) {
            Object e = expected.get(i);
            Object a = actual.get(i);
            String where = context + " field " + i;
            if (e instanceof byte[] eb) {
                assertTrue(a instanceof byte[], where);
                assertTrue(Arrays.equals(eb, (byte[]) a), where);
            } else if (e instanceof int[] ei) {
                assertTrue(Arrays.equals(ei, (int[]) a), where);
            } else if (e instanceof List<?> el) {
                assertTrue(a instanceof List<?>, where);
                assertFieldsEqual(new ArrayList<>(el), new ArrayList<>((List<?>) a), where);
            } else {
                assertEquals(e, a, where);
            }
        }
    }

    @Test
    void everyFlatPacketRoundTrips() {
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(78);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name());
        }
    }

    @Test
    void everyFlatPacketRoundTrips74() {
        // Protocol 74 uses the same layouts (measured identical), but
        // the codec must honour the protocol argument end to end.
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(74);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(74, packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(74, wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " (74) consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name() + " (74)");
            // And the bytes are exactly the 78 encoding.
            assertTrue(Arrays.equals(wire, LegacyPacketIO.write(packet)),
                    def.name() + " (74) wire must equal the 78 wire");
        }
    }

    @Test
    void everyFlatPacketRoundTrips60() {
        // Protocol 60 uses the same layouts as 61 (measured
        // identical), but the codec must honour the protocol argument
        // end to end.
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(60);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(60, packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(60, wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " (60) consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name() + " (60)");
            // And the bytes are exactly the 61 encoding.
            assertTrue(Arrays.equals(wire, LegacyPacketIO.write(61, packet)),
                    def.name() + " (60) wire must equal the 61 wire");
        }
    }

    @Test
    void streamParser74HandlesArbitraryChunking() {
        List<LegacyPacket> expected = List.of(
                new LegacyPacket(0x00, List.of(777)),
                new LegacyPacket(0x03, List.of("1.6.2です")),
                new LegacyPacket(0xFF, List.of("bye")));
        byte[] stream = new byte[0];
        for (LegacyPacket p : expected) {
            byte[] wire = LegacyPacketIO.write(74, p);
            byte[] merged = Arrays.copyOf(stream, stream.length + wire.length);
            System.arraycopy(wire, 0, merged, stream.length, wire.length);
            stream = merged;
        }
        LegacyPacketIO.StreamParser parser = new LegacyPacketIO.StreamParser(74);
        assertEquals(74, parser.legacyProtocol());
        List<LegacyPacket> got = new ArrayList<>();
        for (int i = 0; i < stream.length; i += 2) {
            parser.feed(stream, i, Math.min(2, stream.length - i));
            LegacyPacket p;
            while ((p = parser.next()) != null) {
                got.add(p);
            }
        }
        assertEquals(expected.size(), got.size());
        for (int i = 0; i < expected.size(); i++) {
            assertFieldsEqual(expected.get(i).fields(), got.get(i).fields(), "74 packet " + i);
        }
        assertEquals(0, parser.bufferedBytes());
    }

    @Test
    void everyFlatPacketRoundTrips73() {
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(73);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(73, packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(73, wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " (73) consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name() + " (73)");
        }
    }

    @Test
    void entityProperties73DropsModifiersOnTheWire() {
        // A property WITH a modifier, written for protocol 73, must
        // come back with an empty modifier list (1.6.1 has no
        // modifiers on the wire), while protocol 78 keeps it.
        LegacyPacket packet = new LegacyPacket(0x2C, List.of(7,
                List.of(new Property("generic.maxHealth", 20.0,
                        List.of(new Modifier(1L, 2L, 0.5, 1))))));
        byte[] wire73 = LegacyPacketIO.write(73, packet);
        LegacyPacketIO.ParseResult parsed73 = LegacyPacketIO.parseOne(73, wire73, 0, wire73.length);
        assertEquals(wire73.length, parsed73.consumed());
        @SuppressWarnings("unchecked")
        List<Property> props73 = (List<Property>) parsed73.packet().field(1);
        assertEquals(1, props73.size());
        assertEquals("generic.maxHealth", props73.get(0).key());
        assertEquals(20.0, props73.get(0).base());
        assertTrue(props73.get(0).modifiers().isEmpty(),
                "1.6.1 wire must not carry attribute modifiers");

        byte[] wire78 = LegacyPacketIO.write(packet);
        assertTrue(wire78.length > wire73.length,
                "the 78 wire carries the modifier bytes the 73 wire lacks");
        LegacyPacketIO.ParseResult parsed78 = LegacyPacketIO.parseOne(wire78, 0, wire78.length);
        @SuppressWarnings("unchecked")
        List<Property> props78 = (List<Property>) parsed78.packet().field(1);
        assertEquals(1, props78.get(0).modifiers().size());
    }

    @Test
    void signEditorIsUnregisteredFor73() {
        LegacyPacket sign = new LegacyPacket(0x85, List.of(0, 1, 64, 2));
        assertThrows(IllegalArgumentException.class, () -> LegacyPacketIO.write(73, sign));
        assertEquals(14, LegacyPacketIO.write(78, sign).length);
    }

    @Test
    void everyFlatPacketRoundTrips61() {
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(61);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(61, packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(61, wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " (61) consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name() + " (61)");
        }
    }

    @Test
    void changedPackets61HaveMeasuredWireShapes() {
        // 0x08 health: I16 health (docs/PROXY-1.5.2.md Stage A).
        byte[] health = LegacyPacketIO.write(61,
                new LegacyPacket(0x08, List.of(20, 18, 0.5f)));
        assertEquals(1 + 2 + 2 + 4, health.length);
        assertEquals(20, LegacyPacketIO.parseOne(61, health, 0, health.length)
                .packet().intField(0));
        assertEquals(11, LegacyPacketIO.write(
                new LegacyPacket(0x08, List.of(20.0f, 18, 0.5f))).length,
                "the 78 health wire carries a float");
        // 0x13 entity action: no trailing I32.
        assertEquals(1 + 4 + 1, LegacyPacketIO.write(61,
                new LegacyPacket(0x13, List.of(5, 1))).length);
        // 0x27 attach: no leash flag byte.
        assertEquals(1 + 4 + 4, LegacyPacketIO.write(61,
                new LegacyPacket(0x27, List.of(7, 9))).length);
        // 0xC8 statistics: the amount is one byte.
        assertEquals(1 + 4 + 1, LegacyPacketIO.write(61,
                new LegacyPacket(0xC8, List.of(1000, 5))).length);
        // 0xCA abilities: flags + two speed bytes.
        byte[] abilities = LegacyPacketIO.write(61,
                new LegacyPacket(0xCA, List.of(3, 13, 26)));
        assertEquals(1 + 1 + 1 + 1, abilities.length);
        LegacyPacket parsed = LegacyPacketIO.parseOne(61, abilities, 0, abilities.length)
                .packet();
        assertEquals(13, parsed.intField(1));
        assertEquals(26, parsed.intField(2));
    }

    @Test
    void openWindow61DropsTheHorseEntityId() {
        LegacyPacket horse = new LegacyPacket(0x64,
                Arrays.asList(2, 11, "Horse", 18, true, 555));
        byte[] wire61 = LegacyPacketIO.write(61, horse);
        byte[] wire78 = LegacyPacketIO.write(horse);
        assertEquals(wire78.length - 4, wire61.length,
                "the 61 wire has no entity id tail");
        LegacyPacket parsed = LegacyPacketIO.parseOne(61, wire61, 0, wire61.length).packet();
        assertEquals(6, parsed.fields().size());
        assertEquals(null, parsed.field(5));
    }

    @Test
    void packetsAbsentIn61Throw() {
        assertThrows(IllegalArgumentException.class, () -> LegacyPacketIO.write(61,
                new LegacyPacket(0x85, List.of(0, 1, 64, 2))));
        assertThrows(IllegalArgumentException.class, () -> LegacyPacketIO.write(61,
                new LegacyPacket(0x1B, List.of(0.0f, 0.0f, true, false))));
        assertThrows(IllegalArgumentException.class, () -> LegacyPacketIO.write(61,
                new LegacyPacket(0x2C, List.of(7, List.of()))));
    }

    @Test
    void streamParser61HandlesArbitraryChunking() {
        List<LegacyPacket> expected = List.of(
                new LegacyPacket(0x00, List.of(777)),
                new LegacyPacket(0x08, List.of(20, 18, 0.5f)),
                new LegacyPacket(0xCA, List.of(3, 13, 26)),
                new LegacyPacket(0xFF, List.of("bye")));
        byte[] stream = new byte[0];
        for (LegacyPacket p : expected) {
            byte[] wire = LegacyPacketIO.write(61, p);
            byte[] merged = Arrays.copyOf(stream, stream.length + wire.length);
            System.arraycopy(wire, 0, merged, stream.length, wire.length);
            stream = merged;
        }
        LegacyPacketIO.StreamParser parser = new LegacyPacketIO.StreamParser(61);
        assertEquals(61, parser.legacyProtocol());
        List<LegacyPacket> got = new ArrayList<>();
        for (int i = 0; i < stream.length; i += 2) {
            parser.feed(stream, i, Math.min(2, stream.length - i));
            LegacyPacket p;
            while ((p = parser.next()) != null) {
                got.add(p);
            }
        }
        assertEquals(expected.size(), got.size());
        for (int i = 0; i < expected.size(); i++) {
            assertFieldsEqual(expected.get(i).fields(), got.get(i).fields(), "61 packet " + i);
        }
        assertEquals(0, parser.bufferedBytes());
    }

    @Test
    void everyFlatPacketRoundTrips51() {
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(51);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(51, packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(51, wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " (51) consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name() + " (51)");
        }
    }

    @Test
    void openWindow51HasNoUseTitleOnTheWire() {
        // Protocol 51 (docs/PROXY-1.4.7.md): [windowId, typeId,
        // title, slots] only. Reading supplies useTitle = true
        // (1.4.7 titles are always literal) and a null entity id.
        LegacyPacket packet = new LegacyPacket(0x64,
                Arrays.asList(2, 0, "Chest", 27, true, null));
        byte[] wire51 = LegacyPacketIO.write(51, packet);
        byte[] wire61 = LegacyPacketIO.write(61, packet);
        assertEquals(wire61.length - 1, wire51.length,
                "the 51 wire lacks the useTitle byte the 61 wire has");
        LegacyPacket parsed = LegacyPacketIO.parseOne(51, wire51, 0, wire51.length).packet();
        assertEquals(6, parsed.fields().size());
        assertEquals(Boolean.TRUE, parsed.field(4));
        assertEquals(null, parsed.field(5));
    }

    @Test
    void packetsAbsentIn51Throw() {
        // The scoreboard set and the named-particle packet arrived
        // in 1.5; writing them for 51 must fail loudly (the session
        // relies on this to never emit an unregistered ID).
        assertThrows(IllegalArgumentException.class, () -> LegacyPacketIO.write(51,
                new LegacyPacket(0x3F, List.of("note", 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1))));
        assertThrows(IllegalArgumentException.class, () -> LegacyPacketIO.write(51,
                new LegacyPacket(0xCE, List.of("obj", "title", 0))));
        assertThrows(IllegalArgumentException.class, () -> LegacyPacketIO.write(51,
                new LegacyPacket(0xCF, Arrays.asList("Steve", 0, "obj", 42))));
        assertThrows(IllegalArgumentException.class, () -> LegacyPacketIO.write(51,
                new LegacyPacket(0xD0, List.of(1, "obj"))));
        assertThrows(IllegalArgumentException.class, () -> LegacyPacketIO.write(51,
                new LegacyPacket(0xD1, Arrays.asList("team", 1, null, null, null, null, null))));
    }

    @Test
    void streamParser51HandlesArbitraryChunking() {
        List<LegacyPacket> expected = List.of(
                new LegacyPacket(0x00, List.of(777)),
                new LegacyPacket(0x08, List.of(20, 18, 0.5f)),
                new LegacyPacket(0xCA, List.of(3, 13, 26)),
                new LegacyPacket(0xFF, List.of("bye")));
        byte[] stream = new byte[0];
        for (LegacyPacket p : expected) {
            byte[] wire = LegacyPacketIO.write(51, p);
            byte[] merged = Arrays.copyOf(stream, stream.length + wire.length);
            System.arraycopy(wire, 0, merged, stream.length, wire.length);
            stream = merged;
        }
        LegacyPacketIO.StreamParser parser = new LegacyPacketIO.StreamParser(51);
        assertEquals(51, parser.legacyProtocol());
        List<LegacyPacket> got = new ArrayList<>();
        for (int i = 0; i < stream.length; i += 2) {
            parser.feed(stream, i, Math.min(2, stream.length - i));
            LegacyPacket p;
            while ((p = parser.next()) != null) {
                got.add(p);
            }
        }
        assertEquals(expected.size(), got.size());
        for (int i = 0; i < expected.size(); i++) {
            assertFieldsEqual(expected.get(i).fields(), got.get(i).fields(), "51 packet " + i);
        }
        assertEquals(0, parser.bufferedBytes());
    }

    @Test
    void everyFlatPacketRoundTrips49() {
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(49);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(49, packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(49, wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " (49) consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name() + " (49)");
        }
    }

    @Test
    void pickupSpawn49RoundTrips() {
        // 0x15 exists only in protocol 49 (docs/PROXY-1.4.5.md).
        LegacyPacket packet = new LegacyPacket(0x15, List.of(
                7, new Slot(276, 1, 0, null), 160, 2048, -160, 10, 20, 30));
        byte[] wire = LegacyPacketIO.write(49, packet);
        LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(49, wire, 0, wire.length);
        assertEquals(wire.length, parsed.consumed());
        assertFieldsEqual(packet.fields(), parsed.packet().fields(), "pickup_spawn (49)");
        assertThrows(IllegalArgumentException.class, () -> LegacyPacketIO.write(51, packet),
                "0x15 must not be writable for protocol 51");
    }

    @Test
    void spawnObject49HasNoRotationBytes() {
        LegacyPacket packet = new LegacyPacket(0x17,
                List.of(1, 50, 100, 200, 300, 10, 20, 999, 100, 200, 300));
        byte[] wire49 = LegacyPacketIO.write(49, packet);
        byte[] wire51 = LegacyPacketIO.write(51, packet);
        assertEquals(wire51.length - 2, wire49.length,
                "the 49 wire lacks the pitch/yaw bytes the 51 wire has");
        LegacyPacket parsed = LegacyPacketIO.parseOne(49, wire49, 0, wire49.length).packet();
        assertEquals(0, parsed.intField(5), "pitch reads back as 0 for 49");
        assertEquals(0, parsed.intField(6), "yaw reads back as 0 for 49");
        assertEquals(999, parsed.intField(7), "data survives");
    }

    @Test
    void chunkBulk49HasNoSkyFlagByte() {
        LegacyPacket packet = new LegacyPacket(0x38, List.of(true, new byte[]{9, 9},
                List.of(new int[]{0, 0, 1, 0}, new int[]{1, 0, 3, 0})));
        byte[] wire49 = LegacyPacketIO.write(49, packet);
        byte[] wire51 = LegacyPacketIO.write(51, packet);
        assertEquals(wire51.length - 1, wire49.length,
                "the 49 wire lacks the skyLight flag byte the 51 wire has");
        LegacyPacket parsed = LegacyPacketIO.parseOne(49, wire49, 0, wire49.length).packet();
        assertEquals(Boolean.FALSE, parsed.field(0),
                "the 49 skyLight field is a placeholder (the wire has no flag)");
    }

    @Test
    void streamParser49HandlesArbitraryChunking() {
        List<LegacyPacket> expected = List.of(
                new LegacyPacket(0x00, List.of(777)),
                new LegacyPacket(0x08, List.of(20, 18, 0.5f)),
                new LegacyPacket(0x15, List.of(
                        7, new Slot(276, 1, 0, null), 160, 2048, -160, 10, 20, 30)),
                new LegacyPacket(0xFF, List.of("bye")));
        byte[] stream = new byte[0];
        for (LegacyPacket p : expected) {
            byte[] wire = LegacyPacketIO.write(49, p);
            byte[] merged = Arrays.copyOf(stream, stream.length + wire.length);
            System.arraycopy(wire, 0, merged, stream.length, wire.length);
            stream = merged;
        }
        LegacyPacketIO.StreamParser parser = new LegacyPacketIO.StreamParser(49);
        assertEquals(49, parser.legacyProtocol());
        List<LegacyPacket> got = new ArrayList<>();
        for (int i = 0; i < stream.length; i += 2) {
            parser.feed(stream, i, Math.min(2, stream.length - i));
            LegacyPacket p;
            while ((p = parser.next()) != null) {
                got.add(p);
            }
        }
        assertEquals(expected.size(), got.size());
        for (int i = 0; i < expected.size(); i++) {
            assertFieldsEqual(expected.get(i).fields(), got.get(i).fields(), "49 packet " + i);
        }
        assertEquals(0, parser.bufferedBytes());
    }

    // ------------------------------------------------------------------
    // Legacy protocol 47 (1.4.2): wire-identical to 49 (measured,
    // docs/PROXY-1.4.2.md). The protocol argument is always the
    // LegacyProtocol.V1_4_2 constant — never a bare 47 — so these
    // tests cannot be mistaken for the Netty protocol 47 (1.8).
    // ------------------------------------------------------------------

    @Test
    void everyFlatPacketRoundTrips47Old() {
        int p = LegacyProtocol.V1_4_2;
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(p);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(p, packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " (legacy 47) consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name() + " (legacy 47)");
        }
    }

    @Test
    void pickupSpawn47OldRoundTrips() {
        int p = LegacyProtocol.V1_4_2;
        LegacyPacket packet = new LegacyPacket(0x15, List.of(
                7, new Slot(276, 1, 0, null), 160, 2048, -160, 10, 20, 30));
        byte[] wire = LegacyPacketIO.write(p, packet);
        // The wire bytes are identical to the 49 encoding (measured
        // wire-identity), and both protocols parse each other's.
        assertEquals(LegacyPacketIO.write(49, packet).length, wire.length);
        LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length);
        assertEquals(wire.length, parsed.consumed());
        assertFieldsEqual(packet.fields(), parsed.packet().fields(), "pickup_spawn (legacy 47)");
    }

    @Test
    void spawnObject47OldHasNoRotationBytes() {
        int p = LegacyProtocol.V1_4_2;
        LegacyPacket packet = new LegacyPacket(0x17,
                List.of(1, 50, 100, 200, 300, 10, 20, 999, 100, 200, 300));
        byte[] wire = LegacyPacketIO.write(p, packet);
        assertEquals(LegacyPacketIO.write(51, packet).length - 2, wire.length,
                "the legacy-47 wire lacks the pitch/yaw bytes the 51 wire has");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertEquals(0, parsed.intField(5), "pitch reads back as 0 for legacy 47");
        assertEquals(0, parsed.intField(6), "yaw reads back as 0 for legacy 47");
        assertEquals(999, parsed.intField(7), "data survives");
    }

    @Test
    void chunkBulk47OldHasNoSkyFlagByte() {
        int p = LegacyProtocol.V1_4_2;
        LegacyPacket packet = new LegacyPacket(0x38, List.of(true, new byte[]{9, 9},
                List.of(new int[]{0, 0, 1, 0}, new int[]{1, 0, 3, 0})));
        byte[] wire = LegacyPacketIO.write(p, packet);
        assertEquals(LegacyPacketIO.write(51, packet).length - 1, wire.length,
                "the legacy-47 wire lacks the skyLight flag byte the 51 wire has");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertEquals(Boolean.FALSE, parsed.field(0),
                "the legacy-47 skyLight field is a placeholder (the wire has no flag)");
    }

    @Test
    void streamParser47OldHandlesArbitraryChunking() {
        int p = LegacyProtocol.V1_4_2;
        List<LegacyPacket> expected = List.of(
                new LegacyPacket(0x00, List.of(777)),
                new LegacyPacket(0x08, List.of(20, 18, 0.5f)),
                new LegacyPacket(0x15, List.of(
                        7, new Slot(276, 1, 0, null), 160, 2048, -160, 10, 20, 30)),
                new LegacyPacket(0xFF, List.of("bye")));
        byte[] stream = new byte[0];
        for (LegacyPacket pkt : expected) {
            byte[] wire = LegacyPacketIO.write(p, pkt);
            byte[] merged = Arrays.copyOf(stream, stream.length + wire.length);
            System.arraycopy(wire, 0, merged, stream.length, wire.length);
            stream = merged;
        }
        LegacyPacketIO.StreamParser parser = new LegacyPacketIO.StreamParser(p);
        assertEquals(p, parser.legacyProtocol());
        List<LegacyPacket> got = new ArrayList<>();
        for (int i = 0; i < stream.length; i += 2) {
            parser.feed(stream, i, Math.min(2, stream.length - i));
            LegacyPacket pkt;
            while ((pkt = parser.next()) != null) {
                got.add(pkt);
            }
        }
        assertEquals(expected.size(), got.size());
        for (int i = 0; i < expected.size(); i++) {
            assertFieldsEqual(expected.get(i).fields(), got.get(i).fields(), "legacy 47 packet " + i);
        }
        assertEquals(0, parser.bufferedBytes());
    }

    // ------------------------------------------------------------------
    // Protocol 39 (1.3.2 / 1.3.1): five measured differences from
    // legacy protocol 47 (docs/PROXY-1.3.2.md).
    // ------------------------------------------------------------------

    @Test
    void everyFlatPacketRoundTrips39() {
        int p = LegacyProtocol.V1_3_2;
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(p);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(p, packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " (39) consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name() + " (39)");
        }
    }

    @Test
    void timeUpdate39CarriesOneLong() {
        int p = LegacyProtocol.V1_3_2;
        LegacyPacket packet = new LegacyPacket(0x04, List.of(123456789L));
        byte[] wire = LegacyPacketIO.write(p, packet);
        assertEquals(1 + 8, wire.length, "39 time_update is id + one i64");
        assertEquals(LegacyPacketIO.write(LegacyProtocol.V1_4_2,
                new LegacyPacket(0x04, List.of(1L, 2L))).length - 8, wire.length,
                "the legacy-47 wire carries a second i64 the 39 wire lacks");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertEquals(List.of(123456789L), parsed.fields());
    }

    @Test
    void pickupSpawn39IsExpandedAndDropsNbt() {
        int p = LegacyProtocol.V1_3_2;
        byte[] nbt = new byte[]{10, 0, 0, 0};
        LegacyPacket packet = new LegacyPacket(0x15, List.of(
                7, new Slot(276, 1, 0, nbt), 160, 2048, -160, 10, 20, 30));
        byte[] wire = LegacyPacketIO.write(p, packet);
        // id(1) + entity(4) + item(2) + count(1) + damage(2) +
        // xyz(12) + yaw/pitch/roll(3) = 25 bytes, no NBT tail.
        assertEquals(25, wire.length, "39 pickup_spawn inlines the stack, no NBT");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertEquals(7, parsed.intField(0));
        Slot stack = (Slot) parsed.field(1);
        assertEquals(276, stack.itemId());
        assertEquals(1, stack.count());
        assertEquals(0, stack.damage());
        assertNull(stack.nbtGzip(), "a parsed 39 pickup stack never has NBT");
        assertEquals(-160, parsed.intField(4));
        // The same packet on the legacy-47 wire carries the slot's
        // NBT length + bytes, so it is longer.
        assertTrue(LegacyPacketIO.write(LegacyProtocol.V1_4_2, packet).length > wire.length);
    }

    @Test
    void effect39HasNoGlobalFlag() {
        int p = LegacyProtocol.V1_3_2;
        LegacyPacket packet = new LegacyPacket(0x3D, List.of(1001, 10, 64, -20, 5));
        byte[] wire = LegacyPacketIO.write(p, packet);
        assertEquals(1 + 4 + 4 + 1 + 4 + 4, wire.length);
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertFieldsEqual(packet.fields(), parsed.fields(), "effect (39)");
    }

    @Test
    void clientSettings39HasNoCapeFlag() {
        int p = LegacyProtocol.V1_3_2;
        LegacyPacket packet = new LegacyPacket(0xCC, List.of("en_US", 0, 0, 0));
        byte[] wire = LegacyPacketIO.write(p, packet);
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertFieldsEqual(packet.fields(), parsed.fields(), "client_settings (39)");
    }

    @Test
    void watcherSlot39And47OldCarryNoNbt() {
        // Entity metadata with a slot entry: on the 49 wire the
        // slot is a full slot (NBT length + bytes follow the
        // damage); on the 39 and legacy-47 wires only id, count
        // and damage are written (measured in the 1.3.2 / 1.4.2
        // jars' DataWatcher readers).
        byte[] nbt = new byte[]{1, 2, 3, 4};
        List<MetaEntry> meta = List.of(
                new MetaEntry(0, 0, (byte) 3),
                new MetaEntry(10, 5, new Slot(276, 1, 0, nbt)));
        LegacyPacket packet = new LegacyPacket(0x28, List.of(42, meta));
        byte[] wire49 = LegacyPacketIO.write(49, packet);
        for (int p : new int[]{LegacyProtocol.V1_3_2, LegacyProtocol.V1_4_2}) {
            byte[] wire = LegacyPacketIO.write(p, packet);
            assertEquals(wire49.length - (2 + nbt.length), wire.length,
                    "protocol " + p + " watcher slot has no NBT length or bytes");
            LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
            @SuppressWarnings("unchecked")
            List<MetaEntry> got = (List<MetaEntry>) parsed.field(1);
            assertEquals(2, got.size());
            Slot stack = (Slot) got.get(1).value();
            assertEquals(276, stack.itemId());
            assertNull(stack.nbtGzip(), "protocol " + p + " watcher slot parses with null NBT");
        }
        // And the 49 parse of its own wire keeps the NBT.
        LegacyPacket parsed49 = LegacyPacketIO.parseOne(49, wire49, 0, wire49.length).packet();
        @SuppressWarnings("unchecked")
        List<MetaEntry> got49 = (List<MetaEntry>) parsed49.field(1);
        assertTrue(Arrays.equals(nbt, ((Slot) got49.get(1).value()).nbtGzip()));
    }

    @Test
    void chunkBulk39HasNoSkyFlagByte() {
        int p = LegacyProtocol.V1_3_2;
        LegacyPacket packet = new LegacyPacket(0x38, List.of(true, new byte[]{9, 9},
                List.of(new int[]{0, 0, 1, 0}, new int[]{1, 0, 3, 0})));
        byte[] wire = LegacyPacketIO.write(p, packet);
        assertEquals(LegacyPacketIO.write(51, packet).length - 1, wire.length,
                "the 39 wire lacks the skyLight flag byte the 51 wire has");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertEquals(Boolean.FALSE, parsed.field(0),
                "the 39 skyLight field is a placeholder (the wire has no flag)");
    }

    @Test
    void streamParser39HandlesArbitraryChunking() {
        int p = LegacyProtocol.V1_3_2;
        List<LegacyPacket> expected = List.of(
                new LegacyPacket(0x00, List.of(777)),
                new LegacyPacket(0x04, List.of(6000L)),
                new LegacyPacket(0x15, List.of(
                        7, new Slot(276, 1, 0, null), 160, 2048, -160, 10, 20, 30)),
                new LegacyPacket(0xFF, List.of("bye")));
        byte[] stream = new byte[0];
        for (LegacyPacket pkt : expected) {
            byte[] wire = LegacyPacketIO.write(p, pkt);
            byte[] merged = Arrays.copyOf(stream, stream.length + wire.length);
            System.arraycopy(wire, 0, merged, stream.length, wire.length);
            stream = merged;
        }
        LegacyPacketIO.StreamParser parser = new LegacyPacketIO.StreamParser(p);
        List<LegacyPacket> got = new ArrayList<>();
        for (int i = 0; i < stream.length; i += 2) {
            parser.feed(stream, i, Math.min(2, stream.length - i));
            LegacyPacket pkt;
            while ((pkt = parser.next()) != null) {
                got.add(pkt);
            }
        }
        assertEquals(expected.size(), got.size());
        for (int i = 0; i < expected.size(); i++) {
            assertFieldsEqual(expected.get(i).fields(), got.get(i).fields(), "39 packet " + i);
        }
        assertEquals(0, parser.bufferedBytes());
    }

    // ------------------------------------------------------------------
    // Protocol 29 (1.2.5)
    // ------------------------------------------------------------------

    @Test
    void everyFlatPacketRoundTrips29() {
        int p = LegacyProtocol.V1_2_5;
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(p);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(p, packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " (29) consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name() + " (29)");
        }
    }

    @Test
    void login29HasEightFields() {
        int p = LegacyProtocol.V1_2_5;
        LegacyPacket packet = new LegacyPacket(0x01, List.of(
                7, "", "flat", 0, 0, 1, 0, 20));
        byte[] wire = LegacyPacketIO.write(p, packet);
        // id(1) + entity(4) + ""(2) + "flat"(2+8) + gamemode(4) +
        // dimension(4) + difficulty(1) + height(1) + maxPlayers(1)
        assertEquals(28, wire.length, "29 login response is the eight-field form");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertFieldsEqual(packet.fields(), parsed.fields(), "login (29)");
    }

    @Test
    void handshake29IsASingleStringBothWays() {
        int p = LegacyProtocol.V1_2_5;
        LegacyPacket packet = new LegacyPacket(0x02, List.of("-"));
        byte[] wire = LegacyPacketIO.write(p, packet);
        assertEquals(1 + 2 + 2, wire.length, "29 handshake reply is id + one string");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertEquals(List.of("-"), parsed.fields());
    }

    @Test
    void equipment29IsInlineAndDropsCountAndNbt() {
        int p = LegacyProtocol.V1_2_5;
        byte[] nbt = new byte[]{10, 0, 0, 0};
        LegacyPacket packet = new LegacyPacket(0x05, List.of(
                7, 0, new Slot(276, 5, 3, nbt)));
        byte[] wire = LegacyPacketIO.write(p, packet);
        // id(1) + entity(4) + slot(2) + item(2) + damage(2)
        assertEquals(11, wire.length, "29 equipment inlines (item, damage) only");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        Slot stack = (Slot) parsed.field(2);
        assertEquals(276, stack.itemId());
        assertEquals(1, stack.count(), "a parsed 29 equipment slot has count 1");
        assertEquals(3, stack.damage());
        assertNull(stack.nbtGzip(), "a parsed 29 equipment slot never has NBT");
    }

    @Test
    void destroy29CarriesExactlyOneId() {
        int p = LegacyProtocol.V1_2_5;
        LegacyPacket packet = new LegacyPacket(0x1D, List.of(List.of(42)));
        byte[] wire = LegacyPacketIO.write(p, packet);
        assertEquals(1 + 4, wire.length, "29 destroy is id + one i32");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertEquals(List.of(42), parsed.field(0));
        assertThrows(IllegalArgumentException.class, () ->
                LegacyPacketIO.write(p, new LegacyPacket(0x1D, List.of(List.of(1, 2)))),
                "a multi-id destroy cannot be written on the 29 wire");
    }

    @Test
    void chunkData29HasTwoLengthInts() {
        int p = LegacyProtocol.V1_2_5;
        LegacyPacket packet = new LegacyPacket(0x33, List.of(
                3, -5, true, 1, 0, new byte[]{9, 8, 7}));
        byte[] wire = LegacyPacketIO.write(p, packet);
        // id(1) + x(4) + z(4) + groundUp(1) + bitmaps(2+2) +
        // compressed length(4) + unused length(4) + data(3)
        assertEquals(25, wire.length);
        assertEquals(3, wire[17], "the first length int is the compressed length");
        assertEquals(0, wire[21], "the second length int is the unused one (0)");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertFieldsEqual(packet.fields(), parsed.fields(), "chunk_data (29)");
    }

    @Test
    void explosion29DropsPlayerMotion() {
        int p = LegacyProtocol.V1_2_5;
        LegacyPacket packet = new LegacyPacket(0x3C, List.of(
                1.5, 64.0, -2.5, 3.0f, List.of(new int[]{1, 0, -1}), 1.0f, 2.0f, 3.0f));
        byte[] wire = LegacyPacketIO.write(p, packet);
        // id(1) + xyz(24) + radius(4) + count(4) + one record(3)
        assertEquals(36, wire.length, "29 explosion has no motion floats");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertEquals(0f, parsed.field(5), "a parsed 29 explosion has zero motion");
        assertEquals(0f, parsed.field(6));
        assertEquals(0f, parsed.field(7));
    }

    @Test
    void slot29CarriesNbtOnlyForDamageableItems() {
        // Measured in the 1.2.5 jar: the slot NBT tail exists only
        // for damageable items (maxDamage > 0). A diamond sword
        // (276) keeps its NBT on the 29 wire; an apple (260) does
        // not even get the length field.
        int p = LegacyProtocol.V1_2_5;
        byte[] nbt = new byte[]{10, 0, 0, 0};
        LegacyPacket packet = new LegacyPacket(0x68, List.of(0, List.of(
                new Slot(276, 1, 0, nbt), new Slot(260, 2, 0, nbt), Slot.EMPTY)));
        byte[] wire = LegacyPacketIO.write(p, packet);
        // id(1) + window(1) + count(2) + sword(2+1+2 + 2+4) +
        // apple(2+1+2) + empty(2)
        assertEquals(22, wire.length, "only the damageable slot carries its NBT");
        // The same packet on the 39 wire carries both NBT tails.
        assertEquals(22 + 2 + nbt.length,
                LegacyPacketIO.write(LegacyProtocol.V1_3_2, packet).length);
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        @SuppressWarnings("unchecked")
        List<Slot> slots = (List<Slot>) parsed.field(1);
        assertTrue(Arrays.equals(nbt, slots.get(0).nbtGzip()),
                "the damageable slot's NBT survives the 29 wire");
        assertNull(slots.get(1).nbtGzip(),
                "the non-damageable slot's NBT is not on the 29 wire");
        assertTrue(slots.get(2).isEmpty());
    }

    @Test
    void watcherSlot29CarriesNoNbt() {
        int p = LegacyProtocol.V1_2_5;
        byte[] nbt = new byte[]{1, 2, 3, 4};
        List<MetaEntry> meta = List.of(
                new MetaEntry(10, 5, new Slot(276, 1, 0, nbt)));
        LegacyPacket packet = new LegacyPacket(0x28, List.of(42, meta));
        byte[] wire = LegacyPacketIO.write(p, packet);
        // id(1) + entity(4) + header(1) + slot(2+1+2) + terminator(1)
        assertEquals(12, wire.length, "29 watcher slots are id/count/damage only");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        @SuppressWarnings("unchecked")
        List<MetaEntry> got = (List<MetaEntry>) parsed.field(1);
        assertNull(((Slot) got.get(0).value()).nbtGzip());
    }

    @Test
    void packetsAbsentIn29Throw() {
        int p = LegacyProtocol.V1_2_5;
        for (int id : new int[]{0x37, 0x38, 0x3E, 0xCB, 0xCC, 0xCD, 0xFC, 0xFD}) {
            assertThrows(IllegalArgumentException.class, () ->
                    LegacyPacketIO.write(p, new LegacyPacket(id, List.of())),
                    "packet 0x" + Integer.toHexString(id) + " is not registered in 29");
            assertThrows(IllegalArgumentException.class, () ->
                    LegacyPacketIO.parseOne(p, new byte[]{(byte) id}, 0, 1),
                    "packet 0x" + Integer.toHexString(id) + " is not registered in 29");
        }
    }

    @Test
    void streamParser29HandlesArbitraryChunking() {
        int p = LegacyProtocol.V1_2_5;
        List<LegacyPacket> expected = List.of(
                new LegacyPacket(0x00, List.of(777)),
                new LegacyPacket(0x32, List.of(3, -5, 1)),
                new LegacyPacket(0x02, List.of("-")),
                new LegacyPacket(0xFF, List.of("bye")));
        byte[] stream = new byte[0];
        for (LegacyPacket pkt : expected) {
            byte[] wire = LegacyPacketIO.write(p, pkt);
            byte[] merged = Arrays.copyOf(stream, stream.length + wire.length);
            System.arraycopy(wire, 0, merged, stream.length, wire.length);
            stream = merged;
        }
        LegacyPacketIO.StreamParser parser = new LegacyPacketIO.StreamParser(p);
        List<LegacyPacket> got = new ArrayList<>();
        for (int i = 0; i < stream.length; i += 2) {
            parser.feed(stream, i, Math.min(2, stream.length - i));
            LegacyPacket pkt;
            while ((pkt = parser.next()) != null) {
                got.add(pkt);
            }
        }
        assertEquals(expected.size(), got.size());
        for (int i = 0; i < expected.size(); i++) {
            assertFieldsEqual(expected.get(i).fields(), got.get(i).fields(), "29 packet " + i);
        }
        assertEquals(0, parser.bufferedBytes());
    }

    // ------------------------------------------------------------------
    // Protocols 28 (1.2.3), 23 (1.1) and 22 (1.0.0)
    // ------------------------------------------------------------------

    @Test
    void everyFlatPacketRoundTrips28() {
        int p = LegacyProtocol.V1_2_3;
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(p);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(p, packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " (28) consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name() + " (28)");
            // Every packet 28 shares with 29 encodes byte-identically.
            assertTrue(Arrays.equals(wire,
                            LegacyPacketIO.write(LegacyProtocol.V1_2_5, packet)),
                    def.name() + " (28) wire must equal the 29 wire");
        }
    }

    @Test
    void everyFlatPacketRoundTrips23() {
        int p = LegacyProtocol.V1_1;
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(p);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(p, packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " (23) consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name() + " (23)");
        }
    }

    @Test
    void everyFlatPacketRoundTrips22() {
        int p = LegacyProtocol.V1_0;
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(p);
        for (PacketDef def : table.values()) {
            if (def.isCustom()) {
                continue;
            }
            List<Object> fields = new ArrayList<>();
            for (Field f : def.fields()) {
                fields.add(sampleValue(f));
            }
            LegacyPacket packet = new LegacyPacket(def.id(), fields);
            byte[] wire = LegacyPacketIO.write(p, packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(), def.name() + " (22) consumed");
            assertFieldsEqual(fields, parsed.packet().fields(), def.name() + " (22)");
        }
    }

    @Test
    void login23HasSeedAndNineFields() {
        int p = LegacyProtocol.V1_1;
        LegacyPacket packet = new LegacyPacket(0x01, List.of(
                7, "", 9876543210L, "default", 0, 0, 1, -128, 20));
        byte[] wire = LegacyPacketIO.write(p, packet);
        // id(1) + entity(4) + ""(2) + seed(8) + "default"(2+14) +
        // gamemode(4) + dimension(1) + difficulty(1) + height(1) +
        // maxPlayers(1)
        assertEquals(39, wire.length, "23 login is the nine-field seed form");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertFieldsEqual(packet.fields(), parsed.fields(), "login (23)");
        assertEquals(9876543210L, parsed.field(2), "the seed survives the 23 wire");
    }

    @Test
    void login22HasNoLevelType() {
        int p = LegacyProtocol.V1_0;
        LegacyPacket packet = new LegacyPacket(0x01, List.of(
                7, "", 9876543210L, 0, 0, 1, -128, 20));
        byte[] wire = LegacyPacketIO.write(p, packet);
        // id(1) + entity(4) + ""(2) + seed(8) + gamemode(4) + 4 x i8
        assertEquals(23, wire.length, "22 login is the 23 form minus the level type");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertFieldsEqual(packet.fields(), parsed.fields(), "login (22)");
    }

    @Test
    void respawn23CarriesSeedAndLevelType() {
        int p = LegacyProtocol.V1_1;
        LegacyPacket packet = new LegacyPacket(0x09, List.of(
                -1, 1, 0, 128, 9876543210L, "default"));
        byte[] wire = LegacyPacketIO.write(p, packet);
        // id(1) + dimension(1) + difficulty(1) + gamemode(1) +
        // height(2) + seed(8) + "default"(2+14)
        assertEquals(30, wire.length);
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertFieldsEqual(packet.fields(), parsed.fields(), "respawn (23)");
        // The 22 form is the same minus the level-type string.
        int p22 = LegacyProtocol.V1_0;
        LegacyPacket packet22 = new LegacyPacket(0x09, List.of(
                -1, 1, 0, 128, 9876543210L));
        byte[] wire22 = LegacyPacketIO.write(p22, packet22);
        assertEquals(14, wire22.length);
        LegacyPacket parsed22 = LegacyPacketIO.parseOne(p22, wire22, 0, wire22.length).packet();
        assertFieldsEqual(packet22.fields(), parsed22.fields(), "respawn (22)");
    }

    @Test
    void chunkCuboidRoundTrips23And22() {
        for (int p : new int[]{LegacyProtocol.V1_1, LegacyProtocol.V1_0}) {
            LegacyPacket packet = new LegacyPacket(0x33, List.of(
                    16, 32, -16, 16, 128, 16, new byte[]{9, 8, 7, 6}));
            byte[] wire = LegacyPacketIO.write(p, packet);
            // id(1) + x(4) + y(2) + z(4) + sizes(1+1+1) + length(4) + data(4)
            assertEquals(22, wire.length);
            // The sizes are counts-minus-one on the wire.
            assertEquals(15, wire[11] & 0xFF, "sizeX is a count-minus-one");
            assertEquals(127, wire[12] & 0xFF, "sizeY is a count-minus-one");
            assertEquals(15, wire[13] & 0xFF, "sizeZ is a count-minus-one");
            LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
            assertFieldsEqual(packet.fields(), parsed.fields(), "chunk cuboid (" + p + ")");
        }
    }

    @Test
    void multiBlockChangeEarlyRoundTrips23And22() {
        for (int p : new int[]{LegacyProtocol.V1_1, LegacyProtocol.V1_0}) {
            List<int[]> records = List.of(
                    new int[]{(3 << 12) | (5 << 8) | 70, (89 << 4) | 0},
                    new int[]{(0 << 12) | (15 << 8) | 255, (1 << 4) | 15});
            LegacyPacket packet = new LegacyPacket(0x34, List.of(3, -2, records));
            byte[] wire = LegacyPacketIO.write(p, packet);
            // id(1) + chunkX(4) + chunkZ(4) + count(2) + coords(2x2)
            // + types(2x1) + metas(2x1)
            assertEquals(19, wire.length, "the early form is three parallel arrays");
            assertEquals(2, wire[10] & 0xFF, "the count is a u16");
            LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
            assertFieldsEqual(packet.fields(), parsed.fields(),
                    "multi_block_change_early (" + p + ")");
        }
    }

    @Test
    void playerInput22RoundTrips() {
        int p = LegacyProtocol.V1_0;
        LegacyPacket packet = new LegacyPacket(0x1B, List.of(
                0.5f, -1.0f, 30.0f, 90.0f, true, false));
        byte[] wire = LegacyPacketIO.write(p, packet);
        // id(1) + 4 x f32 + 2 x bool
        assertEquals(19, wire.length);
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        assertFieldsEqual(packet.fields(), parsed.fields(), "player_input (22)");
    }

    @Test
    void slotNbtIsConditionalFor28And23And22Too() {
        // The conditional-NBT slot form is shared by all four
        // plaintext generations with the same 49-item damageable
        // set (runtime-measured identical in every jar 1.0.0-1.2.5).
        byte[] nbt = new byte[]{10, 0, 0, 0};
        LegacyPacket packet = new LegacyPacket(0x68, List.of(0, List.of(
                new Slot(276, 1, 0, nbt), new Slot(260, 2, 0, nbt), Slot.EMPTY)));
        for (int p : new int[]{LegacyProtocol.V1_2_3, LegacyProtocol.V1_1,
                LegacyProtocol.V1_0}) {
            byte[] wire = LegacyPacketIO.write(p, packet);
            assertEquals(22, wire.length,
                    "only the damageable slot carries its NBT (" + p + ")");
            LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
            @SuppressWarnings("unchecked")
            List<Slot> slots = (List<Slot>) parsed.field(1);
            assertTrue(Arrays.equals(nbt, slots.get(0).nbtGzip()));
            assertNull(slots.get(1).nbtGzip());
        }
    }

    @Test
    void packetsAbsentIn28And23And22Throw() {
        assertThrows(IllegalArgumentException.class, () ->
                LegacyPacketIO.write(LegacyProtocol.V1_2_3,
                        new LegacyPacket(0xCA, List.of(true, true, true, true))),
                "0xCA is not registered in 28");
        for (int p : new int[]{LegacyProtocol.V1_1, LegacyProtocol.V1_0}) {
            for (int id : new int[]{0x23, 0x84, 0xCA}) {
                assertThrows(IllegalArgumentException.class, () ->
                        LegacyPacketIO.write(p, new LegacyPacket(id, List.of())),
                        "packet 0x" + Integer.toHexString(id)
                                + " is not registered in " + p);
            }
        }
        assertThrows(IllegalArgumentException.class, () ->
                LegacyPacketIO.write(LegacyProtocol.V1_0,
                        new LegacyPacket(0xFA, List.of("MC|TPack", new byte[0]))),
                "0xFA is not registered in 22");
        // ... while 0x1B IS registered in 22 (as player_input).
        assertNotNull(LegacyPacketTable.packet(LegacyProtocol.V1_0, 0x1B));
    }

    @Test
    void streamParser23HandlesArbitraryChunking() {
        int p = LegacyProtocol.V1_1;
        List<LegacyPacket> expected = List.of(
                new LegacyPacket(0x00, List.of(777)),
                new LegacyPacket(0x01, List.of(7, "Tester", 123L, "default",
                        0, 0, 1, -128, 20)),
                new LegacyPacket(0x33, List.of(0, 0, 0, 16, 16, 16,
                        new byte[]{1, 2, 3})),
                new LegacyPacket(0xFF, List.of("bye")));
        byte[] stream = new byte[0];
        for (LegacyPacket pkt : expected) {
            byte[] wire = LegacyPacketIO.write(p, pkt);
            byte[] merged = Arrays.copyOf(stream, stream.length + wire.length);
            System.arraycopy(wire, 0, merged, stream.length, wire.length);
            stream = merged;
        }
        LegacyPacketIO.StreamParser parser = new LegacyPacketIO.StreamParser(p);
        List<LegacyPacket> got = new ArrayList<>();
        for (int i = 0; i < stream.length; i += 2) {
            parser.feed(stream, i, Math.min(2, stream.length - i));
            LegacyPacket pkt;
            while ((pkt = parser.next()) != null) {
                got.add(pkt);
            }
        }
        assertEquals(expected.size(), got.size());
        for (int i = 0; i < expected.size(); i++) {
            assertFieldsEqual(expected.get(i).fields(), got.get(i).fields(), "23 packet " + i);
        }
        assertEquals(0, parser.bufferedBytes());
    }

    @Test
    void customPacketsRoundTrip() {
        List<LegacyPacket> packets = List.of(
                new LegacyPacket(0x17, List.of(1, 50, 100, 200, 300, 10, 20, 999, 100, 200, 300)),
                new LegacyPacket(0x17, List.of(1, 50, 100, 200, 300, 10, 20, 0, 0, 0, 0)),
                new LegacyPacket(0x1D, List.of(List.of(1, 2, 3))),
                new LegacyPacket(0x2C, List.of(7, List.of(new Property("generic.maxHealth",
                        20.0, List.of(new Modifier(1L, 2L, 0.5, 1)))))),
                new LegacyPacket(0x33, List.of(3, -2, true, 1, 0, new byte[]{1, 2, 3, 4})),
                new LegacyPacket(0x34, List.of(1, 2, List.of(new int[]{0x123, 0x456}))),
                new LegacyPacket(0x38, List.of(true, new byte[]{9, 9},
                        List.of(new int[]{0, 0, 1, 0}, new int[]{1, 0, 3, 0}))),
                new LegacyPacket(0x3C, List.of(1.0, 2.0, 3.0, 4.0f,
                        List.of(new int[]{1, 2, 3}), 0.1f, 0.2f, 0.3f)),
                new LegacyPacket(0x68, List.of(0, List.of(Slot.EMPTY, new Slot(5, 1, 0, null)))),
                new LegacyPacket(0xCF, Arrays.asList("Steve", 0, "obj", 42)),
                new LegacyPacket(0xCF, Arrays.asList("Steve", 1, null, null)),
                new LegacyPacket(0xD1, Arrays.asList("team", 0, "disp", "pre", "suf", 1,
                        List.of("a", "b"))),
                new LegacyPacket(0xD1, Arrays.asList("team", 1, null, null, null, null, null)),
                new LegacyPacket(0x83, List.of(358, 3, new byte[]{1, 2})),
                new LegacyPacket(0x64, Arrays.asList(1, 0, "Chest", 27, true, null)),
                new LegacyPacket(0x64, Arrays.asList(2, 11, "Horse", 18, true, 555)));
        for (LegacyPacket packet : packets) {
            byte[] wire = LegacyPacketIO.write(packet);
            LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(wire, 0, wire.length);
            assertEquals(wire.length, parsed.consumed(),
                    "0x" + Integer.toHexString(packet.id()) + " consumed");
            assertFieldsEqual(packet.fields(), parsed.packet().fields(),
                    "0x" + Integer.toHexString(packet.id()));
        }
    }

    @Test
    void streamParserHandlesArbitraryChunking() {
        List<LegacyPacket> expected = List.of(
                new LegacyPacket(0x00, List.of(424242)),
                new LegacyPacket(0x03, List.of("こんにちは")),
                new LegacyPacket(0x04, List.of(12000L, 6000L)),
                new LegacyPacket(0xFF, List.of("bye")));
        byte[] stream = new byte[0];
        for (LegacyPacket p : expected) {
            byte[] wire = LegacyPacketIO.write(p);
            byte[] merged = Arrays.copyOf(stream, stream.length + wire.length);
            System.arraycopy(wire, 0, merged, stream.length, wire.length);
            stream = merged;
        }
        for (int chunkSize : new int[]{1, 3, 7, stream.length}) {
            LegacyPacketIO.StreamParser parser = new LegacyPacketIO.StreamParser();
            List<LegacyPacket> got = new ArrayList<>();
            for (int i = 0; i < stream.length; i += chunkSize) {
                parser.feed(stream, i, Math.min(chunkSize, stream.length - i));
                LegacyPacket p;
                while ((p = parser.next()) != null) {
                    got.add(p);
                }
            }
            assertEquals(expected.size(), got.size(), "chunkSize " + chunkSize);
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(LegacyPacketIO.write(expected.get(i)).length > 0, true);
                assertFieldsEqual(expected.get(i).fields(), got.get(i).fields(),
                        "packet " + i + " chunkSize " + chunkSize);
            }
            assertEquals(0, parser.bufferedBytes());
        }
    }

    @Test
    void unregisteredIdIsAnErrorNotUnderflow() {
        byte[] bad = new byte[]{0x15, 0, 0};
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LegacyPacketIO.parseOne(bad, 0, bad.length));
        assertTrue(!(e instanceof LegacyWire.Underflow));
    }

    @Test
    void truncatedPacketIsUnderflow() {
        byte[] login = LegacyPacketIO.write(new LegacyPacket(0x01,
                List.of(123, "default", 0, 0, 1, 0, 20)));
        byte[] truncated = Arrays.copyOf(login, login.length - 2);
        assertThrows(LegacyWire.Underflow.class,
                () -> LegacyPacketIO.parseOne(truncated, 0, truncated.length));
    }

    @Test
    void watcherSlot47OldCarriesNoNbt() {
        // Entity metadata with a slot entry: on the 49 wire the
        // slot is a full slot (NBT length + bytes follow the
        // damage); on the legacy-47 wire only id, count and
        // damage are written (measured in the 1.4.2 jar's
        // DataWatcher reader during the protocol-39 work; the
        // full-slot watcher form arrived in 1.4.4). Ported into
        // this line at legacy-47 Stage E.
        byte[] nbt = new byte[]{1, 2, 3, 4};
        List<MetaEntry> meta = List.of(
                new MetaEntry(0, 0, (byte) 3),
                new MetaEntry(10, 5, new Slot(276, 1, 0, nbt)));
        LegacyPacket packet = new LegacyPacket(0x28, List.of(42, meta));
        byte[] wire49 = LegacyPacketIO.write(49, packet);
        int p = LegacyProtocol.V1_4_2;
        byte[] wire = LegacyPacketIO.write(p, packet);
        assertEquals(wire49.length - (2 + nbt.length), wire.length,
                "legacy-47 watcher slot has no NBT length or bytes");
        LegacyPacket parsed = LegacyPacketIO.parseOne(p, wire, 0, wire.length).packet();
        @SuppressWarnings("unchecked")
        List<MetaEntry> got = (List<MetaEntry>) parsed.field(1);
        assertEquals(2, got.size());
        Slot stack = (Slot) got.get(1).value();
        assertEquals(276, stack.itemId());
        assertNull(stack.nbtGzip(), "legacy-47 watcher slot parses with null NBT");
        // And the 49 parse of its own wire keeps the NBT.
        LegacyPacket parsed49 = LegacyPacketIO.parseOne(49, wire49, 0, wire49.length).packet();
        @SuppressWarnings("unchecked")
        List<MetaEntry> got49 = (List<MetaEntry>) parsed49.field(1);
        assertTrue(Arrays.equals(nbt, ((Slot) got49.get(1).value()).nbtGzip()));
    }

    @Test
    void loginPacketShapeMatchesMeasuredForm() {
        // The 1.6.4 login response measured on the wire: entity i32,
        // level type string, then gamemode/dimension/difficulty/
        // height/maxPlayers bytes.
        byte[] wire = LegacyPacketIO.write(new LegacyPacket(0x01,
                List.of(42, "default", 0, 0, 2, 0, 20)));
        assertEquals(42, wire[4] & 0xFF); // last byte of entity id
        LegacyPacketIO.ParseResult parsed = LegacyPacketIO.parseOne(wire, 0, wire.length);
        assertEquals(42, parsed.packet().intField(0));
        assertEquals("default", parsed.packet().field(1));
        if (parsed.packet().fields().size() != 7) {
            fail("login must have 7 fields");
        }
    }
}
