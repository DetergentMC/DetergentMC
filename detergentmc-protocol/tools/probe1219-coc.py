#!/usr/bin/env python3
"""実バニラ1.21.9へ偽773クライアントで直接つなぎ、Code of Conduct の扱いを調べる。

- ハンドシェイク(773) → Login Start → Login Success → Login Acknowledged
- Client Information（9フィールド、772と同じ）を送る
- 設定フェーズで届くパケットのIDと時刻を記録する
- Code of Conduct（設定 0x13）が届いたら、まず accept を送らずに
  Finish Configuration（設定 0x03）が来るか 15 秒待つ
- 来なければ Accept（設定サーバー行き 0x09、空ペイロード）を送り、
  Finish Configuration が来るか 10 秒待つ

使い方: python3 probe1219-coc.py <port> [send-accept-at-start]
第2引数が "accept" の場合、0x13 を受けた直後に accept を送る。
"""
import socket
import struct
import sys
import time
import zlib

PORT = int(sys.argv[1])
ACCEPT_IMMEDIATELY = len(sys.argv) > 2 and sys.argv[2] == "accept"

T0 = time.monotonic()
# ログインの Set Compression（0x03）を受けたら有効化される。
COMPRESSION = [False]


def log(msg):
    print(f"[{time.monotonic() - T0:6.2f}s] {msg}", flush=True)


def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def read_varint(f):
    n = 0
    shift = 0
    while True:
        b = f.read(1)
        if not b:
            raise EOFError("接続が切れました")
        v = b[0]
        n |= (v & 0x7F) << shift
        if not (v & 0x80):
            return n
        shift += 7


def read_exact(f, n):
    buf = b""
    while len(buf) < n:
        chunk = f.read(n - len(buf))
        if not chunk:
            raise EOFError("接続が切れました")
        buf += chunk
    return buf


def send_packet(f, pid, payload):
    frame = varint(pid) + payload
    if COMPRESSION[0]:
        # 小さいパケットは data length 0（非圧縮）で送る。
        f.write(varint(len(frame) + 1) + b"\x00" + frame)
    else:
        f.write(varint(len(frame)) + frame)
    f.flush()


def read_packet(f):
    length = read_varint(f)
    frame = read_exact(f, length)
    if COMPRESSION[0]:
        # 先頭が data length の varint。0＝非圧縮。
        idx = 0
        dlen = 0
        shift = 0
        while True:
            v = frame[idx]
            idx += 1
            dlen |= (v & 0x7F) << shift
            if not (v & 0x80):
                break
            shift += 7
        if dlen == 0:
            frame = frame[idx:]
        else:
            frame = zlib.decompress(frame[idx:])
    # packet id は varint
    idx = 0
    pid = 0
    shift = 0
    while True:
        v = frame[idx]
        idx += 1
        pid |= (v & 0x7F) << shift
        if not (v & 0x80):
            break
        shift += 7
    return pid, frame[idx:]


def write_string(s):
    b = s.encode("utf-8")
    return varint(len(b)) + b


def main():
    sock = socket.create_connection(("127.0.0.1", PORT), timeout=30)
    f = sock.makefile("rwb")

    # ハンドシェイク（773、ログイン状態へ）
    payload = varint(773) + write_string("127.0.0.1") + struct.pack(">H", PORT) + varint(2)
    send_packet(f, 0x00, payload)
    log("handshake 送信 (773)")

    # Login Start（名前＋UUID）
    uuid = bytes.fromhex("00000000000000000000000000000001")
    send_packet(f, 0x00, write_string("Probe") + uuid)
    log("login start 送信")

    state = "login"
    coc_at = None
    coc_accepted = False
    finish_at = None
    deadline = time.monotonic() + 60
    try:
        while time.monotonic() < deadline:
            # 残り時間でタイムアウト付き受信
            sock.settimeout(max(0.1, deadline - time.monotonic()))
            try:
                pid, payload = read_packet(f)
            except socket.timeout:
                continue
            except EOFError as e:
                log(f"EOF: {e}")
                break
            if state == "login":
                log(f"login パケット id=0x{pid:02x} len={len(payload)}")
                if pid == 0x03:  # Set Compression
                    COMPRESSION[0] = True
                    log("Set Compression を受信（以降は圧縮形式）")
                elif pid == 0x02:  # Login Success
                    log("Login Success を受信")
                    send_packet(f, 0x03, b"")  # Login Acknowledged
                    log("Login Acknowledged 送信")
                    # Client Information（9フィールド）
                    ci = (write_string("ja_JP") + bytes([8]) + varint(0) + b"\x01"
                          + bytes([0x7F]) + varint(1) + b"\x00\x00" + varint(0))
                    send_packet(f, 0x00, ci)
                    log("Client Information 送信")
                    state = "config"
                elif pid == 0x01:  # Encryption Request（online-mode なら来る）
                    log("Encryption Request が来た（online-mode のため続行不可）")
                    return
                elif pid == 0x00:  # Disconnect
                    log(f"Disconnect: {payload!r}")
                    return
            else:
                log(f"config パケット id=0x{pid:02x} len={len(payload)}")
                if pid == 0x04:  # Keep Alive
                    send_packet(f, 0x04, payload)  # 同じ long を返す
                    log("  -> Keep Alive に応答")
                elif pid == 0x05:  # Ping
                    send_packet(f, 0x05, payload)
                    log("  -> Ping に応答")
                elif pid == 0x0E:  # Select Known Packs
                    send_packet(f, 0x07, varint(0))
                    log("  -> Select Known Packs に応答（0件）")
                elif pid == 0x09:  # Add Resource Pack
                    # payload 先頭16バイトが UUID
                    send_packet(f, 0x06, payload[:16] + varint(3))
                    log("  -> Add Resource Pack に応答（accepted）")
                elif pid == 0x13:  # Code of Conduct
                    coc_at = time.monotonic() - T0
                    log("  ** Code of Conduct (0x13) を受信 **")
                    if ACCEPT_IMMEDIATELY:
                        send_packet(f, 0x09, b"")
                        coc_accepted = True
                        log("  -> Accept Code of Conduct (0x09 空) を即送信")
                    else:
                        log("  -> accept は送らずに Finish Configuration を待つ")
                elif pid == 0x03:  # Finish Configuration
                    finish_at = time.monotonic() - T0
                    log("  ** Finish Configuration (0x03) を受信 **")
                    break
                elif pid == 0x00:  # Disconnect
                    log(f"  ** Disconnect: {payload!r}")
                    break
            # accept を送らずに 15 秒待っても Finish が来なければ送る
            if (coc_at is not None and not coc_accepted
                    and time.monotonic() - T0 - coc_at > 15
                    and finish_at is None):
                send_packet(f, 0x09, b"")
                coc_accepted = True
                log("  -> 15秒待っても Finish が来ないため Accept (0x09 空) を送信")
                deadline = time.monotonic() + 15
    except socket.timeout:
        log("タイムアウト")
    finally:
        sock.close()
    print("=== 結果 ===", flush=True)
    print(f"Code of Conduct 受信: {'あり (' + format(coc_at, '.2f') + 's)' if coc_at is not None else 'なし'}", flush=True)
    print(f"Accept 送信: {'あり' if coc_accepted else 'なし'}", flush=True)
    print(f"Finish Configuration 受信: {'あり (' + format(finish_at, '.2f') + 's)' if finish_at is not None else 'なし'}", flush=True)


if __name__ == "__main__":
    main()
