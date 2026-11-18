#!/usr/bin/env python3
"""ResourcePackServer.java に 776（26.2）パックを追加する。

775 の追加（patch-resourcepackserver-775.py）と同じパターンで、以下の変更を行う。

* PACK_PATH_776 / pack776File・sha1776 フィールド / pack776Bytes
* フル Path コンストラクタ 5 つ（main・762・761・760・759）に 776 ペアを追加
  （シグネチャ・sha1 検証・フィールド代入）。各コンストラクタの javadoc も更新。
* main チェーンに "without 776" オーバーロードを新設し、"without 775" の
  委譲 null を 2 個から 4 個に増やす（"without 774" は arity 経由で新しい
  "without 776" に繋がるため変更不要）。
* BuiltPack prompt-key コンストラクタ（through 762/761/760）の Path 委譲に
  776 用 null を追加する。through 774（"twelve"）は arity 経由で新しい
  "without 776" に繋がるため変更不要。
* BuiltPack "seventeen"（through 759、DetergentServer が使うもの）に built776
  を追加（シグネチャ・委譲・javadoc、prompt-key 有り／無しの両方）。
* start() での読み込み・配信、stop() での破棄、url776()/pack776()、
  packForProtocol() の V26_2 分岐、クラス javadoc の 26.2 節。

委譲は arity（引数の個数）で解決されるため、位置ずれの罠に注意すること。
各置換は出現回数を検証してから適用する（想定と違う回数なら失敗する）。
"""
import re
import sys

PATH = "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackServer.java"

with open(PATH, encoding="utf-8") as f:
    src = f.read()


def replace_count(old, new, expected):
    global src
    n = src.count(old)
    assert n == expected, f"expected {expected} occurrences, got {n}: {old[:90]!r}"
    src = src.replace(old, new)


def replace_after(anchor, old, new, window=2500):
    """anchor の直後 window 文字以内にある old を 1 回だけ置換する。"""
    global src
    ai = src.find(anchor)
    assert ai >= 0, f"anchor not found: {anchor[:80]!r}"
    start = ai + len(anchor)
    seg = src[start:start + window]
    n = seg.count(old)
    assert n == 1, f"expected 1 occurrence after anchor, got {n}: {old[:80]!r} (anchor {anchor[:60]!r})"
    src = src[:start] + seg.replace(old, new, 1) + src[start + window:]


# ---- クラス javadoc: 26.2 節を追加 ----
replace_count(
    """ * {@link #pack775()} returns the {@link ResourcePack} the proxy offers
 * to 775 clients (pack_format 84). All thirteen packs share the same port.
 */""",
    """ * {@link #pack775()} returns the {@link ResourcePack} the proxy offers
 * to 775 clients (pack_format 84). All thirteen packs share the same port.
 *
 * <h2>26.2 variant</h2>
 *
 * <p>When a fourteenth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_776} and
 * {@link #pack776()} returns the {@link ResourcePack} the proxy offers
 * to 776 clients (pack_format 88). All fourteen packs share the same port.
 */""",
    1)

# ---- 定数・フィールド ----
replace_count(
    """    /** Path of the 26.1 (pack_format 84) variant, when one is served. */
    public static final String PACK_PATH_775 = "/detergentmc-pack-26.1.zip";""",
    """    /** Path of the 26.1 (pack_format 84) variant, when one is served. */
    public static final String PACK_PATH_775 = "/detergentmc-pack-26.1.zip";

    /** Path of the 26.2 (pack_format 88) variant, when one is served. */
    public static final String PACK_PATH_776 = "/detergentmc-pack-26.2.zip";""",
    1)
replace_count(
    """    private final Path pack775File;
    private final String sha1775;""",
    """    private final Path pack775File;
    private final String sha1775;
    private final Path pack776File;
    private final String sha1776;""",
    1)
replace_count(
    "    private byte[] pack775Bytes;",
    "    private byte[] pack775Bytes;\n    private byte[] pack776Bytes;",
    1)

# ---- フル Path コンストラクタ 5 つ：シグネチャ・検証・代入（各 5 回） ----
replace_count(
    """Path pack775File, String sha1775,
            int port, boolean required, String promptKey) {""",
    """Path pack775File, String sha1775, Path pack776File, String sha1776,
            int port, boolean required, String promptKey) {""",
    5)
