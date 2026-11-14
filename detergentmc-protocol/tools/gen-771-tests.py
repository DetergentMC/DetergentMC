#!/usr/bin/env python3
"""770の単体テストを771の単体テストへ写す（小さい6本）。

機械シフト（「新しすぎる」771→772を先に、次に770→771、
1.21.5→1.21.6、1_21_5→1_21_6、1215→1216）のあと、
ファイルごとの FIXES（件数・引数の数・比較対象の版）を当てる。
各 FIX はファイル中にちょうど1回だけ現れること。

ProtocolVersion771Test は LATEST_KNOWN が771のままのため
機械シフトが通用せず、手書きで別途作る。
Play／Chunk／ProxySession／RegistryRemapper の771テストも
手書き（769⇔770の対を770⇔771の対に読み替える必要があるため）。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src" / "test" / "java" / "io" / "detergentmc" / "protocol"


def shift(text):
    text = text.replace("771", "772")
    text = text.replace("770", "771")
    text = text.replace("1.21.5", "1.21.6")
    text = text.replace("1_21_5", "1_21_6")
    text = text.replace("1215", "1216")
    return text


def write_shifted(src_name, dst_name, fixes):
    src = (TEST / src_name).read_text(encoding="utf-8")
    out = shift(src)
    for label, old, new in fixes:
        if label.startswith("ALL:"):
            n = out.count(old)
            if n < 1:
                print(f"NG: {dst_name} {label}: {old[:60]!r} が 0 回",
                      file=sys.stderr)
                sys.exit(1)
            out = out.replace(old, new)
            print(f"OK: {dst_name} {label} ({n} 回)")
            continue
        n = out.count(old)
        if n != 1:
            print(f"NG: {dst_name} {label}: {old[:60]!r} が {n} 回（1回のはず）",
                  file=sys.stderr)
            sys.exit(1)
        out = out.replace(old, new)
        print(f"OK: {dst_name} {label}")
    (TEST / dst_name).write_text(out, encoding="utf-8")


# ------------------------------------------------------------------
# ClientAdapter771Test
# ------------------------------------------------------------------
write_shifted("ClientAdapter770Test.java", "ClientAdapter771Test.java", [
    ("server list +771", "for (int server : new int[]{763, 764, 765, 766, 767, 768, 769, 771}) {",
     "for (int server : new int[]{763, 764, 765, 766, 767, 768, 769, 770, 771}) {"),
    ("new packets 8", '.anyMatch(d -> d.summary().contains("3件"))',
     '.anyMatch(d -> d.summary().contains("8件"))'),
    ("changed ids 58", '.anyMatch(d -> d.summary().contains("122件"))',
     '.anyMatch(d -> d.summary().contains("58件"))'),
    ("strict bool 770/771", "// Login Success の strict bool は769・771とも無い（詳細に記載）。",
     "// Login Success の strict bool は770・771とも無い（詳細に記載）。"),
    ("tears instead of tinted_leaves", """        // tinted_leaves・firefly の追加。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("tinted_leaves")),
                differences.toString());""",
     """        // tears（ジュークボックスの歌）の挿入。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("tears")),
                differences.toString());"""),
])

# ------------------------------------------------------------------
# ClientJoinFlow771Test
# ------------------------------------------------------------------
write_shifted("ClientJoinFlow770Test.java", "ClientJoinFlow771Test.java", [
    ("stages like 770", "void stagesFor771IncludeConfigurationLike769() {",
     "void stagesFor771IncludeConfigurationLike770() {"),
    ("stages compare 770", "assertEquals(new ClientJoinFlow(769).stages(), flow.stages());",
     "assertEquals(new ClientJoinFlow(770).stages(), flow.stages());"),
    ("stages comment 770", "// 769と段階の順番は同じ（設定フェーズは769と同形）。",
     "// 770と段階の順番は同じ（設定フェーズは770と同形）。"),
])

# ------------------------------------------------------------------
# ResourcePackBuilder771Test
# ------------------------------------------------------------------
write_shifted("ResourcePackBuilder770Test.java", "ResourcePackBuilder771Test.java", [
    ("ALL:pack_format 55->63", "55", "63"),
    ("not-55 negative check", """        assertTrue(!mcmeta.contains("\\"pack_format\\":46"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);""",
     """        assertTrue(!mcmeta.contains("\\"pack_format\\":46"), mcmeta);
        assertTrue(!mcmeta.contains("\\"pack_format\\":55"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);"""),
    ("compare 770 vs 771", """    @Test
    void pack771DiffersFromPack769OnlyInMcmeta() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack pack769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack pack771 = builder.build771(tempDir.resolve("771"));
        // Different files and different SHA-1s (the mcmeta differs).
        assertNotEquals(pack769.sha1(), pack771.sha1());
        // Rebuilding the 771 pack is deterministic, too.
        ResourcePackBuilder.BuiltPack again = builder.build771(tempDir.resolve("771b"));
        assertEquals(pack771.sha1(), again.sha1());
    }""",
     """    @Test
    void pack771DiffersFromPack770OnlyInMcmeta() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack pack770 = builder.build770(tempDir.resolve("770"));
        ResourcePackBuilder.BuiltPack pack771 = builder.build771(tempDir.resolve("771"));
        // Different files and different SHA-1s (the mcmeta differs).
        assertNotEquals(pack770.sha1(), pack771.sha1());
        // Rebuilding the 771 pack is deterministic, too.
        ResourcePackBuilder.BuiltPack again = builder.build771(tempDir.resolve("771b"));
        assertEquals(pack771.sha1(), again.sha1());
    }"""),
])

# ------------------------------------------------------------------
# ResourcePackMode771Test
# ------------------------------------------------------------------
write_shifted("ResourcePackMode770Test.java", "ResourcePackMode771Test.java", [
    ("770 constants", """    private static final String URL_771 =
            "https://example.invalid/packs/detergentmc-pack-1.21.6.zip";
    private static final String VALID_SHA1_771 =
            "ffffffffffffffffffffffffffffffffffffffff";""",
     """    private static final String URL_771 =
            "https://example.invalid/packs/detergentmc-pack-1.21.6.zip";
    private static final String VALID_SHA1_771 =
            "ffffffffffffffffffffffffffffffffffffffff";
    private static final String URL_770 =
            "https://example.invalid/packs/detergentmc-pack-1.21.5.zip";
    private static final String VALID_SHA1_770 =
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";"""),
    ("valid771 decide 20-arg", """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_771, VALID_SHA1_771, false, "");""",
     """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, false, "");"""),
    ("valid771 pack770 asserts", """        assertNull(decision.pack763());
        assertNull(decision.pack764());
        assertNull(decision.pack766());
        assertNull(decision.pack767());
        assertNull(decision.pack768());
        assertNull(decision.pack769());
        assertNotNull(decision.pack771());
        assertEquals(URL_771, decision.pack771().url());
        assertEquals(VALID_SHA1_771, decision.pack771().sha1());
        assertFalse(decision.pack771().required());
        // packForProtocol picks the 771 pack for 771 clients, and the
        // 765 pack for 765 clients.
        assertEquals(URL_771, decision.packForProtocol(771).url());
        assertEquals(URL, decision.packForProtocol(765).url());""",
     """        assertNull(decision.pack763());
        assertNull(decision.pack764());
        assertNull(decision.pack766());
        assertNull(decision.pack767());
        assertNull(decision.pack768());
        assertNull(decision.pack769());
        assertNotNull(decision.pack770());
        assertEquals(URL_770, decision.pack770().url());
        assertEquals(VALID_SHA1_770, decision.pack770().sha1());
        assertNotNull(decision.pack771());
        assertEquals(URL_771, decision.pack771().url());
        assertEquals(VALID_SHA1_771, decision.pack771().sha1());
        assertFalse(decision.pack771().required());
        // packForProtocol picks the 771 pack for 771 clients, the
        // 770 pack for 770 clients, and the 765 pack for 765 clients.
        assertEquals(URL_771, decision.packForProtocol(771).url());
        assertEquals(URL_770, decision.packForProtocol(770).url());
        assertEquals(URL, decision.packForProtocol(765).url());"""),
    ("empty771 decide 20-arg", """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", false, "");""",
     """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, "", "", false, "");"""),
    ("empty771 fallback comment", """        assertNull(decision.pack771());
        // Without a 771 pack, 771 clients fall back to the main pack.
        assertEquals(URL, decision.packForProtocol(771).url());""",
     """        assertNotNull(decision.pack770());
        assertNull(decision.pack771());
        // Without a 771 pack, 771 clients fall back to the main pack.
        assertEquals(URL, decision.packForProtocol(771).url());"""),
    ("invalid771 decide 20-arg", """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_771, "not-a-sha1", false, "");""",
     """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, "not-a-sha1", false, "");"""),
    ("nonHttps771 decide 20-arg", """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                "http://example.invalid/packs/detergentmc-pack-1.21.6.zip",
                VALID_SHA1_771, false, "");""",
     """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770,
                "http://example.invalid/packs/detergentmc-pack-1.21.6.zip",
                VALID_SHA1_771, false, "");"""),
    ("eighteenArg decide", """    @Test
    void sixteenArgDecideWithout771StillWorks() {
        // The older 16-argument decide (without url-771 / sha1-771)
        // keeps working and yields no 771 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack771());
        assertEquals(URL, decision.packForProtocol(771).url());""",
     """    @Test
    void eighteenArgDecideWithout771StillWorks() {
        // The older 18-argument decide (without url-771 / sha1-771)
        // keeps working and yields no 771 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack770());
        assertNull(decision.pack771());
        assertEquals(URL, decision.packForProtocol(771).url());"""),
])

