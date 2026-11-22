package io.detergentmc.legacy;

import io.detergentmc.legacy.LegacyPacketTable.PacketDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the measured protocol-78 table and the supported-list. The
 * supported list pinning mirrors the "oldest protocol silently drops
 * out" guard used for the Netty generations: the legacy list is a
 * separate list (separate port, separate numbering space) and must
 * contain exactly the legacy protocols implemented so far.
 */
class LegacyPacketTableTest {

    @Test
    void supportedLegacyProtocolsIsExactly78And74And73And61And60And51And49And47OldAnd39And29And28And23And22() {
        assertEquals(List.of(78, 74, 73, 61, 60, 51, 49, 47, 39, 29, 28, 23, 22),
                LegacyProtocol.supportedLegacyProtocols());
        assertEquals(78, LegacyProtocol.supportedLegacyProtocols().get(0));
        assertTrue(LegacyProtocol.isSupported(78));
        assertTrue(LegacyProtocol.isSupported(74));
        assertTrue(LegacyProtocol.isSupported(73));
        assertTrue(LegacyProtocol.isSupported(61));
        assertTrue(LegacyProtocol.isSupported(60));
        assertTrue(LegacyProtocol.isSupported(51));
        assertTrue(LegacyProtocol.isSupported(49));
        // Legacy protocol 47 = Minecraft 1.4.2. This is the LEGACY
        // numbering space only; the Netty protocol 47 (1.8) is a
        // different list and is not what isSupported() speaks of.
        assertTrue(LegacyProtocol.isSupported(LegacyProtocol.V1_4_2));
        // Legacy protocol 39 = Minecraft 1.3.2 and 1.3.1 (the
        // widespread "1.3.1 = 38" table entry is wrong; measured).
        assertTrue(LegacyProtocol.isSupported(LegacyProtocol.V1_3_2));
        assertFalse(LegacyProtocol.isSupported(38));
        // Legacy protocol 29 = Minecraft 1.2.5 and 1.2.4 (the
        // widespread "1.2.4 = 28" table entry is wrong; measured).
        assertTrue(LegacyProtocol.isSupported(LegacyProtocol.V1_2_5));
        // Legacy protocol 28 = Minecraft 1.2.3, 1.2.2 and 1.2.1.
        assertTrue(LegacyProtocol.isSupported(LegacyProtocol.V1_2_3));
        // Legacy protocol 23 = Minecraft 1.1.
        assertTrue(LegacyProtocol.isSupported(LegacyProtocol.V1_1));
        // Legacy protocol 22 = Minecraft 1.0.0, the oldest
        // generation (its server jar came via the Omniarchive
        // route; piston-meta distributes nothing below 1.2.5).
        assertTrue(LegacyProtocol.isSupported(LegacyProtocol.V1_0));
        assertFalse(LegacyProtocol.isSupported(21));
    }

    @Test
    void versionNameMappingRoundTrips() {
        assertEquals("1.6.4", LegacyProtocol.versionName(78));
        assertEquals("1.6.2", LegacyProtocol.versionName(74));
        assertEquals("1.6.1", LegacyProtocol.versionName(73));
        assertEquals("1.5.2", LegacyProtocol.versionName(61));
        assertEquals("1.5.1", LegacyProtocol.versionName(60));
        assertEquals("1.4.7", LegacyProtocol.versionName(51));
        assertEquals("1.4.5", LegacyProtocol.versionName(49));
        assertEquals("1.4.2", LegacyProtocol.versionName(LegacyProtocol.V1_4_2));
        assertEquals(78, LegacyProtocol.protocolForVersionName("1.6.4"));
        assertEquals(74, LegacyProtocol.protocolForVersionName("1.6.2"));
        assertEquals(73, LegacyProtocol.protocolForVersionName("1.6.1"));
        assertEquals(61, LegacyProtocol.protocolForVersionName("1.5.2"));
        assertEquals(60, LegacyProtocol.protocolForVersionName("1.5.1"));
        assertEquals(60, LegacyProtocol.protocolForVersionName("1.5"));
        assertEquals(51, LegacyProtocol.protocolForVersionName("1.4.7"));
        assertEquals(51, LegacyProtocol.protocolForVersionName("1.4.6"));
        assertEquals(49, LegacyProtocol.protocolForVersionName("1.4.5"));
        assertEquals(49, LegacyProtocol.protocolForVersionName("1.4.4"));
        assertEquals(LegacyProtocol.V1_4_2, LegacyProtocol.protocolForVersionName("1.4.2"));
        assertEquals("1.3.2", LegacyProtocol.versionName(LegacyProtocol.V1_3_2));
        assertEquals(LegacyProtocol.V1_3_2, LegacyProtocol.protocolForVersionName("1.3.2"));
        assertEquals(LegacyProtocol.V1_3_2, LegacyProtocol.protocolForVersionName("1.3.1"));
        assertEquals("1.2.5", LegacyProtocol.versionName(LegacyProtocol.V1_2_5));
        assertEquals(LegacyProtocol.V1_2_5, LegacyProtocol.protocolForVersionName("1.2.5"));
        // 1.2.4 speaks protocol 29 (wire-identical to 1.2.5, measured).
        assertEquals(LegacyProtocol.V1_2_5, LegacyProtocol.protocolForVersionName("1.2.4"));
        assertEquals("1.2.3", LegacyProtocol.versionName(LegacyProtocol.V1_2_3));
        assertEquals(LegacyProtocol.V1_2_3, LegacyProtocol.protocolForVersionName("1.2.3"));
        assertEquals(LegacyProtocol.V1_2_3, LegacyProtocol.protocolForVersionName("1.2.2"));
        assertEquals(LegacyProtocol.V1_2_3, LegacyProtocol.protocolForVersionName("1.2.1"));
        assertEquals("1.1", LegacyProtocol.versionName(LegacyProtocol.V1_1));
        assertEquals(LegacyProtocol.V1_1, LegacyProtocol.protocolForVersionName("1.1"));
        assertEquals("1.0.0", LegacyProtocol.versionName(LegacyProtocol.V1_0));
        assertEquals(LegacyProtocol.V1_0, LegacyProtocol.protocolForVersionName("1.0.0"));
        assertEquals(-1, LegacyProtocol.protocolForVersionName("1.20.4"));
        assertEquals(-1, LegacyProtocol.protocolForVersionName(null));
    }