replace_count(
    """        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }""",
    """        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (pack776File != null && (sha1776 == null || sha1776.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.2 pack is required");
        }""",
    5)
replace_count(
    """        this.pack775File = pack775File;
        this.sha1775 = sha1775;""",
    """        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.pack776File = pack776File;
        this.sha1776 = sha1776;""",
    5)

# ---- フル Path コンストラクタの javadoc ----
replace_count(
    """     * @param pack775File 26.1 variant zip file, or {@code null}
     *                    to serve no 26.1 variant (one pack serves
     *                    26.1 / 26.1.1 / 26.1.2: protocol 775)
     * @param sha1775     lower-case hex SHA-1 of {@code pack775File}
     *                    (required when {@code pack775File} is given)
     */""",
    """     * @param pack775File 26.1 variant zip file, or {@code null}
     *                    to serve no 26.1 variant (one pack serves
     *                    26.1 / 26.1.1 / 26.1.2: protocol 775)
     * @param sha1775     lower-case hex SHA-1 of {@code pack775File}
     *                    (required when {@code pack775File} is given)
     * @param pack776File 26.2 variant zip file, or {@code null}
     *                    to serve no 26.2 variant (26.2 has no hotfix
     *                    releases: protocol 776)
     * @param sha1776     lower-case hex SHA-1 of {@code pack776File}
     *                    (required when {@code pack776File} is given)
     */""",
    1)
replace_count("alongside the 1.20.1–26.1 variants.",
              "alongside the 1.20.1–26.2 variants.", 1)
replace_count("alongside the 1.19.1–26.1 variants.",
              "alongside the 1.19.1–26.2 variants.", 1)
replace_count("alongside the 1.19.3–26.1 variants.",
              "alongside the 1.19.3–26.2 variants.", 1)
replace_count("alongside the 1.19.4–26.1 variants.",
              "alongside the 1.19.4–26.2 variants.", 1)

# ---- main チェーン： "without 775" の委譲 null を 2 個から 4 個に ----
replace_count(
    """                pack772File, sha1772, pack773File, sha1773, pack774File, sha1774, null, null,
                port, required, promptKey);""",
    """                pack772File, sha1772, pack773File, sha1773, pack774File, sha1774, null, null, null, null,
                port, required, promptKey);""",
    1)

# ---- main チェーン： "without 776" オーバーロードを新設 ----
replace_count(
    """                pack772File, sha1772, pack773File, sha1773, pack774File, sha1774, null, null, null, null,
                port, required, promptKey);
    }

    /**
     * @param pack769File 1.21.4 variant zip file, or {@code null}""",
    """                pack772File, sha1772, pack773File, sha1773, pack774File, sha1774, null, null, null, null,
                port, required, promptKey);
    }

    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 26.2 variant: no 776 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774,
            Path pack775File, String sha1775,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, pack770File, sha1770, pack771File, sha1771,
                pack772File, sha1772, pack773File, sha1773, pack774File, sha1774, pack775File, sha1775, null, null,
                port, required, promptKey);
    }

    /**
     * @param pack769File 1.21.4 variant zip file, or {@code null}""",
    1)

# ---- BuiltPack prompt-key コンストラクタ（through 762/761/760）：Path 委譲に null を追加 ----
# "thirteen"（through 762）、"fourteen"（through 761）、"fifteen"（through 760）の
# 3 つだけ。through 774（"twelve"）は arity 経由で新しい "without 776" に繋がる
# ため変更不要。through 759（"seventeen"→"eighteen"）は built776 を渡す（後で別途処理）。
for lead in ("built762", "built761", "built760"):
    replace_after(
        f"this(built.file(), built.sha1(), {lead}.file(), {lead}.sha1(),",
        """                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                null, null,
                port, required, promptKey);""",
        """                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                null, null, null, null,
                port, required, promptKey);""")

# ---- BuiltPack "seventeen" prompt-key：built776 を追加 ----
replace_after(
    "/** Convenience: serve all seventeen packs, with an explicit prompt key. */",
    """            ResourcePackBuilder.BuiltPack built775,
            int port, boolean required, String promptKey) {""",
    """            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {""")
