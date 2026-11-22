#!/usr/bin/env python3
"""DetergentServer.java に 776（26.2）リソースパック配線を入れる。

* resourcePack776 フィールド
* packForInternalServer()：V26_2 分岐
* buildClientProxy()：packFor776Clients を新ビッグコンストラクタへ渡す
* startResourcePack()：decide() 呼び出しに url-776／sha1-776 を挿入
  （罠7：url775／sha1775 の直後に入れること。末尾に足すと 760／759 がずれる）
* EXTERNAL 分岐：resourcePack776 の初期化・decision.pack776() の保持
* LOOPBACK 分岐：built776 の生成・ResourcePackServer への受け渡し・
  resourcePack776 の保持、コメントの17つ→18つ・16番目／17番目→17番目／18番目
* stopResourcePack()：resourcePack776 = null
* generateDatapack() のコメント：26.2 のデータパック形式 107 を追加
"""
import sys
from pathlib import Path

PATH = Path("detergentmc-core/src/main/java/io/detergentmc/core/DetergentServer.java")
failures = []


def rep(src, old, new, note, count=1):
    n = src.count(old)
    if n != count:
        failures.append(f"{note}: 期待 {count} 回だが {n} 回（{old[:70]!r}）")
        return src
    return src.replace(old, new)


def main():
    src = PATH.read_text(encoding="utf-8")

    # 1. フィールド
    src = rep(src,
              "    /** 775クライアントに送るリソースパック（pack_format 84）。無ければ null。26.1／26.1.1／26.1.2で共用。 */\n"
              "    private ResourcePack resourcePack775;",
              "    /** 775クライアントに送るリソースパック（pack_format 84）。無ければ null。26.1／26.1.1／26.1.2で共用。 */\n"
              "    private ResourcePack resourcePack775;\n"
              "    /** 776クライアントに送るリソースパック（pack_format 88）。無ければ null。26.2にホットフィックスは無い。 */\n"
              "    private ResourcePack resourcePack776;",
              "1 フィールド")

    # 2. packForInternalServer()
    src = rep(src,
              "        if (serverProtocol == ProtocolVersion.V26_1 && resourcePack775 != null) {\n"
              "            return resourcePack775;\n"
              "        }\n"
              "        return resourcePack;",
              "        if (serverProtocol == ProtocolVersion.V26_1 && resourcePack775 != null) {\n"
              "            return resourcePack775;\n"
              "        }\n"
              "        if (serverProtocol == ProtocolVersion.V26_2 && resourcePack776 != null) {\n"
              "            return resourcePack776;\n"
              "        }\n"
              "        return resourcePack;",
              "2 packForInternalServer")

    # 3. buildClientProxy()
    src = rep(src,
              "        ResourcePack packFor775Clients =\n"
              "                serverProtocol == ProtocolVersion.V26_1 ? null : resourcePack775;\n"
              "        return new ClientProxy(port, \"127.0.0.1\", internalPort, onlineMode,",
              "        ResourcePack packFor775Clients =\n"
              "                serverProtocol == ProtocolVersion.V26_1 ? null : resourcePack775;\n"
              "        ResourcePack packFor776Clients =\n"
              "                serverProtocol == ProtocolVersion.V26_2 ? null : resourcePack776;\n"
              "        return new ClientProxy(port, \"127.0.0.1\", internalPort, onlineMode,",
              "3 buildClientProxy 変数")
    src = rep(src,
              "                packFor772Clients, packFor773Clients, packFor774Clients, packFor775Clients, serverProtocol);",
              "                packFor772Clients, packFor773Clients, packFor774Clients, packFor775Clients, packFor776Clients, serverProtocol);",
              "3 buildClientProxy 呼び出し")

    # 4. startResourcePack() javadoc
    src = rep(src,
              "     * {@code resource-pack.url-774} / {@code resource-pack.sha1-774} and\n"
              "     * {@code resource-pack.url-775} / {@code resource-pack.sha1-775}\n",
              "     * {@code resource-pack.url-774} / {@code resource-pack.sha1-774},\n"
              "     * {@code resource-pack.url-775} / {@code resource-pack.sha1-775} and\n"
              "     * {@code resource-pack.url-776} / {@code resource-pack.sha1-776}\n",
              "4 startResourcePack javadoc")
    src = rep(src,
              "     * pack to 773 clients, the 1.21.11 pack to 774 clients, the 26.1 pack\n"
              "     * to 775 clients, and the 1.20.4 pack to 765",
              "     * pack to 773 clients, the 1.21.11 pack to 774 clients, the 26.1 pack\n"
              "     * to 775 clients, the 26.2 pack to 776 clients, and the 1.20.4 pack to 765",
              "4 startResourcePack javadoc 2")

    # 5. decide() 呼び出し：url-776／sha1-776 を url-775／sha1-775 の直後に挿入
    src = rep(src,
              "                        config.getString(\"resource-pack.url-775\", \"\"),\n"
              "                        config.getString(\"resource-pack.sha1-775\", \"\"),\n"
              "                        config.getString(\"resource-pack.url-760\", \"\"),",
              "                        config.getString(\"resource-pack.url-775\", \"\"),\n"
              "                        config.getString(\"resource-pack.sha1-775\", \"\"),\n"
              "                        config.getString(\"resource-pack.url-776\", \"\"),\n"
              "                        config.getString(\"resource-pack.sha1-776\", \"\"),\n"
              "                        config.getString(\"resource-pack.url-760\", \"\"),",
              "5 decide() 呼び出し")

    # 6. EXTERNAL 分岐：失敗時の初期化
    src = rep(src,
              "                    resourcePack774 = null;\n"
              "                    resourcePack775 = null;\n"
              "                    return null;",
              "                    resourcePack774 = null;\n"
              "                    resourcePack775 = null;\n"
              "                    resourcePack776 = null;\n"
              "                    return null;",
              "6 EXTERNAL 初期化")
    # 6b. EXTERNAL 分岐：decision.pack776() の保持
    src = rep(src,
              "                resourcePack775 = decision.pack775();\n"
              "                if (resourcePack775 != null) {\n"
              "                    LOG.info(() -> translator.translate(\n"
              "                            \"resource-pack.external-enabled\", resourcePack775.url()));\n"
              "                }\n"
              "                return resourcePack;",
              "                resourcePack775 = decision.pack775();\n"
              "                if (resourcePack775 != null) {\n"
              "                    LOG.info(() -> translator.translate(\n"
              "                            \"resource-pack.external-enabled\", resourcePack775.url()));\n"
              "                }\n"
              "                resourcePack776 = decision.pack776();\n"
              "                if (resourcePack776 != null) {\n"
              "                    LOG.info(() -> translator.translate(\n"
              "                            \"resource-pack.external-enabled\", resourcePack776.url()));\n"
              "                }\n"
              "                return resourcePack;",
              "6b EXTERNAL 保持")

    # 7. LOOPBACK 分岐：コメントと built776
    src = rep(src,
              "            // 26.1用（pack_format 84、26.1／26.1.1／26.1.2で共用）の\n"
              "            // 17つを生成し、同じHTTPサーバー（同じ25567番ポート）で配信する。",
              "            // 26.1用（pack_format 84、26.1／26.1.1／26.1.2で共用）、\n"
              "            // 26.2用（pack_format 88）の\n"
              "            // 18つを生成し、同じHTTPサーバー（同じ25567番ポート）で配信する。",
              "7 LOOPBACK コメント")
    src = rep(src,
              "            // 774クライアントには16番目、775クライアントには17番目をプロキシが出し分ける。",
              "            // 774クライアントには16番目、775クライアントには17番目、776クライアントには18番目をプロキシが出し分ける。",
              "7 LOOPBACK コメント2")
    src = rep(src,
              "            ResourcePackBuilder.BuiltPack built775 = new ResourcePackBuilder()\n"
              "                    .build775(workingDir.resolve(\"data\").resolve(\"resource-pack\"));\n"
              "            resourcePackServer = new ResourcePackServer(\n"
              "                    built, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,\n"
              "                    built770, built771, built772, built773, built774, built775, port, required,",
              "            ResourcePackBuilder.BuiltPack built775 = new ResourcePackBuilder()\n"
              "                    .build775(workingDir.resolve(\"data\").resolve(\"resource-pack\"));\n"
              "            ResourcePackBuilder.BuiltPack built776 = new ResourcePackBuilder()\n"
              "                    .build776(workingDir.resolve(\"data\").resolve(\"resource-pack\"));\n"
              "            resourcePackServer = new ResourcePackServer(\n"
              "                    built, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,\n"
              "                    built770, built771, built772, built773, built774, built775, built776, port, required,",
              "7 LOOPBACK built776")
    src = rep(src,
              "            resourcePack774 = resourcePackServer.pack774();\n"
              "            resourcePack775 = resourcePackServer.pack775();",
              "            resourcePack774 = resourcePackServer.pack774();\n"
              "            resourcePack775 = resourcePackServer.pack775();\n"
              "            resourcePack776 = resourcePackServer.pack776();",
              "7 LOOPBACK 保持")

    # 8. stopResourcePack()
    src = rep(src,
              "        resourcePack774 = null;\n"
              "        resourcePack775 = null;\n"
              "    }\n"
              "\n"
              "    /**\n"
              "     * Bedrock リスナー（Geyser 風＋Floodgate 風）の設定を",
              "        resourcePack774 = null;\n"
              "        resourcePack775 = null;\n"
              "        resourcePack776 = null;\n"
              "    }\n"
              "\n"
              "    /**\n"
              "     * Bedrock リスナー（Geyser 風＋Floodgate 風）の設定を",
              "8 stopResourcePack")

    # 9. generateDatapack() のコメント
    src = rep(src,
              "            // 57 for 1.21.2 / 1.21.3, 61 for 1.21.4, 71 for 1.21.5, 80 for 1.21.6, 81 for 1.21.7／1.21.8, 88 for 1.21.9／1.21.10, 94 for 1.21.11, 101 for 26.1 / 26.1.1 / 26.1.2.",
              "            // 57 for 1.21.2 / 1.21.3, 61 for 1.21.4, 71 for 1.21.5, 80 for 1.21.6, 81 for 1.21.7／1.21.8, 88 for 1.21.9／1.21.10, 94 for 1.21.11, 101 for 26.1 / 26.1.1 / 26.1.2, 107 for 26.2.",
              "9 generateDatapack コメント")

    if failures:
        print("失敗:")
        for f in failures:
            print("  -", f)
        print("何も書き込まない。")
        return 1
    PATH.write_text(src, encoding="utf-8")
    print("DetergentServer.java に 776 を適用した。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
