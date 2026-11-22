package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 1.6.4 adapter must be discoverable through the same ServiceLoader
 * mechanism as every other version, so DetergentServer accepts
 * minecraft-version 1.6.4 — while its protocol number stays in the
 * legacy numbering space.
 */
class LegacyAdapterTest {

    @Test
    void adapterIsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_6_4Adapter) {
                found = true;
                assertEquals("1.6.4", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_6_4, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_6_4Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter162IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_6_2Adapter) {
                found = true;
                assertEquals("1.6.2", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_6_2, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_6_2Adapter must be registered in META-INF/services");
    }

    @Test
    void blocks162Match164() {
        // The 1.6.2 and 1.6.4 registries are identical (measured), so
        // the adapters expose the same block set.
        V1_6_2Adapter adapter162 = new V1_6_2Adapter();
        V1_6_4Adapter adapter164 = new V1_6_4Adapter();
        for (BlockType block : BlockType.values()) {
            assertEquals(adapter164.toMinecraftId(block), adapter162.toMinecraftId(block),
                    block.name());
        }
        assertTrue(adapter162.toMinecraftId(BlockType.SLIME_BLOCK).isEmpty());
    }

    @Test
    void adapter161IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_6_1Adapter) {
                found = true;
                assertEquals("1.6.1", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_6_1, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_6_1Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter152IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_5_2Adapter) {
                found = true;
                assertEquals("1.5.2", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_5_2, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_5_2Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter151IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_5_1Adapter) {
                found = true;
                assertEquals("1.5.1", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_5_1, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_5_1Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter15IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_5Adapter) {
                found = true;
                assertEquals("1.5", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_5_1, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_5Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter147IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_4_7Adapter) {
                found = true;
                assertEquals("1.4.7", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_4_7, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_4_7Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter146IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_4_6Adapter) {
                found = true;
                assertEquals("1.4.6", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_4_7, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_4_6Adapter must be registered in META-INF/services");
    }

    @Test
    void blocks147And146Match152WithinTheRepresentativeSet() {
        // BlockType is a representative set whose since values jump
        // from 1.0 to 1.8, so within it the 1.4.7 / 1.4.6 / 1.5.2
        // adapters expose the same blocks. (The full-registry
        // difference — the 1.5 additions — is measured in
        // docs/PROXY-1.4.7.md and handled by the chunk/slot remaps,
        // not by this enum.)
        V1_5_2Adapter adapter152 = new V1_5_2Adapter();
        for (VersionAdapter adapter : List.of(new V1_4_7Adapter(), new V1_4_6Adapter())) {
            for (BlockType block : BlockType.values()) {
                assertEquals(adapter152.toMinecraftId(block), adapter.toMinecraftId(block),
                        block.name());
            }
        }
        assertTrue(new V1_4_7Adapter().toMinecraftId(BlockType.STONE).isPresent());
        assertTrue(new V1_4_7Adapter().toMinecraftId(BlockType.SLIME_BLOCK).isEmpty());
    }

    @Test
    void adapter145IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_4_5Adapter) {
                found = true;
                assertEquals("1.4.5", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_4_5, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_4_5Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter144IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_4_4Adapter) {
                found = true;
                assertEquals("1.4.4", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_4_5, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_4_4Adapter must be registered in META-INF/services");
    }

    @Test
    void blocks145And144Match147WithinTheRepresentativeSet() {
        // BlockType is a representative set whose since values jump
        // from 1.0 to 1.8, so within it the 1.4.5 / 1.4.4 / 1.4.7
        // adapters expose the same blocks. (The full registries of
        // 1.4.5 and 1.4.7 are in fact identical for blocks —
        // measured in docs/PROXY-1.4.5.md.)
        V1_4_7Adapter adapter147 = new V1_4_7Adapter();
        for (VersionAdapter adapter : List.of(new V1_4_5Adapter(), new V1_4_4Adapter())) {
            for (BlockType block : BlockType.values()) {
                assertEquals(adapter147.toMinecraftId(block), adapter.toMinecraftId(block),
                        block.name());
            }
        }
    }

    @Test
    void adapter142IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_4_2Adapter) {
                found = true;
                assertEquals("1.4.2", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_4_2, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_4_2Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter132IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_3_2Adapter) {
                found = true;
                assertEquals("1.3.2", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_3_2, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_3_2Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter131IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_3_1Adapter) {
                found = true;
                assertEquals("1.3.1", adapter.minecraftVersion());
                // 1.3.1 is the same protocol number as 1.3.2
                // (measured; the "1.3.1 = 38" table entry is wrong).
                assertEquals(LegacyProtocol.V1_3_2, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_3_1Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter125IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_2_5Adapter) {
                found = true;
                assertEquals("1.2.5", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_2_5, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_2_5Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter124IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_2_4Adapter) {
                found = true;
                assertEquals("1.2.4", adapter.minecraftVersion());
                // 1.2.4 is the same protocol number as 1.2.5
                // (measured; the "1.2.4 = 28" table entry is wrong).
                assertEquals(LegacyProtocol.V1_2_5, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_2_4Adapter must be registered in META-INF/services");
    }

    @Test
    void blocks124Match125WithinTheRepresentativeSet() {
        // The full 1.2.4 and 1.2.5 registries are identical
        // (measured in docs/PROXY-1.2.5.md), so the two adapters
        // expose the same blocks.
        V1_2_5Adapter adapter125 = new V1_2_5Adapter();
        V1_2_4Adapter adapter124 = new V1_2_4Adapter();
        for (BlockType block : BlockType.values()) {
            assertEquals(adapter125.toMinecraftId(block), adapter124.toMinecraftId(block),
                    block.name());
        }
    }

    @Test
    void blocks125Match132WithinTheRepresentativeSet() {
        // BlockType is a representative set whose since values jump
        // from 1.0 to 1.8, so within it the 1.2.5 and 1.3.2 adapters
        // expose the same blocks. (The full registries differ by
        // the twelve 1.3 block additions — measured in
        // docs/PROXY-1.2.5.md — which the representative set does
        // not contain.)
        V1_3_2Adapter adapter132 = new V1_3_2Adapter();
        V1_2_5Adapter adapter125 = new V1_2_5Adapter();
        for (BlockType block : BlockType.values()) {
            assertEquals(adapter132.toMinecraftId(block), adapter125.toMinecraftId(block),
                    block.name());
        }
    }

    @Test
    void blocks132And131Match142WithinTheRepresentativeSet() {
        // BlockType is a representative set whose since values jump
        // from 1.0 to 1.8, so within it the 1.3.2 / 1.3.1 / 1.4.2
        // adapters expose the same blocks. (The full registries
        // differ by the nine 1.4 block additions — measured in
        // docs/PROXY-1.3.2.md — which the representative set does
        // not contain.)
        V1_4_2Adapter adapter142 = new V1_4_2Adapter();
        for (VersionAdapter adapter : List.of(new V1_3_2Adapter(), new V1_3_1Adapter())) {
            for (BlockType block : BlockType.values()) {
                assertEquals(adapter142.toMinecraftId(block), adapter.toMinecraftId(block),
                        block.name());
            }
        }
    }

    @Test
    void blocks142Match145WithinTheRepresentativeSet() {
        // BlockType is a representative set whose since values jump
        // from 1.0 to 1.8, so within it the 1.4.2 and 1.4.5 adapters
        // expose the same blocks. (The full registries of 1.4.2 and
        // 1.4.5 are in fact identical for blocks — measured in
        // docs/PROXY-1.4.2.md.)
        V1_4_5Adapter adapter145 = new V1_4_5Adapter();
        V1_4_2Adapter adapter142 = new V1_4_2Adapter();
        for (BlockType block : BlockType.values()) {
            assertEquals(adapter145.toMinecraftId(block), adapter142.toMinecraftId(block),
                    block.name());
        }
    }

    @Test
    void blocks151And15Match152() {
        // The 1.5 / 1.5.1 / 1.5.2 registries are identical (measured
        // via the client lang keys), so the adapters expose the same
        // block set.
        V1_5_2Adapter adapter152 = new V1_5_2Adapter();
        for (VersionAdapter adapter : List.of(new V1_5_1Adapter(), new V1_5Adapter())) {
            for (BlockType block : BlockType.values()) {
                assertEquals(adapter152.toMinecraftId(block), adapter.toMinecraftId(block),
                        block.name());
            }
        }
    }

    @Test
    void blocks152Match161WithinTheRepresentativeSet() {
        // BlockType is a representative set whose since values jump
        // from 1.0 to 1.8, so within it the 1.5.2 and 1.6.1 adapters
        // expose the same blocks. (The full-registry difference —
        // the 1.6 additions — is measured in docs/PROXY-1.5.2.md and
        // handled by the chunk/slot remaps, not by this enum.)
        V1_5_2Adapter adapter152 = new V1_5_2Adapter();
        V1_6_1Adapter adapter161 = new V1_6_1Adapter();
        for (BlockType block : BlockType.values()) {
            assertEquals(adapter161.toMinecraftId(block), adapter152.toMinecraftId(block),
                    block.name());
        }
        assertTrue(adapter152.toMinecraftId(BlockType.STONE).isPresent());
        assertTrue(adapter152.toMinecraftId(BlockType.SLIME_BLOCK).isEmpty());
    }

    @Test
    void blocks161Match162() {
        // The 1.6.1 and 1.6.2 registries are identical (measured), so
        // the adapters expose the same block set.
        V1_6_1Adapter adapter161 = new V1_6_1Adapter();
        V1_6_2Adapter adapter162 = new V1_6_2Adapter();
        for (BlockType block : BlockType.values()) {
            assertEquals(adapter162.toMinecraftId(block), adapter161.toMinecraftId(block),
                    block.name());
        }
    }

    @Test
    void blocksFollowSinceVersions() {
        V1_6_4Adapter adapter = new V1_6_4Adapter();
        assertTrue(adapter.toMinecraftId(BlockType.STONE).isPresent());
        assertTrue(adapter.toMinecraftId(BlockType.GLASS).isPresent());
        // Slime block arrived in 1.8: not present in 1.6.4.
        assertTrue(adapter.toMinecraftId(BlockType.SLIME_BLOCK).isEmpty());
    }

    @Test
    void adapter123IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_2_3Adapter) {
                found = true;
                assertEquals("1.2.3", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_2_3, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_2_3Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter122IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_2_2Adapter) {
                found = true;
                assertEquals("1.2.2", adapter.minecraftVersion());
                // 1.2.2 is the same protocol number as 1.2.3 (measured).
                assertEquals(LegacyProtocol.V1_2_3, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_2_2Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter121IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_2_1Adapter) {
                found = true;
                assertEquals("1.2.1", adapter.minecraftVersion());
                // 1.2.1 is the same protocol number as 1.2.3 (measured).
                assertEquals(LegacyProtocol.V1_2_3, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_2_1Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter11IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_1Adapter) {
                found = true;
                assertEquals("1.1", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_1, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_1Adapter must be registered in META-INF/services");
    }

    @Test
    void adapter100IsDiscoverable() {
        boolean found = false;
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter instanceof V1_0_0Adapter) {
                found = true;
                assertEquals("1.0.0", adapter.minecraftVersion());
                assertEquals(LegacyProtocol.V1_0, adapter.protocolVersion());
            }
        }
        assertTrue(found, "V1_0_0Adapter must be registered in META-INF/services");
    }

    @Test
    void blocks123And122And121And11And100Match125WithinTheRepresentativeSet() {
        // BlockType is a representative set whose since values jump
        // from 1.0 to 1.8, so within it all the 1.2.x / 1.1 / 1.0.0
        // adapters expose the same blocks. (The full registries
        // differ by the 1.2 lamp blocks and nothing else —
        // measured in docs/PROXY-1.2.3.md and docs/PROXY-1.1.md —
        // which the representative set does not contain.)
        V1_2_5Adapter adapter125 = new V1_2_5Adapter();
        for (VersionAdapter adapter : List.of(
                new V1_2_4Adapter(), new V1_2_3Adapter(), new V1_2_2Adapter(),
                new V1_2_1Adapter(), new V1_1Adapter(), new V1_0_0Adapter())) {
            for (BlockType block : BlockType.values()) {
                assertEquals(adapter125.toMinecraftId(block), adapter.toMinecraftId(block),
                        block.name());
            }
        }
        assertTrue(adapter125.toMinecraftId(BlockType.STONE).isPresent());
        assertTrue(new V1_0_0Adapter().toMinecraftId(BlockType.SLIME_BLOCK).isEmpty());
    }

    @Test
    void legacyBackendVersionDetection() {
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.6.4"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.6.2"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.6.1"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.5.2"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.5.1"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.5"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.4.7"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.4.6"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.4.5"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.4.4"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.4.2"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.3.2"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.3.1"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.2.5"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.2.4"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.2.3"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.2.2"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.2.1"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.1"));
        assertTrue(LegacyProtocol.isLegacyBackendVersion("1.0.0"));
        assertEquals(false, LegacyProtocol.isLegacyBackendVersion("1.7.10"));
        assertEquals(false, LegacyProtocol.isLegacyBackendVersion("1.20.4"));
    }
}
