"""Adapt reference OBJ using Blender's decimator; no preview or game is rendered.

Run: blender --background --factory-startup --python tools/generate_crystal_frog_model.py
"""
import base64
import json
from pathlib import Path
import uuid
import zipfile
import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/uncannyencounters'
SOURCE = ROOT / 'reference/low_poly'
TEMP = ROOT / '.gradle/inspected-sources/frog-revision/model'
for folder in [ASSETS / 'geometry', ASSETS / 'textures/entity', SOURCE, TEMP]:
    folder.mkdir(parents=True, exist_ok=True)

with zipfile.ZipFile(ROOT / 'reference/水晶青蛙.zip') as archive:
    data = archive.read(next(n for n in archive.namelist() if n.endswith('.obj')))
    texture_path = TEMP / 'reference-color.png'
    texture_path.write_bytes(archive.read('texture_pbr_20250901.png'))

vertices, uvs, faces, face_uvs = [], [], [], []
for line in data.splitlines():
    p = line.split()
    if not p:
        continue
    if p[0] == b'v':
        vertices.append(tuple(map(float, p[1:4])))
    elif p[0] == b'vt':
        uvs.append(tuple(map(float, p[1:3])))
    elif p[0] == b'f':
        indices = [v.split(b'/') for v in p[1:]]
        for i in range(1, len(indices)-1):
            tri = [indices[0], indices[i], indices[i+1]]
            faces.append([int(v[0])-1 for v in tri])
            face_uvs.extend(uvs[int(v[1])-1] for v in tri)
del data
for obj in list(bpy.data.objects):
    bpy.data.objects.remove(obj, do_unlink=True)
mesh = bpy.data.meshes.new('reference_crystal_frog')
mesh.from_pydata(vertices, [], faces)
mesh.uv_layers.new(name='ReferenceUV').data.foreach_set('uv', [c for uv in face_uvs for c in uv])
mesh.update()
original_faces = len(faces)
lower = [min(v[a] for v in vertices) for a in range(3)]
upper = [max(v[a] for v in vertices) for a in range(3)]
obj = bpy.data.objects.new('Crystal Frog', mesh)
bpy.context.collection.objects.link(obj)
bpy.context.view_layer.objects.active = obj
obj.select_set(True)
modifier = obj.modifiers.new('Preserve reference silhouette', 'DECIMATE')
modifier.ratio = min(1, 6000 / original_faces)
modifier.use_collapse_triangulate = True
bpy.ops.object.modifier_apply(modifier=modifier.name)
mesh = obj.data
mesh.calc_loop_triangles()
print(f'Reference reduction: {original_faces} -> {len(mesh.loop_triangles)} triangles', flush=True)

texture = bpy.data.images.load(str(texture_path), check_existing=False)
texture.scale(1024, 1024)
texture.filepath_raw = str(ASSETS / 'textures/entity/crystal_frog.png')
texture.file_format = 'PNG'
texture.save()

# Preserve original proportions; OBJ faces +Z, Minecraft models face -Z.
scale = 16 * 0.72 / (upper[0]-lower[0])
cx, cz = (upper[0]+lower[0])/2, (upper[2]+lower[2])/2
def convert(p):
    return Vector(((p[0]-cx)*scale, 24-(p[1]-lower[1])*scale, -(p[2]-cz)*scale))

bind = {
    'body': ('root', (cx, lower[1], cz)),
    'head': ('body', (0, 0.25, 0.13)),
    'left_arm': ('root', (0.24, 0.22, 0.21)),
    'right_arm': ('root', (-0.24, 0.22, 0.21)),
    'left_leg': ('root', (0.24, 0.2, -0.22)),
    'right_leg': ('root', (-0.24, 0.2, -0.22)),
    'crystals': ('body', (0, 0.29, -0.16)),
    'bond': ('body', (0, 0.19, 0.37)),
}
origins = {name: convert(pivot) for name, (_, pivot) in bind.items()}
parts = {name: dict(name=name, parent=parent, origin=list(origins[name]), triangles=[])
         for name, (parent, _) in bind.items()}

def choose_part(center):
    x, y, z = center
    side = 'left' if x > 0 else 'right'
    if abs(x) > 0.22 and y < 0.24:
        return side + ('_arm' if z > 0.04 else '_leg')
    if y > 0.35 and z < 0.08:
        return 'crystals'
    if z > 0.13 and y > 0.2 and abs(x) < 0.245:
        return 'head'
    return 'body'

