#!/usr/bin/env python3
"""ResourcePackServer.java に 393（1.13）パックを追加する。

401 の追加と同じ形：PACK_PATH_393、フィールド、最大コンストラクタの
引数、BuiltPack からの委譲（既存の委譲には null を差し込み、新しい
最長コンストラクタを追加）、start の読み込みと HTTP コンテキスト、
url393()/pack393()、packForProtocol の 393 分岐（代替は 401 分岐へ）。

使い方：
  python3 tools/patch-393-resourcepack-server.py
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/ResourcePackServer.java"


def rep(text, old, new, count):
    n = text.count(old)
    if n != count:
        raise SystemExit(f"置換回数が合いません（{n} != {count}）: {old[:70]}...")
    return text.replace(old, new)


def main():
    text = P.read_text(encoding="utf-8")
    if "PACK_PATH_393" in text:
        print("skip（既存）")
        return 0

    text = rep(text,
        """    public static final String PACK_PATH_401 = "/detergentmc-pack-1.13.1.zip";
""",
        """    public static final String PACK_PATH_401 = "/detergentmc-pack-1.13.1.zip";

    /** Path of the 1.13 (pack_format 4) variant, when one is served. */
    public static final String PACK_PATH_393 = "/detergentmc-pack-1.13.zip";
""", 1)
    text = rep(text,
        """    private final Path pack401File;
    private final String sha1401;
""",
        """    private final Path pack393File;
    private final String sha1393;
    private final Path pack401File;
    private final String sha1401;
""", 1)
    text = rep(text,
        """    private byte[] pack401Bytes;
""",
        """    private byte[] pack393Bytes;
    private byte[] pack401Bytes;
""", 1)
    # 最大コンストラクタ：引数・検証・代入。
    text = rep(text,
        "public ResourcePackServer(Path packFile, String sha1, Path pack401File, String sha1401,",
        "public ResourcePackServer(Path packFile, String sha1, Path pack393File, String sha1393, Path pack401File, String sha1401,", 1)
    text = rep(text,
        """        if (pack401File != null && (sha1401 == null || sha1401.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.13.1 pack is required");
        }
""",
        """        if (pack393File != null && (sha1393 == null || sha1393.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.13 pack is required");
        }
        if (pack401File != null && (sha1401 == null || sha1401.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.13.1 pack is required");
        }
""", 1)
    text = rep(text,
        """        this.pack401File = pack401File;
        this.sha1401 = sha1401;
""",
        """        this.pack393File = pack393File;
        this.sha1393 = sha1393;
        this.pack401File = pack401File;
        this.sha1401 = sha1401;
""", 1)
    # 末端コンストラクタ群の null 代入（6か所）。
    text = rep(text,
        """        this.pack401File = null;
        this.sha1401 = null;
""",
        """        this.pack393File = null;
        this.sha1393 = null;
        this.pack401File = null;
        this.sha1401 = null;
""", 6)
    # BuiltPack から最大コンストラクタへの委譲（null 埋め 8 件＋最長 1 件）。
    text = rep(text,
        "this(built.file(), built.sha1(), null, null,",
        "this(built.file(), built.sha1(), null, null, null, null,", 8)
    text = rep(text,
        "this(built.file(), built.sha1(), built401.file(), built401.sha1(),",
        "this(built.file(), built.sha1(), null, null, built401.file(), built401.sha1(),", 1)
    # start()：読み込み・HTTP コンテキスト・停止時のクリア。
    text = rep(text,
        """        if (pack401File != null) {
            pack401Bytes = readAndVerify(pack401File, sha1401);
        }
""",
        """        if (pack393File != null) {
            pack393Bytes = readAndVerify(pack393File, sha1393);
        }
        if (pack401File != null) {
            pack401Bytes = readAndVerify(pack401File, sha1401);
        }
""", 1)
    text = rep(text,
        """        if (pack401Bytes != null) {
            httpServer.createContext(PACK_PATH_401, exchange -> serveBytes(exchange, pack401Bytes));
        }
""",
        """        if (pack393Bytes != null) {
            httpServer.createContext(PACK_PATH_393, exchange -> serveBytes(exchange, pack393Bytes));
        }
        if (pack401Bytes != null) {
            httpServer.createContext(PACK_PATH_401, exchange -> serveBytes(exchange, pack401Bytes));
        }
""", 1)
    text = rep(text,
        """        pack401Bytes = null;
""",
        """        pack393Bytes = null;
        pack401Bytes = null;
""", 1)
    # url393()/pack393() を url401() の前に追加。
    text = rep(text,
        """    /** The download URL of the 1.13.1 variant, or {@code null} when not served. */
    public synchronized String url401() {
""",
        """    /** The download URL of the 1.13 variant, or {@code null} when not served. */
    public synchronized String url393() {
        if (pack393File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_393;
    }

    /**
     * The 1.13 {@link ResourcePack} to offer to 393 clients, or
     * {@code null} when no 1.13 variant is served.
     */
    public synchronized ResourcePack pack393() {
        return pack393File == null ? null
                : new ResourcePack(url393(), sha1393, required, promptKey);
    }

    /** The download URL of the 1.13.1 variant, or {@code null} when not served. */
    public synchronized String url401() {
""", 1)
    # packForProtocol の 393 分岐を先頭に追加。
    text = rep(text,
        """    public synchronized ResourcePack packForProtocol(int clientProtocol) {
        if (clientProtocol == ProtocolVersion.V1_13_1) {
""",
        """    public synchronized ResourcePack packForProtocol(int clientProtocol) {
        if (clientProtocol == ProtocolVersion.V1_13) {
            // 393パックが無い場合は401パック（同じ pack_format 4）、
            // さらに無い場合は401分岐と同じ順に代替する。
            ResourcePack pack393 = pack393();
            if (pack393 != null) {
                return pack393;
            }
            return packForProtocol(ProtocolVersion.V1_13_1);
        }
        if (clientProtocol == ProtocolVersion.V1_13_1) {
""", 1)

    # 新しい最長 BuiltPack コンストラクタを、従来の最長（built401 始まり）
    # の直後に追加する。次のコンストラクタ（26 パック版）の javadoc を目印にする。
    anchor = """    /** Convenience: serve all twenty-six packs, with an explicit prompt key. */
"""
    if text.count(anchor) != 1:
        raise SystemExit("次のコンストラクタのアンカーが一意ではありません")
    new_ctor = """
    /** Convenience: serve all thirty packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }
"""
    text = text.replace(anchor, new_ctor + anchor, 1)

    P.write_text(text, encoding="utf-8")
    print("ResourcePackServer.java に 393 パックを追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
