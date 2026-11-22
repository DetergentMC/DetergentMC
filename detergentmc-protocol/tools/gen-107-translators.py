#!/usr/bin/env python3
"""107（1.9）向け ChunkTranslator / SlotTranslator 節と PlayTranslator の
Join Game 節を生成する（108 の節を土台に、107 固有の差分だけ手を入れる）。

107⇔108 の差分（Stage A 実測で確定）：
- パケット集合・ID・形式は Join Game の dimension（107 は i8、108 以降は
  i32）を除いて完全同一（protocol.json 全面比較で差分はこの1件だけ）。
- レジストリ完全同一。チャンク末尾に blockEntities が無い扱いも同じ。
- 107 固有の癖：107 バックエンドの Chunk Data は dataLen に大きな
  ゼロ埋めを含む。既存の読み取り（dataLen 分を切り出し、セクションを
  順次読んで残りを捨てる。末尾 requireEnd は dataLen の外側だけ）は
  この癖に自然に耐えるため、変換器側の追加調整は不要（単体テストで固定）。

そのため ChunkTranslator / SlotTranslator は 108 の節を写して番号を
差し替えるだけ。PlayTranslator の Join Game だけは i8⇔i32 の付け替えが
要るため手書きする（Respawn は形式同一のため 108 の関数へ委譲）。
"""
import pathlib

BASE = pathlib.Path(__file__).resolve().parent.parent
PROTO = BASE / "src/main/java/io/detergentmc/protocol"


def rename(text: str) -> str:
    text = text.replace("V1_9_1", "V1_9")
    text = text.replace("1.9.1", "1.9")
    return text.replace("108", "107")


def copy_tail_section(path: pathlib.Path, marker: str, new_header: str) -> str:
    """末尾の節（108 の節）を写し、クラスの閉じ括弧の直前へ挿入する。
    gen-108-translators.py と同じ行ベースの方式。"""
    text = path.read_text(encoding="utf-8")
    if "// 107（1.9）対応" in text:
        print(f"skip（既存）: {path.name}")
        return text
    lines = text.split("\n")
    marker_idx = next(i for i, ln in enumerate(lines) if marker in ln)
    start = marker_idx
    while not lines[start].strip().startswith("// ---"):
        start -= 1
    assert lines[-1] == "}" or (lines[-1] == "" and lines[-2] == "}"), lines[-3:]
    end = len(lines) - 1 if lines[-1] == "}" else len(lines) - 2
    section = "\n".join(lines[start:end])
    head_end = section.index("\n\n")
    section = new_header.rstrip("\n") + section[head_end:]
    section = rename(section)
    assert "// 107（1.9）対応" in section
    if lines[-1] == "}":
        return "\n".join(lines[:end]) + "\n" + section + "\n}\n"
    return "\n".join(lines[:end]) + "\n" + section + "\n}"


CHUNK_HEADER = """    // ------------------------------------------------------------------
    // 107（1.9）対応
    //
    // 107 は 1.9 のプロトコル。107 と 108 は Join Game の dimension
    // （107 は i8）を除きレジストリ・ワイヤ形式とも完全同一
    // （Stage A 実測）ため、この節は 108 の節を写して RegistryRemapper
    // の 107 写像（108 へ全面委譲）に差し替えたもの。チャンク末尾に
    // blockEntities が無い扱いも 108 と同じ。ブロックエンティティ名は
    // 旧式名（"Furnace" 等、名前空間なし）で、名前表は
    // RegistryTables110（107／108／109 と同一内容）をそのまま参照する。
    //
    // 107 固有の癖：107 バックエンドの Chunk Data は dataLen に大きな
    // ゼロ埋めを含む（Stage A 実測：dataLen 184,665 に対し実データ
    // 30,809＋バイオーム 256、残り 153,600 バイトが全てゼロ）。この
    // 節の読み取りは dataLen 分を切り出した部分バッファをセクション
    // 順次読みし、部分バッファの末尾一致を要求しないため、ゼロ埋めは
    // 自然に捨てられる（単体テストで固定）。
    // ------------------------------------------------------------------
"""

SLOT_HEADER = """    // ------------------------------------------------------------------
    // 107（1.9）対応
    //
    // 107 のスロット形式は 108 と完全同一（itemId i16＋count i8＋
    // itemDamage i16＋NBT、メタデータの型は i8 で 0〜12 のみ）の
    // ため、この節は 108 の節を写して番号だけ 107 に差し替えた
    // もの。メタデータ型 13〜16 の落としも 108 と同じ
    // （writeMetadataEntries107）。パケット名の引き直しは
    // PacketIds の 107 列（108 列と同値）を使う。
    // ------------------------------------------------------------------
"""

