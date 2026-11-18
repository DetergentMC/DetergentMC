#!/usr/bin/env python3
"""ResourcePackServer.java に 340（1.12.2）パックを追加する。

393 の追加と同じ形：PACK_PATH_340、フィールド、最大コンストラクタの
引数、BuiltPack からの委譲（既存の委譲には null を差し込み、新しい
最長コンストラクタを追加）、start の読み込みと HTTP コンテキスト、
url340()/pack340()、packForProtocol の 340 分岐（代替は 393 分岐へ）。

使い方：
  python3 tools/patch-340-resourcepack-server.py
"""

import re
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
    if "PACK_PATH_340" in text:
        print("skip（既存）")
        return 0

    text = rep(text,
        """    public static final String PACK_PATH_393 = "/detergentmc-pack-1.13.zip";
""",
        """    public static final String PACK_PATH_393 = "/detergentmc-pack-1.13.zip";

    /** Path of the 1.12.2 (pack_format 3) variant, when one is served. */
    public static final String PACK_PATH_340 = "/detergentmc-pack-1.12.2.zip";
""", 1)
    text = rep(text,
        """    private final Path pack393File;
    private final String sha1393;
""",
        """    private final Path pack340File;
    private final String sha1340;
    private final Path pack393File;
    private final String sha1393;
""", 1)
    text = rep(text,
        """    private byte[] pack393Bytes;
""",
        """    private byte[] pack340Bytes;
    private byte[] pack393Bytes;
""", 1)
    # 最大コンストラクタ：引数・検証・代入。
    text = rep(text,
        "public ResourcePackServer(Path packFile, String sha1, Path pack393File, String sha1393,",
        "public ResourcePackServer(Path packFile, String sha1, Path pack340File, String sha1340, Path pack393File, String sha1393,", 1)
    text = rep(text,
        """        if (pack393File != null && (sha1393 == null || sha1393.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.13 pack is required");
        }
""",
        """        if (pack340File != null && (sha1340 == null || sha1340.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.12.2 pack is required");
        }
        if (pack393File != null && (sha1393 == null || sha1393.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.13 pack is required");
        }
""", 1)
    text = rep(text,
        """        this.pack393File = pack393File;
        this.sha1393 = sha1393;
""",
        """        this.pack340File = pack340File;
        this.sha1340 = sha1340;
        this.pack393File = pack393File;
        this.sha1393 = sha1393;
""", 1)
    # 末端コンストラクタ群の null 代入（6か所）。
    text = rep(text,
        """        this.pack393File = null;
        this.sha1393 = null;
""",
        """        this.pack340File = null;
        this.sha1340 = null;
        this.pack393File = null;
        this.sha1393 = null;
""", 6)
    # BuiltPack から最大コンストラクタへの委譲（null 埋め 9 件＋最長 1 件）。
    text = rep(text,
        "this(built.file(), built.sha1(), null, null,",
        "this(built.file(), built.sha1(), null, null, null, null,", 9)
    text = rep(text,
        "this(built.file(), built.sha1(), built393.file(), built393.sha1(),",
        "this(built.file(), built.sha1(), null, null, built393.file(), built393.sha1(),", 1)
    # start()：読み込み・HTTP コンテキスト・停止時のクリア。
    text = rep(text,
        """        if (pack393File != null) {
            pack393Bytes = readAndVerify(pack393File, sha1393);
        }
""",
        """        if (pack340File != null) {
            pack340Bytes = readAndVerify(pack340File, sha1340);
        }
        if (pack393File != null) {
            pack393Bytes = readAndVerify(pack393File, sha1393);
        }
""", 1)
    text = rep(text,
        """        if (pack393Bytes != null) {
            httpServer.createContext(PACK_PATH_393, exchange -> serveBytes(exchange, pack393Bytes));
        }
""",
        """        if (pack340Bytes != null) {
            httpServer.createContext(PACK_PATH_340, exchange -> serveBytes(exchange, pack340Bytes));
        }
        if (pack393Bytes != null) {
            httpServer.createContext(PACK_PATH_393, exchange -> serveBytes(exchange, pack393Bytes));
        }
""", 1)
    text = rep(text,
        """        pack393Bytes = null;
""",
        """        pack340Bytes = null;
        pack393Bytes = null;
""", 1)
    # url340()/pack340() を url393() の前に追加。
    text = rep(text,
        """    /** The download URL of the 1.13 variant, or {@code null} when not served. */
    public synchronized String url393() {
""",
        """    /** The download URL of the 1.12.2 variant, or {@code null} when not served. */
    public synchronized String url340() {
        if (pack340File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_340;
    }

    /**
     * The 1.12.2 {@link ResourcePack} to offer to 340 clients, or
     * {@code null} when no 1.12.2 variant is served.
     */
    public synchronized ResourcePack pack340() {
        return pack340File == null ? null
                : new ResourcePack(url340(), sha1340, required, promptKey);
    }

    /** The download URL of the 1.13 variant, or {@code null} when not served. */
    public synchronized String url393() {
""", 1)
    # packForProtocol の 340 分岐を先頭に追加。
    text = rep(text,
        """    public synchronized ResourcePack packForProtocol(int clientProtocol) {
        if (clientProtocol == ProtocolVersion.V1_13) {
""",
        """    public synchronized ResourcePack packForProtocol(int clientProtocol) {
        if (clientProtocol == ProtocolVersion.V1_12_2) {
            // 340パックが無い場合は393パック（pack_format 4。1.12.2
            // クライアントは読み込めないが、代替が無い場合の最終手段）、
            // さらに無い場合は393分岐と同じ順に代替する。
            ResourcePack pack340 = pack340();
            if (pack340 != null) {
                return pack340;
            }
            return packForProtocol(ProtocolVersion.V1_13);
        }
        if (clientProtocol == ProtocolVersion.V1_13) {
""", 1)

    # 新しい最長 BuiltPack コンストラクタを、従来の最長（30 パック版）
    # から組み立てて、その直前に追加する。
    anchor = """    /** Convenience: serve all thirty packs, with an explicit prompt key. */
"""
    if text.count(anchor) != 1:
        raise SystemExit("次のコンストラクタのアンカーが一意ではありません")
    m = re.search(re.escape(anchor) + r"(    public ResourcePackServer\(.*?\n    \}\n)",
                  text, re.S)
    if not m:
        raise SystemExit("30 パック版コンストラクタが見つかりません")
    ctor = m.group(1)
    new_ctor = ctor.replace("ResourcePackBuilder.BuiltPack built,",
                            "ResourcePackBuilder.BuiltPack built,\n"
                            "            ResourcePackBuilder.BuiltPack built340,", 1)
    new_ctor = new_ctor.replace("this(built.file(), built.sha1(),",
                                "this(built.file(), built.sha1(), built340.file(), built340.sha1(),", 1)
    if new_ctor == ctor or "built340" not in new_ctor:
        raise SystemExit("31 パック版コンストラクタの組み立てに失敗しました")
    new_block = ("""    /** Convenience: serve all thirty-one packs, with an explicit prompt key. */
"""
                 + new_ctor + "\n")
    text = text.replace(anchor, new_block + anchor, 1)

    P.write_text(text, encoding="utf-8")
    print("ResourcePackServer.java に 340 パックを追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
