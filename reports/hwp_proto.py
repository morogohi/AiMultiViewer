# -*- coding: utf-8 -*-
"""HWP 5.0 텍스트 추출 알고리즘 검증 프로토타입 (Kotlin 포팅 전 확인용)."""
import sys
import zlib
import struct
import olefile

HWPTAG_PARA_TEXT = 67  # HWPTAG_BEGIN(16) + 51

# 제어문자 크기: 인라인/확장 컨트롤은 8 WCHAR(16바이트) 차지
EXTENDED = {1, 2, 3, 11, 12, 14, 15, 16, 17, 18, 21, 22, 23}
INLINE = {4, 5, 6, 7, 8, 9, 19, 20}


def extract_para_text(payload: bytes) -> str:
    out = []
    i = 0
    n = len(payload)
    while i + 1 < n:
        ch = struct.unpack_from("<H", payload, i)[0]
        if ch < 32:
            if ch in EXTENDED or ch in INLINE:
                i += 16  # 자신(2) + 나머지 7 WCHAR(14)
                continue
            if ch == 10 or ch == 13:
                out.append("\n")
            elif ch == 24:  # 하이픈
                out.append("-")
            elif ch in (30, 31):  # 묶음/고정폭 빈칸
                out.append(" ")
            i += 2
            continue
        out.append(chr(ch))
        i += 2
    return "".join(out)


def parse_records(data: bytes):
    pos = 0
    while pos + 4 <= len(data):
        (hdr,) = struct.unpack_from("<I", data, pos)
        tag = hdr & 0x3FF
        size = (hdr >> 20) & 0xFFF
        pos += 4
        if size == 0xFFF:
            (size,) = struct.unpack_from("<I", data, pos)
            pos += 4
        yield tag, data[pos:pos + size]
        pos += size


def main(path):
    ole = olefile.OleFileIO(path)
    header = ole.openstream("FileHeader").read()
    sig = header[:17].decode("ascii", "replace")
    flags = struct.unpack_from("<I", header, 36)[0]
    compressed = flags & 1
    encrypted = flags & 2
    distribution = flags & 4
    ver = struct.unpack_from("<I", header, 32)[0]
    print(f"signature={sig!r} version={ver:08x} compressed={compressed} "
          f"encrypted={encrypted} distribution={distribution}")
    if encrypted or distribution:
        print("암호화/배포용 문서 — 텍스트 추출 불가")
        return

    sections = sorted(
        (e for e in ole.listdir() if e[0] == "BodyText"),
        key=lambda e: int(e[1].replace("Section", ""))
    )
    print("sections:", sections)
    texts = []
    for entry in sections:
        raw = ole.openstream(entry).read()
        data = zlib.decompress(raw, -15) if compressed else raw
        for tag, payload in parse_records(data):
            if tag == HWPTAG_PARA_TEXT:
                t = extract_para_text(payload)
                if t.strip():
                    texts.append(t)
    result = "\n".join(texts)
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        f.write(result)
    print(f"chars={len(result)}")


if __name__ == "__main__":
    main(sys.argv[1])