chunk_path = PROTO / "ChunkTranslator.java"
chunk_path.write_text(
    copy_tail_section(chunk_path, "// 108（1.9.1）対応", CHUNK_HEADER),
    encoding="utf-8")
print("ChunkTranslator: 107 節を追加")

slot_path = PROTO / "SlotTranslator.java"
slot_path.write_text(
    copy_tail_section(slot_path, "// 108（1.9.1）対応", SLOT_HEADER),
    encoding="utf-8")
print("SlotTranslator: 107 節を追加")

# --- PlayTranslator：Join Game 節（手書き）＋ Respawn 委譲 ---
play_path = PROTO / "PlayTranslator.java"
play = play_path.read_text(encoding="utf-8")
if "JoinGame107" in play:
    print("PlayTranslator: 107 節は追加済みのため飛ばす")
    raise SystemExit(0)
anchor = """    /**
     * Player Info（759 0x34 → 758 0x36）。"""
assert play.count(anchor) == 1
section = '''    /**
     * 107 の Join Game。108 との唯一の形式差分は dimension が
     * i8（107）⇔ i32（108 以降）であること（Stage A 実測：
     * 実バニラ 1.9 の生バイト 17 バイトを i8 で読み切れることを確認）。
     */
    public record JoinGame107(int entityId, int gameMode, int dimension,
                              int difficulty, int maxPlayers, String levelType,
                              boolean reducedDebug) {
    }

    /** 107 形式の Join Game を読む。dimension は符号付きバイト。 */
    public static JoinGame107 parseJoinGame107(byte[] payload) {
        Reader reader = new Reader(payload);
        int entityId = reader.readInt();
        int gameMode = reader.readUnsignedByte();
        int dimension = reader.readByte();
        int difficulty = reader.readUnsignedByte();
        int maxPlayers = reader.readUnsignedByte();
        String levelType = reader.readString();
        boolean reducedDebug = reader.readBoolean();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Join Game（107）の末尾に余分なバイトがあります");
        }
        return new JoinGame107(entityId, gameMode, dimension, difficulty,
                maxPlayers, levelType, reducedDebug);
    }

    /**
     * 758 形式の Join Game を 107 形式に直す。108 形式（dimension i32）
     * へ直してから読み直し、dimension だけ符号付きバイトで書き直す。
     */
    public static byte[] translateJoinGame758To107(byte[] payload) {
        byte[] as108 = translateJoinGame758To108(payload);
        try {
            JoinGame108 join = parseJoinGame108(as108);
            Writer writer = new Writer();
            writer.writeInt(join.entityId());
            writer.writeByte(join.gameMode());
            writer.writeByte(join.dimension());
            writer.writeByte(join.difficulty());
            writer.writeByte(join.maxPlayers());
            writer.writeString(join.levelType());
            writer.writeBoolean(join.reducedDebug());
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Join Game（758→107）の変換に失敗したため、そのまま転送します: "
                    + e.getMessage());
            return payload;
        }
    }

    /**
     * 107 形式の Join Game を 758 形式に直す。404 形式（dimension i32）
     * を合成してから 404→758 変換に渡す。
     */
    public static byte[] translateJoinGame107To758(byte[] payload) {
        try {
            JoinGame107 join = parseJoinGame107(payload);
            Writer writer = new Writer();
            writer.writeInt(join.entityId());
            writer.writeByte(join.gameMode());
            writer.writeInt(join.dimension());
            writer.writeByte(join.difficulty());
            writer.writeByte(join.maxPlayers());
            writer.writeString(join.levelType());
            writer.writeBoolean(join.reducedDebug());
            return translateJoinGame404To758(writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("Join Game（107→758）の変換に失敗したため、そのまま転送します: "
                    + e.getMessage());
            return payload;
        }
    }

    /**
     * 758 形式の Respawn を 107 形式に直す。Respawn の形式は 107・108 で
     * 完全同一（dimension は両方とも i32。Stage A の protocol.json
     * 全面比較で確定）のため 108 の変換へ委譲する。
     */
    public static byte[] translateRespawn758To107(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn758To108(payload, registries);
    }

    /** 107 形式の Respawn を 758 形式に直す（108 の変換へ委譲）。 */
    public static byte[] translateRespawn107To758(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn108To758(payload, registries);
    }

'''
play = play.replace(anchor, section + anchor, 1)
play_path.write_text(play, encoding="utf-8")
print("PlayTranslator: 107 の Join Game 節（i8 dimension）を追加")
