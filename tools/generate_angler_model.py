"""Build editable cuboid source, pixel atlas and Java geometry using only stdlib.

Coordinates use Minecraft model space: Y points down; feet are at Y=24.
Original GLB/OBJ files are never modified.
"""
import base64
from itertools import combinations
import json
from pathlib import Path
import random
import struct
import uuid
import zlib

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/uncannyencounters'
MODEL = ROOT / 'src/client/java/com/ignilumen/uncannyencounters/client/model'
SOURCE = ROOT / 'reference/low_poly'
for directory in [ASSETS / 'textures/entity', MODEL, SOURCE]:
    directory.mkdir(parents=True, exist_ok=True)

# Four atlas bands: limestone, mossy limestone, exposed flesh and ivory.
palettes = [(111, 106, 91), (87, 88, 58), (111, 41, 44), (193, 180, 143)]
rng = random.Random(713)
pixels = bytearray()
for y in range(128):
    pixels.append(0)
    for x in range(128):
        base = palettes[y // 32]
        cell = random.Random((x // 3) * 971 + (y // 3) * 3191).randint(-20, 20)
        grain = rng.randint(-5, 5)
        pixels.extend(max(0, min(255, c + cell + grain)) for c in base)
        pixels.append(255)

def chunk(kind, payload):
    return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload))

png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 128, 128, 8, 6, 0, 0, 0))
png += chunk(b'IDAT', zlib.compress(bytes(pixels), 9)) + chunk(b'IEND', b'')
(ASSETS / 'textures/entity/cave_angler.png').write_bytes(png)

# Standalone spawn-egg icon: 26.3 no longer ships template_spawn_egg.
egg = bytearray()
for y in range(16):
    egg.append(0)
    for x in range(16):
        dx, dy = (x - 7.5) / (4.5 if y < 7 else 5.5), (y - 8) / 6.5
        if dx * dx + dy * dy > 1:
            egg.extend((0, 0, 0, 0))
            continue
        edge = dx * dx + dy * dy > 0.72
        spot = (x//2 + (y//2)*3) % 7 < 2
        color = (62, 55, 46) if edge else ((114, 44, 48) if spot else (146, 140, 117))
        egg.extend((*color, 255))
egg_png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
egg_png += chunk(b'IDAT', zlib.compress(bytes(egg), 9)) + chunk(b'IEND', b'')
(ASSETS / 'textures/item').mkdir(parents=True, exist_ok=True)
(ASSETS / 'textures/item/cave_angler_spawn_egg.png').write_bytes(egg_png)

cubes = []
def box(name, xyz, size, material=0, group='body'):
    # Box UV footprint fits within one 128x32 material band.
    w, h, d = size
    assert 2 * (w + d) <= 128 and h + d <= 32
    cubes.append(dict(name=name, xyz=list(xyz), size=list(size), uv=[0, material * 32], group=group))

box('ceiling_plate', (-12, -4, -10), (24, 4, 20))
box('upper_core', (-9, 0, -7), (18, 8, 14), 1)
box('middle_core', (-7, 8, -5), (14, 7, 11), 1)
box('lower_back', (-5, 15, -1), (10, 7, 6))
box('mouth_cavity', (-4, 14, -4), (8, 8, 2), 2)
box('mouth_left', (-6, 12, -6), (2, 11, 5))
box('mouth_right', (4, 12, -6), (2, 11, 5))
box('chin', (-4, 22, -5), (8, 2, 5))
# Asymmetric hanging rock ribs preserve the silhouette of the reference.
for i, (x, z, start, length, width) in enumerate([
    (-11, -9, -0.25, 7, 3), (-7, -9, -0.25, 12, 3), (-2, -9, -0.25, 10, 3),
    (3, -9, -0.25, 14, 3), (8, -8, -0.25, 8, 3), (-10, -3, 1, 12, 3),
    (8, -3, 1, 15, 3), (-9.25, 4.25, -0.25, 10, 3), (6.25, 5, -0.25, 12, 3),
    (-4, 6, 4, 13, 3), (1, 7, 2, 12, 3), (-7.25, -6.25, 9, 12, 2),
    (5.25, -6.25, 10, 10, 2), (-3, 3, 16, 8, 3), (1, 3.25, 16.25, 6, 2),
]):
    box(f'rock_rib_{i}', (x, start, z), (width, length, width))
for i, x in enumerate([-3, 0, 3]):
    box(f'upper_fang_{i}', (x - 0.5, 14, -6), (1, 3 if i != 1 else 2, 2), 3)
    box(f'lower_fang_{i}', (x - 0.5, 20, -6), (1, 2, 2), 3)
# Short resting tongue. The deployed tongue is rendered from synchronized endpoints.
# Keep the tongue in front of the center tooth over its entire +/-0.35 Z sway.
box('resting_tongue', (-1, 19.75, -6.5), (2, 5, 2), 2, 'tongue')
box('resting_hook', (-2, 24, -7), (4, 1, 2), 3, 'tongue')

def validate_faces(cubes):
    """Reject overlapping faces with the same normal, including during idle sway.

    Opposite-facing joins between adjacent solid cubes are intentional. A pair
    of equally oriented, coplanar faces is a depth conflict even if textures match.
    """
    conflicts = []
    for a, b in combinations(cubes, 2):
        for axis in range(3):
            for side in (0, 1):
                offsets = [0.0]
                a_moves, b_moves = a['group'] == 'tongue', b['group'] == 'tongue'
                pa = a['xyz'][axis] + side * a['size'][axis]
                pb = b['xyz'][axis] + side * b['size'][axis]
                if a_moves != b_moves:
                    if axis == 2:
                        # Exact sway value at which these two face planes coincide.
                        offsets = [(pb - pa) / (int(a_moves) - int(b_moves))]
                    else:
                        # Closest alignment of the Z intervals gives their maximum
                        # overlap over the continuous sway, including narrow cubes.
                        center_delta = b['xyz'][2] + b['size'][2] / 2 - a['xyz'][2] - a['size'][2] / 2
                        offsets = [max(-0.35, min(0.35, center_delta / (int(a_moves) - int(b_moves))))]
                for offset in offsets:
                    if abs(offset) > 0.350001:
                        continue
                    ap = [p + (offset if i == 2 and a_moves else 0) for i, p in enumerate(a['xyz'])]
                    bp = [p + (offset if i == 2 and b_moves else 0) for i, p in enumerate(b['xyz'])]
                    if abs(ap[axis] + side * a['size'][axis] - bp[axis] - side * b['size'][axis]) > 1e-6:
                        continue
                    if all(min(ap[i] + a['size'][i], bp[i] + b['size'][i]) - max(ap[i], bp[i]) > 1e-6
                           for i in range(3) if i != axis):
                        conflicts.append(f"{a['name']} / {b['name']}: {'XYZ'[axis]} face {side}")
                        break
    if conflicts:
        raise ValueError('Coplanar model faces:\n' + '\n'.join(conflicts))

validate_faces(cubes)

(SOURCE / 'cave_angler.geometry.json').write_text(json.dumps(cubes, indent=2), encoding='utf-8')

java = ['package com.ignilumen.uncannyencounters.client.model;', '',
        'import net.minecraft.client.model.geom.PartPose;',
        'import net.minecraft.client.model.geom.builders.*;', '',
        '/** Generated by tools/generate_angler_model.py; edit the generator to change geometry. */',
        'public final class CaveAnglerGeometry {',
        '    private CaveAnglerGeometry() {}',
        '    public static LayerDefinition createLayer() {',
        '        MeshDefinition mesh = new MeshDefinition();',
        '        PartDefinition root = mesh.getRoot();']
for group in ['body', 'tongue']:
    java.append(f'        CubeListBuilder {group} = CubeListBuilder.create();')
    for cube in [c for c in cubes if c['group'] == group]:
        args = ', '.join(f'{float(n)}F' for n in cube['xyz'] + cube['size'])
        java.append(f'        {group}.texOffs({cube["uv"][0]}, {cube["uv"][1]}).addBox({args});')
    java.append(f'        root.addOrReplaceChild("{group}", {group}, PartPose.ZERO);')
java += ['        return LayerDefinition.create(mesh, 128, 128);', '    }', '}']
(MODEL / 'CaveAnglerGeometry.java').write_text('\n'.join(java) + '\n', encoding='utf-8')

def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'uncannyencounters/cave_angler/' + name))

