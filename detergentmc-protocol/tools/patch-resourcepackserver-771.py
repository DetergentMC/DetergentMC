#!/usr/bin/env python3
"""ResourcePackServer.java へ Java 1.21.6（プロトコル771）の対応を入れる。

771用パック（pack_format 63、detergentmc-pack-1.21.6.zip）を9番目に増やす。
770の流儀をなぞる：フルコンストラクタを771まで伸ばし、770までの
コンストラクタを「771無し」の委譲版にする。stop() の pack770Bytes の
null 戻し漏れ（770ステージの見落とし）も一緒に直す。
各アンカーはファイル中にちょうど1回だけ現れること。
"""
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol" / "ResourcePackServer.java"

EDITS = [
    ("class javadoc 771 section", """\
 * <h2>1.21.5 variant</h2>
 *
 * <p>When an eighth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_770} and
 * {@link #pack770()} returns the {@link ResourcePack} the proxy offers
 * to 770 clients (pack_format 55). All eight packs share the same port.
 */""",
     """\
 * <h2>1.21.5 variant</h2>
 *
 * <p>When an eighth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_770} and
 * {@link #pack770()} returns the {@link ResourcePack} the proxy offers
 * to 770 clients (pack_format 55). All eight packs share the same port.
 *
 * <h2>1.21.6 variant</h2>
 *
 * <p>When a ninth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_771} and
 * {@link #pack771()} returns the {@link ResourcePack} the proxy offers
 * to 771 clients (pack_format 63). All nine packs share the same port.
 */"""),
    ("PACK_PATH_771", """\
    /** Path of the 1.21.5 (pack_format 55) variant, when one is served. */
    public static final String PACK_PATH_770 = "/detergentmc-pack-1.21.5.zip";""",
     """\
    /** Path of the 1.21.5 (pack_format 55) variant, when one is served. */
    public static final String PACK_PATH_770 = "/detergentmc-pack-1.21.5.zip";

    /** Path of the 1.21.6 (pack_format 63) variant, when one is served. */
    public static final String PACK_PATH_771 = "/detergentmc-pack-1.21.6.zip";"""),
    ("pack771 fields", """\
    private final Path pack770File;
    private final String sha1770;""",
     """\
    private final Path pack770File;
    private final String sha1770;
    private final Path pack771File;
    private final String sha1771;"""),
    ("pack771Bytes field", """\
    private byte[] pack769Bytes;
    private byte[] pack770Bytes;""",
     """\
    private byte[] pack769Bytes;
    private byte[] pack770Bytes;
    private byte[] pack771Bytes;"""),
    ("full constructor javadoc", """\
     * @param pack770File 1.21.5 variant zip file, or {@code null}
     *                    to serve no 1.21.5 variant
     * @param sha1770     lower-case hex SHA-1 of {@code pack770File}
     *                    (required when {@code pack770File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            int port, boolean required, String promptKey) {""",
     """\
     * @param pack770File 1.21.5 variant zip file, or {@code null}
     *                    to serve no 1.21.5 variant
     * @param sha1770     lower-case hex SHA-1 of {@code pack770File}
     *                    (required when {@code pack770File} is given)
     * @param pack771File 1.21.6 variant zip file, or {@code null}
     *                    to serve no 1.21.6 variant
     * @param sha1771     lower-case hex SHA-1 of {@code pack771File}
     *                    (required when {@code pack771File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771,
            int port, boolean required, String promptKey) {"""),
    ("full constructor validation", """\
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }""",
     """\
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }"""),
    ("full constructor assignment", """\
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.requestedPort = port;""",
     """\
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.requestedPort = port;"""),
    ("without-771 constructor", """\
    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 1.21.5 variant: no 770 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, null, null, port, required, promptKey);
    }""",
     """\
    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 1.21.5 variant: no 770 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, null, null, null, null, port, required, promptKey);
    }

    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 1.21.6 variant: no 771 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, pack770File, sha1770, null, null,
                port, required, promptKey);
    }"""),
    ("BuiltPack 9-pack constructors", """\
    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4 and
     * 1.21.5 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770,
            int port, boolean required) {
        this(built, built763, built764, built766, built767, built768, built769, built770,
                port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all eight packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                port, required, promptKey);
    }""",
     """\
    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4 and
     * 1.21.5 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770,
            int port, boolean required) {
        this(built, built763, built764, built766, built767, built768, built769, built770,
                port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all eight packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5
     * and 1.21.6 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            int port, boolean required) {
        this(built, built763, built764, built766, built767, built768, built769, built770,
                built771, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all nine packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(),
                port, required, promptKey);
    }"""),
    ("start() load 771", """\
        if (pack770File != null) {
            pack770Bytes = readAndVerify(pack770File, sha1770);
        }""",
     """\
        if (pack770File != null) {
            pack770Bytes = readAndVerify(pack770File, sha1770);
        }
        if (pack771File != null) {
            pack771Bytes = readAndVerify(pack771File, sha1771);
        }"""),
    ("start() serve 771", """\
        if (pack770Bytes != null) {
            httpServer.createContext(PACK_PATH_770, exchange -> serveBytes(exchange, pack770Bytes));
        }""",
     """\
        if (pack770Bytes != null) {
            httpServer.createContext(PACK_PATH_770, exchange -> serveBytes(exchange, pack770Bytes));
        }
        if (pack771Bytes != null) {
            httpServer.createContext(PACK_PATH_771, exchange -> serveBytes(exchange, pack771Bytes));
        }"""),
    ("stop() clear 770/771", """\
        pack768Bytes = null;
        pack769Bytes = null;
        mainPack = null;""",
     """\
        pack768Bytes = null;
        pack769Bytes = null;
        pack770Bytes = null;
        pack771Bytes = null;
        mainPack = null;"""),
    ("url771/pack771", """\
    /**
     * The 1.21.5 {@link ResourcePack} to offer to 770 clients,
     * or {@code null} when no 1.21.5 variant is served.
     */
    public synchronized ResourcePack pack770() {
        return pack770File == null ? null
                : new ResourcePack(url770(), sha1770, required, promptKey);
    }""",
     """\
    /**
     * The 1.21.5 {@link ResourcePack} to offer to 770 clients,
     * or {@code null} when no 1.21.5 variant is served.
     */
    public synchronized ResourcePack pack770() {
        return pack770File == null ? null
                : new ResourcePack(url770(), sha1770, required, promptKey);
    }

    /** The download URL of the 1.21.6 variant, or {@code null} when not served. */
    public synchronized String url771() {
        if (pack771File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_771;
    }

    /**
     * The 1.21.6 {@link ResourcePack} to offer to 771 clients,
     * or {@code null} when no 1.21.6 variant is served.
     */
    public synchronized ResourcePack pack771() {
        return pack771File == null ? null
                : new ResourcePack(url771(), sha1771, required, promptKey);
    }"""),
    ("packForProtocol javadoc", """\
     * 769→{@link #pack769()}（pack_format 46）、
     * 770→{@link #pack770()}（pack_format 55）、
     * 765→{@link #start()} が返した本体（pack_format 22）。開始前は本体が""",
     """\
     * 769→{@link #pack769()}（pack_format 46）、
     * 770→{@link #pack770()}（pack_format 55）、
     * 771→{@link #pack771()}（pack_format 63）、
     * 765→{@link #start()} が返した本体（pack_format 22）。開始前は本体が"""),
    ("packForProtocol branch", """\
        if (clientProtocol == ProtocolVersion.V1_21_5) {
            return pack770();
        }
        return mainPack;""",
     """\
        if (clientProtocol == ProtocolVersion.V1_21_5) {
            return pack770();
        }
        if (clientProtocol == ProtocolVersion.V1_21_6) {
            return pack771();
        }
        return mainPack;"""),
]


def main():
    src = PATH.read_text(encoding="utf-8")
    for name, old, new in EDITS:
        n = src.count(old)
        if n != 1:
            print(f"NG: {name}: anchor が {n} 回見つかった（1回のはず）", file=sys.stderr)
            sys.exit(1)
        src = src.replace(old, new, 1)
        print(f"OK: {name}")
    PATH.write_text(src, encoding="utf-8")
    print("ResourcePackServer.java に 771 の対応を挿入した")


if __name__ == "__main__":
    main()
