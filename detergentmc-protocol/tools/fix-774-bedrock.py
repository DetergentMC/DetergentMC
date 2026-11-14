#!/usr/bin/env python3
"""774（1.21.11）の Bedrock 側拡張：773と同じ形の箇所に774を加える。

根拠（773→774で確認済み）:
  - パケットIDは0変更。Spawn Position／Respawn／Join Game の形は
    mc-1.21.11-protocol.json で773と同一（Spawn Position は RespawnData、
    Respawn は SpawnInfo＋copyMetadata、Join Game は LoginData）。
  - ブロック状態は同一（identity）。アイテム・エンティティ・音は
    RegistryRemapper の map*774To765 が用意済み（Stage 2）。
  - エフェクトは 39（BreathOfTheNautilus）が末尾追加のため、
    774だけ mapEffect774To765 を使う（767〜773は mapEffect767To765 のまま）。
  - スロットの外形（個数・added・removed の varint 3個）は774も同じ。
    成分の中身は読まないため KIND 番号のずれは影響しない。
  - Settings は768から9フィールドで、774も同じ。
  - Chat Message の末尾 checksum u8 は774にもある（770で追加、
    パケット構造の変更はなし）。値は 0x00（773までと同じ扱い）。

全置き換えは exact-match＋出現回数検査。
"""
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent.parent


def replace(text, old, new, count=1, name=""):
    found = text.count(old)
    if found != count:
        raise SystemExit(
            f"anchor mismatch ({name}): expected {count}, found {found}: {old[:100]!r}")
    return text.replace(old, new)


def write(path, text):
    path.write_text(text, encoding="utf-8")
    print(f"wrote {path.relative_to(ROOT)}")


# ---------------------------------------------------------------- JavaBackendConnection
p = ROOT / "detergentmc-bedrock/src/main/java/io/detergentmc/bedrock/JavaBackendConnection.java"
t = p.read_text(encoding="utf-8")
t = replace(t,
            "    /** 次元種別の番号→名前（766／767／768／769／770／771／772／773 の Join Game／Respawn の番号解決用）。 */",
            "    /** 次元種別の番号→名前（766／767／768／769／770／771／772／773／774 の Join Game／Respawn の番号解決用）。 */",
            name="backend-dim-comment")
t = replace(t,
            "            if (serverProtocol == 770 || serverProtocol == 771 || serverProtocol == 772\n"
            "                    || serverProtocol == 773) {\n"
            "                // 770／771／772／773の Chat Message は末尾に checksum u8 が増えた。\n"
            "                // プロキシは770／771／772／773クライアントからの Chat Message の末尾1バイトを\n"
            "                // checksum として落とすため、ここで付けないと acknowledged の\n"
            "                // 末尾バイトが削られて壊れる。値は 0x00（771までの実バニラで確認済み：統合テストと\n"
            "                // 実バニラ1.21.6のプローブ。1.21.7／1.21.8／1.21.9／1.21.10は未確認）。\n",
            "            if (serverProtocol == 770 || serverProtocol == 771 || serverProtocol == 772\n"
            "                    || serverProtocol == 773 || serverProtocol == 774) {\n"
            "                // 770／771／772／773／774の Chat Message は末尾に checksum u8 が増えた。\n"
            "                // プロキシは770／771／772／773／774クライアントからの Chat Message の末尾1バイトを\n"
            "                // checksum として落とすため、ここで付けないと acknowledged の\n"
            "                // 末尾バイトが削られて壊れる。値は 0x00（771までの実バニラで確認済み：統合テストと\n"
            "                // 実バニラ1.21.6のプローブ。1.21.7／1.21.8／1.21.9／1.21.10／1.21.11は未確認）。\n",
            name="backend-chat-checksum")
t = replace(t,
            "                // 追加パック（765: 0x07、766／767／768／769／770／771／772／773: 0x09）。UUID を読んで accepted（3）で返す。",
            "                // 追加パック（765: 0x07、766／767／768／769／770／771／772／773／774: 0x09）。UUID を読んで accepted（3）で返す。",
            name="backend-addpack-comment")
t = replace(t,
            "                if (serverProtocol == 773) {\n"
            "                    // 1.21.9／1.21.10（773）：Spawn Position は RespawnData\n",
            "                if (serverProtocol == 773 || serverProtocol == 774) {\n"
            "                    // 1.21.9／1.21.10（773）と1.21.11（774）：Spawn Position は RespawnData\n",
            name="backend-spawn-position")
