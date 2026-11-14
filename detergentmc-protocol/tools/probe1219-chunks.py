#!/usr/bin/env python3
"""実バニラ1.21.9へ偽773クライアントでつなぎ、プレイ開始後のパケット到着順を記録する。

目的：BedrockMatrixIntegrationTest.matrixAgainst1_21_9/1_21_10 で
スポーン地点のチャンクが90秒届かなかった原因の切り分け。
- Join Game (0x30)、Spawn Position (0x5F)、Chunk Batch Start (0x0C)、
  Chunk Data (0x2C)、Chunk Batch Finished (0x0B) の到着時刻と順序を記録する。
- --ack を付けると Chunk Batch Finished のたびに Chunk Batch Received
  (サーバー行き 0x0A、float 2.0) を返す。付けない場合は Ack を送らない。
  両モードでチャンクが流れるかを比較する。

使い方：vanilla 1.21.9 サーバーを別途 25599 番などで起動してから
  python3 probe1219-chunks.py 25599 [--ack]
"""
import socket
import struct
import sys
import time
import zlib

PORT = int(sys.argv[1])
ACK = "--ack" in sys.argv
T0 = time.monotonic()
COMPRESSION = [False]
COUNTS = {}


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
        f.write(varint(len(frame) + 1) + b"\x00" + frame)
    else:
        f.write(varint(len(frame)) + frame)
    f.flush()


def read_packet(f):
    length = read_varint(f)
    frame = read_exact(f, length)
    if COMPRESSION[0]:
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
        frame = frame[idx:] if dlen == 0 else zlib.decompress(frame[idx:])
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


def read_string(payload, off):
    n = 0
    shift = 0
    while True:
        v = payload[off]
        off += 1
        n |= (v & 0x7F) << shift
        if not (v & 0x80):
            break
        shift += 7
    return payload[off:off + n].decode("utf-8", "replace"), off + n


NAMES = {
    0x30: "Join Game",
    0x5F: "Spawn Position",
    0x0C: "Chunk Batch Start",
    0x2C: "Chunk Data",
    0x0B: "Chunk Batch Finished",
    0x2B: "Keep Alive",
    0x3B: "Ping",
    0x20: "Disconnect",
    0x50: "Respawn",
    0x44: "Player Info Update",
    0x08: "Chat Message",
    0x36: "Set Held Item",
    0x3F: "Player Chat",
}


def decode_position(packed):
    # Javaの (packed>>38) / (packed<<52>>52) / (packed<<26>>38) と等価な
    # 符号付きデコード。Pythonのintは任意精度のためビットマスクで切り出す。
    x = (packed >> 38) & 0x3FFFFFF
    if x >= (1 << 25):
        x -= (1 << 26)
    y = packed & 0xFFF
    if y >= (1 << 11):
        y -= (1 << 12)
    z = (packed >> 12) & 0x3FFFFFF
    if z >= (1 << 25):
        z -= (1 << 26)
    return x, y, z


def note(pid, payload):
    COUNTS[pid] = COUNTS.get(pid, 0) + 1
    name = NAMES.get(pid, f"id=0x{pid:02x}")
    if pid == 0x2C and len(payload) >= 8:
        cx, cz = struct.unpack(">ii", payload[:8])
        log(f"Chunk Data chunk=({cx},{cz}) len={len(payload)} (通算 {COUNTS[pid]} 件)")
    elif pid == 0x5F and len(payload) >= 8:
        packed = struct.unpack(">q", payload[:8])[0]
        x, y, z = decode_position(packed)
        log(f"Spawn Position ({x},{y},{z}) chunk=({x // 16},{z // 16})")
    elif pid in (0x30, 0x0C, 0x0B, 0x20, 0x50):
        log(f"** {name} (0x{pid:02x}) len={len(payload)} **")
    # その他は数えるだけ（ログを増やさない）


def main():
    log(f"接続開始 port={PORT} ack={'ON' if ACK else 'OFF'}")
    sock = socket.create_connection(("127.0.0.1", PORT), timeout=30)
    f = sock.makefile("rwb")
    send_packet(f, 0x00, varint(773) + write_string("127.0.0.1") + struct.pack(">H", PORT) + varint(2))
    send_packet(f, 0x00, write_string("ProbeChunk") + bytes.fromhex("00000000000000000000000000000003"))
    log("handshake＋login start 送信")
    state = "login"
    join_at = None
    deadline = time.monotonic() + 150
    try:
        while time.monotonic() < deadline:
            sock.settimeout(max(0.1, deadline - time.monotonic()))
            try:
                pid, payload = read_packet(f)
            except socket.timeout:
                continue
            except EOFError as e:
                log(f"EOF: {e}")
                break
            if state == "login":
                if pid == 0x03:
                    COMPRESSION[0] = True
                    log("Set Compression を受信")
                elif pid == 0x02:
                    log("Login Success を受信")
                    send_packet(f, 0x03, b"")
                    ci = (write_string("ja_JP") + bytes([8]) + varint(0) + b"\x01"
                          + bytes([0x7F]) + varint(1) + b"\x00\x00" + varint(0))
                    send_packet(f, 0x00, ci)
                    log("Login Acknowledged＋Client Information 送信")
                    state = "config"
                elif pid == 0x00:
                    log(f"Disconnect: {payload[:80]!r}")
                    return
                else:
                    log(f"login パケット id=0x{pid:02x} len={len(payload)}")
            elif state == "config":
                if pid == 0x01:
                    try:
                        channel, _ = read_string(payload, 0)
                        log(f"config Custom Payload チャンネル={channel!r} len={len(payload)}")
                    except Exception:
                        log(f"config Custom Payload len={len(payload)} 先頭={payload[:16]!r}")
                elif pid == 0x04:
                    send_packet(f, 0x04, payload)
                elif pid == 0x05:
                    send_packet(f, 0x05, payload)
                elif pid == 0x0E:
                    send_packet(f, 0x07, varint(0))
                elif pid == 0x09:
                    send_packet(f, 0x06, payload[:16] + varint(3))
                elif pid == 0x13:
                    log("** Code of Conduct (0x13) を受信 **")
                elif pid == 0x03:
                    log("** Finish Configuration (0x03) を受信 **")
                    send_packet(f, 0x03, b"")
                    state = "play"
                elif pid in (0x07, 0x0D):
                    pass
                else:
                    log(f"config パケット id=0x{pid:02x} len={len(payload)}")
            else:  # play
                if join_at is None and pid == 0x30:
                    join_at = time.monotonic() - T0
                    log(f"** Join Game (0x30) を受信 len={len(payload)} **")
                if pid == 0x2B:
                    send_packet(f, 0x1B, payload)
                elif pid == 0x3B:
                    send_packet(f, 0x2C, payload)
                elif pid == 0x0B and ACK:
                    send_packet(f, 0x0A, struct.pack(">f", 2.0))
                    log("Chunk Batch Received (0x0A) を送信")
                note(pid, payload)
                # Join Game から75秒たったら終わる
                if join_at is not None and time.monotonic() - T0 - join_at > 75:
                    log("Join Game から75秒経過。終了。")
                    break
    except socket.timeout:
        log("タイムアウト")
    finally:
        sock.close()
    print("=== 結果 ===", flush=True)
    print(f"ack={'ON' if ACK else 'OFF'}", flush=True)
    for pid in sorted(COUNTS):
        print(f"  {NAMES.get(pid, 'id=0x%02x' % pid)} (0x{pid:02x}): {COUNTS[pid]} 件", flush=True)


if __name__ == "__main__":
    main()