# ------------------------------------------------------------------
# ResourcePackServer771Test
# ------------------------------------------------------------------
write_shifted("ResourcePackServer770Test.java", "ResourcePackServer771Test.java", [
    ("nine packs name", "servesAllEightPacksOnTheSamePort",
     "servesAllNinePacksOnTheSamePort"),
    ("build771", """        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built771, 0, false);""",
     """        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built770 = builder.build770(tempDir.resolve("770"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built770, built771,
                0, false);"""),
    ("pack771 asserts base", """            // The 1.21.6 pack (pack_format 55) serves 771 clients
            // from its own file on the same port.""",
     """            // The 1.21.5 pack (pack_format 55) is served too.
            ResourcePack pack770 = server.pack770();
            assertNotNull(pack770);
            assertEquals(built770.sha1(), pack770.sha1());
            assertTrue(pack770.url().endsWith(ResourcePackServer.PACK_PATH_770),
                    pack770.url());
            // The 1.21.6 pack (pack_format 63) serves 771 clients
            // from its own file on the same port."""),
    ("nine share one port", "            // All eight share one port, but have different paths and SHA-1s.",
     "            // All nine share one port, but have different paths and SHA-1s."),
    ("pack771 vs pack770", """            assertFalse(pack771.url().equals(pack.url()));
            assertFalse(pack771.url().equals(server.pack763().url()));""",
     """            assertFalse(pack771.url().equals(pack.url()));
            assertFalse(pack771.url().equals(pack770.url()));
            assertFalse(pack771.url().equals(server.pack763().url()));"""),
    ("pack771 sha1 vs pack770", """            assertFalse(pack771.sha1().equals(pack.sha1()));
            assertFalse(pack771.sha1().equals(server.pack763().sha1()));""",
     """            assertFalse(pack771.sha1().equals(pack.sha1()));
            assertFalse(pack771.sha1().equals(pack770.sha1()));
            assertFalse(pack771.sha1().equals(server.pack763().sha1()));"""),
    ("packForProtocol 770", """            // packForProtocol picks the 771 pack for 771 clients.
            assertEquals(pack771.sha1(), server.packForProtocol(771).sha1());
            assertEquals(built769.sha1(), server.packForProtocol(769).sha1());""",
     """            // packForProtocol picks the 771 pack for 771 clients.
            assertEquals(pack771.sha1(), server.packForProtocol(771).sha1());
            assertEquals(built770.sha1(), server.packForProtocol(770).sha1());
            assertEquals(built769.sha1(), server.packForProtocol(769).sha1());"""),
    ("eightPackServerHasNoPack771", """    @Test
    void sevenPackServerHasNoPack771() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackBuilder.BuiltPack built768 = builder.build768(tempDir.resolve("768"));
        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, 0, false);""",
     """    @Test
    void eightPackServerHasNoPack771() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackBuilder.BuiltPack built768 = builder.build768(tempDir.resolve("768"));
        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built770 = builder.build770(tempDir.resolve("770"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built770, 0, false);"""),
    ("noPack771 comment", """            // 771パックが無ければ packForProtocol(771) は null になる
            // （6パックのときの packForProtocol(769) と同じ契約。呼び出し側が
            // 本体パックへフォールバックする）。""",
     """            // 771パックが無ければ packForProtocol(771) は null になる
            // （7パックのときの packForProtocol(770) と同じ契約。呼び出し側が
            // 本体パックへフォールバックする）。"""),
])

print("771テスト（小さい6本）を生成した")
