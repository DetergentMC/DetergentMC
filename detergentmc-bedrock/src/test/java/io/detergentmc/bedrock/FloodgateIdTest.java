package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Floodgate 風 ID（決定的 UUID・Java 名整形）のテスト。 */
class FloodgateIdTest {

    @Test
    void xuidUuidIsDeterministicAndVersion3() {
        UUID first = FloodgateId.uuidForXuid("2535461234567890");
        UUID second = FloodgateId.uuidForXuid("2535461234567890");
        assertEquals(first, second, "同じ XUID は常に同じ UUID になること");
        assertEquals(3, first.version(), "UUID v3（名前ベース）であること");
        assertNotEquals(first, FloodgateId.uuidForXuid("2535461234567891"));
    }

    @Test
    void nameUuidDiffersFromXuidUuid() {
        // XUID なしのプレイヤーが XUID ありのプレイヤーと衝突しないこと。
        UUID fromName = FloodgateId.uuidForName("Steve");
        UUID fromXuid = FloodgateId.uuidForXuid("2535461234567890");
        assertNotEquals(fromName, fromXuid);
        assertEquals(fromName, FloodgateId.uuidForName("Steve"));
    }

    @Test
    void javaNameFormatting() {
        assertEquals(".Steve", FloodgateId.formatJavaName(".", "Steve"));
        // 16 文字制限：プレフィックス込みで切り捨てる。
        assertEquals(".VeryLongGamerTa", FloodgateId.formatJavaName(".", "VeryLongGamerTag12345"));
        assertEquals(16, FloodgateId.formatJavaName(".", "VeryLongGamerTag12345").length());
        // Java 名に使えない文字は落とす。
        assertEquals(".Steve", FloodgateId.formatJavaName(".", "St!eve@#"));
        // 空のゲーマータグは Player になる。
        assertEquals(".Player", FloodgateId.formatJavaName(".", ""));
        assertEquals(".Player", FloodgateId.formatJavaName(".", null));
        // プレフィックスなし。
        assertEquals("Steve", FloodgateId.formatJavaName("", "Steve"));
    }

    @Test
    void uuidForLoginInfo() {
        BedrockLogin.LoginInfo withXuid = BedrockLogin.parse(
                TestJwt.loginPayload(630, "Steve", "2535461234567890",
                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        assertEquals(FloodgateId.uuidForXuid("2535461234567890"),
                FloodgateId.uuidFor(withXuid));
        assertEquals(".Steve", FloodgateId.javaNameFor(withXuid, "."));
        assertTrue(FloodgateId.javaNameFor(withXuid, ".").length() <= 16);
    }
}
