# -*- coding: utf-8 -*-
"""HWP 5.0 서식(글자모양/문단모양/이미지) 추출 검증 프로토타입."""
import sys
import io
import zlib
import struct
import olefile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

TAG_BIN_DATA = 18
TAG_CHAR_SHAPE = 21
TAG_PARA_SHAPE = 25
TAG_PARA_HEADER = 66
TAG_PARA_TEXT = 67
TAG_PARA_CHAR_SHAPE = 68
TAG_CTRL_HEADER = 71
TAG_SHAPE_PIC = 85

EXTENDED = {1, 2, 3, 11, 12, 14, 15, 16, 17, 18, 21, 22, 23}
INLINE = {4, 5, 6, 7, 8, 9, 19, 20}


def records(data):
    pos = 0
    while pos + 4 <= len(data):
        (hdr,) = struct.unpack_from("<I", data, pos)
        tag = hdr & 0x3FF
        level = (hdr >> 10) & 0x3FF
        size = (hdr >> 20) & 0xFFF
        pos += 4
        if size == 0xFFF:
            (size,) = struct.unpack_from("<I", data, pos)
            pos += 4
        yield tag, level, data[pos:pos + size]
        pos += size


def decode_with_pos(payload):
    """(decoded_chars, raw_positions) — raw pos는 WCHAR 단위, 컨트롤=8칸"""
    chars, poss = [], []
    i = 0
    raw = 0
    n = len(payload)
    while i + 1 < n:
        ch = struct.unpack_from("<H", payload, i)[0]
        if ch < 32:
            if ch in EXTENDED or ch in INLINE:
                i += 16
                raw += 8
                continue
            if ch in (10, 13):
                chars.append("\n"); poss.append(raw)
            elif ch == 24:
                chars.append("-"); poss.append(raw)
            elif ch in (30, 31):
                chars.append(" "); poss.append(raw)
            i += 2
            raw += 1
            continue
        chars.append(chr(ch)); poss.append(raw)
        i += 2
        raw += 1
    return "".join(chars), poss


def main(path):
    ole = olefile.OleFileIO(path)
    header = ole.openstream("FileHeader").read()
    compressed = struct.unpack_from("<I", header, 36)[0] & 1

    raw = ole.openstream("DocInfo").read()
    docinfo = zlib.decompress(raw, -15) if compressed else raw

    char_shapes = []   # (sizePt, bold, italic, color)
    para_shapes = []   # align
    bin_items = []
    for tag, level, payload in records(docinfo):
        if tag == TAG_CHAR_SHAPE and len(payload) >= 56:
            base_size = struct.unpack_from("<i", payload, 42)[0]
            attr = struct.unpack_from("<I", payload, 46)[0]
            color = struct.unpack_from("<I", payload, 52)[0]
            r, g, b = color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF
            char_shapes.append((base_size / 100.0, bool(attr & 2), bool(attr & 1), (r, g, b)))
        elif tag == TAG_PARA_SHAPE and len(payload) >= 4:
            attr1 = struct.unpack_from("<I", payload, 0)[0]
            align = (attr1 >> 2) & 7
            para_shapes.append(align)
        elif tag == TAG_BIN_DATA:
            attr = struct.unpack_from("<H", payload, 0)[0]
            btype = attr & 0xF
            item = None
            ext = ""
            if btype in (1, 2) and len(payload) >= 4:
                item = struct.unpack_from("<H", payload, 2)[0]
                if btype == 1 and len(payload) >= 6:
                    elen = struct.unpack_from("<H", payload, 4)[0]
                    ext = payload[6:6 + elen * 2].decode("utf-16-le", "replace")
            bin_items.append((btype, item, ext))

    print(f"CHAR_SHAPES ({len(char_shapes)}):")
    for i, cs in enumerate(char_shapes):
        print(f"  [{i}] size={cs[0]}pt bold={cs[1]} italic={cs[2]} rgb={cs[3]}")
    print(f"PARA_SHAPES ({len(para_shapes)}): aligns={para_shapes}")
    print(f"BIN_DATA: {bin_items}")
    print("BinData streams:", [e for e in ole.listdir() if e[0] == "BinData"])

    # 본문 첫 30개 문단: paraShapeId + runs
    for entry in sorted((e for e in ole.listdir() if e[0] == "BodyText"),
                        key=lambda e: int(e[1].replace("Section", ""))):
        data = ole.openstream(entry).read()
        if compressed:
            data = zlib.decompress(data, -15)
        para_shape_id = None
        shown = 0
        pending_runs = None
        for tag, level, payload in records(data):
            if shown >= 25:
                break
            if tag == TAG_PARA_HEADER and len(payload) >= 10:
                para_shape_id = struct.unpack_from("<H", payload, 8)[0]
            elif tag == TAG_PARA_CHAR_SHAPE:
                pairs = []
                for off in range(0, len(payload) - 7, 8):
                    p, sid = struct.unpack_from("<II", payload, off)
                    pairs.append((p, sid))
                pending_runs = pairs
            elif tag == TAG_PARA_TEXT:
                text, poss = decode_with_pos(payload)
                if not text.strip():
                    continue
                align = para_shapes[para_shape_id] if para_shape_id is not None and para_shape_id < len(para_shapes) else "?"
                print(f"\nPARA(lv{level}) shape={para_shape_id} align={align} runs={pending_runs}")
                if pending_runs:
                    for j, (p, sid) in enumerate(pending_runs):
                        end = pending_runs[j + 1][0] if j + 1 < len(pending_runs) else 10**9
                        seg = "".join(c for c, rp in zip(text, poss) if p <= rp < end)
                        cs = char_shapes[sid] if sid < len(char_shapes) else None
                        print(f"  run sid={sid} {cs}: {seg[:40]!r}")
                else:
                    print(f"  text: {text[:60]!r}")
                shown += 1
            elif tag == TAG_SHAPE_PIC:
                item68 = struct.unpack_from("<H", payload, 68)[0] if len(payload) >= 70 else None
                print(f"\nSHAPE_PICTURE len={len(payload)} binItem@68={item68}")


if __name__ == "__main__":
    main(sys.argv[1])
