"""Convert the supplied GLB into native animated meshes; no preview/game is rendered.

blender --background --factory-startup --python tools/generate_light_moth_model.py
"""
import base64
import json
import struct
import uuid
from pathlib import Path
import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/uncannyencounters'
EDITABLE = ROOT / 'reference/low_poly'
TEMP = ROOT / '.gradle/light-moth-audit/model'
for folder in [ASSETS/'geometry', ASSETS/'textures/entity', EDITABLE, TEMP]:
    folder.mkdir(parents=True, exist_ok=True)

source = (ROOT/'reference/夺光蛾.glb').read_bytes()
json_size = struct.unpack_from('<I', source, 12)[0]
gltf = json.loads(source[20:20+json_size])
binary = source[28+json_size:]

def accessor(index):
    a = gltf['accessors'][index]
    view = gltf['bufferViews'][a['bufferView']]
    components = {'SCALAR':1, 'VEC2':2, 'VEC3':3}[a['type']]
    fmt = {5126:'f', 5125:'I', 5123:'H'}[a['componentType']]
    stride = view.get('byteStride', struct.calcsize(fmt)*components)
    offset = view.get('byteOffset', 0) + a.get('byteOffset', 0)
    return [struct.unpack_from('<'+fmt*components, binary, offset+i*stride) for i in range(a['count'])]

primitive = gltf['meshes'][0]['primitives'][0]
vertices = accessor(primitive['attributes']['POSITION'])
uvs = accessor(primitive['attributes']['TEXCOORD_0'])
indices = [v[0] for v in accessor(primitive['indices'])]
faces = [indices[i:i+3] for i in range(0,len(indices),3)]
material = gltf['materials'][primitive['material']]
texture_index = material['pbrMetallicRoughness']['baseColorTexture']['index']
image = gltf['images'][gltf['textures'][texture_index]['source']]
view = gltf['bufferViews'][image['bufferView']]
texture_path = TEMP/'original-color.png'
texture_path.write_bytes(binary[view['byteOffset']:view['byteOffset']+view['byteLength']])

for obj in list(bpy.data.objects):
    bpy.data.objects.remove(obj,do_unlink=True)
mesh = bpy.data.meshes.new('light_moth_reference')
mesh.from_pydata(vertices,[],faces)
mesh.uv_layers.new(name='ReferenceUV').data.foreach_set('uv',[c for face in faces for i in face for c in uvs[i]])
mesh.update()
obj = bpy.data.objects.new('Light Moth',mesh)
bpy.context.collection.objects.link(obj)
bpy.context.view_layer.objects.active = obj
obj.select_set(True)

detail = obj.vertex_groups.new(name='Head antennae and wing edges')
protected = [i for i,(x,y,z) in enumerate(vertices) if abs(x)>.43 or (z>-.12 and abs(x)<.20)]
detail.add(protected,1,'REPLACE')
modifier = obj.modifiers.new('Conservative detail reduction','DECIMATE')
modifier.ratio = min(1,16000/len(faces))
modifier.use_collapse_triangulate = True
modifier.vertex_group = detail.name
modifier.invert_vertex_group = True
modifier.vertex_group_factor = .01
bpy.ops.object.modifier_apply(modifier=modifier.name)
mesh = obj.data
mesh.calc_loop_triangles()

texture = bpy.data.images.load(str(texture_path),check_existing=False)
if max(texture.size)>2048:
    ratio = 2048/max(texture.size)
    texture.scale(round(texture.size[0]*ratio),round(texture.size[1]*ratio))
width,height = texture.size
texture.filepath_raw = str(ASSETS/'textures/entity/light_moth.png')
texture.file_format = 'PNG'
texture.save()

