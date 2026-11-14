package io.detergentmc.core.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VanillaPlayerTest {

    @Test
    void tellrawKeepsJapaneseAsUtf8() {
        assertEquals(
                "tellraw Steve {\"text\":\"こんにちは、Steveさん！\"}",
                VanillaPlayer.tellrawCommand("Steve", "こんにちは、Steveさん！"));
    }

    @Test
    void tellrawEscapesQuotesBackslashesAndNewlines() {
        assertEquals(
                "tellraw Alex {\"text\":\"彼は \\\"やあ\\\" と言った\\\\笑\\n次の行\"}",
                VanillaPlayer.tellrawCommand("Alex", "彼は \"やあ\" と言った\\笑\n次の行"));
    }

    @Test
    void escapeJsonEscapesControlCharacters() {
        assertEquals("a\\tb\\rc\\u0001d", VanillaPlayer.escapeJson("a\tb\rc\u0001d"));
        assertEquals("", VanillaPlayer.escapeJson(""));
    }

    @Test
    void sendMessageSendsOneTellrawLine() {
        List<String> sent = new ArrayList<>();
        VanillaPlayer player = new VanillaPlayer(
                "Steve", UUID.randomUUID(), Locale.JAPANESE, sent::add);

        player.sendMessage("こんにちは \"世界\"\nまたね");
        assertEquals(1, sent.size());
        assertEquals(
                "tellraw Steve {\"text\":\"こんにちは \\\"世界\\\"\\nまたね\"}",
                sent.get(0));
    }

    @Test
    void resolveUuidUsesRealUuidWhenPresent() {
        UUID real = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertEquals(real, VanillaPlayer.resolveUuid("Steve", real.toString()));
    }

    @Test
    void resolveUuidIsDeterministicOfflineUuidWhenMissingOrInvalid() {
        UUID expected = UUID.nameUUIDFromBytes(
                "OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, VanillaPlayer.resolveUuid("Steve", ""));
        assertEquals(expected, VanillaPlayer.resolveUuid("Steve", null));
        assertEquals(expected, VanillaPlayer.resolveUuid("Steve", "not-a-uuid"));
        assertTrue(VanillaPlayer.resolveUuid("Steve", "").equals(
                VanillaPlayer.resolveUuid("Steve", "")));
    }
}