t = replace(t,
            "            if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770\n"
            "                    || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773) {\n"
            "                // 768／769／770／771／772／773：teleportId が先頭、dx／dy／dz が追加、flags は u32。\n"
            "                // 769／770／771／772／773 の Synchronize Player Position は 768 と同形。\n",
            "            if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770\n"
            "                    || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773\n"
            "                    || serverProtocol == 774) {\n"
            "                // 768／769／770／771／772／773／774：teleportId が先頭、dx／dy／dz が追加、flags は u32。\n"
            "                // 769／770／771／772／773／774 の Synchronize Player Position は 768 と同形。\n",
            name="backend-teleport")
t = replace(t,
            "                if (serverProtocol == 766 || serverProtocol == 767\n"
            "                        || serverProtocol == 768 || serverProtocol == 769\n"
            "                        || serverProtocol == 770 || serverProtocol == 771\n"
            "                        || serverProtocol == 772 || serverProtocol == 773) {",
            "                if (serverProtocol == 766 || serverProtocol == 767\n"
            "                        || serverProtocol == 768 || serverProtocol == 769\n"
            "                        || serverProtocol == 770 || serverProtocol == 771\n"
            "                        || serverProtocol == 772 || serverProtocol == 773\n"
            "                        || serverProtocol == 774) {",
            name="backend-respawn-dimension")
t = replace(t,
            "        if (serverProtocol == 766 || serverProtocol == 767 || serverProtocol == 768\n"
            "                || serverProtocol == 769 || serverProtocol == 770\n"
            "                || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773) {\n"
            "            // 768／769／770／771／772／773 の Join Game は767形式に seaLevel を補ったもののため、\n"
            "            // 767へ直してから読む（parseJoinGame766 は余分なバイトを拒む）。\n"
            "            // 769／770／771／772／773 の Join Game は 768 と同形のため同じ変換が使える。\n"
            "            byte[] payload767 = serverProtocol == 768 || serverProtocol == 769\n"
            "                    || serverProtocol == 770 || serverProtocol == 771 || serverProtocol == 772\n"
            "                    || serverProtocol == 773\n"
            "                    ? PlayTranslator.translateJoinGame768To767(payload) : payload;",
            "        if (serverProtocol == 766 || serverProtocol == 767 || serverProtocol == 768\n"
            "                || serverProtocol == 769 || serverProtocol == 770\n"
            "                || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773\n"
            "                || serverProtocol == 774) {\n"
            "            // 768／769／770／771／772／773／774 の Join Game は767形式に seaLevel を補ったもののため、\n"
            "            // 767へ直してから読む（parseJoinGame766 は余分なバイトを拒む）。\n"
            "            // 769／770／771／772／773／774 の Join Game は 768 と同形のため同じ変換が使える。\n"
            "            byte[] payload767 = serverProtocol == 768 || serverProtocol == 769\n"
            "                    || serverProtocol == 770 || serverProtocol == 771 || serverProtocol == 772\n"
            "                    || serverProtocol == 773 || serverProtocol == 774\n"
            "                    ? PlayTranslator.translateJoinGame768To767(payload) : payload;",
            name="backend-joingame")
t = replace(t,
            "        if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770\n"
            "                || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773) {\n"
            "            // 768／769／770／771／772／773 は末尾に particleStatus varint がある（0=all）。\n"
            "            // 付けないと 1.21.2／1.21.3／1.21.4／1.21.5／1.21.6／1.21.7／1.21.8／1.21.9／1.21.10 のバニラが読み取り途中で切断する。\n",
            "        if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770\n"
            "                || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773\n"
            "                || serverProtocol == 774) {\n"
            "            // 768／769／770／771／772／773／774 は末尾に particleStatus varint がある（0=all）。\n"
            "            // 付けないと 1.21.2／1.21.3／1.21.4／1.21.5／1.21.6／1.21.7／1.21.8／1.21.9／1.21.10／1.21.11 のバニラが読み取り途中で切断する。\n",
            name="backend-settings")
write(p, t)
print("backend OK")