    @Test
    void table61DiffersFrom78AsMeasured() {
        // Measured (docs/PROXY-1.5.2.md): 79 registrations; 0x85,
        // 0x1B and 0x2C absent (1.6.2 / 1.6 arrivals); six packets
        // change shape; everything else entry-for-entry identical
        // to 78.
        Map<Integer, PacketDef> table61 = LegacyPacketTable.tableFor(61);
        Map<Integer, PacketDef> table78 = LegacyPacketTable.tableFor(78);
        assertEquals(79, table61.size());
        assertNull(LegacyPacketTable.packet(61, 0x85));
        assertNull(LegacyPacketTable.packet(61, 0x1B));
        assertNull(LegacyPacketTable.packet(61, 0x2C));
        assertEquals(List.of(LegacyPacketTable.Field.I16, LegacyPacketTable.Field.I16,
                        LegacyPacketTable.Field.F32),
                LegacyPacketTable.packet(61, 0x08).fields(), "61 health is I16");
        assertEquals(List.of(LegacyPacketTable.Field.I32, LegacyPacketTable.Field.I8),
                LegacyPacketTable.packet(61, 0x13).fields(), "61 entity action has no aux");
        assertEquals(List.of(LegacyPacketTable.Field.I32, LegacyPacketTable.Field.I32),
                LegacyPacketTable.packet(61, 0x27).fields(), "61 attach has no leash flag");
        assertEquals("open_window_nohorse",
                LegacyPacketTable.packet(61, 0x64).custom());
        assertEquals(List.of(LegacyPacketTable.Field.I32, LegacyPacketTable.Field.I8),
                LegacyPacketTable.packet(61, 0xC8).fields(), "61 statistic amount is I8");
        assertEquals(List.of(LegacyPacketTable.Field.U8, LegacyPacketTable.Field.I8,
                        LegacyPacketTable.Field.I8),
                LegacyPacketTable.packet(61, 0xCA).fields(), "61 ability speeds are bytes");
        for (Integer id : table61.keySet()) {
            if (id == 0x08 || id == 0x13 || id == 0x27 || id == 0x64
                    || id == 0xC8 || id == 0xCA) {
                continue;
            }
            assertEquals(table78.get(id), table61.get(id),
                    "packet 0x" + Integer.toHexString(id));
        }
    }

    @Test
    void table60DelegatesFullyTo61() {
        // Measured: 1.5.1's and 1.5's registrations and every packet
        // layout are identical to 1.5.2's (docs/PROXY-1.5.1.md), so
        // the 60 table is the 61 table, entry for entry.
        Map<Integer, PacketDef> table60 = LegacyPacketTable.tableFor(60);
        Map<Integer, PacketDef> table61 = LegacyPacketTable.tableFor(61);
        assertEquals(79, table60.size());
        assertEquals(table61.keySet(), table60.keySet());
        for (Integer id : table61.keySet()) {
            assertEquals(table61.get(id), table60.get(id),
                    "packet 0x" + Integer.toHexString(id));
        }
        assertTrue(LegacyProtocol.is1_5Generation(61));
        assertTrue(LegacyProtocol.is1_5Generation(60));
        assertFalse(LegacyProtocol.is1_5Generation(73));
        assertFalse(LegacyProtocol.is1_5Generation(78));
    }

