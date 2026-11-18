#!/usr/bin/env python3
"""ClientAdapter／ClientJoinFlow／ClientProxy に 490（1.14.3）を追加する。

490 は 498 と形式がほぼ同一（差はパケット1件と Trade List の demand
だけ）のため、498 の分岐へ 490 を足す形にする。最古の対応クライアントは
490 になる。

使い方: python3 tools/patch-490-client.py
冪等：490 が既にあれば何もしない。
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROTO = ROOT / "src/main/java/io/detergentmc/protocol"


def fail(msg):
    print(f"NG: {msg}", file=sys.stderr)
    sys.exit(1)


def rep(text, old, new, count=1):
    n = text.count(old)
    if n != count:
        fail(f"置換対象 {old[:60]!r} が {n} 件（期待 {count}）")
    return text.replace(old, new)


def patch_client_adapter():
    p = PROTO / "ClientAdapter.java"
    text = p.read_text(encoding="utf-8")
    if "V1_14_3" in text:
        print("skip（既存）: ClientAdapter は 490 済み")
        return
    # 1. 最古の対応クライアント。
    text = rep(text,
               "    /** 受け入れる一番古いクライアントのプロトコル（1.14.4）。 */\n"
               "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_14_4;",
               "    /** 受け入れる一番古いクライアントのプロトコル（1.14.3）。 */\n"
               "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_14_3;")
    # 2. decide() の受け入れ一覧の先頭に 490。
    text = rep(text,
               "        if (clientProtocol == ProtocolVersion.V1_14_4\n",
               "        if (clientProtocol == ProtocolVersion.V1_14_3\n"
               "                || clientProtocol == ProtocolVersion.V1_14_4\n")
    # 3. decide() の 490 専用メッセージ（498 分岐の前）。
    text = rep(text,
               "            if (clientProtocol == ProtocolVersion.V1_14_4) {\n"
               "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
               "                        \"プロトコル498（1.14.4）のクライアントを変換して受け入れます。\"",
               "            if (clientProtocol == ProtocolVersion.V1_14_3) {\n"
               "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
               "                        \"プロトコル490（1.14.3）のクライアントを変換して受け入れます。\"\n"
               "                                + \"設定フェーズの省略とブロックの代替が必要です。\"\n"
               "                                + \"形式は498とほぼ同一で、パケットIDの差は1件だけ\"\n"
               "                                + \"（掘削の確認応答 acknowledge_player_digging が490には無いため落とします）。\"\n"
               "                                + \"Trade List の各取引には demand が無いため付け外しします。\"\n"
               "                                + \"Join Game／Respawn に hashedSeed は無く、チャンクのバイオームは\"\n"
               "                                + \"バッファ末尾の256件、出現には末尾メタデータが付きます。\"\n"
               "                                + \"チャットは署名なしの旧式で、チャンクの光は別パケット、\"\n"
               "                                + \"出現は5種に分かれています。\");\n"
               "            }\n"
               "            if (clientProtocol == ProtocolVersion.V1_14_4) {\n"
               "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
               "                        \"プロトコル498（1.14.4）のクライアントを変換して受け入れます。\"")
    # 4. 古すぎる側の注記と文言。
    text = rep(text,
               "            // 498 未満、および 499〜572・574〜757 の未対応帯（1.14〜1.14.3、1.16〜1.17.x 等）は「古すぎる」。\n"
               "            // 498・573・575・578 自体は上の分岐で受け入れ済みのためここには来ない。",
               "            // 490 未満、および 491〜497・499〜572・574〜757 の未対応帯（1.14〜1.14.2、1.16〜1.17.x 等）は「古すぎる」。\n"
               "            // 490・498・573・575・578 自体は上の分岐で受け入れ済みのためここには来ない。")
    text = rep(text,
               "                            + \"1.14.4（498）以降のクライアントで参加してください。\");",
               "                            + \"1.14.3（490）以降のクライアントで参加してください。\");")
    # 5. supportedClientProtocols() の注記。
    text = rep(text,
               "        // OLDEST（498）だけでは573・575・578・758・759・760が漏れるため、573・575・578・758・759・760は明示する。",
               "        // OLDEST（490）だけでは498・573・575・578・758・759・760が漏れるため、498・573・575・578・758・759・760は明示する。")
    # supportedClientProtocols() 本体：OLDEST の後ろに 498 を明示する
    # （OLDEST が 490 になるため、498 が漏れる）。
    text = rep(text,
               "        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_15, ProtocolVersion.V1_15_1, ProtocolVersion.V1_15_2, ProtocolVersion.V1_18_2, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3,",
               "        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_14_4, ProtocolVersion.V1_15, ProtocolVersion.V1_15_1, ProtocolVersion.V1_15_2, ProtocolVersion.V1_18_2, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3,")
    # 6. versionName()。
    text = rep(text,
               "        if (protocol == ProtocolVersion.V1_14_4) {\n"
               "            return \"1.14.4\";\n"
               "        }",
               "        if (protocol == ProtocolVersion.V1_14_3) {\n"
               "            return \"1.14.3\";\n"
               "        }\n"
               "        if (protocol == ProtocolVersion.V1_14_4) {\n"
               "            return \"1.14.4\";\n"
               "        }")
    p.write_text(text, encoding="utf-8")
    print("ClientAdapter.java を更新した")


def patch_client_join_flow():
    p = PROTO / "ClientJoinFlow.java"
    text = p.read_text(encoding="utf-8")
    if "V1_14_3" in text:
        print("skip（既存）: ClientJoinFlow は 490 済み")
        return
    text = rep(text,
               "クライアントのプロトコル番号（498か573か",
               "クライアントのプロトコル番号（490か498か573か")
    text = rep(text,
               "                || clientProtocol == ProtocolVersion.V1_14_4\n",
               "                || clientProtocol == ProtocolVersion.V1_14_4\n"
               "                || clientProtocol == ProtocolVersion.V1_14_3\n")
    text = rep(text,
               "498と573と575と578と758と759と760と761と762と763はなし",
               "490と498と573と575と578と758と759と760と761と762と763はなし")
    p.write_text(text, encoding="utf-8")
    print("ClientJoinFlow.java を更新した")


def patch_client_proxy():
    p = PROTO / "ClientProxy.java"
    text = p.read_text(encoding="utf-8")
    if "pack490" in text:
        print("skip（既存）: ClientProxy は 490 済み")
        return
    # 1. pack490 フィールド（pack498 の前）。
    text = rep(text,
               "    /**\n"
               "     * 498クライアントに送るリソースパック（pack_format 4）。無ければ null。\n"
               "     * 573 と同じ後付けの流儀（{@link #setPack498(ResourcePack)}）。\n"
               "     */\n"
               "    private ResourcePack pack498;",
               "    /**\n"
               "     * 490クライアントに送るリソースパック（pack_format 4）。無ければ null。\n"
               "     * 498 と同じ後付けの流儀（{@link #setPack490(ResourcePack)}）。\n"
               "     */\n"
               "    private ResourcePack pack490;\n"
               "    /**\n"
               "     * 498クライアントに送るリソースパック（pack_format 4）。無ければ null。\n"
               "     * 573 と同じ後付けの流儀（{@link #setPack498(ResourcePack)}）。\n"
               "     */\n"
               "    private ResourcePack pack498;")
    # 2. コンストラクタ検証（3件とも）。
    text = rep(text,
               "        if (internalProtocol != ProtocolVersion.V1_14_4\n",
               "        if (internalProtocol != ProtocolVersion.V1_14_3\n"
               "                && internalProtocol != ProtocolVersion.V1_14_4\n", 3)
    text = rep(text,
               '"internalProtocol must be 498 (1.14.4), 573 (1.15),',
               '"internalProtocol must be 490 (1.14.3), 498 (1.14.4), 573 (1.15),', 3)
    # 3. pack490 アクセサ（setPack498 の前）。
    text = rep(text,
               "    public void setPack498(ResourcePack pack498) {",
               "    /**\n"
               "     * 490クライアントに送るリソースパックを設定する\n"
               "     * （{@code detergentmc.yml} の {@code resource-pack.url-490} 等）。\n"
               "     * 起動（{@link #start()}）前に呼ぶこと。\n"
               "     */\n"
               "    public void setPack490(ResourcePack pack490) {\n"
               "        this.pack490 = pack490;\n"
               "    }\n\n"
               "    /** 490クライアントに送るリソースパック（未設定なら null）。 */\n"
               "    public ResourcePack pack490() {\n"
               "        return pack490;\n"
               "    }\n\n"
               "    public void setPack498(ResourcePack pack498) {")
    # 4. セッションへの受け渡し。
    text = rep(text,
               "                session.setPack498(proxy.pack498);",
               "                session.setPack490(proxy.pack490);\n"
               "                session.setPack498(proxy.pack498);")
    p.write_text(text, encoding="utf-8")
    print("ClientProxy.java を更新した")


def main():
    patch_client_adapter()
    patch_client_join_flow()
    patch_client_proxy()
    return 0


if __name__ == "__main__":
    sys.exit(main())
