package io.detergentmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** The core discovers version adapters through ServiceLoader. */
class AdapterDiscoveryTest {

    @Test
    void discoversThe1143Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.14.3")
                        && adapter.protocolVersion() == 490);
        assertTrue(found, "Expected a 1.14.3 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.14.3");
        assertEquals("1.14.3", adapter.minecraftVersion());
        assertEquals(490, adapter.protocolVersion());
    }

    @Test
    void discoversThe112Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.12")
                        && adapter.protocolVersion() == 335);
        assertTrue(found, "Expected a 1.12 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.12");
        assertEquals("1.12", adapter.minecraftVersion());
        assertEquals(335, adapter.protocolVersion());
    }

    @Test
    void discoversThe1121Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.12.1")
                        && adapter.protocolVersion() == 338);
        assertTrue(found, "Expected a 1.12.1 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.12.1");
        assertEquals("1.12.1", adapter.minecraftVersion());
        assertEquals(338, adapter.protocolVersion());
    }

    @Test
    void discoversThe1122Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.12.2")
                        && adapter.protocolVersion() == 340);
        assertTrue(found, "Expected a 1.12.2 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.12.2");
        assertEquals("1.12.2", adapter.minecraftVersion());
        assertEquals(340, adapter.protocolVersion());
    }

    @Test
    void discoversThe113Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.13")
                        && adapter.protocolVersion() == 393);
        assertTrue(found, "Expected a 1.13 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.13");
        assertEquals("1.13", adapter.minecraftVersion());
        assertEquals(393, adapter.protocolVersion());
    }

    @Test
    void discoversThe1132Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.13.2")
                        && adapter.protocolVersion() == 404);
        assertTrue(found, "Expected a 1.13.2 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.13.2");
        assertEquals("1.13.2", adapter.minecraftVersion());
        assertEquals(404, adapter.protocolVersion());
    }

    @Test
    void discoversThe114Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.14")
                        && adapter.protocolVersion() == 477);
        assertTrue(found, "Expected a 1.14 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.14");
        assertEquals("1.14", adapter.minecraftVersion());
        assertEquals(477, adapter.protocolVersion());
    }

    @Test
    void discoversThe1141Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.14.1")
                        && adapter.protocolVersion() == 480);
        assertTrue(found, "Expected a 1.14.1 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.14.1");
        assertEquals("1.14.1", adapter.minecraftVersion());
        assertEquals(480, adapter.protocolVersion());
    }

    @Test
    void discoversThe1142Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.14.2")
                        && adapter.protocolVersion() == 485);
        assertTrue(found, "Expected a 1.14.2 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.14.2");
        assertEquals("1.14.2", adapter.minecraftVersion());
        assertEquals(485, adapter.protocolVersion());
    }

    @Test
    void discoversThe1144Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.14.4")
                        && adapter.protocolVersion() == 498);
        assertTrue(found, "Expected a 1.14.4 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.14.4");
        assertEquals("1.14.4", adapter.minecraftVersion());
        assertEquals(498, adapter.protocolVersion());
    }

    @Test
    void discoversThe115Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.15")
                        && adapter.protocolVersion() == 573);
        assertTrue(found, "Expected a 1.15 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.15");
        assertEquals("1.15", adapter.minecraftVersion());
        assertEquals(573, adapter.protocolVersion());
    }

    @Test
    void discoversThe1151Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.15.1")
                        && adapter.protocolVersion() == 575);
        assertTrue(found, "Expected a 1.15.1 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.15.1");
        assertEquals("1.15.1", adapter.minecraftVersion());
        assertEquals(575, adapter.protocolVersion());
    }

    @Test
    void discoversThe1152Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.15.2")
                        && adapter.protocolVersion() == 578);
        assertTrue(found, "Expected a 1.15.2 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.15.2");
        assertEquals("1.15.2", adapter.minecraftVersion());
        assertEquals(578, adapter.protocolVersion());
    }

    @Test
    void discoversThe1182Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.18.2")
                        && adapter.protocolVersion() == 758);
        assertTrue(found, "Expected a 1.18.2 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.18.2");
        assertEquals("1.18.2", adapter.minecraftVersion());
        assertEquals(758, adapter.protocolVersion());
    }

    @Test
    void discoversThe1191Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.19.1")
                        && adapter.protocolVersion() == 760);
        assertTrue(found, "Expected a 1.19.1 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.19.1");
        assertEquals("1.19.1", adapter.minecraftVersion());
        assertEquals(760, adapter.protocolVersion());
    }

    @Test
    void discoversThe1192Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.19.2")
                        && adapter.protocolVersion() == 760);
        assertTrue(found, "Expected a 1.19.2 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.19.2");
        assertEquals("1.19.2", adapter.minecraftVersion());
        assertEquals(760, adapter.protocolVersion());
    }

    @Test
    void discoversThe1193Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.19.3")
                        && adapter.protocolVersion() == 761);
        assertTrue(found, "Expected a 1.19.3 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.19.3");
        assertEquals("1.19.3", adapter.minecraftVersion());
        assertEquals(761, adapter.protocolVersion());
    }

    @Test
    void discoversThe1194Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.19.4")
                        && adapter.protocolVersion() == 762);
        assertTrue(found, "Expected a 1.19.4 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.19.4");
        assertEquals("1.19.4", adapter.minecraftVersion());
        assertEquals(762, adapter.protocolVersion());
    }

    @Test
    void discoversThe1204Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.20.4")
                        && adapter.protocolVersion() == 765);
        assertTrue(found, "Expected a 1.20.4 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.20.4");
        assertEquals("1.20.4", adapter.minecraftVersion());
        assertEquals(765, adapter.protocolVersion());
    }

    @Test
    void discoversThe1201Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.20.1")
                        && adapter.protocolVersion() == 763);
        assertTrue(found, "Expected a 1.20.1 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.20.1");
        assertEquals("1.20.1", adapter.minecraftVersion());
        assertEquals(763, adapter.protocolVersion());
    }

    @Test
    void discoversThe1202Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.20.2")
                        && adapter.protocolVersion() == 764);
        assertTrue(found, "Expected a 1.20.2 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.20.2");
        assertEquals("1.20.2", adapter.minecraftVersion());
        assertEquals(764, adapter.protocolVersion());
    }

    @Test
    void discoversThe1205Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.20.5")
                        && adapter.protocolVersion() == 766);
        assertTrue(found, "Expected a 1.20.5 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.20.5");
        assertEquals("1.20.5", adapter.minecraftVersion());
        assertEquals(766, adapter.protocolVersion());
    }

    @Test
    void discoversThe1206Adapter() {
        // 1.20.5 and 1.20.6 share protocol 766 and live in one module.
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.20.6")
                        && adapter.protocolVersion() == 766);
        assertTrue(found, "Expected a 1.20.6 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.20.6");
        assertEquals("1.20.6", adapter.minecraftVersion());
        assertEquals(766, adapter.protocolVersion());
    }

    @Test
    void discoversThe121Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.21")
                        && adapter.protocolVersion() == 767);
        assertTrue(found, "Expected a 1.21 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.21");
        assertEquals("1.21", adapter.minecraftVersion());
        assertEquals(767, adapter.protocolVersion());
    }

    @Test
    void discoversThe1211Adapter() {
        // 1.21 and 1.21.1 share protocol 767 and live in one module.
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.21.1")
                        && adapter.protocolVersion() == 767);
        assertTrue(found, "Expected a 1.21.1 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.21.1");
        assertEquals("1.21.1", adapter.minecraftVersion());
        assertEquals(767, adapter.protocolVersion());
    }

    @Test
    void discoversThe1214Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.21.4")
                        && adapter.protocolVersion() == 769);
        assertTrue(found, "Expected a 1.21.4 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.21.4");
        assertEquals("1.21.4", adapter.minecraftVersion());
        assertEquals(769, adapter.protocolVersion());
    }

    @Test
    void discoversThe1215Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.21.5")
                        && adapter.protocolVersion() == 770);
        assertTrue(found, "Expected a 1.21.5 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.21.5");
        assertEquals("1.21.5", adapter.minecraftVersion());
        assertEquals(770, adapter.protocolVersion());
    }

    @Test
    void discoversThe1217And1218Adapters() {
        boolean found1217 = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.21.7")
                        && adapter.protocolVersion() == 772);
        assertTrue(found1217, "Expected a 1.21.7 adapter on the test classpath");

        VersionAdapter adapter1217 = DetergentServer.selectAdapter("1.21.7");
        assertEquals("1.21.7", adapter1217.minecraftVersion());
        assertEquals(772, adapter1217.protocolVersion());

        boolean found1218 = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.21.8")
                        && adapter.protocolVersion() == 772);
        assertTrue(found1218, "Expected a 1.21.8 adapter on the test classpath");

        VersionAdapter adapter1218 = DetergentServer.selectAdapter("1.21.8");
        assertEquals("1.21.8", adapter1218.minecraftVersion());
        assertEquals(772, adapter1218.protocolVersion());
    }

    @Test
    void discoversThe1219And12110Adapters() {
        boolean found1219 = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.21.9")
                        && adapter.protocolVersion() == 773);
        assertTrue(found1219, "Expected a 1.21.9 adapter on the test classpath");

        VersionAdapter adapter1219 = DetergentServer.selectAdapter("1.21.9");
        assertEquals("1.21.9", adapter1219.minecraftVersion());
        assertEquals(773, adapter1219.protocolVersion());

        boolean found12110 = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.21.10")
                        && adapter.protocolVersion() == 773);
        assertTrue(found12110, "Expected a 1.21.10 adapter on the test classpath");

        VersionAdapter adapter12110 = DetergentServer.selectAdapter("1.21.10");
        assertEquals("1.21.10", adapter12110.minecraftVersion());
        assertEquals(773, adapter12110.protocolVersion());
    }

    @Test
    void discoversThe12111Adapter() {
        boolean found12111 = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.21.11")
                        && adapter.protocolVersion() == 774);
        assertTrue(found12111, "Expected a 1.21.11 adapter on the test classpath");

        VersionAdapter adapter12111 = DetergentServer.selectAdapter("1.21.11");
        assertEquals("1.21.11", adapter12111.minecraftVersion());
        assertEquals(774, adapter12111.protocolVersion());
    }

    @Test
    void discoversThe1261Adapters() {
        for (String version : new String[]{"26.1", "26.1.1", "26.1.2"}) {
            boolean found = StreamSupport.stream(
                            ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                    .anyMatch(adapter -> adapter.minecraftVersion().equals(version)
                            && adapter.protocolVersion() == 775);
            assertTrue(found, "Expected a " + version + " adapter on the test classpath");

            VersionAdapter adapter = DetergentServer.selectAdapter(version);
            assertEquals(version, adapter.minecraftVersion());
            assertEquals(775, adapter.protocolVersion());
        }
    }

    @Test
    void discoversThe1262Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("26.2")
                        && adapter.protocolVersion() == 776);
        assertTrue(found, "Expected a 26.2 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("26.2");
        assertEquals("26.2", adapter.minecraftVersion());
        assertEquals(776, adapter.protocolVersion());
    }

    @Test
    void discoversThe1216Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.21.6")
                        && adapter.protocolVersion() == 771);
        assertTrue(found, "Expected a 1.21.6 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.21.6");
        assertEquals("1.21.6", adapter.minecraftVersion());
        assertEquals(771, adapter.protocolVersion());
    }
}
