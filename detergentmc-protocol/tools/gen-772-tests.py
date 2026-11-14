#!/usr/bin/env python3
"""771の単体テストを772の単体テストへ写す（小さい6本）。

機械シフト（「新しすぎる」772→773を先に、次に771→772、770→771、
1.21.6→1.21.7、1_21_6→1_21_7、1216→1217）のあと、
ファイルごとの FIXES（件数・引数の数・比較対象の版）を当てる。
各 FIX はファイル中にちょうど1回だけ現れること。

ClientJoinFlow772Test は機械シフトだけで正しくなる（771テストの
「Like770」等の名前がそのまま772の形になる）ため FIXES は無い。
Play／Chunk／ProxySession／RegistryRemapper／JavaBackendConnection の
772テストは手書き（771⇔772の対に読み替える必要があるため）。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src" / "test" / "java" / "io" / "detergentmc" / "protocol"


def shift(text):
    text = text.replace("772", "773")
    text = text.replace("771", "772")
    text = text.replace("770", "771")
    text = text.replace("1.21.6", "1.21.7")
    text = text.replace("1_21_6", "1_21_7")
    text = text.replace("1216", "1217")
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
# ClientAdapter772Test
# ------------------------------------------------------------------
write_shifted("ClientAdapter771Test.java", "ClientAdapter772Test.java", [
    ("server list +770", "for (int server : new int[]{763, 764, 765, 766, 767, 768, 769, 771, 772}) {",
     "for (int server : new int[]{763, 764, 765, 766, 767, 768, 769, 770, 771, 772}) {"),
    ("no new packets", """        // 新規パケット3件、既存IDの変更122件。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("8件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("58件")),
                differences.toString());
        // tears（ジュークボックスの歌）の挿入。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("tears")),
                differences.toString());""",
     """        // 新規パケットは無く、ID変更も無い（247件すべて771と同一）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("無い")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("247件")),
                differences.toString());
        // lava_chicken（ジュークボックスの歌）の挿入。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("lava_chicken")),
                differences.toString());"""),
])

# ------------------------------------------------------------------
# ClientJoinFlow772Test
# ------------------------------------------------------------------
write_shifted("ClientJoinFlow771Test.java", "ClientJoinFlow772Test.java", [])

# ------------------------------------------------------------------
# ResourcePackBuilder772Test
# ------------------------------------------------------------------
write_shifted("ResourcePackBuilder771Test.java", "ResourcePackBuilder772Test.java", [
    ("ALL:pack_format 63->64", "63", "64"),
    ("not-63 negative check", """        assertTrue(!mcmeta.contains("\\"pack_format\\":55"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);""",
     """        assertTrue(!mcmeta.contains("\\"pack_format\\":55"), mcmeta);
        assertTrue(!mcmeta.contains("\\"pack_format\\":63"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);"""),
])

# ------------------------------------------------------------------
# ResourcePackMode772Test
# ------------------------------------------------------------------
write_shifted("ResourcePackMode771Test.java", "ResourcePackMode772Test.java", [
    ("772 constants", """    private static final String URL_772 =
            "https://example.invalid/packs/detergentmc-pack-1.21.7.zip";
    private static final String VALID_SHA1_772 =
            "ffffffffffffffffffffffffffffffffffffffff";
    private static final String URL_771 =
            "https://example.invalid/packs/detergentmc-pack-1.21.5.zip";
    private static final String VALID_SHA1_771 =
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";""",
     """    private static final String URL_772 =
            "https://example.invalid/packs/detergentmc-pack-1.21.7.zip";
    private static final String VALID_SHA1_772 =
            "ffffffffffffffffffffffffffffffffffffffff";
    private static final String URL_771 =
            "https://example.invalid/packs/detergentmc-pack-1.21.6.zip";
    private static final String VALID_SHA1_771 =
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String URL_770 =
            "https://example.invalid/packs/detergentmc-pack-1.21.5.zip";
    private static final String VALID_SHA1_770 =
            "dddddddddddddddddddddddddddddddddddddddd";"""),
    ("valid772 decide 22-arg", """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772, false, "");""",
     """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                false, "");"""),
    ("valid772 pack771 asserts", """        assertNotNull(decision.pack771());
        assertEquals(URL_771, decision.pack771().url());
        assertEquals(VALID_SHA1_771, decision.pack771().sha1());
        assertNotNull(decision.pack772());
        assertEquals(URL_772, decision.pack772().url());
        assertEquals(VALID_SHA1_772, decision.pack772().sha1());
        assertFalse(decision.pack772().required());""",
     """        assertNotNull(decision.pack770());
        assertEquals(URL_770, decision.pack770().url());
        assertEquals(VALID_SHA1_770, decision.pack770().sha1());
        assertNotNull(decision.pack771());
        assertEquals(URL_771, decision.pack771().url());
        assertEquals(VALID_SHA1_771, decision.pack771().sha1());
        assertNotNull(decision.pack772());
        assertEquals(URL_772, decision.pack772().url());
        assertEquals(VALID_SHA1_772, decision.pack772().sha1());
        assertFalse(decision.pack772().required());"""),
    ("empty772 decide 22-arg", """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_771, VALID_SHA1_771, "", "", false, "");""",
     """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, "", "", false, "");"""),
    ("empty772 fallback comment", """        assertNotNull(decision.pack771());
        assertNull(decision.pack772());
        // Without a 772 pack, 772 clients fall back to the main pack.
        assertEquals(URL, decision.packForProtocol(772).url());""",
     """        assertNotNull(decision.pack770());
        assertNotNull(decision.pack771());
        assertNull(decision.pack772());
        // Without a 772 pack, 772 clients fall back to the main pack.
        assertEquals(URL, decision.packForProtocol(772).url());"""),
    ("invalid772 decide 22-arg", """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_771, VALID_SHA1_771, URL_772, "not-a-sha1", false, "");""",
     """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, "not-a-sha1",
                false, "");"""),
    ("nonHttps772 decide 22-arg", """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_771, VALID_SHA1_771,
                "http://example.invalid/packs/detergentmc-pack-1.21.7.zip",
                VALID_SHA1_772, false, "");""",
     """        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771,
                "http://example.invalid/packs/detergentmc-pack-1.21.7.zip",
                VALID_SHA1_772, false, "");"""),
    ("twentyArg decide", """    @Test
    void eighteenArgDecideWithout772StillWorks() {
        // The older 18-argument decide (without url-772 / sha1-772)
        // keeps working and yields no 772 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_771, VALID_SHA1_771, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack771());
        assertNull(decision.pack772());
        assertEquals(URL, decision.packForProtocol(772).url());""",
     """    @Test
    void twentyArgDecideWithout772StillWorks() {
        // The older 20-argument decide (without url-772 / sha1-772)
        // keeps working and yields no 772 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack770());
        assertNotNull(decision.pack771());
        assertNull(decision.pack772());
        assertEquals(URL, decision.packForProtocol(772).url());"""),
])