# ---------------------------------------------------------------- BedrockChunkTranslator
p = ROOT / "detergentmc-bedrock/src/main/java/io/detergentmc/bedrock/BedrockChunkTranslator.java"
t = p.read_text(encoding="utf-8")
t = replace(t,
            "     * 771 は 771→765 表、772 は 772→765 表、773 は 773→765 表、763 は",
            "     * 771 は 771→765 表、772 は 772→765 表、773 は 773→765 表、774 は 774→765 表、763 は",
            name="chunk-javadoc")
t = replace(t,
            "            case RegistryRemapper.PROTOCOL_773 -> RegistryRemapper.mapBlockState773To765(stateId);\n",
            "            case RegistryRemapper.PROTOCOL_773 -> RegistryRemapper.mapBlockState773To765(stateId);\n"
            "            case RegistryRemapper.PROTOCOL_774 -> RegistryRemapper.mapBlockState774To765(stateId);\n",
            name="chunk-normalize")
t = replace(t,
            "        } else if (input.serverProtocol() == RegistryRemapper.PROTOCOL_770\n"
            "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_771\n"
            "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_772\n"
            "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_773) {\n"
            "            // 770／771／772／773 の heightmap は NBT ではなく配列（varint 個数 ×\n",
            "        } else if (input.serverProtocol() == RegistryRemapper.PROTOCOL_770\n"
            "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_771\n"
            "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_772\n"
            "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_773\n"
            "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_774) {\n"
            "            // 770／771／772／773／774 の heightmap は NBT ではなく配列（varint 個数 ×\n",
            name="chunk-heightmap")
t = replace(t,
            "            if (serverProtocol != RegistryRemapper.PROTOCOL_770\n"
            "                    && serverProtocol != RegistryRemapper.PROTOCOL_771\n"
            "                    && serverProtocol != RegistryRemapper.PROTOCOL_772\n"
            "                    && serverProtocol != RegistryRemapper.PROTOCOL_773) {",
            "            if (serverProtocol != RegistryRemapper.PROTOCOL_770\n"
            "                    && serverProtocol != RegistryRemapper.PROTOCOL_771\n"
            "                    && serverProtocol != RegistryRemapper.PROTOCOL_772\n"
            "                    && serverProtocol != RegistryRemapper.PROTOCOL_773\n"
            "                    && serverProtocol != RegistryRemapper.PROTOCOL_774) {",
            name="chunk-single-palette")
t = replace(t,
            "            // 770／771／772／773では Data Array の長さ接頭辞が無いため、シングルパレットは",
            "            // 770／771／772／773／774では Data Array の長さ接頭辞が無いため、シングルパレットは",
            name="chunk-single-palette-comment")
t = replace(t,
            "        if (serverProtocol == RegistryRemapper.PROTOCOL_770\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_771\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_772\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_773) {\n"
            "            // 1.21.5（770）／1.21.6（771）／1.21.7／1.21.8（772）／1.21.9／1.21.10（773）ではパレット付きコンテナの Data Array に長さの",
            "        if (serverProtocol == RegistryRemapper.PROTOCOL_770\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_771\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_772\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_773\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_774) {\n"
            "            // 1.21.5（770）／1.21.6（771）／1.21.7／1.21.8（772）／1.21.9／1.21.10（773）／1.21.11（774）ではパレット付きコンテナの Data Array に長さの",
            name="chunk-paletted-container")
write(p, t)
print("chunk OK")

# ---------------------------------------------------------------- BedrockEntityTranslator
p = ROOT / "detergentmc-bedrock/src/main/java/io/detergentmc/bedrock/BedrockEntityTranslator.java"
t = p.read_text(encoding="utf-8")
t = replace(t,
            "            case RegistryRemapper.PROTOCOL_773 ->\n"
            "                    RegistryRemapper.mapEntityType773To765(typeId);\n",
            "            case RegistryRemapper.PROTOCOL_773 ->\n"
            "                    RegistryRemapper.mapEntityType773To765(typeId);\n"
            "            case RegistryRemapper.PROTOCOL_774 ->\n"
            "                    RegistryRemapper.mapEntityType774To765(typeId);\n",
            name="entity-type-774")