elements = []
groups = []
for c in cubes:
    x, y, z = c['xyz']; w, h, d = c['size']; u, v = c['uv']
    # Java model Y-down -> Blockbench Y-up, preserving model feet at zero.
    faces = {
        'north': [u+d, v+d, u+d+w, v+d+h],
        'east': [u, v+d, u+d, v+d+h],
        'south': [u+2*d+w, v+d, u+2*d+2*w, v+d+h],
        'west': [u+d+w, v+d, u+2*d+w, v+d+h],
        'up': [u+d, v, u+d+w, v+d],
        'down': [u+d+w, v, u+d+2*w, v+d],
    }
    elements.append(dict(name=c['name'], type='cube', uuid=uid(c['name']),
                         **{'from': [x, 24-y-h, z], 'to': [x+w, 24-y, z+d]},
                         box_uv=True, uv_offset=c['uv'], autouv=0, color=0,
                         faces={f: {'uv': uv, 'texture': 0} for f, uv in faces.items()}))
for group in ['body', 'tongue']:
    groups.append(dict(name=group, uuid=uid(group), origin=[0, 0, 0], rotation=[0, 0, 0],
                       children=[uid(c['name']) for c in cubes if c['group'] == group]))
bbmodel = dict(meta={'format_version': '4.10', 'model_format': 'modded_entity', 'box_uv': True},
               name='cave_angler', model_identifier='cave_angler', visible_box=[3, 4, 1],
               resolution={'width': 128, 'height': 128}, elements=elements, outliner=groups,
               textures=[dict(name='cave_angler.png', id='0', uuid=uid('texture'),
                              mode='bitmap', saved=False, width=128, height=128,
                              source='data:image/png;base64,' + base64.b64encode(png).decode())])
(SOURCE / 'cave_angler.bbmodel').write_text(json.dumps(bbmodel, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Generated {len(cubes)} cuboids / {len(cubes)*12} triangles, 128x128 atlas, Java geometry and .bbmodel')