    @Test
    void table51DiffersFrom61AsMeasured() {
        // Measured (docs/PROXY-1.4.7.md): 74 registrations; the five
        // 1.5 additions (0x3F particle and the scoreboard set) are
        // absent, 0x64 is on the no-useTitle codec, and everything
        // else is entry-for-entry identical to 61 — including the
        // 1.5-generation wire traits (I16 health, no leash flag,
        // I8 statistic amount, byte ability speeds).
        Map<Integer, PacketDef> table51 = LegacyPacketTable.tableFor(51);
        Map<Integer, PacketDef> table61 = LegacyPacketTable.tableFor(61);
        assertEquals(74, table51.size());
        assertNull(LegacyPacketTable.packet(51, 0x3F));
        assertNull(LegacyPacketTable.packet(51, 0xCE));
        assertNull(LegacyPacketTable.packet(51, 0xCF));
        assertNull(LegacyPacketTable.packet(51, 0xD0));
        assertNull(LegacyPacketTable.packet(51, 0xD1));
        assertNotNull(LegacyPacketTable.packet(61, 0x3F));
        assertNotNull(LegacyPacketTable.packet(61, 0xCE));
        assertEquals("open_window_notitle",
                LegacyPacketTable.packet(51, 0x64).custom());
        assertEquals(List.of(LegacyPacketTable.Field.I16, LegacyPacketTable.Field.I16,
                        LegacyPacketTable.Field.F32),
                LegacyPacketTable.packet(51, 0x08).fields(), "51 health is I16");
        assertEquals(List.of(LegacyPacketTable.Field.I32, LegacyPacketTable.Field.I32),
                LegacyPacketTable.packet(51, 0x27).fields(), "51 attach has no leash flag");
        assertEquals(List.of(LegacyPacketTable.Field.U8, LegacyPacketTable.Field.I8,
                        LegacyPacketTable.Field.I8),
                LegacyPacketTable.packet(51, 0xCA).fields(), "51 ability speeds are bytes");
        for (Integer id : table51.keySet()) {
            if (id == 0x64) {
                continue;
            }
            assertEquals(table61.get(id), table51.get(id),
                    "packet 0x" + Integer.toHexString(id));
        }
        assertTrue(LegacyProtocol.is1_4Generation(51));
        assertFalse(LegacyProtocol.is1_4Generation(61));
        assertTrue(LegacyProtocol.is1_5OrOlderGeneration(51));
        assertTrue(LegacyProtocol.is1_5OrOlderGeneration(61));
        assertFalse(LegacyProtocol.is1_5OrOlderGeneration(73));
    }

    @Test
    void table49DiffersFrom51AsMeasured() {
        // Measured (docs/PROXY-1.4.5.md): 75 registrations; 0x15
        // pickup_spawn still exists (51 merged it into spawn_object
        // type 2), 0x17 is on the no-rotation codec, 0x38 is on the
        // no-sky-flag codec, 0x10 is serverbound only, and
        // everything else is entry-for-entry identical to 51.
        Map<Integer, PacketDef> table49 = LegacyPacketTable.tableFor(49);
        Map<Integer, PacketDef> table51 = LegacyPacketTable.tableFor(51);
        assertEquals(75, table49.size());
        assertNotNull(LegacyPacketTable.packet(49, 0x15));
        assertEquals("pickup_spawn", LegacyPacketTable.packet(49, 0x15).custom());
        assertTrue(LegacyPacketTable.packet(49, 0x15).toClient());
        assertFalse(LegacyPacketTable.packet(49, 0x15).toServer());
        assertNull(LegacyPacketTable.packet(51, 0x15));
        assertEquals("spawn_object_norot", LegacyPacketTable.packet(49, 0x17).custom());
        assertEquals("spawn_object", LegacyPacketTable.packet(51, 0x17).custom());
        assertEquals("chunk_bulk_nosky", LegacyPacketTable.packet(49, 0x38).custom());
        assertEquals("chunk_bulk", LegacyPacketTable.packet(51, 0x38).custom());
        assertFalse(LegacyPacketTable.packet(49, 0x10).toClient(),
                "49 held_item_change is serverbound only");
        assertTrue(LegacyPacketTable.packet(49, 0x10).toServer());
        assertTrue(LegacyPacketTable.packet(51, 0x10).toClient());
        for (Integer id : table49.keySet()) {
            if (id == 0x15 || id == 0x17 || id == 0x38 || id == 0x10) {
                continue;
            }
            assertEquals(table51.get(id), table49.get(id),
                    "packet 0x" + Integer.toHexString(id));
        }
        assertTrue(LegacyProtocol.is1_4Generation(49));
        assertTrue(LegacyProtocol.is1_5OrOlderGeneration(49));
    }

