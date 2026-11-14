#!/usr/bin/env python3
"""実バニラ1.21.9へ偽773クライアントで直接つなぐ第2ラウンド。

- 0x01 Custom Payload のチャンネル名を記録する
- Finish Configuration を受けたら Acknowledge（設定 0x03）を送り、
  Join Game（プレイ 0x30）が届くか確認する
- 各イベントの時刻を記録し、Finish までの遅れを測る
"""
import socket
import struct
import sys
import time
import zlib

PORT = int(sys.argv[1])
T0 = time.monotonic()
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
    # varint 長さ＋UTF-8
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


def main():
    sock = socket.create_connection(("127.0.0.1", PORT), timeout=30)
    f = sock.makefile("rwb")
    send_packet(f, 0x00, varint(773) + write_string("127.0.0.1") + struct.pack(">H", PORT) + varint(2))
    log("handshake 送信 (773)")
    send_packet(f, 0x00, write_string("Probe2") + bytes.fromhex("00000000000000000000000000000002"))
    log("login start 送信")
    state = "login"
    finish_at = None
    join_at = None
    deadline = time.monotonic() + 90
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
                    log("Select Known Packs に応答（0件）")
                elif pid == 0x09:
                    send_packet(f, 0x06, payload[:16] + varint(3))
                    log("Add Resource Pack に応答（accepted）")
                elif pid == 0x13:
                    log("** Code of Conduct (0x13) を受信 **")
                elif pid == 0x03:
                    finish_at = time.monotonic() - T0
                    log("** Finish Configuration (0x03) を受信 **")
                    send_packet(f, 0x03, b"")
                    log("Acknowledge Finish Configuration (0x03) を送信")
                    state = "play"
                elif pid == 0x07:
                    pass
                elif pid == 0x0D:
                    pass
                else:
                    log(f"config パケット id=0x{pid:02x} len={len(payload)}")
            else:  # play
                if pid == 0x30:
                    join_at = time.monotonic() - T0
                    log(f"** Join Game (0x30) を受信 len={len(payload)} **")
                    break
                elif pid == 0x2B:
                    send_packet(f, 0x1B, payload)
                elif pid == 0x3B:
                    send_packet(f, 0x2C, payload)
                else:
                    log(f"play パケット id=0x{pid:02x} len={len(payload)}")
    except socket.timeout:
        log("タイムアウト")
    finally:
        sock.close()
    print("=== 結果 ===", flush=True)
    print(f"Finish Configuration: {'あり (' + format(finish_at, '.2f') + 's)' if finish_at else 'なし'}", flush=True)
    print(f"Join Game: {'あり (' + format(join_at, '.2f') + 's)' if join_at else 'なし'}", flush=True)


if __name__ == "__main__":
    main()
