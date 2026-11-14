#!/usr/bin/env python3
"""ClientJoinFlow.java へ Java 1.21.6（プロトコル771）の受け入れを入れる。

段階の遷移自体は汎用（763以外は設定フェーズあり）で、771も770と同形の
ため、文書の更新だけ。ClientAdapter.supportedClientProtocols() が771を
含むようになったため、771の ClientJoinFlow 生成はそのまま通る。
各アンカーはファイル中にちょうど1回だけ現れること。
"""
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol" / "ClientJoinFlow.java"

EDITS = [
    ("class javadoc 771 entry", """\
 *   <li>1.21.5（770）：769と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は769から変わらず、新規パケットはプレイ状態に
 *       増えただけである。</li>""",
     """\
 *   <li>1.21.5（770）：769と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は769から変わらず、新規パケットはプレイ状態に
 *       増えただけである。</li>
 *   <li>1.21.6（771）：770と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は770から変わらず、新規パケットはプレイ状態に
 *       増えただけである。</li>"""),
    ("constructor javadoc", """\
     * @param clientProtocol クライアントのプロトコル番号（763か764か765か766か767か768か769か770）。""",
     """\
     * @param clientProtocol クライアントのプロトコル番号（763か764か765か766か767か768か769か770か771）。"""),
    ("hasConfigurationPhase javadoc", """\
    /** 設定フェーズがあるか（764と765と766と767と768と769と770はあり、763はなし）。 */""",
     """\
    /** 設定フェーズがあるか（764と765と766と767と768と769と770と771はあり、763はなし）。 */"""),
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
    print("ClientJoinFlow.java に 771 の対応を挿入した")


if __name__ == "__main__":
    main()
