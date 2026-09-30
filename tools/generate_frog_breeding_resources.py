"""Pixel assets and recipes for frog cages and the breeding box (Pillow)."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/uncannyencounters'
DATA = ROOT / 'data/uncannyencounters'


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def save(image, path):
    target = ASSETS / 'textures' / path
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)


def frog(draw, x, y):
    draw.rectangle((x, y + 2, x + 5, y + 4), fill=(175, 122, 215, 255))
    draw.rectangle((x + 1, y, x + 2, y + 2), fill=(213, 178, 243, 255))
    draw.rectangle((x + 4, y, x + 5, y + 2), fill=(213, 178, 243, 255))
    draw.point((x + 1, y + 1), fill=(39, 28, 57, 255))
    draw.point((x + 4, y + 1), fill=(39, 28, 57, 255))
    draw.line((x - 1, y + 5, x + 6, y + 5), fill=(126, 86, 164, 255))


for name, occupied in [('frog_cage', False), ('caged_crystal_frog', True)]:
    icon = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(icon)
    d.rectangle((6, 0, 9, 3), outline=(199, 183, 212, 255))
    d.rectangle((1, 4, 14, 14), fill=(39, 27, 54, 235), outline=(172, 154, 183, 255))
    d.rectangle((1, 3, 14, 4), fill=(125, 87, 155, 255))
    d.rectangle((1, 13, 14, 15), fill=(113, 79, 51, 255))
    if occupied:
        frog(d, 5, 6)
    for x in [3, 12]:
        d.line((x, 5, x, 12), fill=(208, 192, 219, 255))
    save(icon, f'item/{name}.png')
    write(ASSETS / f'models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'uncannyencounters:item/{name}'}})
    write(ASSETS / f'items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'uncannyencounters:item/{name}'}})

side = Image.new('RGBA', (16, 16), (115, 78, 49, 255))
d = ImageDraw.Draw(side)
d.rectangle((1, 1, 14, 14), outline=(188, 139, 215, 255))
d.rectangle((2, 3, 13, 11), fill=(41, 31, 54, 255), outline=(175, 194, 212, 255))
frog(d, 5, 5)
d.line((8, 3, 8, 11), fill=(168, 190, 200, 255))
d.rectangle((0, 13, 15, 15), fill=(91, 62, 44, 255))
save(side, 'block/frog_breeding_box_side.png')
top = Image.new('RGBA', (16, 16), (117, 81, 53, 255))
d = ImageDraw.Draw(top)
d.rectangle((1, 1, 14, 14), outline=(180, 131, 218, 255))
for y in [4, 8, 12]:
    d.line((2, y, 13, y), fill=(80, 56, 42, 255))
d.polygon([(8, 3), (12, 8), (8, 12), (4, 8)], fill=(169, 120, 213, 255))
d.polygon([(8, 4), (10, 8), (8, 10), (6, 8)], fill=(229, 198, 247, 255))
save(top, 'block/frog_breeding_box_top.png')
write(ASSETS / 'blockstates/frog_breeding_box.json', {'variants': {'': {'model': 'uncannyencounters:block/frog_breeding_box'}}})
write(ASSETS / 'models/block/frog_breeding_box.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
    'top': 'uncannyencounters:block/frog_breeding_box_top', 'side': 'uncannyencounters:block/frog_breeding_box_side', 'bottom': 'minecraft:block/oak_planks'}})
write(ASSETS / 'models/item/frog_breeding_box.json', {'parent': 'uncannyencounters:block/frog_breeding_box'})
write(ASSETS / 'items/frog_breeding_box.json', {'model': {'type': 'minecraft:model', 'model': 'uncannyencounters:item/frog_breeding_box'}})
write(DATA / 'loot_table/blocks/frog_breeding_box.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
    'entries': [{'type': 'minecraft:item', 'name': 'uncannyencounters:frog_breeding_box'}],
    'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})

recipes = {
    'frog_cage': (['SNS', 'NAN', 'SNS'], {'S': 'minecraft:stick', 'N': 'minecraft:iron_nugget', 'A': 'minecraft:amethyst_shard'}),
    'frog_breeding_box': (['PGP', 'ACA', 'PPP'], {'P': '#minecraft:planks', 'G': 'minecraft:glass', 'A': 'minecraft:amethyst_shard', 'C': 'minecraft:chest'})
}
for name, (pattern, key) in recipes.items():
    write(DATA / f'recipe/{name}.json', {'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': key,
        'result': {'id': f'uncannyencounters:{name}', 'count': 1}})
    write(DATA / f'advancement/recipes/misc/{name}.json', {'parent': 'minecraft:recipes/root', 'criteria': {
        'has_amethyst': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': 'minecraft:amethyst_shard'}]}},
        'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipes': f'uncannyencounters:{name}'}}},
        'requirements': [['has_amethyst', 'has_the_recipe']], 'rewards': {'recipes': [f'uncannyencounters:{name}']}})

axe_tag = ROOT / 'data/minecraft/tags/block/mineable/axe.json'
axe_data = json.loads(axe_tag.read_text(encoding='utf-8')) if axe_tag.exists() else {'replace': False, 'values': []}
if 'uncannyencounters:frog_breeding_box' not in axe_data['values']:
    axe_data['values'].append('uncannyencounters:frog_breeding_box')
write(axe_tag, axe_data)
print('Generated frog cage and breeding box resources.')
