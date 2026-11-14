"""BedrockData canonical_block_states.nbt (varint/network NBT) の読み取り。"""
import struct

def uvarint(d, p):
    r = 0; s = 0
    while True:
        b = d[p]; p += 1
        r |= (b & 0x7F) << s
        if not (b & 0x80): break
        s += 7
    return r, p

def svarint(d, p):
    u, p = uvarint(d, p)
    return (u >> 1) ^ -(u & 1), p

def read_name(d, p):
    n, p = uvarint(d, p)
    return d[p:p+n].decode('utf-8'), p+n

def read_payload(d, p, t):
    if t == 0: return None, p
    if t == 1: return d[p], p+1
    if t in (2, 3, 4): return svarint(d, p)
    if t == 5: return struct.unpack_from('<f', d, p)[0], p+4
    if t == 6: return struct.unpack_from('<d', d, p)[0], p+8
    if t == 7:
        n, p = uvarint(d, p); return list(d[p:p+n]), p+n
    if t == 8:
        s, p = read_name(d, p); return s, p
    if t == 9:
        et = d[p]; p += 1
        n, p = uvarint(d, p)
        out = []
        for _ in range(n):
            v, p = read_payload(d, p, et); out.append(v)
        return out, p
    if t == 10:
        obj = {}
        while True:
            ct = d[p]; p += 1
            if ct == 0: break
            nm, p = read_name(d, p)
            v, p = read_payload(d, p, ct)
            obj[nm] = v
        return obj, p
    if t == 11:
        n, p = uvarint(d, p)
        return list(struct.unpack_from('<%di' % n, d, p)), p+4*n
    if t == 12:
        n, p = uvarint(d, p)
        return list(struct.unpack_from('<%dq' % n, d, p)), p+8*n
    raise ValueError('type %d at %d' % (t, p))

def parse_canonical(path):
    """(entries, slices) を返す。entries[i] = {'name','states','version'}。
    slices[i] = (start, end) バイト範囲（StartGame 用にそのまま使える形）。"""
    data = open(path, 'rb').read()
    entries, slices = [], []
    pos = 0
    while pos < len(data):
        start = pos
        t = data[pos]; pos += 1
        assert t == 10, (t, pos)
        name, pos = read_name(data, pos)
        comp, pos = read_payload(data, pos, 10)
        entries.append(comp)
        slices.append((start, pos))
    assert pos == len(data), (pos, len(data))
    return entries, slices, data
