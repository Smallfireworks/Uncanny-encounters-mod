"""Generate the keeper's pixel textures and static structure/altar resources (Pillow)."""
from pathlib import Path
import json
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = ROOT / "assets/uncannyencounters"
DATA = ROOT / "data/uncannyencounters"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


write(DATA / "worldgen/structure/frog_court.json", {
    "type": "uncannyencounters:frog_court", "biomes": "#uncannyencounters:has_structure/frog_court",
    "spawn_overrides": {}, "step": "surface_structures", "terrain_adaptation": "beard_thin"
})
write(DATA / "worldgen/structure_set/frog_courts.json", {
    "structures": [{"structure": "uncannyencounters:frog_court", "weight": 1}],
    "placement": {"type": "minecraft:random_spread", "salt": 93742631, "spacing": 48, "separation": 16}
})
write(DATA / "tags/worldgen/biome/has_structure/frog_court.json", {
    "replace": False, "values": ["minecraft:" + biome for biome in [
        "plains", "sunflower_plains", "meadow", "forest", "birch_forest", "old_growth_birch_forest", "cherry_grove", "savanna"]]
})
write(DATA / "tags/entity_type/frog_swallow_immune.json", {
    "replace": False, "values": ["minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "uncannyencounters:frog_keeper"]
})
write(DATA / "damage_type/crystal_swallow.json", {
    "exhaustion": 0.1, "message_id": "uncannyencounters.crystal_swallow", "scaling": "never"
})
# Rewards remain undecided. There is intentionally no provisional item reward.
write(DATA / "loot_table/entities/frog_keeper.json", {"type": "minecraft:entity", "pools": []})
write(ASSETS / "blockstates/frog_altar.json", {"variants": {"": {"model": "uncannyencounters:block/frog_altar"}}})
write(ASSETS / "models/block/frog_altar.json", {
    "parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": "uncannyencounters:block/frog_altar_top", "bottom": "minecraft:block/calcite",
        "side": "minecraft:block/chiseled_quartz_block"}
})
write(ASSETS / "models/item/frog_altar.json", {"parent": "uncannyencounters:block/frog_altar"})
write(ASSETS / "items/frog_altar.json", {"model": {"type": "minecraft:model", "model": "uncannyencounters:item/frog_altar"}})
write(ASSETS / "models/item/frog_keeper_spawn_egg.json", {
    "parent": "minecraft:item/generated", "textures": {"layer0": "uncannyencounters:item/frog_keeper_spawn_egg"}
})
write(ASSETS / "items/frog_keeper_spawn_egg.json", {
    "model": {"type": "minecraft:model", "model": "uncannyencounters:item/frog_keeper_spawn_egg"}
})


def save_image(image, name):
    path = ASSETS / "textures" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


rng = random.Random(2603)
skin = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
d = ImageDraw.Draw(skin)
# Standard humanoid UV islands; outer hat remains transparent.
for box in [(0, 0, 31, 15), (0, 16, 15, 31), (16, 16, 39, 31), (40, 16, 55, 31),
            (16, 48, 31, 63), (32, 48, 47, 63)]:
    d.rectangle(box, fill=(47, 36, 65, 255))
for box in [(0, 0, 31, 15), (40, 24, 55, 31), (32, 56, 47, 63)]:
    d.rectangle(box, fill=(170, 142, 143, 255))
# Cropped grey hair, dark eyes, asymmetric crystal growth over left face.
d.rectangle((0, 0, 31, 7), fill=(67, 58, 83, 255))
d.rectangle((8, 8, 15, 9), fill=(88, 75, 105, 255))
d.rectangle((9, 11, 10, 11), fill=(29, 21, 40, 255))
d.rectangle((13, 11, 14, 11), fill=(220, 190, 255, 255))
d.rectangle((10, 14, 13, 14), fill=(92, 58, 91, 255))
for y in range(10, 16):
    for x in range(13 + (y % 2), 16):
        skin.putpixel((x, y), rng.choice([(143, 97, 206, 255), (192, 151, 241, 255), (113, 65, 166, 255)]))
# Robe trim, crystal brooch and belt on front torso; pale hands and dark boots.
d.rectangle((20, 20, 21, 31), fill=(105, 69, 149, 255))
d.rectangle((26, 20, 27, 31), fill=(105, 69, 149, 255))
d.rectangle((16, 28, 39, 29), fill=(141, 113, 76, 255))
d.polygon([(24, 20), (26, 22), (24, 25), (22, 22)], fill=(204, 164, 248, 255))
for x0, y0 in [(0, 16), (16, 48)]:
    d.rectangle((x0, y0 + 12, x0 + 15, y0 + 15), fill=(29, 23, 37, 255))
# Reserved crystal UV tile used by the model's crown/shoulder/arm protrusions.
for y in range(12):
    for x in range(52, 64):
        skin.putpixel((x, y), (min(230, 134 + (x % 4) * 22), 96 + (x % 4) * 22, 194 + (x % 3) * 20, 255))
save_image(skin, "entity/frog_keeper.png")

tongue = Image.new("RGBA", (16, 16), (156, 84, 174, 255))
d = ImageDraw.Draw(tongue)
d.rectangle((5, 0, 10, 15), fill=(227, 168, 229, 255))
d.line((8, 0, 8, 15), fill=(249, 213, 246, 255))
save_image(tongue, "entity/frog_tongue.png")

top = Image.new("RGBA", (16, 16), (65, 41, 99, 255))
d = ImageDraw.Draw(top)
d.rectangle((1, 1, 14, 14), outline=(220, 204, 239, 255))
d.polygon([(8, 3), (12, 8), (8, 12), (3, 8)], fill=(162, 118, 218, 255))
for x in [5, 10]:
    d.rectangle((x, 5, x + 1, 6), fill=(248, 235, 255, 255))
d.line((6, 9, 10, 9), fill=(236, 213, 255, 255))
save_image(top, "block/frog_altar_top.png")

egg = Image.new("RGBA", (16, 16))
d = ImageDraw.Draw(egg)
d.ellipse((3, 1, 12, 14), fill=(75, 51, 102, 255), outline=(36, 24, 51, 255))
for x, y in [(6, 3), (9, 6), (5, 9), (8, 12)]:
    d.rectangle((x, y, x + 2, y + 1), fill=(188, 149, 230, 255))
save_image(egg, "item/frog_keeper_spawn_egg.png")
print("Generated frog court, altar, keeper and swallow resources.")