t = replace(t,
            "            case RegistryRemapper.PROTOCOL_773 -> RegistryRemapper.mapItem773To765(itemId);\n",
            "            case RegistryRemapper.PROTOCOL_773 -> RegistryRemapper.mapItem773To765(itemId);\n"
            "            case RegistryRemapper.PROTOCOL_774 -> RegistryRemapper.mapItem774To765(itemId);\n",
            name="entity-item-774")
t = replace(t,
            "            // エフェクトは 767⇔768⇔769⇔770⇔771⇔772⇔773 で同一のため 768／769／770／771／772／773 も同じ表でよい。\n"
            "            case RegistryRemapper.PROTOCOL_767, RegistryRemapper.PROTOCOL_768,\n"
            "                    RegistryRemapper.PROTOCOL_769, RegistryRemapper.PROTOCOL_770,\n"
            "                    RegistryRemapper.PROTOCOL_771, RegistryRemapper.PROTOCOL_772,\n"
            "                    RegistryRemapper.PROTOCOL_773 ->\n"
            "                    RegistryRemapper.mapEffect767To765(effectId);",
            "            // エフェクトは 767⇔768⇔769⇔770⇔771⇔772⇔773 で同一のため 768／769／770／771／772／773 も同じ表でよい。\n"
            "            // 774 は BreathOfTheNautilus（39）が末尾追加のため専用の表を使う。\n"
            "            case RegistryRemapper.PROTOCOL_767, RegistryRemapper.PROTOCOL_768,\n"
            "                    RegistryRemapper.PROTOCOL_769, RegistryRemapper.PROTOCOL_770,\n"
            "                    RegistryRemapper.PROTOCOL_771, RegistryRemapper.PROTOCOL_772,\n"
            "                    RegistryRemapper.PROTOCOL_773 ->\n"
            "                    RegistryRemapper.mapEffect767To765(effectId);\n"
            "            case RegistryRemapper.PROTOCOL_774 ->\n"
            "                    RegistryRemapper.mapEffect774To765(effectId);",
            name="entity-effect-774")
t = replace(t,
            "            case RegistryRemapper.PROTOCOL_773 ->\n"
            "                    RegistryRemapper.mapParticle773To765(particleId);\n",
            "            case RegistryRemapper.PROTOCOL_773 ->\n"
            "                    RegistryRemapper.mapParticle773To765(particleId);\n"
            "            case RegistryRemapper.PROTOCOL_774 ->\n"
            "                    RegistryRemapper.mapParticle774To765(particleId);\n",
            name="entity-particle-774")
t = replace(t,
            "     * 名前なし）を読み飛ばす。766／767／768／769／770／771／772／773 はデータコンポーネント形式で、\n"
            "     * 中身は読まずに個数だけ確認する（成分付きだと後ろは読めないため\n"
            "     * {@code clean=false} を返す）。768／769／770／771／772／773 のスロットの外形（個数・added・removed\n"
            "     * の varint 3 個）は 767 と同じため、同じ読み方でよい。\n",
            "     * 名前なし）を読み飛ばす。766／767／768／769／770／771／772／773／774 はデータコンポーネント形式で、\n"
            "     * 中身は読まずに個数だけ確認する（成分付きだと後ろは読めないため\n"
            "     * {@code clean=false} を返す）。768／769／770／771／772／773／774 のスロットの外形（個数・added・removed\n"
            "     * の varint 3 個）は 767 と同じため、同じ読み方でよい。\n",
            name="entity-slot-javadoc")
t = replace(t,
            "        if (serverProtocol == RegistryRemapper.PROTOCOL_766\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_767\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_768\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_769\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_770\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_771\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_772\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_773) {",
            "        if (serverProtocol == RegistryRemapper.PROTOCOL_766\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_767\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_768\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_769\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_770\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_771\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_772\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_773\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_774) {",
            name="entity-slot-branch")
t = replace(t,
            "     * 766／767／768／769／770／771／772／773 は番号（属性名の表で識別子に直す）。",
            "     * 766／767／768／769／770／771／772／773／774 は番号（属性名の表で識別子に直す）。",
            name="entity-attr-comment-1")
t = replace(t,
            "     * 767／768／769／770／771／772／773 は modifier の UUID が文字列（766 までは 16 バイトの UUID）。",
            "     * 767／768／769／770／771／772／773／774 は modifier の UUID が文字列（766 までは 16 バイトの UUID）。",
            name="entity-attr-comment-2")
write(p, t)
print("entity OK")
print("ALL OK")
