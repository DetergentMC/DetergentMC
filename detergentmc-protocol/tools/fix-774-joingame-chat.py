#!/usr/bin/env python3
"""774統合テスト5件のうち4件の失敗（2026-11-12）の修正。

原因（実バニラ＋プロキシログで確認）：
A. Join Game が774クライアントに届かない（774×767、774×763）：
   handleServerJoinGame766／763／（汎用）／handleSynthesizedJoinGame に
   is774() 分岐が無く、774クライアントが764／765向けの 0x29（765形式）に
   落ちていた。773と774の Join Game は番号（0x30）も形式も同一のため、
   is773() 分岐に is774() を足すだけで直る。
B. チャットが774サーバーに届かない（767×774、763×774）：
   handleClientPlayTo774 に PLAY_CHAT_MESSAGE／PLAY_CHAT_COMMAND_SIGNED の
   分岐が無く、既定分岐が番号だけ付け替えて素通ししていた。770から付いた
   末尾の checksum（u8）が無いため、774バニラがチャットを読み取れない。
   763〜769クライアントには checksum 0x00 を付けて送る。

注意：同じ欠けは handleClientPlayTo773 にもある（767／763クライアント→
773サーバーのチャットも同じ理屈で届かないはず・未検証）。774専任のため
773側は直さない。制限一覧に記録する。
"""
import sys

PATH = "/home/hatch/workspace/detergentmc/detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProxySession.java"

with open(PATH, encoding="utf-8") as f:
    text = f.read()


def replace_once(old, new, label):
    global text
    n = text.count(old)
    assert n == 1, f"{label}: count={n}"
    text = text.replace(old, new)
    print(f"OK: {label}")


# --- 1. handleServerJoinGame766（内部766／767 → 774クライアント） ---
replace_once(
    """        if (is773()) {
            // 773クライアント：766の Join Game は767と同一形式のため、
            // 767→768へ直して 0x30 で送る。Add Resource Pack は 0x4F。""",
    """        if (is773() || is774()) {
            // 773／774クライアント：766の Join Game は767と同一形式のため、
            // 767→768へ直して 0x30 で送る。Add Resource Pack は 0x4F。""",
    "JoinGame766 is774",
)

# --- 2. handleServerJoinGame763（内部763 → 774クライアント。合成中でない場合） ---
replace_once(
    """        if (is773()) {
            // 773の Join Game は770／771／772と同形式だが、番号だけ 0x30（770／771／772は 0x2B）。
            // プレイ状態の Add Resource Pack は 0x4F（770／771／772は 0x4A）。
            toClient.add(new MinecraftPacket(0x30,""",
    """        if (is773() || is774()) {
            // 773／774の Join Game は770／771／772と同形式だが、番号だけ 0x30（770／771／772は 0x2B）。
            // プレイ状態の Add Resource Pack は 0x4F（770／771／772は 0x4A）。
            toClient.add(new MinecraftPacket(0x30,""",
    "JoinGame763 is774",
)

# --- 3. handleSynthesizedJoinGame（内部763の合成設定フェーズ → 774クライアント） ---
replace_once(
    """        if (is773()) {
            // 773の Join Game は770／771／772と同形式だが、番号だけ 0x30（770／771／772は 0x2B）。
            heldJoinGame = PlayTranslator.translateJoinGame767To768(""",
    """        if (is773() || is774()) {
            // 773／774の Join Game は770／771／772と同形式だが、番号だけ 0x30（770／771／772は 0x2B）。
            heldJoinGame = PlayTranslator.translateJoinGame767To768(""",
    "SynthesizedJoinGame is774",
)

# --- 4. 汎用 handleServerJoinGame（内部764／765 → 774クライアント） ---
replace_once(
    """        if (is773()) {
            // 773クライアント：dimension を文字列からレジストリ番号にし、
            // SpawnInfo の末尾に seaLevel を補う（770・771・772は769と同形式だが、
            // Join Game の番号だけ 0x30、Add Resource Pack は 0x4F）。""",
    """        if (is773() || is774()) {
            // 773／774クライアント：dimension を文字列からレジストリ番号にし、
            // SpawnInfo の末尾に seaLevel を補う（770・771・772は769と同形式だが、
            // Join Game の番号だけ 0x30、Add Resource Pack は 0x4F）。""",
    "JoinGame(764/765) is774",
)

# --- 5. handleClientPlayTo774：チャットに checksum を付ける ---
replace_once(
    """            case PLAY_PICK_ITEM -> {
                // 旧形式の Pick Item（スロット番号）は774に相当が無い。
                logUntranslatedOnce(name);
                return Outcome.drop();
            }
            case PLAY_CHAT_COMMAND -> {""",
    """            case PLAY_PICK_ITEM -> {
                // 旧形式の Pick Item（スロット番号）は774に相当が無い。
                logUntranslatedOnce(name);
                return Outcome.drop();
            }
            case PLAY_CHAT_MESSAGE, PLAY_CHAT_COMMAND_SIGNED -> {
                // Chat Message／Chat Command Signed：770から末尾に checksum（u8）が付く。
                // 763〜769クライアントには checksum が無いため 0x00 を付けて774へ送る
                // （実バニラ1.21.11は checksum 0x00 を受け入れることを
                // fake774ClientJoinsRealVanilla12111ThroughProxy で確認済み）。
                // 770〜772クライアントは checksum 付きのためそのまま送る。
                // これが無いと774サーバーがチャットを読み取れず、チャットが届かない。
                boolean needsChecksum = is763() || is764() || is765() || is766()
                        || is767() || is768() || is769();
                byte[] payload = needsChecksum
                        ? PlayTranslator.appendChatChecksum770(packet.payload())
                        : packet.payload();
                int outId = name == PacketName.PLAY_CHAT_MESSAGE ? 0x08 : 0x07;
                return Outcome.toServer(new MinecraftPacket(outId, payload));
            }
            case PLAY_CHAT_COMMAND -> {""",
    "To774 chat checksum",
)

with open(PATH, "w", encoding="utf-8") as f:
    f.write(text)
print("ALL OK")
