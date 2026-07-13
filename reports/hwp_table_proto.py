# -*- coding: utf-8 -*-
"""HWP 5.0 표 구조(행/열/셀) 추출 알고리즘 검증 프로토타입."""
import sys
import io
import zlib
import struct
import olefile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

TAG_PARA_HEADER = 66
TAG_PARA_TEXT = 67
TAG_CTRL_HEADER = 71
TAG_LIST_HEADER = 72
TAG_TABLE = 77

EXTENDED = {1, 2, 3, 11, 12, 14, 15, 16, 17, 18, 21, 22, 23}
INLINE = {4, 5, 6, 7, 8, 9, 19, 20}
CTRL_TBL = (ord('t') << 24) | (ord('b') << 16) | (ord('l') << 8) | ord(' ')


def decode_text(payload):
    out = []
    i = 0
    n = len(payload)
    while i + 1 < n:
        ch = struct.unpack_from("<H", payload, i)[0]
        if ch < 32:
            if ch in EXTENDED or ch in INLINE:
                i += 16
                continue
            if ch in (10, 13):
                out.append("\n")
            elif ch == 24:
                out.append("-")
            elif ch in (30, 31):
                out.append(" ")
            i += 2
            continue
        out.append(chr(ch))
        i += 2
    return "".join(out)


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


def main(path):
    ole = olefile.OleFileIO(path)
    header = ole.openstream("FileHeader").read()
    compressed = struct.unpack_from("<I", header, 36)[0] & 1

    for entry in sorted((e for e in ole.listdir() if e[0] == "BodyText"),
                        key=lambda e: int(e[1].replace("Section", ""))):
        raw = ole.openstream(entry).read()
        data = zlib.decompress(raw, -15) if compressed else raw

        # 상태
        table = None       # dict(rows, cols, grid, ctrl_level)
        cell = None        # (row, col)
        body = []

        def flush_table():
            nonlocal table
            if table:
                print(f"### TABLE {table['rows']}x{table['cols']} (ctrl_lv={table['ctrl_level']})")
                for r in table['grid']:
                    print(" | ".join(x.replace("\n", "⏎")[:20] for x in r))
                print()
            table = None

        pending_tbl_level = None
        for tag, level, payload in records(data):
            if tag == TAG_CTRL_HEADER:
                ctrl_id = struct.unpack_from("<I", payload, 0)[0]
                if ctrl_id == CTRL_TBL:
                    flush_table()
                    pending_tbl_level = level
                elif table and level <= table['ctrl_level']:
                    flush_table()
            elif tag == TAG_TABLE and pending_tbl_level is not None:
                rows, cols = struct.unpack_from("<HH", payload, 4)
                table = {
                    'rows': rows, 'cols': cols,
                    'grid': [["" for _ in range(cols)] for _ in range(rows)],
                    'ctrl_level': pending_tbl_level, 'seq': 0
                }
                pending_tbl_level = None
                print(f"[TABLE record] level={level} rows={rows} cols={cols} paylen={len(payload)}")
            elif tag == TAG_LIST_HEADER and table and level > table['ctrl_level']:
                # 셀 리스트 헤더: paraCount(2)+unknown(2)+attr(4) 뒤에 col,row,colspan,rowspan
                if len(payload) >= 16:
                    col, row = struct.unpack_from("<HH", payload, 8)
                    if row < table['rows'] and col < table['cols']:
                        cell = (row, col)
                    else:
                        # 주소 해석 실패 → 순차 채움
                        seq = table['seq']
                        cell = (seq // table['cols'], seq % table['cols'])
                    table['seq'] += 1
                print(f"  [LIST_HEADER] level={level} len={len(payload)} "
                      f"hex8_16={payload[8:16].hex()} -> cell={cell}")
            elif tag == TAG_PARA_TEXT:
                t = decode_text(payload).strip()
                if not t:
                    continue
                if table and cell and level > table['ctrl_level']:
                    r, c = cell
                    g = table['grid']
                    g[r][c] = (g[r][c] + "\n" + t).strip()
                else:
                    if table:
                        flush_table()
                        cell = None
                    body.append(f"(lv{level}) {t[:60]}")
            elif tag == TAG_PARA_HEADER and table and level <= table['ctrl_level']:
                flush_table()
                cell = None
        flush_table()
        print("--- BODY ---")
        for b in body[:15]:
            print(b)


if __name__ == "__main__":
    main(sys.argv[1])