# This asset's mesh has its belly toward +Y and its head toward -Z. Minecraft
# models use +Y down and -Z forward: rotate the previous Y/Z mapping by 180
# degrees about X. Keep the lowest point at model Y=24 and transform pivots
# together with the mesh; the GLB scene's display rotation is not a flight pose.
lower = [min(v[a] for v in vertices) for a in range(3)]
upper = [max(v[a] for v in vertices) for a in range(3)]
scale = 16*.9/(upper[0]-lower[0])
cx,cz = (upper[0]+lower[0])/2,(upper[2]+lower[2])/2
def convert(p):
    return Vector(((p[0]-cx)*scale,24-(upper[1]-p[1])*scale,(p[2]-cz)*scale))

bind = {'body':('root',(cx,upper[1],cz)),
        'left_wing':('body',(.105,0,-.25)), 'right_wing':('body',(-.105,0,-.25))}
origins = {name:convert(p) for name,(_,p) in bind.items()}
parts = {name:dict(name=name,parent=parent,origin=list(origins[name]),triangles=[])
         for name,(parent,_) in bind.items()}
uv_layer = mesh.uv_layers.active.data
for triangle in mesh.loop_triangles:
    raw = [mesh.vertices[i].co for i in triangle.vertices]
    center = sum(raw,Vector())/3
    # Keep the head/antennae on the body; broad lateral surfaces form each wing.
    name = ('left_wing' if center.x>0 else 'right_wing') if abs(center.x)>.105 and center.z<-.12 else 'body'
    points = [convert(p)-origins[name] for p in raw]
    normal = (points[1]-points[0]).cross(points[2]-points[0])
    if normal.length<1e-8: continue
    normal.normalize()
    row = list(normal)
    for point,loop in zip(points,triangle.loops):
        uv = uv_layer[loop].uv
        # GLTF UV is top-down; Blender stores these manually without an import flip.
        row.extend((*point,uv.x,uv.y))
    parts[name]['triangles'].append([round(v,6) for v in row])

payload = dict(format=1,texture=[width,height],parts=list(parts.values()))
(ASSETS/'geometry/light_moth.json').write_text(json.dumps(payload,separators=(',',':'))+'\n',encoding='utf-8')
summary = dict(original_triangles=len(faces),triangles=sum(len(p['triangles']) for p in parts.values()),
               protected_vertices=len(protected),double_sided=material.get('doubleSided',False),
               texture=[width,height],size_blocks=[round((upper[a]-lower[a])*scale/16,4) for a in range(3)],
               parts=[dict(name=p['name'],triangles=len(p['triangles']),origin=p['origin']) for p in parts.values()])
(EDITABLE/'light_moth.geometry.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')

def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL,'uncannyencounters/light_moth/'+name))
elements=[]
for part in parts.values():
    points,bbfaces={},{}
    origin=Vector(part['origin'])
    for index,row in enumerate(part['triangles']):
        keys,uv=[],{}
        for j in range(3):
            x,y,z,u,v=row[3+j*5:8+j*5]
            key=f'{index}_{j}'; world=origin+Vector((x,y,z))
            points[key]=[round(world.x,6),round(24-world.y,6),round(world.z,6)]
            keys.append(key); uv[key]=[u*width,v*height]
        bbfaces[str(index)]=dict(vertices=list(reversed(keys)),uv=uv,texture=0)
    elements.append(dict(name=part['name']+'_mesh',type='mesh',uuid=uid(part['name']+'_mesh'),origin=[0,0,0],vertices=points,faces=bbfaces))
def group(name):
    origin=origins[name]
    return dict(name=name,uuid=uid(name),origin=[origin.x,24-origin.y,origin.z],rotation=[0,0,0],
                children=[uid(name+'_mesh')]+[group(child) for child,(parent,_) in bind.items() if parent==name])
png=(ASSETS/'textures/entity/light_moth.png').read_bytes()
model=dict(meta={'format_version':'4.10','model_format':'free','box_uv':False},name='light_moth',
           resolution={'width':width,'height':height},elements=elements,outliner=[group('body')],
           textures=[dict(name='light_moth.png',id='0',uuid=uid('texture'),mode='bitmap',saved=False,width=width,height=height,
                          source='data:image/png;base64,'+base64.b64encode(png).decode())])
(EDITABLE/'light_moth.bbmodel').write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf-8')
print(json.dumps(summary),flush=True)