uv_layer = mesh.uv_layers.active.data
for triangle in mesh.loop_triangles:
    raw = [mesh.vertices[i].co for i in triangle.vertices]
    name = choose_part(sum(raw, Vector())/3)
    points = [convert(p)-origins[name] for p in raw]
    normal = (points[1]-points[0]).cross(points[2]-points[0])
    if normal.length < 1e-8:
        continue
    normal.normalize()
    row = list(normal)
    for point, loop in zip(points, triangle.loops):
        uv = uv_layer[loop].uv
        row.extend((*point, uv.x, 1-uv.y))
    parts[name]['triangles'].append([round(v, 6) for v in row])

# Small amethyst badge for tamed frogs, colored from the original diffuse texture.
pixels = list(texture.pixels)
best = min(range(0, len(pixels), 64), key=lambda i:
           sum((pixels[i+k]-c)**2 for k, c in enumerate((0.82, 0.66, 0.94))))
pixel = best//4
badge_uv = ((pixel%1024+0.5)/1024, 1-(pixel//1024+0.5)/1024)
badge = [Vector((0,-0.3,0)), Vector((0.24,0,0)), Vector((0,0.3,0)),
         Vector((-0.24,0,0)), Vector((0,0,-0.13))]
for a, b in [(0,1),(1,2),(2,3),(3,0)]:
    points = [badge[b], badge[a], badge[4]]
    normal = (points[1]-points[0]).cross(points[2]-points[0]).normalized()
    row = list(normal)
    for point in points:
        row.extend((*point, *badge_uv))
    parts['bond']['triangles'].append([round(v, 6) for v in row])

payload = dict(format=1, texture=[1024,1024], parts=list(parts.values()))
(ASSETS/'geometry/crystal_frog.json').write_text(json.dumps(payload, separators=(',',':'))+'\n', encoding='utf-8')
summary = dict(source='reference/水晶青蛙.zip', original_triangles=original_faces,
               triangles=sum(len(p['triangles']) for p in parts.values()),
               size_blocks=[round((upper[a]-lower[a])*scale/16,4) for a in range(3)],
               parts=[dict(name=p['name'], triangles=len(p['triangles']), origin=p['origin']) for p in parts.values()])
(SOURCE/'crystal_frog.geometry.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

# Blockbench free-model meshes preserve actual triangles, UVs and movable part origins.
def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'uncannyencounters/crystal_frog/'+name))

elements = []
for part in parts.values():
    points, bbfaces = {}, {}
    origin = Vector(part['origin'])
    for index, row in enumerate(part['triangles']):
        keys, uv = [], {}
        for j in range(3):
            x, y, z, u, v = row[3+j*5:8+j*5]
            key = f'{index}_{j}'
            world = origin+Vector((x,y,z))
            points[key] = [round(world.x,6), round(24-world.y,6), round(world.z,6)]
            keys.append(key)
            uv[key] = [u*1024,v*1024]
        bbfaces[str(index)] = dict(vertices=list(reversed(keys)), uv=uv, texture=0)
    elements.append(dict(name=part['name']+'_mesh', type='mesh', uuid=uid(part['name']+'_mesh'),
                         origin=[0,0,0], vertices=points, faces=bbfaces))

def group(name):
    origin = origins[name]
    return dict(name=name, uuid=uid(name), origin=[origin.x,24-origin.y,origin.z], rotation=[0,0,0],
                children=[uid(name+'_mesh')]+[group(child) for child,(parent,_) in bind.items() if parent==name])

png = (ASSETS/'textures/entity/crystal_frog.png').read_bytes()
bbmodel = dict(meta={'format_version':'4.10','model_format':'free','box_uv':False}, name='crystal_frog',
               resolution={'width':1024,'height':1024}, elements=elements,
               outliner=[group(name) for name,(parent,_) in bind.items() if parent=='root'],
               textures=[dict(name='crystal_frog.png', id='0', uuid=uid('texture'), mode='bitmap', saved=False,
                              width=1024,height=1024,source='data:image/png;base64,'+base64.b64encode(png).decode())])
(SOURCE/'crystal_frog.bbmodel').write_text(json.dumps(bbmodel,separators=(',',':'))+'\n',encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False),flush=True)
