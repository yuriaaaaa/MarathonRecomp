# Classifies Sonic '06 Mod Manager patches (.mlua) by what they touch (code or data, which
# files), against the mapped default.xex image that host/xexinfo writes. Used while porting
# the Mod Manager's patch engine; not needed for the build.
# Usage: python3 classify_patches.py <patches dir> <image.bin> [classified.json]
import os, re, sys, json
PATCHES, IMAGE = sys.argv[1], sys.argv[2]
OUT = sys.argv[3] if len(sys.argv) > 3 else 'classified.json'
CODE = [(0x82160000, 0x82aeaa6c), (0x82aeac00, 0x82b0abb8)]
image = open(IMAGE, 'rb').read()
def is_code(va): return any(a <= va < b for a, b in CODE)
def params(line, cmd):
    inner = line.strip()[len(cmd) + 1:]
    inner = inner[:inner.rfind(')')]
    parts = inner.split('|')
    return [p[1:-1] if len(p) >= 2 and p.startswith('"') and p.endswith('"') else p for p in parts]
def phys2va(off): return 0x82000000 + off - 0x3000
out = {}
for name in sorted(os.listdir(PATCHES)):
    if not name.endswith('.mlua'): continue
    text = open(os.path.join(PATCHES, name), encoding='utf-8', errors='replace').read()
    meta = {k: (re.search(k + r'\("((?:[^"\\]|\\.)*)"\)', text) or [None, ''])[1] for k in ['Title', 'Author', 'Platform', 'Blurb']}
    writes, cmds = [], set()
    section = 'All Systems'
    for raw in text.splitlines():
        line = raw.strip()
        if line in ('All Systems:', 'Xbox 360:', 'PlayStation 3:'): section = line[:-1]; continue
        if section == 'PlayStation 3': continue
        m = re.match(r'([A-Za-z]+)\(', line)
        if not m: continue
        cmd = m.group(1)
        if cmd in ('Title', 'Author', 'Platform', 'Blurb', 'Description'): continue
        cmds.add(cmd)
        p = params(line, cmd)
        try:
            if cmd == 'WriteVirtualBytes': writes.append((int(p[0], 16), bytes.fromhex(p[1].replace(' ', ''))))
            elif cmd == 'WriteVirtualNop':
                n = int(p[1]) if len(p) > 1 else 1; writes.append((int(p[0], 16), bytes.fromhex('60000000') * n))
            elif p and p[0] == 'Executable':
                off = int(p[1], 16)
                if cmd == 'WriteBytes': writes.append((phys2va(off), bytes.fromhex(p[2].replace(' ', ''))))
                elif cmd == 'WriteByte': writes.append((phys2va(off), bytes([int(p[2], 16)])))
                elif cmd == 'WriteNullBytes': writes.append((phys2va(off), bytes(int(p[2]))))
                elif cmd == 'WriteNopPPC': writes.append((phys2va(off), bytes.fromhex('60000000') * (int(p[2]) if len(p) > 2 else 1)))
                elif cmd == 'WriteTextBytes': writes.append((phys2va(off), p[2].encode('ascii')))
        except Exception as e:
            cmds.add('PARSE_ERROR:' + cmd)
    code = [w for w in writes if is_code(w[0])]
    data = [w for w in writes if not is_code(w[0])]
    archive_cmds = cmds - {'DecryptExecutable', 'EncryptExecutable', 'WriteVirtualBytes', 'WriteVirtualNop'}
    out[name] = dict(meta, cmds=sorted(cmds), code_writes=len(code), data_writes=len(data),
                     data=[(hex(a), b.hex()) for a, b in data])
json.dump(out, open(OUT, 'w'), indent=1)
nc = sum(1 for v in out.values() if v['code_writes'] == 0)
print('patches', len(out), 'without code writes', nc)
for k, v in out.items():
    if v['code_writes'] == 0 and v['data_writes']:
        print('DATA-ONLY XEX', k, v['data'][:4])
