# -*- coding: utf-8 -*-
import sys, io, zlib, struct, olefile
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

NAMES = {66: "PARA_HEADER", 67: "PARA_TEXT", 68: "PARA_CHAR_SHAPE", 69: "PARA_LINE_SEG",
         71: "CTRL_HEADER", 72: "LIST_HEADER", 77: "TABLE", 85: "SHAPE_PIC"}

ole = olefile.OleFileIO(sys.argv[1])
header = ole.openstream("FileHeader").read()
compressed = struct.unpack_from("<I", header, 36)[0] & 1
data = ole.openstream(["BodyText", "Section0"]).read()
if compressed:
    data = zlib.decompress(data, -15)

pos = 0
count = 0
while pos + 4 <= len(data) and count < 40:
    (hdr,) = struct.unpack_from("<I", data, pos)
    tag = hdr & 0x3FF
    level = (hdr >> 10) & 0x3FF
    size = (hdr >> 20) & 0xFFF
    pos += 4
    if size == 0xFFF:
        (size,) = struct.unpack_from("<I", data, pos)
        pos += 4
    print(f"{NAMES.get(tag, tag)} lv={level} size={size}")
    pos += size
    count += 1
