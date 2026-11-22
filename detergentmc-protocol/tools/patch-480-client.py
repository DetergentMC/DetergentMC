#!/usr/bin/env python3
"""ClientAdapter／ClientJoinFlow／ClientProxy に 480（1.14.1）を追加する。

480 は 485 とパケットID・形式・レジストリが完全一致（差分0件、実 jar の
data-generator ダンプと minecraft-data の protocol.json で証明済み）の
ため、485 の分岐へ 480 を足す形にする。最古の対応クライアントは
480 になる。

使い方: python3 tools/patch-480-client.py
冪等：480 が既にあれば何もしない。
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
    if "V1_14_1" in text:
        print("skip（既存）: ClientAdapter は 480 済み")
        return
    # 1. 最古の対応クライアント。
    text = rep(text,
               "    /** 受け入れる一番古いクライアントのプロトコル（1.14.2）。 */\n"
               "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_14_2;",
               "    /** 受け入れる一番古いクライアントのプロトコル（1.14.1）。 */\n"
               "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_14_1;")
    # 2. decide() の受け入れ一覧の先頭に 480。
    text = rep(text,
               "        if (clientProtocol == ProtocolVersion.V1_14_2\n",
               "        if (clientProtocol == ProtocolVersion.V1_14_1\n"
               "                || clientProtocol == ProtocolVersion.V1_14_2\n")
    # 3. decide() の 480 専用メッセージ（485 分岐の前）。
    text = rep(text,
               "            if (clientProtocol == ProtocolVersion.V1_14_2) {\n"
               "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
               "                        \"プロトコル485（1.14.2）のクライアントを変換して受け入れます。\"",
               "            if (clientProtocol == ProtocolVersion.V1_14_1) {\n"
               "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
               "                        \"プロトコル480（1.14.1）のクライアントを変換して受け入れます。\"\n"
               "                                + \"設定フェーズの省略とブロックの代替が必要です。\"\n"
               "                                + \"パケットID・形式・レジストリは485（1.14.2）と完全一致（差分0件、\"\n"
               "                                + \"実jarのデータ生成ダンプで照合済み）で、変換は485の経路を共用します。\"\n"
               "                                + \"掘削の確認応答 acknowledge_player_digging は480にも485にも無いため落とします。\"\n"
               "                                + \"Trade List の各取引には demand が、末尾には canRestock が無いため付け外しします。\"\n"
               "                                + \"Join Game／Respawn に hashedSeed は無く、チャンクのバイオームは\"\n"
               "                                + \"バッファ末尾の256件、出現には末尾メタデータが付きます。\"\n"
               "                                + \"チャットは署名なしの旧式で、チャンクの光は別パケット、\"\n"
               "                                + \"出現は5種に分かれています。\");\n"
               "            }\n"
               "            if (clientProtocol == ProtocolVersion.V1_14_2) {\n"
               "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
               "                        \"プロトコル485（1.14.2）のクライアントを変換して受け入れます。\"")
    # 4. 古すぎる側の注記と文言。
    text = rep(text,
               "            // 485 未満、および 486〜489・491〜497・499〜572・574〜757 の未対応帯（1.14〜1.14.1、1.16〜1.17.x 等）は「古すぎる」。\n"
               "            // 485・490・498・573・575・578 自体は上の分岐で受け入れ済みのためここには来ない。",
               "            // 480 未満、および 481〜484・486〜489・491〜497・499〜572・574〜757 の未対応帯（1.14、1.16〜1.17.x 等）は「古すぎる」。\n"
               "            // 480・485・490・498・573・575・578 自体は上の分岐で受け入れ済みのためここには来ない。")
    text = rep(text,
               "                            + \"1.14.2（485）以降のクライアントで参加してください。\");",
               "                            + \"1.14.1（480）以降のクライアントで参加してください。\");")
    # 5. supportedClientProtocols() の注記と本体（OLDEST が 480 になるため
    # 485 を明示する）。
    text = rep(text,
               "        // OLDEST（485）だけでは490・498・573・575・578・758・759・760が漏れるため、490・498・573・575・578・758・759・760は明示する。",
               "        // OLDEST（480）だけでは485・490・498・573・575・578・758・759・760が漏れるため、485・490・498・573・575・578・758・759・760は明示する。")
    text = rep(text,
               "        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_14_3, ProtocolVersion.V1_14_4, ProtocolVersion.V1_15, ProtocolVersion.V1_15_1, ProtocolVersion.V1_15_2, ProtocolVersion.V1_18_2, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3,",
               "        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_14_2, ProtocolVersion.V1_14_3, ProtocolVersion.V1_14_4, ProtocolVersion.V1_15, ProtocolVersion.V1_15_1, ProtocolVersion.V1_15_2, ProtocolVersion.V1_18_2, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3,")
    # 6. versionName()。
    text = rep(text,
               "        if (protocol == ProtocolVersion.V1_14_2) {\n"
               "            return \"1.14.2\";\n"
               "        }",
               "        if (protocol == ProtocolVersion.V1_14_1) {\n"
               "            return \"1.14.1\";\n"
               "        }\n"
               "        if (protocol == ProtocolVersion.V1_14_2) {\n"
               "            return \"1.14.2\";\n"
               "        }")
    p.write_text(text, encoding="utf-8")
    print("ClientAdapter.java を更新した")


def patch_client_join_flow():
    p = PROTO / "ClientJoinFlow.java"
    text = p.read_text(encoding="utf-8")
    if "V1_14_1" in text:
        print("skip（既存）: ClientJoinFlow は 480 済み")
        return
    text = rep(text,
               "クライアントのプロトコル番号（485か490か498か573か",
               "クライアントのプロトコル番号（480か485か490か498か573か")
    text = rep(text,
               "                || clientProtocol == ProtocolVersion.V1_14_2\n",
               "                || clientProtocol == ProtocolVersion.V1_14_2\n"
               "                || clientProtocol == ProtocolVersion.V1_14_1\n")
    text = rep(text,
               "485と490と498と573と575と578と758と759と760と761と762と763はなし",
               "480と485と490と498と573と575と578と758と759と760と761と762と763はなし")
    p.write_text(text, encoding="utf-8")
    print("ClientJoinFlow.java を更新した")


def patch_client_proxy():
    p = PROTO / "ClientProxy.java"
    text = p.read_text(encoding="utf-8")
    if "pack480" in text:
        print("skip（既存）: ClientProxy は 480 済み")
        return
    # 1. pack480 フィールド（pack485 の前）。
    text = rep(text,
               "    /**\n"
               "     * 485クライアントに送るリソースパック（pack_format 4）。無ければ null。\n"
               "     * 490 と同じ後付けの流儀（{@link #setPack485(ResourcePack)}）。\n"
               "     */\n"
               "    private ResourcePack pack485;",
               "    /**\n"
               "     * 480クライアントに送るリソースパック（pack_format 4）。無ければ null。\n"
               "     * 485 と同じ後付けの流儀（{@link #setPack480(ResourcePack)}）。\n"
               "     */\n"
               "    private ResourcePack pack480;\n"
               "    /**\n"
               "     * 485クライアントに送るリソースパック（pack_format 4）。無ければ null。\n"
               "     * 490 と同じ後付けの流儀（{@link #setPack485(ResourcePack)}）。\n"
               "     */\n"
               "    private ResourcePack pack485;")
    # 2. コンストラクタ検証（3件とも）。
    text = rep(text,
               "        if (internalProtocol != ProtocolVersion.V1_14_2\n",
               "        if (internalProtocol != ProtocolVersion.V1_14_1\n"
               "                && internalProtocol != ProtocolVersion.V1_14_2\n", 3)
    text = rep(text,
               '"internalProtocol must be 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15),',
               '"internalProtocol must be 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15),', 3)
    # 3. pack480 アクセサ（setPack485 の前）。
    text = rep(text,
               "    /**\n"
               "     * 485クライアントに送るリソースパックを設定する\n",
               "    /**\n"
               "     * 480クライアントに送るリソースパックを設定する\n"
               "     * （{@code detergentmc.yml} の {@code resource-pack.url-480} 等）。\n"
               "     * 起動（{@link #start()}）前に呼ぶこと。\n"
               "     */\n"
               "    public void setPack480(ResourcePack pack480) {\n"
               "        this.pack480 = pack480;\n"
               "    }\n\n"
               "    /** 480クライアントに送るリソースパック（未設定なら null）。 */\n"
               "    public ResourcePack pack480() {\n"
               "        return pack480;\n"
               "    }\n\n"
               "    /**\n"
               "     * 485クライアントに送るリソースパックを設定する\n")
    # 4. セッションへの受け渡し。
    text = rep(text,
               "                session.setPack485(proxy.pack485);",
               "                session.setPack480(proxy.pack480);\n"
               "                session.setPack485(proxy.pack485);")
    p.write_text(text, encoding="utf-8")
    print("ClientProxy.java を更新した")


def main():
    patch_client_adapter()
    patch_client_join_flow()
    patch_client_proxy()
    return 0


if __name__ == "__main__":
    sys.exit(main())
