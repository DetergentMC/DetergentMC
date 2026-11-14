package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * セッションサーバー照会の検証（担当1）。相手はローカルの偽サーバーで、
 * 本物の Mojang には触らない（サンドボックスでは届かない場合があるため）。
 */
class SessionServerClientTest {

    private HttpServer fakeServer;
    private String baseUrl;
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private volatile int statusToReturn = 200;
    private volatile String bodyToReturn = "{"
            + "\"id\":\"123e4567e89b12d3a456426614174000\","
            + "\"name\":\"Steve\","
            + "\"properties\":[{\"name\":\"textures\","
            + "\"value\":\"dGV4dHVyZXNWYWx1ZQ==\","
            + "\"signature\":\"c2lnbmF0dXJl\"}]}";

    @BeforeEach
    void startFakeServer() throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeServer.createContext("/session/minecraft/hasJoined", exchange -> {
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = bodyToReturn.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusToReturn, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        fakeServer.start();
        baseUrl = "http://127.0.0.1:" + fakeServer.getAddress().getPort();
    }

    @AfterEach
    void stopFakeServer() {
        if (fakeServer != null) {
            fakeServer.stop(0);
        }
    }

    @Test
    void verifiesProfileAndParsesProperties() throws Exception {
        SessionServerClient client =
                new SessionServerClient.HttpSessionServerClient(baseUrl);
        Optional<AuthenticatedProfile> profile = client.verify("Steve", "abc123");
        assertTrue(profile.isPresent());
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                profile.get().uuid());
        assertEquals("Steve", profile.get().name());
        assertEquals(1, profile.get().properties().size());
        assertEquals("textures", profile.get().properties().get(0).name());
        assertEquals("dGV4dHVyZXNWYWx1ZQ==", profile.get().properties().get(0).value());
        assertEquals("c2lnbmF0dXJl", profile.get().properties().get(0).signature());
        // 照会のクエリに名前と serverId が入っている。
        String query = lastQuery.get();
        assertTrue(query.contains("username=Steve"), query);
        assertTrue(query.contains("serverId=abc123"), query);
    }

    @Test
    void notJoinedReturnsEmpty() throws Exception {
        statusToReturn = 404;
        bodyToReturn = "";
        SessionServerClient client =
                new SessionServerClient.HttpSessionServerClient(baseUrl);
        assertTrue(client.verify("Mallory", "abc123").isEmpty());
    }

    @Test
    void rateLimitedThrows() {
        statusToReturn = 429;
        bodyToReturn = "";
        SessionServerClient client =
                new SessionServerClient.HttpSessionServerClient(baseUrl);
        assertThrows(IOException.class, () -> client.verify("Steve", "abc123"));
    }

    @Test
    void malformedJsonThrows() {
        bodyToReturn = "{\"name\":\"Steve\"}";
        SessionServerClient client =
                new SessionServerClient.HttpSessionServerClient(baseUrl);
        assertThrows(IOException.class, () -> client.verify("Steve", "abc123"));
    }

    @Test
    void defaultBaseUrlIsMojang() {
        assertEquals("https://sessionserver.mojang.com",
                SessionServerClient.HttpSessionServerClient.DEFAULT_BASE_URL);
        assertEquals("https://sessionserver.mojang.com",
                ProxyAuthConfig.defaults().sessionServerBaseUrl());
    }

    @Test
    void authConfigOfBlankFallsBackToDefault() {
        assertEquals("https://sessionserver.mojang.com",
                ProxyAuthConfig.of("").sessionServerBaseUrl());
        assertEquals("https://sessionserver.mojang.com",
                ProxyAuthConfig.of(null).sessionServerBaseUrl());
        assertEquals(baseUrl, ProxyAuthConfig.of(baseUrl).sessionServerBaseUrl());
        assertFalse(ProxyAuthConfig.of(baseUrl).verifier() == null);
    }

    @Test
    void parseProfileHandlesEscapesAndMissingProperties() throws Exception {
        AuthenticatedProfile parsed = SessionServerClient.HttpSessionServerClient.parseProfile(
                "{\"id\":\"123e4567e89b12d3a456426614174000\","
                        + "\"name\":\"St\\\"eve\"}");
        assertEquals("St\"eve", parsed.name());
        assertTrue(parsed.properties().isEmpty());
        assertFalse(parsed.hasProperties());
    }
}
