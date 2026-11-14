#!/usr/bin/env python3
"""772の単体テストを773の単体テストへ写す（小さい5本）。

機械シフト（「新しすぎる」773→774を先に、次に772→773、771→772、
770→771、1.21.7／1.21.8→1.21.9／1.21.10、1.21.7→1.21.9、
1.21.6→1.21.7、1.21.5→1.21.6、1_21_7→1_21_9、1_21_6→1_21_7、
1217→1219、1216→1217）のあと、ファイルごとの FIXES（件数・引数の数・
比較対象の版）を当てる。各 FIX はファイル中にちょうど1回だけ現れること。

版文字列の置換は大きい方から小さい方へ行う（1.21.5→1.21.6 を
1.21.6→1.21.7 のあとに置くと、作ったばかりの 1.21.6 がさらに
1.21.7 になってしまうため）。数字も同じ（773→774、772→773、771→772、
770→771 の順）。

ClientJoinFlow773Test は機械シフトだけで正しくなる（772テストの
「Like771」等の名前がそのまま773の形になる）ため FIXES は無い。
Play／Chunk／ProxySession／RegistryRemapper／JavaBackendConnection の
773テストは手書き（772⇔773の対に読み替える必要があるため）。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src" / "test" / "java" / "io" / "detergentmc" / "protocol"


def shift(text):
    text = text.replace("773", "774")
    text = text.replace("772", "773")
    text = text.replace("771", "772")
    text = text.replace("770", "771")
    text = text.replace("1.21.7／1.21.8", "1.21.9／1.21.10")
    text = text.replace("1.21.7", "1.21.9")
    text = text.replace("1.21.6", "1.21.7")
    text = text.replace("1.21.5", "1.21.6")
    text = text.replace("1_21_7", "1_21_9")
    text = text.replace("1_21_6", "1_21_7")
    text = text.replace("1217", "1219")
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
# ClientAdapter773Test
# ------------------------------------------------------------------
write_shifted("ClientAdapter772Test.java", "ClientAdapter773Test.java", [
    ("server list +770", "for (int server : new int[]{763, 764, 765, 766, 767, 768, 769, 771, 772, 773}) {",
     "for (int server : new int[]{763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773}) {"),
    ("new packets 8 / id changes 108", """        // 新規パケットは無く、ID変更も無い（247件すべて772と同一）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("無い")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("247件")),
                differences.toString());
        // lava_chicken（ジュークボックスの歌）の挿入。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("lava_chicken")),
                differences.toString());""",
     """        // 新規パケット8件、ID変更108件（デバッグ系の挿入で0x1A以降が+4、0x27以降が+5）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("8件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("108件")),
                differences.toString());
        // 銅ゴーレム（エンティティ）の追加。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("銅ゴーレム")),
                differences.toString());"""),
])

# ------------------------------------------------------------------
# ClientJoinFlow773Test
# ------------------------------------------------------------------
write_shifted("ClientJoinFlow772Test.java", "ClientJoinFlow773Test.java", [])

# ------------------------------------------------------------------
# ResourcePackBuilder773Test
# ------------------------------------------------------------------
write_shifted("ResourcePackBuilder772Test.java", "ResourcePackBuilder773Test.java", [
    ("javadoc pack_format 64->69", "773（1.21.9）用のリソースパック（pack_format 64）",
     "773（1.21.9）用のリソースパック（pack_format 69）"),
    ("method name Format64->69", "builds773PackWithFormat64AndSameDemoAssets",
     "builds773PackWithFormat69AndSameDemoAssets"),
    ("ALL: assertEquals 64->69", "assertEquals(64, ", "assertEquals(69, "),
    ("ALL: mcmeta positive 64->69", 'mcmeta.contains("\\"pack_format\\":64")',
     'mcmeta.contains("\\"pack_format\\":69")'),
    ("comment format 69", "pack.mcmeta: 1.21.9 format 64 (not 15, not 18, not 22,",
     "pack.mcmeta: 1.21.9 format 69 (not 15, not 18, not 22,"),
    ("comment exclusion +64", "not 32, not 34, not 42, not 46). 15 is 1.20–1.20.1, 18 is 1.20.2, 22 is 1.20.3–1.20.4,",
     "not 32, not 34, not 42, not 46, not 64). 15 is 1.20–1.20.1, 18 is 1.20.2, 22 is 1.20.3–1.20.4,"),
    ("comment 64 is 1.21.7, 69 is 1.21.9", "and 64 is 1.21.9 (Minecraft Wiki pack_format).",
     "and 64 is 1.21.7, 69 is 1.21.9 (Minecraft Wiki pack_format)."),
    ("not-64 negative check", """        assertTrue(!mcmeta.contains("\\"pack_format\\":55"), mcmeta);
        assertTrue(!mcmeta.contains("\\"pack_format\\":63"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);""",
     """        assertTrue(!mcmeta.contains("\\"pack_format\\":55"), mcmeta);
        assertTrue(!mcmeta.contains("\\"pack_format\\":63"), mcmeta);
        assertTrue(!mcmeta.contains("\\"pack_format\\":64"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);"""),
    ("writePack name Format64->69", "writePack773WritesFormat64",
     "writePack773WritesFormat69"),
])

# ------------------------------------------------------------------
# ResourcePackMode773Test
# ------------------------------------------------------------------
write_shifted("ResourcePackMode772Test.java", "ResourcePackMode773Test.java", [
    ("773 constants add URL_770", """    private static final String URL_771 =
            "https://example.invalid/packs/detergentmc-pack-1.21.6.zip";
    private static final String VALID_SHA1_771 =
            "dddddddddddddddddddddddddddddddddddddddd";""",
     """    private static final String URL_771 =
            "https://example.invalid/packs/detergentmc-pack-1.21.6.zip";
    private static final String VALID_SHA1_771 =
            "dddddddddddddddddddddddddddddddddddddddd";
    private static final String URL_770 =
            "https://example.invalid/packs/detergentmc-pack-1.21.5.zip";
    private static final String VALID_SHA1_770 =
            "cccccccccccccccccccccccccccccccccccccccc";"""),
    ("valid773 decide 24-arg", """                URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772, URL_773, VALID_SHA1_773,
                false, "");""",
     """                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                URL_773, VALID_SHA1_773, false, "");"""),
    ("empty773 decide 24-arg", """                URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772, "", "", false, "");""",
     """                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772, "", "", false, "");"""),
    ("invalid773 decide 24-arg", """                URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772, URL_773, "not-a-sha1",
                false, "");""",
     """                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                URL_773, "not-a-sha1", false, "");"""),
    ("nonHttps773 decide 24-arg", """                URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                "http://example.invalid/packs/detergentmc-pack-1.21.9.zip",
                VALID_SHA1_773, false, "");""",
     """                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                "http://example.invalid/packs/detergentmc-pack-1.21.9.zip",
                VALID_SHA1_773, false, "");"""),
    ("twentyTwoArg decide", """    @Test
    void twentyArgDecideWithout773StillWorks() {
        // The older 20-argument decide (without url-773 / sha1-773)
        // keeps working and yields no 773 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack771());
        assertNotNull(decision.pack772());
        assertNull(decision.pack773());
        assertEquals(URL, decision.packForProtocol(773).url());""",
     """    @Test
    void twentyTwoArgDecideWithout773StillWorks() {
        // The older 22-argument decide (without url-773 / sha1-773)
        // keeps working and yields no 773 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack770());
        assertNotNull(decision.pack771());
        assertNotNull(decision.pack772());
        assertNull(decision.pack773());
        assertEquals(URL, decision.packForProtocol(773).url());"""),
])

# ------------------------------------------------------------------
# ResourcePackServer773Test
# ------------------------------------------------------------------
write_shifted("ResourcePackServer772Test.java", "ResourcePackServer773Test.java", [
    ("eleven packs name", "servesAllTenPacksOnTheSamePort",
     "servesAllElevenPacksOnTheSamePort"),
    ("build773", """        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackBuilder.BuiltPack built772 = builder.build772(tempDir.resolve("772"));
        ResourcePackBuilder.BuiltPack built773 = builder.build773(tempDir.resolve("773"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built771, built772,
                built773, 0, false);""",
     """        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built770 = builder.build770(tempDir.resolve("770"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackBuilder.BuiltPack built772 = builder.build772(tempDir.resolve("772"));
        ResourcePackBuilder.BuiltPack built773 = builder.build773(tempDir.resolve("773"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built770, built771,
                built772, built773, 0, false);"""),
    ("pack772 comment format 64", "The 1.21.7 pack (pack_format 63) is served too.",
     "The 1.21.7 pack (pack_format 64) is served too."),
    ("pack773 comment format 69", "The 1.21.9 pack (pack_format 64) serves 773 clients",
     "The 1.21.9 pack (pack_format 69) serves 773 clients"),
    ("eleven share one port", "            // All ten share one port, but have different paths and SHA-1s.",
     "            // All eleven share one port, but have different paths and SHA-1s."),
    ("tenPackServerHasNoPack773", """    @Test
    void ninePackServerHasNoPack773() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackBuilder.BuiltPack built768 = builder.build768(tempDir.resolve("768"));
        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackBuilder.BuiltPack built772 = builder.build772(tempDir.resolve("772"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built771,
                built772, 0, false);""",
     """    @Test
    void tenPackServerHasNoPack773() throws Exception {
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
        ResourcePackBuilder.BuiltPack built772 = builder.build772(tempDir.resolve("772"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built770,
                built771, built772, 0, false);"""),
    ("noPack773 comment", """            // 773パックが無ければ packForProtocol(773) は null になる
            // （8パックのときの packForProtocol(772) と同じ契約。呼び出し側が
            // 本体パックへフォールバックする）。""",
     """            // 773パックが無ければ packForProtocol(773) は null になる
            // （9パックのときの packForProtocol(772) と同じ契約。呼び出し側が
            // 本体パックへフォールバックする）。"""),
])

print("773テスト（小さい5本）を生成した")