replace_after(
    "/** Convenience: serve all seventeen packs, with an explicit prompt key. */",
    """                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                port, required, promptKey);""",
    """                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);""")
replace_count("/** Convenience: serve all seventeen packs, with an explicit prompt key. */",
              "/** Convenience: serve all eighteen packs, with an explicit prompt key. */", 1)

# ---- BuiltPack "seventeen" no-promptKey：built776 を追加 ----
replace_after(
    "1.21.7／1.21.8, 1.21.9／1.21.10, 1.21.11 and 26.1 variants, built by {@link ResourcePackBuilder}.\n     */\n    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n            ResourcePackBuilder.BuiltPack built759,",
    """            ResourcePackBuilder.BuiltPack built775,
            int port, boolean required) {""",
    """            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required) {""")
replace_after(
    "1.21.7／1.21.8, 1.21.9／1.21.10, 1.21.11 and 26.1 variants, built by {@link ResourcePackBuilder}.\n     */\n    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n            ResourcePackBuilder.BuiltPack built759,",
    "built770, built771, built772, built773, built774, built775, port, required, DEFAULT_PROMPT_KEY);",
    "built770, built771, built772, built773, built774, built775, built776, port, required, DEFAULT_PROMPT_KEY);")
replace_count(
    """     * 1.21.7／1.21.8, 1.21.9／1.21.10, 1.21.11 and 26.1 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,""",
    """     * 1.21.7／1.21.8, 1.21.9／1.21.10, 1.21.11, 26.1 and 26.2 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,""",
    1)

# ---- start()：読み込みと配信 ----
replace_count(
    """        if (pack775File != null) {
            pack775Bytes = readAndVerify(pack775File, sha1775);
        }""",
    """        if (pack775File != null) {
            pack775Bytes = readAndVerify(pack775File, sha1775);
        }
        if (pack776File != null) {
            pack776Bytes = readAndVerify(pack776File, sha1776);
        }""",
    1)
replace_count(
    """        if (pack775Bytes != null) {
            httpServer.createContext(PACK_PATH_775, exchange -> serveBytes(exchange, pack775Bytes));
        }""",
    """        if (pack775Bytes != null) {
            httpServer.createContext(PACK_PATH_775, exchange -> serveBytes(exchange, pack775Bytes));
        }
        if (pack776Bytes != null) {
            httpServer.createContext(PACK_PATH_776, exchange -> serveBytes(exchange, pack776Bytes));
        }""",
    1)

# ---- stop() ----
replace_count(
    "        pack775Bytes = null;",
    "        pack775Bytes = null;\n        pack776Bytes = null;",
    1)

# ---- url776() / pack776() アクセサ ----
replace_count(
    """    public synchronized ResourcePack pack775() {
        return pack775File == null ? null
                : new ResourcePack(url775(), sha1775, required, promptKey);
    }""",
    """    public synchronized ResourcePack pack775() {
        return pack775File == null ? null
                : new ResourcePack(url775(), sha1775, required, promptKey);
    }

    /** The download URL of the 26.2 variant, or {@code null} when not served. */
    public synchronized String url776() {
        if (pack776File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_776;
    }

    /**
     * The 26.2 {@link ResourcePack} to offer to 776 clients,
     * or {@code null} when no 26.2 variant is served.
     */
    public synchronized ResourcePack pack776() {
        return pack776File == null ? null
                : new ResourcePack(url776(), sha1776, required, promptKey);
    }""",
    1)

# ---- packForProtocol() ----
replace_count(
    """     * 775→{@link #pack775()}（pack_format 84）、""",
    """     * 775→{@link #pack775()}（pack_format 84）、
     * 776→{@link #pack776()}（pack_format 88）、""",
    1)
replace_count(
    """        if (clientProtocol == ProtocolVersion.V26_1) {
            return pack775();
        }""",
    """        if (clientProtocol == ProtocolVersion.V26_1) {
            return pack775();
        }
        if (clientProtocol == ProtocolVersion.V26_2) {
            return pack776();
        }""",
    1)

with open(PATH, "w", encoding="utf-8") as f:
    f.write(src)
print("ResourcePackServer.java に 776 を追加した")