    @Test
    void table47OldIsWireIdenticalTo49() {
        // Measured (docs/PROXY-1.4.2.md): legacy protocol 47
        // (Minecraft 1.4.2 — NOT the Netty protocol 47 of 1.8) has
        // the same 75 registrations as protocol 49, with identical
        // direction flags and identical read/write layouts for
        // every packet, so the table delegates to 49 outright. The
        // only measured difference is registry-level (item 2267,
        // the "wait" record, arrived in 1.4.4) and lives in
        // LegacyChunkRemap, not here.
        int p47 = LegacyProtocol.V1_4_2;
        Map<Integer, PacketDef> table47 = LegacyPacketTable.tableFor(p47);
        Map<Integer, PacketDef> table49 = LegacyPacketTable.tableFor(49);
        assertEquals(75, table47.size());
        assertEquals(table49.keySet(), table47.keySet());
        for (Integer id : table49.keySet()) {
            assertEquals(table49.get(id), table47.get(id),
                    "packet 0x" + Integer.toHexString(id));
        }
        // The 49 wire traits are all present in legacy 47 too.
        assertEquals("pickup_spawn", LegacyPacketTable.packet(p47, 0x15).custom());
        assertEquals("spawn_object_norot", LegacyPacketTable.packet(p47, 0x17).custom());
        assertEquals("chunk_bulk_nosky", LegacyPacketTable.packet(p47, 0x38).custom());
        assertFalse(LegacyPacketTable.packet(p47, 0x10).toClient(),
                "legacy 47 held_item_change is serverbound only, like 49");
        assertTrue(LegacyProtocol.is1_4Generation(p47));
        assertTrue(LegacyProtocol.is1_5OrOlderGeneration(p47));
        assertTrue(LegacyProtocol.hasNoFireworkRocket(p47));
        assertTrue(LegacyProtocol.hasNoFireworkRocket(49));
        assertFalse(LegacyProtocol.hasNoFireworkRocket(51));
        // The DataWatcher slot form is NOT part of the packet
        // table: legacy 47's watcher slots carry no NBT (the
        // full-slot watcher form arrived in 1.4.4), measured in
        // the 1.4.2 jar during the protocol-39 work.
        assertTrue(LegacyProtocol.watcherSlotHasNoNbt(p47));
        assertFalse(LegacyProtocol.watcherSlotHasNoNbt(49));
        assertFalse(LegacyProtocol.watcherSlotHasNoNbt(51));
        assertFalse(LegacyProtocol.watcherSlotHasNoNbt(78));
    }

    @Test
    void table39DiffersFrom47OldInExactlyFivePackets() {
        // Measured (docs/PROXY-1.3.2.md): protocol 39 (Minecraft
        // 1.3.2 / 1.3.1) has the same 75 registrations as legacy
        // protocol 47 (1.4.2) with identical direction flags, and
        // identical layouts except five packets: 0x04 time_update
        // (one i64, not two), 0x15 pickup_spawn (expanded codec),
        // 0x3D effect (no trailing bool), 0xCC client_settings
        // (no trailing bool) and 0xFE server_list_ping (no fields).
        int p39 = LegacyProtocol.V1_3_2;
        int p47 = LegacyProtocol.V1_4_2;
        Map<Integer, PacketDef> table39 = LegacyPacketTable.tableFor(p39);
        Map<Integer, PacketDef> table47 = LegacyPacketTable.tableFor(p47);
        assertEquals(75, table39.size());
        assertEquals(table47.keySet(), table39.keySet());
        for (Integer id : table47.keySet()) {
            if (id == 0x04 || id == 0x15 || id == 0x3D || id == 0xCC || id == 0xFE) {
                continue;
            }
            assertEquals(table47.get(id), table39.get(id),
                    "packet 0x" + Integer.toHexString(id));
        }
        assertEquals(List.of(LegacyPacketTable.Field.I64),
                LegacyPacketTable.packet(p39, 0x04).fields(),
                "39 time_update carries a single i64");
        assertEquals(List.of(LegacyPacketTable.Field.I64, LegacyPacketTable.Field.I64),
                LegacyPacketTable.packet(p47, 0x04).fields());
        assertEquals("pickup_spawn_expanded",
                LegacyPacketTable.packet(p39, 0x15).custom());
        assertEquals("pickup_spawn", LegacyPacketTable.packet(p47, 0x15).custom());
        assertEquals(5, LegacyPacketTable.packet(p39, 0x3D).fields().size(),
                "39 effect has no trailing global flag");
        assertEquals(6, LegacyPacketTable.packet(p47, 0x3D).fields().size());
        assertEquals(4, LegacyPacketTable.packet(p39, 0xCC).fields().size(),
                "39 client_settings has no showCape flag");
        assertEquals(5, LegacyPacketTable.packet(p47, 0xCC).fields().size());
        assertEquals(List.of(), LegacyPacketTable.packet(p39, 0xFE).fields(),
                "39 server_list_ping's packet class has no fields");
        // The 49/47old wire traits are all present in 39 too.
        assertEquals("spawn_object_norot", LegacyPacketTable.packet(p39, 0x17).custom());
        assertEquals("chunk_bulk_nosky", LegacyPacketTable.packet(p39, 0x38).custom());
        assertFalse(LegacyPacketTable.packet(p39, 0x10).toClient(),
                "39 held_item_change is serverbound only, like 49 and legacy 47");
        assertTrue(LegacyProtocol.is1_3Generation(p39));
        assertFalse(LegacyProtocol.is1_4Generation(p39));
        assertTrue(LegacyProtocol.is1_5OrOlderGeneration(p39));
        assertTrue(LegacyProtocol.hasNoFireworkRocket(p39));
        // The DataWatcher slot entries of 39 (and legacy 47) carry
        // no NBT; 49 and up read a full slot (measured).
        assertTrue(LegacyProtocol.watcherSlotHasNoNbt(p39));
        assertTrue(LegacyProtocol.watcherSlotHasNoNbt(p47));
        assertFalse(LegacyProtocol.watcherSlotHasNoNbt(49));
        assertFalse(LegacyProtocol.watcherSlotHasNoNbt(78));
    }

