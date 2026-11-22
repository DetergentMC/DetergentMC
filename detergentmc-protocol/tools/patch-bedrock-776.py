#!/usr/bin/env python3
"""Bedrock -> 内部 776（26.2）サーバーの対応を追加する。

775 の Bedrock 対応を写す。776 は 775 とペイロードがほぼ同じで、
唯一の形式差は Join Game の末尾 onlineMode（bool 1バイト）だけ。
それ以外は 775 と同じ条件に 776 を足す。
- JavaBackendConnection.java: Chat checksum / Spawn Position / SyncPos /
  Respawn 次元 / Join Game（onlineMode を落としてから 768 形式へ）/
  Client Information / Add Resource Pack（コメントのみ）/ 次元名表
- BedrockChunkTranslator.java: 776 -> 765 の正規化（合成表あり）、
  heightmap 配列 / Data Array 長さ接頭辞なし
- BedrockEntityTranslator.java: 776 -> 765 の正規化（種別・アイテム・
  粒子）、エフェクトは 774／775 と同一の 40 件、スロット形式、属性
  （attributeName776 は 776 専用の表、新規 5 属性あり）

失敗したら何も書き込まない（全置換が成功してから書き出す）。
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent
BEDROCK = HERE.parent.parent / "detergentmc-bedrock" / "src" / "main" / "java" / "io" / "detergentmc" / "bedrock"


def fix(text, old, new, label):
    if old not in text:
        raise SystemExit(f"anchor not found: {label}\n--- expected ---\n{old}")
    if text.count(old) != 1:
        raise SystemExit(f"anchor not unique ({text.count(old)}x): {label}")
    return text.replace(old, new)


def patch_connection():
    p = BEDROCK / "JavaBackendConnection.java"
    t = p.read_text(encoding="utf-8")

    t = fix(t,
            "import java.util.ArrayList;\n",
            "import java.util.ArrayList;\nimport java.util.Arrays;\n",
            "Arrays import")
    t = fix(t,
            "766／767／768／769／770／771／772／773／774／775 の Join Game／Respawn の番号解決用",
            "766／767／768／769／770／771／772／773／774／775／776 の Join Game／Respawn の番号解決用",
            "dimensionNamesById javadoc")
    # Chat Message の末尾 checksum（770〜775 と同形）。
    t = fix(t,
            "            if (serverProtocol == 770 || serverProtocol == 771 || serverProtocol == 772\n"
            "                    || serverProtocol == 773 || serverProtocol == 774\n"
            "                    || serverProtocol == 775) {",
            "            if (serverProtocol == 770 || serverProtocol == 771 || serverProtocol == 772\n"
            "                    || serverProtocol == 773 || serverProtocol == 774\n"
            "                    || serverProtocol == 775 || serverProtocol == 776) {",
            "chat checksum condition")
    t = fix(t,
            "                // 770／771／772／773／774／775の Chat Message は末尾に checksum u8 が増えた。\n"
            "                // プロキシは770／771／772／773／774／775クライアントからの Chat Message の末尾1バイトを",
            "                // 770／771／772／773／774／775／776の Chat Message は末尾に checksum u8 が増えた。\n"
            "                // プロキシは770／771／772／773／774／775／776クライアントからの Chat Message の末尾1バイトを",
            "chat checksum comment")
    t = fix(t,
            "                // 末尾バイトが削られて壊れる。値は 0x00（771までの実バニラで確認済み：統合テストと\n"
            "                // 実バニラ1.21.6のプローブ。1.21.7／1.21.8／1.21.9／1.21.10／1.21.11／26.1は未確認）。",
            "                // 末尾バイトが削られて壊れる。値は 0x00（771までの実バニラで確認済み：統合テストと\n"
            "                // 実バニラ1.21.6のプローブ。1.21.7／1.21.8／1.21.9／1.21.10／1.21.11／26.1／26.2は未確認）。",
            "chat checksum unconfirmed comment")
    # Spawn Position（RespawnData 形式）。
    t = fix(t,
            "                if (serverProtocol == 773 || serverProtocol == 774\n"
            "                        || serverProtocol == 775) {",
            "                if (serverProtocol == 773 || serverProtocol == 774\n"
            "                        || serverProtocol == 775 || serverProtocol == 776) {",
            "spawn position condition")
    t = fix(t,
            "                    // 1.21.9／1.21.10（773）と1.21.11（774）と26.1（775）：Spawn Position は RespawnData",
            "                    // 1.21.9／1.21.10（773）と1.21.11（774）と26.1（775）と26.2（776）：Spawn Position は RespawnData",
            "spawn position comment")
    # Synchronize Player Position。
    t = fix(t,
            "            if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770\n"
            "                    || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773\n"
            "                    || serverProtocol == 774 || serverProtocol == 775) {\n"
            "                // 768／769／770／771／772／773／774／775：teleportId が先頭、dx／dy／dz が追加、flags は u32。\n"
            "                // 769／770／771／772／773／774／775 の Synchronize Player Position は 768 と同形。",
            "            if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770\n"
            "                    || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773\n"
            "                    || serverProtocol == 774 || serverProtocol == 775 || serverProtocol == 776) {\n"
            "                // 768／769／770／771／772／773／774／775／776：teleportId が先頭、dx／dy／dz が追加、flags は u32。\n"
            "                // 769／770／771／772／773／774／775／776 の Synchronize Player Position は 768 と同形。",
            "sync player position")
    # Respawn の次元（番号）。
    t = fix(t,
            "                if (serverProtocol == 766 || serverProtocol == 767\n"
            "                        || serverProtocol == 768 || serverProtocol == 769\n"
            "                        || serverProtocol == 770 || serverProtocol == 771\n"
            "                        || serverProtocol == 772 || serverProtocol == 773\n"
            "                        || serverProtocol == 774 || serverProtocol == 775) {\n"
            "                    // 766〜775：Respawn の次元は番号（dimensionNamesById で名前解決）。\n"
            "                    // 775 の dimension 値は 774 と同一前提（774→775 はペイロード変更 0 件のため）。\n"
            "                    // 実バニラ 26.1 の Registry Data での確認は Stage E で行う。",
            "                if (serverProtocol == 766 || serverProtocol == 767\n"
            "                        || serverProtocol == 768 || serverProtocol == 769\n"
            "                        || serverProtocol == 770 || serverProtocol == 771\n"
            "                        || serverProtocol == 772 || serverProtocol == 773\n"
            "                        || serverProtocol == 774 || serverProtocol == 775 || serverProtocol == 776) {\n"
            "                    // 766〜776：Respawn の次元は番号（dimensionNamesById で名前解決）。\n"
            "                    // 775 の dimension 値は 774 と同一前提（774→775 はペイロード変更 0 件のため）。\n"
            "                    // 776 の dimension 値は 775 と同一（dimension_type は 4 件・同一順序のため）。\n"
            "                    // 実バニラ 26.1 の Registry Data での確認は Stage E で行う。",
            "respawn dimension")
    # Join Game（唯一の形式差：末尾の onlineMode bool）。
    t = fix(t,
            "        if (serverProtocol == 766 || serverProtocol == 767 || serverProtocol == 768\n"
            "                || serverProtocol == 769 || serverProtocol == 770\n"
            "                || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773\n"
            "                || serverProtocol == 774 || serverProtocol == 775) {\n"
            "            // 768／769／770／771／772／773／774／775 の Join Game は767形式に seaLevel を補ったもののため、\n"
            "            // 767へ直してから読む（parseJoinGame766 は余分なバイトを拒む）。\n"
            "            // 769／770／771／772／773／774／775 の Join Game は 768 と同形のため同じ変換が使える。\n"
            "            // 775 は 774 とペイロード変更 0 件のため translateJoinGame768To767 をそのまま使う。\n"
            "            byte[] payload767 = serverProtocol == 768 || serverProtocol == 769\n"
            "                    || serverProtocol == 770 || serverProtocol == 771 || serverProtocol == 772\n"
            "                    || serverProtocol == 773 || serverProtocol == 774\n"
            "                    || serverProtocol == 775\n"
            "                    ? PlayTranslator.translateJoinGame768To767(payload) : payload;",
            "        if (serverProtocol == 766 || serverProtocol == 767 || serverProtocol == 768\n"
            "                || serverProtocol == 769 || serverProtocol == 770\n"
            "                || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773\n"
            "                || serverProtocol == 774 || serverProtocol == 775 || serverProtocol == 776) {\n"
            "            // 768／769／770／771／772／773／774／775／776 の Join Game は767形式に seaLevel を補ったもののため、\n"
            "            // 767へ直してから読む（parseJoinGame766 は余分なバイトを拒む）。\n"
            "            // 769／770／771／772／773／774／775／776 の Join Game は 768 と同形のため同じ変換が使える。\n"
            "            // 775 は 774 とペイロード変更 0 件のため translateJoinGame768To767 をそのまま使う。\n"
            "            // 776 は末尾に onlineMode（bool 1バイト）が増えたため、先に落としてから 768 形式に直す\n"
            "            // （落とさないと translateJoinGame768To767 が余分なバイトで失敗し、未変換のまま\n"
            "            // parseJoinGame766 に渡ってしまう）。\n"
            "            byte[] payload776 = serverProtocol == 776 && payload.length > 0\n"
            "                    ? Arrays.copyOf(payload, payload.length - 1) : payload;\n"
            "            byte[] payload767 = serverProtocol == 768 || serverProtocol == 769\n"
            "                    || serverProtocol == 770 || serverProtocol == 771 || serverProtocol == 772\n"
            "                    || serverProtocol == 773 || serverProtocol == 774\n"
            "                    || serverProtocol == 775 || serverProtocol == 776\n"
            "                    ? PlayTranslator.translateJoinGame768To767(payload776) : payload776;",
            "join game onlineMode strip")
    # Client Information の末尾 particleStatus。
    t = fix(t,
            "        if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770\n"
            "                || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773\n"
            "                || serverProtocol == 774 || serverProtocol == 775) {\n"
            "            // 768／769／770／771／772／773／774／775 は末尾に particleStatus varint がある（0=all）。\n"
            "            // 付けないと 1.21.2／1.21.3／1.21.4／1.21.5／1.21.6／1.21.7／1.21.8／1.21.9／1.21.10／1.21.11／26.1 のバニラが読み取り途中で切断する。\n"
            "            // 775 は 774 とペイロード変更 0 件のため同じ形式（実バニラ 26.1 での確認は Stage E）。",
            "        if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770\n"
            "                || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773\n"
            "                || serverProtocol == 774 || serverProtocol == 775 || serverProtocol == 776) {\n"
            "            // 768／769／770／771／772／773／774／775／776 は末尾に particleStatus varint がある（0=all）。\n"
            "            // 付けないと 1.21.2／1.21.3／1.21.4／1.21.5／1.21.6／1.21.7／1.21.8／1.21.9／1.21.10／1.21.11／26.1／26.2 のバニラが読み取り途中で切断する。\n"
            "            // 775 は 774 とペイロード変更 0 件のため同じ形式（実バニラ 26.1 での確認は Stage E）。\n"
            "            // 776 も同じ形式（Join Game 以外のペイロード変更は無いため）。",
            "client information particleStatus")
    # Add Resource Pack（コメントのみ。ID は PacketIds 経由で 776 も 0x09）。
    t = fix(t,
            "                // 追加パック（765: 0x07、766／767／768／769／770／771／772／773／774／775: 0x09）。UUID を読んで accepted（3）で返す。",
            "                // 追加パック（765: 0x07、766／767／768／769／770／771／772／773／774／775／776: 0x09）。UUID を読んで accepted（3）で返す。",
            "add resource pack comment")
    p.write_text(t, encoding="utf-8")
    print("patched JavaBackendConnection.java")


def patch_chunk():
    p = BEDROCK / "BedrockChunkTranslator.java"
    t = p.read_text(encoding="utf-8")
    t = fix(t,
            "            case RegistryRemapper.PROTOCOL_775 -> RegistryRemapper.mapBlockState775To765(stateId);\n",
            "            case RegistryRemapper.PROTOCOL_775 -> RegistryRemapper.mapBlockState775To765(stateId);\n"
            "            case RegistryRemapper.PROTOCOL_776 -> RegistryRemapper.mapBlockState776To765(stateId);\n",
            "normalizeBlockState 776")
    t = fix(t,
            "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_774\n"
            "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_775) {\n"
            "            // 770／771／772／773／774／775 の heightmap は NBT ではなく配列（varint 個数 ×",
            "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_774\n"
            "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_775\n"
            "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_776) {\n"
            "            // 770／771／772／773／774／775／776 の heightmap は NBT ではなく配列（varint 個数 ×",
            "heightmap array")
    t = fix(t,
            "                    && serverProtocol != RegistryRemapper.PROTOCOL_774\n"
            "                    && serverProtocol != RegistryRemapper.PROTOCOL_775) {",
            "                    && serverProtocol != RegistryRemapper.PROTOCOL_774\n"
            "                    && serverProtocol != RegistryRemapper.PROTOCOL_775\n"
            "                    && serverProtocol != RegistryRemapper.PROTOCOL_776) {",
            "single palette no length prefix")
    t = fix(t,
            "            // 770／771／772／773／774／775では Data Array の長さ接頭辞が無いため、シングルパレットは",
            "            // 770／771／772／773／774／775／776では Data Array の長さ接頭辞が無いため、シングルパレットは",
            "single palette comment")
    t = fix(t,
            "                || serverProtocol == RegistryRemapper.PROTOCOL_774\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_775) {\n"
            "            // 1.21.5（770）／1.21.6（771）／1.21.7／1.21.8（772）／1.21.9／1.21.10（773）／1.21.11（774）／26.1（775）ではパレット付きコンテナの Data Array に長さの",
            "                || serverProtocol == RegistryRemapper.PROTOCOL_774\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_775\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_776) {\n"
            "            // 1.21.5（770）／1.21.6（771）／1.21.7／1.21.8（772）／1.21.9／1.21.10（773）／1.21.11（774）／26.1（775）／26.2（776）ではパレット付きコンテナの Data Array に長さの",
            "data array length prefix")
    # javadoc の一覧（87行目付近）に 776 を足す。
    t = fix(t,
            "774 は 774→765 表、775 は 775→765 表、763 は",
            "774 は 774→765 表、775 は 775→765 表、776 は 776→765 表、763 は",
            "chunk javadoc list")
    p.write_text(t, encoding="utf-8")
    print("patched BedrockChunkTranslator.java")


def patch_entity():
    p = BEDROCK / "BedrockEntityTranslator.java"
    t = p.read_text(encoding="utf-8")
    t = fix(t,
            "            case RegistryRemapper.PROTOCOL_775 ->\n"
            "                    RegistryRemapper.mapEntityType775To765(typeId);\n",
            "            case RegistryRemapper.PROTOCOL_775 ->\n"
            "                    RegistryRemapper.mapEntityType775To765(typeId);\n"
            "            case RegistryRemapper.PROTOCOL_776 ->\n"
            "                    RegistryRemapper.mapEntityType776To765(typeId);\n",
            "normalizeEntityType 776")
    t = fix(t,
            "            case RegistryRemapper.PROTOCOL_775 -> RegistryRemapper.mapItem775To765(itemId);\n",
            "            case RegistryRemapper.PROTOCOL_775 -> RegistryRemapper.mapItem775To765(itemId);\n"
            "            case RegistryRemapper.PROTOCOL_776 -> RegistryRemapper.mapItem776To765(itemId);\n",
            "normalizeItem 776")
    t = fix(t,
            "            // 775 のエフェクトは 774 と同一の 40 件のため 774 と同じ表を使う。\n",
            "            // 775 のエフェクトは 774 と同一の 40 件のため 774 と同じ表を使う。\n"
            "            // 776 のエフェクトも 774／775 と同一の 40 件のため同じ表を使う。\n",
            "effect comment")
    t = fix(t,
            "            case RegistryRemapper.PROTOCOL_774, RegistryRemapper.PROTOCOL_775 ->\n"
            "                    RegistryRemapper.mapEffect774To765(effectId);",
            "            case RegistryRemapper.PROTOCOL_774, RegistryRemapper.PROTOCOL_775,\n"
            "                    RegistryRemapper.PROTOCOL_776 ->\n"
            "                    RegistryRemapper.mapEffect774To765(effectId);",
            "effect case 776")
    t = fix(t,
            "            case RegistryRemapper.PROTOCOL_775 ->\n"
            "                    RegistryRemapper.mapParticle775To765(particleId);\n",
            "            case RegistryRemapper.PROTOCOL_775 ->\n"
            "                    RegistryRemapper.mapParticle775To765(particleId);\n"
            "            case RegistryRemapper.PROTOCOL_776 ->\n"
            "                    RegistryRemapper.mapParticle776To765(particleId);\n",
            "normalizeParticle 776")
    t = fix(t,
            "                || serverProtocol == RegistryRemapper.PROTOCOL_774\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_775) {\n"
            "            count = reader.readVarInt();",
            "                || serverProtocol == RegistryRemapper.PROTOCOL_774\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_775\n"
            "                || serverProtocol == RegistryRemapper.PROTOCOL_776) {\n"
            "            count = reader.readVarInt();",
            "slot format 776")
    t = fix(t,
            "     * 766／767／768／769／770／771／772／773／774／775 は番号（属性名の表で識別子に直す）。",
            "     * 766／767／768／769／770／771／772／773／774／775／776 は番号（属性名の表で識別子に直す）。",
            "attribute javadoc numbers")
    t = fix(t,
            "     * 767／768／769／770／771／772／773／774／775 は modifier の UUID が文字列（766 までは 16 バイトの UUID）。",
            "     * 767／768／769／770／771／772／773／774／775／776 は modifier の UUID が文字列（766 までは 16 バイトの UUID）。",
            "attribute javadoc uuid")
    t = fix(t,
            "     * 775 の属性名は 774 と同一（attributeName775 は774と同じ内容）。\n",
            "     * 775 の属性名は 774 と同一（attributeName775 は774と同じ内容）。\n"
            "     * 776 の属性名は 775 とは違う（air_drag_modifier が 0番、below_name_distance が\n"
            "     * 6番、bounciness が 9番、friction_modifier が 17番、name_tag_distance が 27番に挿入。\n"
            "     * attributeName776 は776専用の表）。\n",
            "attribute javadoc 776")
    t = fix(t,
            "                    || serverProtocol == RegistryRemapper.PROTOCOL_774\n"
            "                    || serverProtocol == RegistryRemapper.PROTOCOL_775) {\n"
            "                // 766／767／768／769／770／771／772／773／774／775 は属性キーが番号。名前表で識別子に直す。",
            "                    || serverProtocol == RegistryRemapper.PROTOCOL_774\n"
            "                    || serverProtocol == RegistryRemapper.PROTOCOL_775\n"
            "                    || serverProtocol == RegistryRemapper.PROTOCOL_776) {\n"
            "                // 766／767／768／769／770／771／772／773／774／775／776 は属性キーが番号。名前表で識別子に直す。",
            "attribute key by number")
    t = fix(t,
            "                String name = serverProtocol == RegistryRemapper.PROTOCOL_775\n"
            "                        ? RegistryRemapper.attributeName775(keyId)",
            "                String name = serverProtocol == RegistryRemapper.PROTOCOL_776\n"
            "                        ? RegistryRemapper.attributeName776(keyId)\n"
            "                        : serverProtocol == RegistryRemapper.PROTOCOL_775\n"
            "                        ? RegistryRemapper.attributeName775(keyId)",
            "attribute name ternary 776")
    t = fix(t,
            "                        || serverProtocol == RegistryRemapper.PROTOCOL_774\n"
            "                        || serverProtocol == RegistryRemapper.PROTOCOL_775) {\n"
            "                    reader.readString(); // modifier UUID（767／768／769／770／771／772／773／774／775 は文字列）",
            "                        || serverProtocol == RegistryRemapper.PROTOCOL_774\n"
            "                        || serverProtocol == RegistryRemapper.PROTOCOL_775\n"
            "                        || serverProtocol == RegistryRemapper.PROTOCOL_776) {\n"
            "                    reader.readString(); // modifier UUID（767／768／769／770／771／772／773／774／775／776 は文字列）",
            "modifier uuid string 776")
    p.write_text(t, encoding="utf-8")
    print("patched BedrockEntityTranslator.java")


patch_connection()
patch_chunk()
patch_entity()
print("done")
