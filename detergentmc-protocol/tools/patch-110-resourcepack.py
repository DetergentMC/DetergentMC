#!/usr/bin/env python3
"""110 のリソースパック単体テストを 210 のテストから生成する。

210 のテスト実物（decideFull ヘルパー方式・BuiltPack 列挙方式）に
合わせた変換。フォールバック順は 110 → 210 → 315 → 316。
各置換は期待件数を検証する。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
TEST = ROOT / "detergentmc-protocol/src/test/java/io/detergentmc/protocol"
failures = []


def gen(name210, name110, replacements):
    path = TEST / name210
    text = path.read_text(encoding="utf-8")
    for old, new, expected in replacements:
        count = text.count(old)
        if count != expected:
            failures.append(f"{name210}: 期待 {expected} 件のところ {count} 件: {old[:70]!r}")
            continue
        text = text.replace(old, new)
    (TEST / name110).write_text(text, encoding="utf-8")
    print(f"{name110} を生成した")


gen("ResourcePackBuilder210Test.java", "ResourcePackBuilder110Test.java", [
    ("/** 210（1.10.2）用のリソースパック（pack_format 2、旧レイアウト）の生成。レイアウトは 315／335／340 と同一で、形式番号だけが 2。 */\nclass ResourcePackBuilder210Test {",
     "/** 110（1.9.4）用のリソースパック（pack_format 2、旧レイアウト）の生成。レイアウトは 210／315／335／340 と同一で、形式番号だけが 2。 */\nclass ResourcePackBuilder110Test {", 1),
    ("void packFormatForProtocol210Is2() {\n        assertEquals(2, ResourcePackBuilder.packFormatForProtocol(210));",
     "void packFormatForProtocol110Is2() {\n        assertEquals(2, ResourcePackBuilder.packFormatForProtocol(110));", 1),
    ("void build210WritesPackFormat2AndValidZip() throws Exception {\n        ResourcePackBuilder.BuiltPack built =\n                new ResourcePackBuilder().build210(tempDir.resolve(\"210\"));",
     "void build110WritesPackFormat2AndValidZip() throws Exception {\n        ResourcePackBuilder.BuiltPack built =\n                new ResourcePackBuilder().build110(tempDir.resolve(\"110\"));", 1),
    ('assertEquals("detergentmc-pack-1.10.2.zip", built.file().getFileName().toString());',
     'assertEquals("detergentmc-pack-1.9.4.zip", built.file().getFileName().toString());', 1),
    ("        // 説明文は 1.10.2 用（335／340 パックと中身を分ける）。\n        assertTrue(entries.get(\"pack.mcmeta\").contains(\"(1.10.2)\"),",
     "        // 説明文は 1.9.4 用（210／335／340 パックと中身を分ける）。\n        assertTrue(entries.get(\"pack.mcmeta\").contains(\"(1.9.4)\"),", 1),
])

gen("ResourcePackMode210Test.java", "ResourcePackMode110Test.java", [
    ("/** 210（1.10.2）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */\nclass ResourcePackMode210Test {",
     "/** 110（1.9.4）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */\nclass ResourcePackMode110Test {", 1),
    ("""    private static final String URL_315 =""",
     """    private static final String URL_110 =
            "https://example.invalid/packs/detergentmc-pack-1.9.4.zip";
    private static final String VALID_SHA1_110 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";
    private static final String URL_315 =""", 1),
    ("    /** 最大オーバーロード：210 ペアまで渡す（315 はフォールバック先として設定）。 */\n    private static ResourcePackMode.Decision decideFull(String url210, String sha1210) {",
     "    /** 最大オーバーロード：110 ペアまで渡す（210・315 はフォールバック先として設定）。 */\n    private static ResourcePackMode.Decision decideFull(String url110, String sha1110) {", 1),
    ("                url210, sha1210,\n                false, \"\");",
     "                // 210（フォールバック先として設定）\n                URL_210, VALID_SHA1_210,\n                url110, sha1110,\n                false, \"\");", 1),
    ("""    void valid210UrlAndSha1YieldPack210() {
        ResourcePackMode.Decision decision = decideFull(URL_210, VALID_SHA1_210);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack210());
        assertEquals(URL_210, decision.pack210().url());
        assertEquals(VALID_SHA1_210, decision.pack210().sha1());
        // packForProtocol は 210 クライアントに 210 パックを返す。
        assertEquals(URL_210, decision.packForProtocol(210).url());
        assertEquals(URL_315, decision.packForProtocol(315).url());
    }""",
     """    void valid110UrlAndSha1YieldPack110() {
        ResourcePackMode.Decision decision = decideFull(URL_110, VALID_SHA1_110);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack110());
        assertEquals(URL_110, decision.pack110().url());
        assertEquals(VALID_SHA1_110, decision.pack110().sha1());
        // packForProtocol は 110 クライアントに 110 パックを返す。
        assertEquals(URL_110, decision.packForProtocol(110).url());
        assertEquals(URL_210, decision.packForProtocol(210).url());
    }""", 1),
    ("""    void empty210UrlFallsBackTo315Pack() {
        ResourcePackMode.Decision decision = decideFull("", "");
        assertNull(decision.pack210());
        assertEquals(URL_315, decision.packForProtocol(210).url());
    }""",
     """    void empty110UrlFallsBackTo210Pack() {
        ResourcePackMode.Decision decision = decideFull("", "");
        assertNull(decision.pack110());
        assertEquals(URL_210, decision.packForProtocol(110).url());
    }""", 1),
    ("""    void invalid210Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(URL_210, "not-a-sha1");
        assertNull(decision.pack210());
        assertTrue(decision.problem() != null && decision.problem().contains("210"),
                String.valueOf(decision.problem()));
        assertEquals(URL_315, decision.packForProtocol(210).url());
    }""",
     """    void invalid110Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(URL_110, "not-a-sha1");
        assertNull(decision.pack110());
        assertTrue(decision.problem() != null && decision.problem().contains("110"),
                String.valueOf(decision.problem()));
        assertEquals(URL_210, decision.packForProtocol(110).url());
    }""", 1),
    ("""    void nonHttps210UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision =
                decideFull("http://example.invalid/pack.zip", VALID_SHA1_210);
        assertNull(decision.pack210());
        assertTrue(decision.problem() != null && decision.problem().contains("210"),
                String.valueOf(decision.problem()));
    }""",
     """    void nonHttps110UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision =
                decideFull("http://example.invalid/pack.zip", VALID_SHA1_110);
        assertNull(decision.pack110());
        assertTrue(decision.problem() != null && decision.problem().contains("110"),
                String.valueOf(decision.problem()));
    }""", 1),
])

gen("ResourcePackServer210Test.java", "ResourcePackServer110Test.java", [
    ("/** 210（1.10.2）パックの配信（ResourcePackServer の35パック構成と210欠落時の代替）。 */\nclass ResourcePackServer210Test {",
     "/** 110（1.9.4）パックの配信（ResourcePackServer の37パック構成と110欠落時の代替）。 */\nclass ResourcePackServer110Test {", 1),
    ("    private ResourcePackServer thirtyFivePackServer() throws Exception {",
     "    private ResourcePackServer thirtySevenPackServer() throws Exception {", 1),
    ("                builder.build210(tempDir.resolve(\"210\")),\n                builder.build315(tempDir.resolve(\"315\")),",
     "                builder.build210(tempDir.resolve(\"210\")),\n                builder.build110(tempDir.resolve(\"110\")),\n                builder.build315(tempDir.resolve(\"315\")),", 1),
    ("""    void pack210IsAvailable() throws Exception {
        ResourcePackServer server = thirtyFivePackServer();
        ResourcePack pack210 = server.pack210();
        assertNotNull(pack210);
        assertTrue(pack210.url().endsWith(ResourcePackServer.PACK_PATH_210), pack210.url());
        assertEquals(pack210.url(), server.url210());
        assertNotNull(server.pack315());
    }""",
     """    void pack110IsAvailable() throws Exception {
        ResourcePackServer server = thirtySevenPackServer();
        ResourcePack pack110 = server.pack110();
        assertNotNull(pack110);
        assertTrue(pack110.url().endsWith(ResourcePackServer.PACK_PATH_110), pack110.url());
        assertEquals(pack110.url(), server.url110());
        assertNotNull(server.pack210());
    }""", 1),
    ("""    void packForProtocol210ReturnsPack210() throws Exception {
        ResourcePackServer server = thirtyFivePackServer();
        assertEquals(server.pack210().url(), server.packForProtocol(210).url());
        assertEquals(server.pack315().url(), server.packForProtocol(315).url());
    }""",
     """    void packForProtocol110ReturnsPack110() throws Exception {
        ResourcePackServer server = thirtySevenPackServer();
        assertEquals(server.pack110().url(), server.packForProtocol(110).url());
        assertEquals(server.pack210().url(), server.packForProtocol(210).url());
    }""", 1),
    ("""    void packForProtocol210FallsBackToPack315When210Missing() throws Exception {
        // 210 パックを持たない構成（35パック構成）では 315 パックで
        // 代替する（pack_format 3・旧レイアウトは同一で、説明文だけが
        // 1.11 用になる）。""",
     """    void packForProtocol110FallsBackToPack210When110Missing() throws Exception {
        // 110 パックを持たない構成（110無し構成）では 210 パックで
        // 代替する（同じ pack_format 2・旧レイアウトは同一で、説明文
        // だけが 1.10.2 用になる）。""", 1),
    ("        assertEquals(server.pack315().url(), server.packForProtocol(210).url());",
     "        assertEquals(server.pack210().url(), server.packForProtocol(110).url());", 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("完了")