    @Test
    void table29DiffersFrom39AsMeasured() {
        // Measured (docs/PROXY-1.2.5.md): protocol 29 (Minecraft
        // 1.2.5) has 68 registrations. Against 39 it loses the 1.3
        // additions (0x37, 0x38, 0x3E, 0xCB, 0xCC, 0xCD) and the
        // encryption pair (0xFC, 0xFD — 29 is below the encryption
        // boundary), and it alone has 0x32 pre_chunk. Thirteen
        // packets change layout; everything else is entry-for-
        // entry identical to 39.
        int p29 = LegacyProtocol.V1_2_5;
        int p39 = LegacyProtocol.V1_3_2;
        Map<Integer, PacketDef> table29 = LegacyPacketTable.tableFor(p29);
        Map<Integer, PacketDef> table39 = LegacyPacketTable.tableFor(p39);
        assertEquals(68, table29.size());
        java.util.Set<Integer> expectedKeys = new java.util.HashSet<>(table39.keySet());
        expectedKeys.removeAll(java.util.Set.of(0x37, 0x38, 0x3E, 0xCB, 0xCC, 0xCD, 0xFC, 0xFD));
        expectedKeys.add(0x32);
        assertEquals(expectedKeys, table29.keySet());
        java.util.Set<Integer> changed = java.util.Set.of(
                0x01, 0x02, 0x05, 0x0F, 0x14, 0x18, 0x1D, 0x33, 0x35, 0x36, 0x3C, 0x84, 0xCA);
        for (Integer id : table29.keySet()) {
            if (id == 0x32 || changed.contains(id)) {
                continue;
            }
            assertEquals(table39.get(id), table29.get(id),
                    "packet 0x" + Integer.toHexString(id));
        }
        // The changed layouts, one by one.
        assertEquals(List.of(LegacyPacketTable.Field.I32, LegacyPacketTable.Field.STRING,
                        LegacyPacketTable.Field.STRING, LegacyPacketTable.Field.I32,
                        LegacyPacketTable.Field.I32, LegacyPacketTable.Field.U8,
                        LegacyPacketTable.Field.U8, LegacyPacketTable.Field.U8),
                LegacyPacketTable.packet(p29, 0x01).fields(),
                "29 login has eight fields");
        assertEquals(List.of(LegacyPacketTable.Field.STRING),
                LegacyPacketTable.packet(p29, 0x02).fields(),
                "29 handshake is a single string");
        assertTrue(LegacyPacketTable.packet(p29, 0x02).toClient(),
                "29 handshake is bidirectional (the server answers with the hash string)");
        assertEquals("equipment_inline", LegacyPacketTable.packet(p29, 0x05).custom());
        assertEquals(5, LegacyPacketTable.packet(p29, 0x0F).fields().size(),
                "29 block_place has no cursor bytes");
        assertEquals(8, LegacyPacketTable.packet(p29, 0x14).fields().size(),
                "29 spawn_player has no trailing metadata");
        assertEquals(9, LegacyPacketTable.packet(p29, 0x18).fields().size(),
                "29 spawn_mob has no velocity shorts");
        assertEquals("destroy_entities_single", LegacyPacketTable.packet(p29, 0x1D).custom());
        assertEquals("chunk_data_125", LegacyPacketTable.packet(p29, 0x33).custom());
        assertEquals(LegacyPacketTable.Field.U8,
                LegacyPacketTable.packet(p29, 0x35).fields().get(3),
                "29 block_change's block type is a u8");
        assertEquals(5, LegacyPacketTable.packet(p29, 0x36).fields().size(),
                "29 block_action has no trailing block type");
        assertEquals("explosion_novel", LegacyPacketTable.packet(p29, 0x3C).custom());
        assertEquals(7, LegacyPacketTable.packet(p29, 0x84).fields().size(),
                "29 tile_entity_data is the int-parameter form");
        assertEquals(List.of(LegacyPacketTable.Field.BOOL, LegacyPacketTable.Field.BOOL,
                        LegacyPacketTable.Field.BOOL, LegacyPacketTable.Field.BOOL),
                LegacyPacketTable.packet(p29, 0xCA).fields(),
                "29 abilities are four bools");
        // The traits 29 shares with 39 (all measured identical).
        assertEquals("pickup_spawn_expanded", LegacyPacketTable.packet(p29, 0x15).custom());
        assertEquals("spawn_object_norot", LegacyPacketTable.packet(p29, 0x17).custom());
        assertEquals(List.of(LegacyPacketTable.Field.I64),
                LegacyPacketTable.packet(p29, 0x04).fields(),
                "29 time_update carries a single i64, like 39");
        assertEquals(5, LegacyPacketTable.packet(p29, 0x3D).fields().size(),
                "29 effect has no trailing global flag, like 39");
        assertFalse(LegacyPacketTable.packet(p29, 0x10).toClient(),
                "29 held_item_change is serverbound only");
        assertTrue(LegacyPacketTable.packet(p29, 0x32).toClient());
        assertFalse(LegacyPacketTable.packet(p29, 0x32).toServer(),
                "pre_chunk is clientbound only");
        // Generation helpers.
        assertTrue(LegacyProtocol.is1_2Generation(p29));
        assertFalse(LegacyProtocol.is1_3Generation(p29));
        assertTrue(LegacyProtocol.is1_5OrOlderGeneration(p29));
        assertTrue(LegacyProtocol.hasNoFireworkRocket(p29));
        assertTrue(LegacyProtocol.watcherSlotHasNoNbt(p29));
    }

