#!/usr/bin/env python3
"""ResourcePackServer.java に 775（26.1）パックを追加する。

774 の追加（commit 0e6dba5、1077acc..0e6dba5 の ResourcePackServer.java 差分）
と同じパターンで、以下の変更を行う。

* PACK_PATH_775 / pack775File・sha1775 フィールド / pack775Bytes
* フル Path コンストラクタ 5 つ（main・762・761・760・759）に 775 ペアを追加
  （シグネチャ・sha1 検証・フィールド代入）。各コンストラクタの javadoc も更新。
* main チェーンに "without 775" オーバーロードを新設し、"without 774" の
  委譲 null を 2 個から 4 個に増やす。
* BuiltPack prompt-key コンストラクタ（through 762/761/760）の Path 委譲に
  775 用 null を追加する。through 774（"twelve"）は arity 経由で新しい
  "without 775" に繋がるため変更不要。
* BuiltPack "sixteen"（through 759、DetergentServer が使うもの）に built775
  を追加（シグネチャ・委譲・javadoc、prompt-key 有り／無しの両方）。
* start() での読み込み・配信、stop() での破棄、url775()/pack775()、
  packForProtocol() の V26_1 分岐、クラス javadoc の 26.1 節。

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


# ---- クラス javadoc: 26.1 節を追加 ----
replace_count(
    """ * {@link #pack774()} returns the {@link ResourcePack} the proxy offers
 * to 774 clients (pack_format 75). All twelve packs share the same port.
 */""",
    """ * {@link #pack774()} returns the {@link ResourcePack} the proxy offers
 * to 774 clients (pack_format 75). All twelve packs share the same port.
 *
 * <h2>26.1 variant</h2>
 *
 * <p>When a thirteenth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_775} and
 * {@link #pack775()} returns the {@link ResourcePack} the proxy offers
 * to 775 clients (pack_format 84). All thirteen packs share the same port.
 */""",
    1)

# ---- 定数・フィールド ----
replace_count(
    """    /** Path of the 1.21.11 (pack_format 75) variant, when one is served. */
    public static final String PACK_PATH_774 = "/detergentmc-pack-1.21.11.zip";""",
    """    /** Path of the 1.21.11 (pack_format 75) variant, when one is served. */
    public static final String PACK_PATH_774 = "/detergentmc-pack-1.21.11.zip";

    /** Path of the 26.1 (pack_format 84) variant, when one is served. */
    public static final String PACK_PATH_775 = "/detergentmc-pack-26.1.zip";""",
    1)
replace_count(
    """    private final Path pack774File;
    private final String sha1774;""",
    """    private final Path pack774File;
    private final String sha1774;
    private final Path pack775File;
    private final String sha1775;""",
    1)
replace_count(
    "    private byte[] pack774Bytes;",
    "    private byte[] pack774Bytes;\n    private byte[] pack775Bytes;",
    1)

# ---- フル Path コンストラクタ 5 つ：シグネチャ・検証・代入（各 5 回） ----
replace_count(
    """            Path pack773File, String sha1773, Path pack774File, String sha1774,
            int port, boolean required, String promptKey) {""",
    """            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775,
            int port, boolean required, String promptKey) {""",
    5)
replace_count(
    """        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }""",
    """        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }""",
    5)
replace_count(
    """        this.pack774File = pack774File;
        this.sha1774 = sha1774;""",
    """        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;""",
    5)

# ---- フル Path コンストラクタの javadoc ----
replace_count(
    """     * @param pack774File 1.21.11 variant zip file, or {@code null}
     *                    to serve no 1.21.11 variant
     * @param sha1774     lower-case hex SHA-1 of {@code pack774File}
     *                    (required when {@code pack774File} is given)
     */""",
    """     * @param pack774File 1.21.11 variant zip file, or {@code null}
     *                    to serve no 1.21.11 variant
     * @param sha1774     lower-case hex SHA-1 of {@code pack774File}
     *                    (required when {@code pack774File} is given)
     * @param pack775File 26.1 variant zip file, or {@code null}
     *                    to serve no 26.1 variant (one pack serves
     *                    26.1 / 26.1.1 / 26.1.2: protocol 775)
     * @param sha1775     lower-case hex SHA-1 of {@code pack775File}
     *                    (required when {@code pack775File} is given)
     */""",
    1)
replace_count("alongside the 1.20.1–1.21.11 variants.",
              "alongside the 1.20.1–26.1 variants.", 1)
replace_count("alongside the 1.19.1–1.21.11 variants.",
              "alongside the 1.19.1–26.1 variants.", 1)
replace_count("alongside the 1.19.3–1.21.11 variants.",
              "alongside the 1.19.3–26.1 variants.", 1)
replace_count("alongside the 1.19.4–1.21.11 variants.",
              "alongside the 1.19.4–26.1 variants.", 1)

# ---- main チェーン： "without 774" の委譲 null を 2 個から 4 個に ----
replace_count(
    """                pack772File, sha1772, pack773File, sha1773, null, null,
                port, required, promptKey);""",
    """                pack772File, sha1772, pack773File, sha1773, null, null, null, null,
                port, required, promptKey);""",
    1)

# ---- main チェーン： "without 775" オーバーロードを新設 ----
replace_count(
    """                pack772File, sha1772, pack773File, sha1773, null, null, null, null,
                port, required, promptKey);
    }

    /**
     * @param pack769File 1.21.4 variant zip file, or {@code null}""",
    """                pack772File, sha1772, pack773File, sha1773, null, null, null, null,
                port, required, promptKey);
    }

    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 26.1 variant: no 775 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, pack770File, sha1770, pack771File, sha1771,
                pack772File, sha1772, pack773File, sha1773, pack774File, sha1774, null, null,
                port, required, promptKey);
    }

    /**
     * @param pack769File 1.21.4 variant zip file, or {@code null}""",
    1)

# ---- BuiltPack prompt-key コンストラクタ（through 762/761/760）：Path 委譲に null を追加 ----
# "thirteen"（through 762）、"fourteen"（through 761）、"fifteen"（through 760）の
# 3 つだけ。through 774（"twelve"）は arity 経由で新しい "without 775" に繋がる
# ため変更不要。through 759（"sixteen"）は built775 を渡す（後で別途処理）。
for lead in ("built762", "built761", "built760"):
    replace_after(
        f"this(built.file(), built.sha1(), {lead}.file(), {lead}.sha1(),",
        """                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                port, required, promptKey);""",
        """                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                null, null,
                port, required, promptKey);""")

# ---- BuiltPack "sixteen" prompt-key：built775 を追加 ----
replace_after(
    "/** Convenience: serve all sixteen packs, with an explicit prompt key. */",
    """            ResourcePackBuilder.BuiltPack built774,
            int port, boolean required, String promptKey) {""",
    """            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            int port, boolean required, String promptKey) {""")
