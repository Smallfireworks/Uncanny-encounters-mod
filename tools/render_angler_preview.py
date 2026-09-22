"""Run with Blender --background --python tools/render_angler_preview.py.

An offline geometry preview, not a Minecraft screenshot.
"""
import json
from pathlib import Path
import bpy
from mathutils import Vector

root = Path(__file__).resolve().parents[1]
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
atlas = bpy.data.images.load(str(root / 'src/main/resources/assets/uncannyencounters/textures/entity/cave_angler.png'))
material = bpy.data.materials.new('Pixel atlas')
material.use_nodes = True
shader = next(n for n in material.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
shader.inputs['Roughness'].default_value = 0.9
texture = material.node_tree.nodes.new('ShaderNodeTexImage')
texture.image = atlas
texture.interpolation = 'Closest'
material.node_tree.links.new(texture.outputs['Color'], shader.inputs['Base Color'])

for cube in json.loads((root / 'reference/low_poly/cave_angler.geometry.json').read_text()):
    x, y, z = cube['xyz']; w, h, d = cube['size']
    bpy.ops.mesh.primitive_cube_add(size=1, location=((x+w/2)/16, (z+d/2)/16, (24-y-h/2)/16))
    obj = bpy.context.object
    obj.name = cube['name']
    obj.dimensions = (w/16, d/16, h/16)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.data.materials.append(material)
    # Match material band and pixel density, using planar face UVs for this preview.
    for face in obj.data.polygons:
        axes = [i for i in range(3) if i != max(range(3), key=lambda a: abs(face.normal[a]))]
        for loop in face.loop_indices:
            v = obj.data.vertices[obj.data.loops[loop].vertex_index].co
            u = (v[axes[0]] * 16 + 16) / 128
            vv = 1 - (cube['uv'][1] + v[axes[1]] * 16 + 16) / 128
            obj.data.uv_layers.active.data[loop].uv = (u, vv)

def aim(obj, point):
    obj.rotation_euler = (Vector(point) - obj.location).to_track_quat('-Z', 'Y').to_euler()

bpy.ops.object.camera_add(location=(3, -5, 2.3))
camera = bpy.context.object
aim(camera, (0, 0, 0.8))
camera.data.type = 'ORTHO'
camera.data.ortho_scale = 2.8
bpy.context.scene.camera = camera
for location, energy, size in [((2, -4, 5), 450, 4), ((-3, -1, 2), 180, 3), ((0, 3, 4), 350, 3)]:
    bpy.ops.object.light_add(type='AREA', location=location)
    light = bpy.context.object
    light.data.energy = energy
    light.data.shape = 'DISK'
    light.data.size = size
    aim(light, (0, 0, 1))
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = 32
scene.world.color = (0.18, 0.18, 0.18)
scene.render.resolution_x = 840
scene.render.resolution_y = 920
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.filepath = str(root / 'reference/low_poly/cave_angler-preview.png')
scene.render.film_transparent = True
bpy.ops.wm.save_as_mainfile(filepath=str(root / 'reference/low_poly/cave_angler-preview.blend'))
bpy.ops.render.render(write_still=True)