    @Test
    void table28DiffersFrom29InExactlyOnePacket() {
        // Measured (docs/PROXY-1.2.3.md): protocol 28 (Minecraft
        // 1.2.3 / 1.2.2 / 1.2.1) has 67 registrations. Against 29
        // the ONLY difference is that 0xCA player_abilities is not
        // registered (it arrived in 1.2.4); every other entry is
        // identical, and the registries match too.
        int p28 = LegacyProtocol.V1_2_3;
        int p29 = LegacyProtocol.V1_2_5;
        Map<Integer, PacketDef> table28 = LegacyPacketTable.tableFor(p28);
        Map<Integer, PacketDef> table29 = LegacyPacketTable.tableFor(p29);
        assertEquals(67, table28.size());
        assertNull(LegacyPacketTable.packet(p28, 0xCA));
        assertNotNull(LegacyPacketTable.packet(p29, 0xCA));
        java.util.Set<Integer> expectedKeys = new java.util.HashSet<>(table29.keySet());
        expectedKeys.remove(0xCA);
        assertEquals(expectedKeys, table28.keySet());
        for (Integer id : table28.keySet()) {
            assertEquals(table29.get(id), table28.get(id),
                    "packet 0x" + Integer.toHexString(id));
        }
        // Generation helpers: 28 is a 1.2-generation, plaintext,
        // conditional-NBT protocol exactly like 29.
        assertTrue(LegacyProtocol.is1_2Generation(p28));
        assertTrue(LegacyProtocol.is1_2OrOlderGeneration(p28));
        assertTrue(LegacyProtocol.isPlaintextGeneration(p28));
        assertTrue(LegacyProtocol.slotNbtIsConditional(p28));
        assertTrue(LegacyProtocol.watcherSlotHasNoNbt(p28));
        assertTrue(LegacyProtocol.hasNoFireworkRocket(p28));
        assertFalse(LegacyProtocol.is1_1OrOlderGeneration(p28));
    }