replace_after(
    "/** Convenience: serve all sixteen packs, with an explicit prompt key. */",
    """                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                port, required, promptKey);""",
    """                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                port, required, promptKey);""")
replace_count("/** Convenience: serve all sixteen packs, with an explicit prompt key. */",
              "/** Convenience: serve all seventeen packs, with an explicit prompt key. */", 1)

# ---- BuiltPack "sixteen" no-promptKey：built775 を追加 ----
replace_after(
    "1.21.7／1.21.8, 1.21.9／1.21.10 and 1.21.11 variants, built by {@link ResourcePackBuilder}.\n     */\n    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n            ResourcePackBuilder.BuiltPack built759,",
    """            ResourcePackBuilder.BuiltPack built774,
            int port, boolean required) {""",
    """            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            int port, boolean required) {""")
replace_after(
    "1.21.7／1.21.8, 1.21.9／1.21.10 and 1.21.11 variants, built by {@link ResourcePackBuilder}.\n     */\n    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n            ResourcePackBuilder.BuiltPack built759,",
    "built770, built771, built772, built773, built774, port, required, DEFAULT_PROMPT_KEY);",
    "built770, built771, built772, built773, built774, built775, port, required, DEFAULT_PROMPT_KEY);")
replace_count(
    """     * 1.21.7／1.21.8, 1.21.9／1.21.10 and 1.21.11 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,""",
    """     * 1.21.7／1.21.8, 1.21.9／1.21.10, 1.21.11 and 26.1 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,""",
    1)

# ---- start()：読み込みと配信 ----
replace_count(
    """        if (pack774File != null) {
            pack774Bytes = readAndVerify(pack774File, sha1774);
        }""",
    """        if (pack774File != null) {
            pack774Bytes = readAndVerify(pack774File, sha1774);
        }
        if (pack775File != null) {
            pack775Bytes = readAndVerify(pack775File, sha1775);
        }""",
    1)
replace_count(
    """        if (pack774Bytes != null) {
            httpServer.createContext(PACK_PATH_774, exchange -> serveBytes(exchange, pack774Bytes));
        }""",
    """        if (pack774Bytes != null) {
            httpServer.createContext(PACK_PATH_774, exchange -> serveBytes(exchange, pack774Bytes));
        }
        if (pack775Bytes != null) {
            httpServer.createContext(PACK_PATH_775, exchange -> serveBytes(exchange, pack775Bytes));
        }""",
    1)

# ---- stop() ----
replace_count(
    "        pack774Bytes = null;",
    "        pack774Bytes = null;\n        pack775Bytes = null;",
    1)

# ---- url775() / pack775() アクセサ ----
replace_count(
    """    public synchronized ResourcePack pack774() {
        return pack774File == null ? null
                : new ResourcePack(url774(), sha1774, required, promptKey);
    }""",
    """    public synchronized ResourcePack pack774() {
        return pack774File == null ? null
                : new ResourcePack(url774(), sha1774, required, promptKey);
    }

    /** The download URL of the 26.1 variant, or {@code null} when not served. */
    public synchronized String url775() {
        if (pack775File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_775;
    }

    /**
     * The 26.1 {@link ResourcePack} to offer to 775 clients,
     * or {@code null} when no 26.1 variant is served.
     */
    public synchronized ResourcePack pack775() {
        return pack775File == null ? null
                : new ResourcePack(url775(), sha1775, required, promptKey);
    }""",
    1)

# ---- packForProtocol() ----
replace_count(
    """     * 774→{@link #pack774()}（pack_format 75）、""",
    """     * 774→{@link #pack774()}（pack_format 75）、
     * 775→{@link #pack775()}（pack_format 84）、""",
    1)
replace_count(
    """        if (clientProtocol == ProtocolVersion.V1_21_11) {
            return pack774();
        }""",
    """        if (clientProtocol == ProtocolVersion.V1_21_11) {
            return pack774();
        }
        if (clientProtocol == ProtocolVersion.V26_1) {
            return pack775();
        }""",
    1)

with open(PATH, "w", encoding="utf-8") as f:
    f.write(src)
print("ResourcePackServer.java に 775 を追加した")
