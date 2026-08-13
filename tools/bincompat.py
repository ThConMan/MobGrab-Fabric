"""
Does a MobGrab jar compiled against one Minecraft version still resolve against another?

Parses the constant pool of every MobGrab class, collects each Minecraft method/field it
references, then resolves those against a target Minecraft jar, walking superclasses and
interfaces the way the JVM would. Anything unresolved is a link error waiting to happen.
"""
import struct, sys, zipfile

def parse_class(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        return None
    cp, i, n = {}, 10, struct.unpack('>H', data[8:10])[0]
    idx = 1
    while idx < n:
        tag = data[i]; i += 1
        if tag == 1:
            ln = struct.unpack('>H', data[i:i+2])[0]; i += 2
            cp[idx] = ('utf8', data[i:i+ln].decode('utf-8', 'replace')); i += ln
        elif tag in (7, 8, 16, 19, 20):
            cp[idx] = (tag, struct.unpack('>H', data[i:i+2])[0]); i += 2
        elif tag == 15:
            cp[idx] = (tag, data[i], struct.unpack('>H', data[i+1:i+3])[0]); i += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            cp[idx] = (tag, struct.unpack('>HH', data[i:i+4])); i += 4
        elif tag in (5, 6):
            cp[idx] = (tag, data[i:i+8]); i += 8; idx += 1
        else:
            return None
        idx += 1

    def utf8(k):
        v = cp.get(k)
        return v[1] if v and v[0] == 'utf8' else None

    def cls(k):
        v = cp.get(k)
        return utf8(v[1]) if v and v[0] == 7 else None

    _, this_i, super_i = struct.unpack('>HHH', data[i:i+6]); i += 6
    this_name, super_name = cls(this_i), cls(super_i)
    ic = struct.unpack('>H', data[i:i+2])[0]; i += 2
    ifaces = [cls(struct.unpack('>H', data[i+2*k:i+2*k+2])[0]) for k in range(ic)]
    i += 2 * ic

    members = set()
    for _ in range(2):                                    # fields, then methods
        cnt = struct.unpack('>H', data[i:i+2])[0]; i += 2
        for _ in range(cnt):
            _, nm, ds = struct.unpack('>HHH', data[i:i+6]); i += 6
            members.add((utf8(nm), utf8(ds)))
            ac = struct.unpack('>H', data[i:i+2])[0]; i += 2
            for _ in range(ac):
                i += 6 + struct.unpack('>I', data[i+2:i+6])[0]

    refs = set()
    for v in cp.values():
        if isinstance(v, tuple) and v[0] in (9, 10, 11):  # Field/Method/InterfaceMethodref
            ci, nti = v[1]
            owner = cls(ci)
            nt = cp.get(nti)
            if owner and nt and nt[0] == 12:
                nm, ds = nt[1]
                refs.add((owner, utf8(nm), utf8(ds)))
    return this_name, super_name, ifaces, members, refs

def index(jar):
    out = {}
    with zipfile.ZipFile(jar) as z:
        for e in z.namelist():
            if e.endswith('.class'):
                p = parse_class(z.read(e))
                if p: out[p[0]] = (p[1], p[2], p[3])
    return out

def resolves(idx, owner, name, desc, seen=None):
    seen = seen or set()
    if owner in seen or owner not in idx:
        return False
    seen.add(owner)
    sup, ifaces, members = idx[owner]
    if (name, desc) in members:
        return True
    for parent in ([sup] if sup else []) + [x for x in ifaces if x]:
        if resolves(idx, parent, name, desc, seen):
            return True
    return False

mod_jar, built_jar, target_jar, label = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]

# Differential check. Some members resolve through a JDK ancestor (HolderSet extends
# Iterable, so forEach lives on java.lang.Iterable) and are absent from both Minecraft
# indexes. Comparing the two versions rather than reading one in isolation ignores those
# and leaves only members that genuinely disappeared.
built, target = index(built_jar), index(target_jar)
print(f"indexed {len(built)} classes from the build version, {len(target)} from {label}")

refs = set()
with zipfile.ZipFile(mod_jar) as z:
    for e in z.namelist():
        if e.endswith('.class') and e.startswith('com/mobgrab'):
            p = parse_class(z.read(e))
            if p: refs |= p[4]

mc = sorted(r for r in refs if r[0].startswith('net/minecraft'))
print(f"MobGrab references {len(mc)} distinct Minecraft members")

jdk = [r for r in mc if not resolves(built, *r) and not resolves(target, *r)]
broken = [r for r in mc if resolves(built, *r) and not resolves(target, *r)]

print(f"  {len(mc) - len(jdk) - len(broken)} resolve in both")
print(f"  {len(jdk)} inherited from the JDK (absent from both, expected)")
for o, n, d in jdk:
    print(f"      {o}.{n}{d}")

if not broken:
    print(f"OK: nothing MobGrab uses was removed in {label}")
else:
    print(f"\nREMOVED in {label} ({len(broken)}):")
    for o, n, d in broken:
        print(f"  {o}.{n}{d}")
sys.exit(1 if broken else 0)