    @Test
    void table23DiffersFrom28AsMeasured() {
        // Measured (docs/PROXY-1.1.md): protocol 23 (Minecraft 1.1)
        // has 65 registrations. Against 28 it loses 0x23 head look
        // and 0x84 tile entity data (both arrived in 1.2), and five
        // packets change layout: the seed login, the seed respawn,
        // the two-rotation spawn_mob, the cuboid chunk and the
        // three-array multi block change. (0xFA differs only in
        // the extractor's tokenisation; the wire form is the
        // same entry.)
        int p23 = LegacyProtocol.V1_1;
        int p28 = LegacyProtocol.V1_2_3;
        Map<Integer, PacketDef> table23 = LegacyPacketTable.tableFor(p23);
        Map<Integer, PacketDef> table28 = LegacyPacketTable.tableFor(p28);
        assertEquals(65, table23.size());
        java.util.Set<Integer> expectedKeys = new java.util.HashSet<>(table28.keySet());
        expectedKeys.removeAll(java.util.Set.of(0x23, 0x84));
        assertEquals(expectedKeys, table23.keySet());
        java.util.Set<Integer> changed = java.util.Set.of(0x01, 0x09, 0x18, 0x33, 0x34);
        for (Integer id : table23.keySet()) {
            if (changed.contains(id)) {
                continue;
            }
            assertEquals(table28.get(id), table23.get(id),
                    "packet 0x" + Integer.toHexString(id));
        }
        // The changed layouts, one by one.
        assertEquals(List.of(LegacyPacketTable.Field.I32, LegacyPacketTable.Field.STRING,
                        LegacyPacketTable.Field.I64, LegacyPacketTable.Field.STRING,
                        LegacyPacketTable.Field.I32, LegacyPacketTable.Field.I8,
                        LegacyPacketTable.Field.I8, LegacyPacketTable.Field.I8,
                        LegacyPacketTable.Field.I8),
                LegacyPacketTable.packet(p23, 0x01).fields(),
                "23 login is the transitional seed form");
        assertEquals(List.of(LegacyPacketTable.Field.I8, LegacyPacketTable.Field.I8,
                        LegacyPacketTable.Field.I8, LegacyPacketTable.Field.I16,
                        LegacyPacketTable.Field.I64, LegacyPacketTable.Field.STRING),
                LegacyPacketTable.packet(p23, 0x09).fields(),
                "23 respawn carries the seed and the level type");
        assertEquals(8, LegacyPacketTable.packet(p23, 0x18).fields().size(),
                "23 spawn_mob has two rotation bytes and no velocities");
        assertEquals("chunk_data_cuboid", LegacyPacketTable.packet(p23, 0x33).custom());
        assertEquals("multi_block_change_early",
                LegacyPacketTable.packet(p23, 0x34).custom());
        // Generation helpers.
        assertTrue(LegacyProtocol.is1_1OrOlderGeneration(p23));
        assertFalse(LegacyProtocol.is1_2Generation(p23));
        assertTrue(LegacyProtocol.is1_2OrOlderGeneration(p23));
        assertTrue(LegacyProtocol.isPlaintextGeneration(p23));
        assertTrue(LegacyProtocol.slotNbtIsConditional(p23));
        assertTrue(LegacyProtocol.watcherSlotHasNoNbt(p23));
        assertTrue(LegacyProtocol.hasNoFireworkRocket(p23));
        assertTrue(LegacyProtocol.is1_5OrOlderGeneration(p23));
    }

    @Test
    void table22DiffersFrom23AsMeasured() {
        // Measured (docs/PROXY-1.0.md): protocol 22 (Minecraft
        // 1.0.0) has 65 registrations. Against 23 the login and
        // respawn lose the level-type string, 0xFA plugin_message
        // is not registered (it arrived in 1.1), and one packet
        // exists only here: 0x1B player_input, serverbound only.
        int p22 = LegacyProtocol.V1_0;
        int p23 = LegacyProtocol.V1_1;
        Map<Integer, PacketDef> table22 = LegacyPacketTable.tableFor(p22);
        Map<Integer, PacketDef> table23 = LegacyPacketTable.tableFor(p23);
        assertEquals(65, table22.size());
        java.util.Set<Integer> expectedKeys = new java.util.HashSet<>(table23.keySet());
        expectedKeys.remove(0xFA);
        expectedKeys.add(0x1B);
        assertEquals(expectedKeys, table22.keySet());
        java.util.Set<Integer> changed = java.util.Set.of(0x01, 0x09);
        for (Integer id : table22.keySet()) {
            if (id == 0x1B || changed.contains(id)) {
                continue;
            }
            assertEquals(table23.get(id), table22.get(id),
                    "packet 0x" + Integer.toHexString(id));
        }
        assertEquals(List.of(LegacyPacketTable.Field.I32, LegacyPacketTable.Field.STRING,
                        LegacyPacketTable.Field.I64, LegacyPacketTable.Field.I32,
                        LegacyPacketTable.Field.I8, LegacyPacketTable.Field.I8,
                        LegacyPacketTable.Field.I8, LegacyPacketTable.Field.I8),
                LegacyPacketTable.packet(p22, 0x01).fields(),
                "22 login is the 23 form without the level type");
        assertEquals(List.of(LegacyPacketTable.Field.I8, LegacyPacketTable.Field.I8,
                        LegacyPacketTable.Field.I8, LegacyPacketTable.Field.I16,
                        LegacyPacketTable.Field.I64),
                LegacyPacketTable.packet(p22, 0x09).fields(),
                "22 respawn is the 23 form without the level type");
        PacketDef input = LegacyPacketTable.packet(p22, 0x1B);
        assertNotNull(input);
        assertEquals("player_input", input.name());
        assertFalse(input.toClient(), "player_input is serverbound only");
        assertTrue(input.toServer());
        assertEquals(List.of(LegacyPacketTable.Field.F32, LegacyPacketTable.Field.F32,
                        LegacyPacketTable.Field.F32, LegacyPacketTable.Field.F32,
                        LegacyPacketTable.Field.BOOL, LegacyPacketTable.Field.BOOL),
                input.fields());
        assertNull(LegacyPacketTable.packet(p23, 0x1B),
                "23 has no 0x1B at all (its table never registers one)");
        // Generation helpers.
        assertTrue(LegacyProtocol.is1_1OrOlderGeneration(p22));
        assertTrue(LegacyProtocol.isPlaintextGeneration(p22));
        assertTrue(LegacyProtocol.slotNbtIsConditional(p22));
        assertTrue(LegacyProtocol.watcherSlotHasNoNbt(p22));
    }

