#!/usr/bin/env python3
"""ResourcePackServer に pack4（1.7.2）を配線する（pack5 の鏡）。

正規コンストラクタの pack5 の直後に pack4File／sha14 を追加し、
フィールド・検証・読み込み・配信・アクセサを足す。委譲コンスト
ラクタの this(...) 引数調整はコンパイルエラーを見て別途行う。
冪等ではない（実行は1回だけ）。件数が合わなければ中止する。
"""
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol" / "ResourcePackServer.java"
src = PATH.read_text(encoding="utf-8")


def rep(old, new, expect=None, min_expect=None):
    global src
    n = src.count(old)
    if expect is not None:
        assert n == expect, f"{n} != {expect}: {old[:70]}"
    if min_expect is not None:
        assert n >= min_expect, f"{n} < {min_expect}: {old[:70]}"
    src = src.replace(old, new)
    print(f"ok {n} x {old[:60]}")


rep('    public static final String PACK_PATH_5 = "/detergentmc-pack-1.7.10.zip";',
    '    public static final String PACK_PATH_5 = "/detergentmc-pack-1.7.10.zip";\n'
    '    public static final String PACK_PATH_4 = "/detergentmc-pack-1.7.2.zip";', expect=1)
rep("    private final Path pack5File;",
    "    private final Path pack5File;\n    private final Path pack4File;", expect=1)
rep("    private final String sha15;",
    "    private final String sha15;\n    private final String sha14;", expect=1)
rep("    private byte[] pack5Bytes;",
    "    private byte[] pack5Bytes;\n    private byte[] pack4Bytes;", expect=1)
rep("Path pack47File, String sha147, Path pack5File, String sha15, Path pack315File, String sha1315,",
    "Path pack47File, String sha147, Path pack5File, String sha15, Path pack4File, String sha14, Path pack315File, String sha1315,",
    expect=1)
rep('        if (pack5File != null && (sha15 == null || sha15.isBlank())) {\n'
    '            throw new IllegalArgumentException("sha15 is required when pack5File is given");\n'
    '        }',
    '        if (pack5File != null && (sha15 == null || sha15.isBlank())) {\n'
    '            throw new IllegalArgumentException("sha15 is required when pack5File is given");\n'
    '        }\n'
    '        if (pack4File != null && (sha14 == null || sha14.isBlank())) {\n'
    '            throw new IllegalArgumentException("sha14 is required when pack4File is given");\n'
    '        }', expect=1)
rep("        this.pack5File = pack5File;",
    "        this.pack5File = pack5File;\n        this.pack4File = pack4File;", expect=1)
rep("        this.sha15 = sha15;",
    "        this.sha15 = sha15;\n        this.sha14 = sha14;", expect=1)
rep("        this.pack5File = null;",
    "        this.pack5File = null;\n        this.pack4File = null;", min_expect=3)
rep("        this.sha15 = null;",
    "        this.sha15 = null;\n        this.sha14 = null;", min_expect=3)
rep("            pack5Bytes = readAndVerify(pack5File, sha15);",
    "            pack5Bytes = readAndVerify(pack5File, sha15);\n"
    "            pack4Bytes = readAndVerify(pack4File, sha14);", expect=1)
rep("        if (pack5Bytes != null) {\n"
    "            httpServer.createContext(PACK_PATH_5, exchange -> serveBytes(exchange, pack5Bytes));",
    "        if (pack5Bytes != null) {\n"
    "            httpServer.createContext(PACK_PATH_5, exchange -> serveBytes(exchange, pack5Bytes));\n"
    "        }\n"
    "        if (pack4Bytes != null) {\n"
    "            httpServer.createContext(PACK_PATH_4, exchange -> serveBytes(exchange, pack4Bytes));",
    expect=1)
rep('''    public synchronized ResourcePack pack5() {
        return pack5File == null ? null
                : new ResourcePack(url5(), sha15, required, promptKey);
    }''',
    '''    public synchronized ResourcePack pack5() {
        return pack5File == null ? null
                : new ResourcePack(url5(), sha15, required, promptKey);
    }

    /** The download URL of the 1.7.2 variant, or {@code null} when not served. */
    public synchronized String url4() {
        if (pack4File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_4;
    }

    /**
     * The 1.7.2 {@link ResourcePack} to offer to 4 clients, or
     * {@code null} when no 1.7.2 variant is served.
     */
    public synchronized ResourcePack pack4() {
        return pack4File == null ? null
                : new ResourcePack(url4(), sha14, required, promptKey);
    }''', expect=1)

PATH.write_text(src, encoding="utf-8")
print("written", PATH)
