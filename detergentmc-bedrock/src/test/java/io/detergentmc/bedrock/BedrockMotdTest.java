package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** MOTD 文字列（MCPE;...）の生成とパースのテスト。 */
class BedrockMotdTest {

    @Test
    void buildAndParseRoundTrip() {
        String motd = BedrockMotd.build("DetergentMC 1.20.4", 630, "1.20.50",
                2, 20, 1234567890123456789L, "DetergentMC", 19132, 19133);
        assertTrue(motd.startsWith("MCPE;DetergentMC 1.20.4;630;1.20.50;2;20;"),
                "MOTD: " + motd);
        assertTrue(motd.endsWith("19132;19133;"), "MOTD: " + motd);
        BedrockMotd.Parsed parsed = BedrockMotd.parse(motd);
        assertNotNull(parsed);
        assertEquals("DetergentMC 1.20.4", parsed.motdLine1());
        assertEquals(630, parsed.protocol());
        assertEquals("1.20.50", parsed.version());
        assertEquals(2, parsed.players());
        assertEquals(20, parsed.maxPlayers());
        assertEquals(1234567890123456789L, parsed.serverId());
        assertEquals("DetergentMC", parsed.motdLine2());
        assertEquals("Survival", parsed.gameMode());
        assertEquals(1, parsed.gameModeNumeric());
        assertEquals(19132, parsed.ipv4Port());
        assertEquals(19133, parsed.ipv6Port());
    }

    @Test
    void semicolonInMotdIsSanitized() {
        String motd = BedrockMotd.build("a;b", 630, "1.20.50", 0, 20, 1L, "x;y", 19132, 19133);
        BedrockMotd.Parsed parsed = BedrockMotd.parse(motd);
        assertNotNull(parsed);
        assertEquals("a；b", parsed.motdLine1());
        assertEquals("x；y", parsed.motdLine2());
    }

    @Test
    void invalidMotdReturnsNull() {
        assertNull(BedrockMotd.parse(null));
        assertNull(BedrockMotd.parse(""));
        assertNull(BedrockMotd.parse("MCEE;DetergentMC;630;1.20.50;0;20;1;DetergentMC;Survival;1;19132;19133;"));
        assertNull(BedrockMotd.parse("MCPE;only;three"));
    }

    @Test
    void motdFieldEncoding() {
        byte[] field = BedrockMotd.encodeMotdField("MCPE;test;");
        // u16 BE 長＋UTF-8。
        assertEquals(10, field[0] << 8 | (field[1] & 0xFF));
        assertEquals("MCPE;test;", new String(field, 2, field.length - 2,
                java.nio.charset.StandardCharsets.UTF_8));
    }
}