    @Test
    void table73DiffersFrom78InExactlyTwoPackets() {
        // Measured (docs/PROXY-1.6.1.md): 81 registrations, 0x85
        // absent, 0x2C on the no-modifier codec, everything else
        // entry-for-entry identical to 78.
        Map<Integer, PacketDef> table73 = LegacyPacketTable.tableFor(73);
        Map<Integer, PacketDef> table78 = LegacyPacketTable.tableFor(78);
        assertEquals(81, table73.size());
        assertNull(LegacyPacketTable.packet(73, 0x85));
        assertNotNull(LegacyPacketTable.packet(78, 0x85));
        assertEquals("entity_properties_nomod",
                LegacyPacketTable.packet(73, 0x2C).custom());
        assertEquals("entity_properties",
                LegacyPacketTable.packet(78, 0x2C).custom());
        for (Integer id : table73.keySet()) {
            if (id == 0x2C) {
                continue;
            }
            assertEquals(table78.get(id), table73.get(id),
                    "packet 0x" + Integer.toHexString(id));
        }
    }

    @Test
    void tableHasAll82RegisteredPackets() {
        Map<Integer, PacketDef> table = LegacyPacketTable.tableFor(78);
        assertEquals(82, table.size());
    }

    @Test
    void table74DelegatesFullyTo78() {
        // Measured: 1.6.2's registrations and every packet layout are
        // identical to 1.6.4's (docs/PROXY-1.6.2.md), so the 74 table
        // is the 78 table, entry for entry.
        Map<Integer, PacketDef> table74 = LegacyPacketTable.tableFor(74);
        Map<Integer, PacketDef> table78 = LegacyPacketTable.tableFor(78);
        assertEquals(82, table74.size());
        assertEquals(table78.keySet(), table74.keySet());
        for (Integer id : table78.keySet()) {
            assertEquals(table78.get(id), table74.get(id),
                    "packet 0x" + Integer.toHexString(id));
        }
        assertNotNull(LegacyPacketTable.packet(74, 0x38));
        assertNull(LegacyPacketTable.packet(74, 0x15));
    }

    @Test
    void keyPacketsExistWithMeasuredDirections() {
        assertPacket(0x00, true, true);   // keep alive
        assertPacket(0x01, true, true);   // login
        assertPacket(0x02, false, true);  // handshake
        assertPacket(0x04, true, false);  // time update
        assertPacket(0x33, true, false);  // chunk data
        assertPacket(0x38, true, false);  // chunk bulk
        assertPacket(0xFD, true, false);  // encryption request
        assertPacket(0xFC, true, true);   // encryption response
        assertPacket(0xFE, false, true);  // ping
        assertPacket(0xFF, true, true);   // disconnect
    }

    @Test
    void packet15IsNotRegisteredIn164() {
        // 0x15 (pickup spawn in some listings) does not exist in 1.6.4;
        // the experience orb is 0x1A and collect is 0x16.
        assertNull(LegacyPacketTable.packet(78, 0x15));
        assertNotNull(LegacyPacketTable.packet(78, 0x1A));
        assertNotNull(LegacyPacketTable.packet(78, 0x16));
        // No pre-chunk (0x32) in 1.6.4 either.
        assertNull(LegacyPacketTable.packet(78, 0x32));
    }

    @Test
    void everyPacketHasAName() {
        for (PacketDef def : LegacyPacketTable.tableFor(78).values()) {
            assertNotNull(def.name());
            assertFalse(def.name().isBlank(), "packet 0x" + Integer.toHexString(def.id()));
        }
    }

    private static void assertPacket(int id, boolean cb, boolean sb) {
        PacketDef def = LegacyPacketTable.packet(78, id);
        assertNotNull(def, "packet 0x" + Integer.toHexString(id));
        assertEquals(cb, def.toClient(), def.name() + " toClient");
        assertEquals(sb, def.toServer(), def.name() + " toServer");
    }
}
