# Copyright (c) 2026 DetergentMC contributors.
# SPDX-License-Identifier: Apache-2.0

"""ProxySession の773パケットIDの誤りを直す（第3弾）。

handleServerPlayFrom773 の末尾に 771／770／769／768クライアント向けの
変換が丸ごと無く、773サーバーからのスロット等のパケットが落とされていた
（ProxySession773Test join769Against773 の Set Slot が 0件）。
handleServerPlayFrom772 の末尾（771→772→771、770→771→770、
769→771→769、768→771→768）を写し、773→772の一段を先に挟む。

使い方：python3 fix-773-proxysession-ids-3.py（ProxySession.java を直接書き換える）
"""

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE.parent / "src/main/java/io/detergentmc/protocol/ProxySession.java"

failures = []


def replace_in_method(src, method_sig, old, new, expect=1, label=""):
    start = src.index(method_sig)
    end = src.index("\n    private ", start + len(method_sig))
    span = src[start:end]
    n = span.count(old)
    if n != expect:
        failures.append(f"{label}: {old[:60]!r} が {n} 回（{expect} 回のはず）")
        return src
    print(f"OK: {label}")
    return src[:start] + span.replace(old, new, 1) + src[end:]


def main():
    src = SRC.read_text(encoding="utf-8")

    src = replace_in_method(src,
        "private Outcome handleServerPlayFrom773(MinecraftPacket packet) {",
        """        // 772クライアント：773→772へ変換する。
        if (is772()) {
            return handleServerPlay773To772(packet);
        }
        // 767クライアント：773→772→771→770→769→768へ変換してから769→768へ。""",
        """        // 772クライアント：773→772へ変換する。
        if (is772()) {
            return handleServerPlay773To772(packet);
        }
        // 771クライアント：773→772へ変換してから772→771へ。
        if (is771()) {
            MinecraftPacket as772 = to772ServerPacketFrom773(packet);
            if (as772 == null) {
                return Outcome.drop();
            }
            return handleServerPlay772To771(as772);
        }
        // 770クライアント：773→772→771へ変換してから771→770へ。
        if (is770()) {
            MinecraftPacket as772 = to772ServerPacketFrom773(packet);
            if (as772 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as771 = to771ServerPacketFrom772(as772);
            if (as771 == null) {
                return Outcome.drop();
            }
            return handleServerPlay771To770(as771);
        }
        // 769クライアント：773→772→771へ変換してから771→769へ
        // （771→770→769は handleServerPlay771To769 の内側で変換）。
        if (is769()) {
            MinecraftPacket as772 = to772ServerPacketFrom773(packet);
            if (as772 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as771 = to771ServerPacketFrom772(as772);
            if (as771 == null) {
                return Outcome.drop();
            }
            return handleServerPlay771To769(as771);
        }
        // 768クライアント：773→772→771へ変換してから771→768へ
        // （771→770→769→768は handleServerPlay771To768 の内側で変換）。
        if (is768()) {
            MinecraftPacket as772 = to772ServerPacketFrom773(packet);
            if (as772 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as771 = to771ServerPacketFrom772(as772);
            if (as771 == null) {
                return Outcome.drop();
            }
            return handleServerPlay771To768(as771);
        }
        // 767クライアント：773→772→771→770→769→768へ変換してから769→768へ。""",
        label="From773 tail 771/770/769/768 branches")

    if failures:
        print("NG:")
        for f in failures:
            print("  " + f)
        sys.exit(1)
    SRC.write_text(src, encoding="utf-8")
    print("ProxySession.java を書き換えた（第3弾）")


if __name__ == "__main__":
    main()