# ------------------------------------------------------------------
# ResourcePackServer772Test
# ------------------------------------------------------------------
write_shifted("ResourcePackServer771Test.java", "ResourcePackServer772Test.java", [
    ("ten packs name", "servesAllNinePacksOnTheSamePort",
     "servesAllTenPacksOnTheSamePort"),
    ("build772", """        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackBuilder.BuiltPack built772 = builder.build772(tempDir.resolve("772"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built771, built772,
                0, false);""",
     """        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built770 = builder.build770(tempDir.resolve("770"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackBuilder.BuiltPack built772 = builder.build772(tempDir.resolve("772"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built770, built771,
                built772, 0, false);"""),
    ("pack771 comment format 63", "The 1.21.5 pack (pack_format 55) is served too.",
     "The 1.21.6 pack (pack_format 63) is served too."),
    ("pack772 comment format 64", "The 1.21.7 pack (pack_format 63) serves 772 clients",
     "The 1.21.7 pack (pack_format 64) serves 772 clients"),
    ("ten share one port", "            // All nine share one port, but have different paths and SHA-1s.",
     "            // All ten share one port, but have different paths and SHA-1s."),
    ("ninePackServerHasNoPack772", """    @Test
    void eightPackServerHasNoPack772() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackBuilder.BuiltPack built768 = builder.build768(tempDir.resolve("768"));
        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built771, 0, false);""",
     """    @Test
    void ninePackServerHasNoPack772() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackBuilder.BuiltPack built768 = builder.build768(tempDir.resolve("768"));
        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built770 = builder.build770(tempDir.resolve("770"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built770,
                built771, 0, false);"""),
    ("noPack772 comment", """            // 772パックが無ければ packForProtocol(772) は null になる
            // （7パックのときの packForProtocol(771) と同じ契約。呼び出し側が
            // 本体パックへフォールバックする）。""",
     """            // 772パックが無ければ packForProtocol(772) は null になる
            // （8パックのときの packForProtocol(771) と同じ契約。呼び出し側が
            // 本体パックへフォールバックする）。"""),
])

print("772テスト（小さい6本）を生成した")
