"""Generate the explicit, single-use bait recipes and shared-model lamp resources."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
BAITS = [
    ("rotten_flesh", "腐肉", "Rotten Flesh"),
    ("bone", "骨头", "Bone"),
    ("string", "线", "String"),
    ("gunpowder", "火药", "Gunpowder"),
    ("ender_pearl", "末影珍珠", "Ender Pearl"),
    ("slime_ball", "黏液球", "Slimeball"),
    ("magma_cream", "岩浆膏", "Magma Cream"),
    ("blaze_rod", "烈焰棒", "Blaze Rod"),
    ("breeze_rod", "旋风棒", "Breeze Rod"),
    ("ghast_tear", "恶魂之泪", "Ghast Tear"),
    ("prismarine_shard", "海晶碎片", "Prismarine Shard"),
]


def write(relative, value):
    path = RESOURCES / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def generate():
    assets = "assets/uncannyencounters"
    data = "data/uncannyencounters"
    languages = {
        locale: json.loads((RESOURCES / assets / "lang" / f"{locale}.json").read_text(encoding="utf-8"))
        for locale in ("zh_cn", "en_us")
    }
    for bait, chinese, english in BAITS:
        name = f"enhanced_moth_lure_{bait}"
        item = f"uncannyencounters:{name}"
        write(f"{data}/recipe/{name}.json", {
            "type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": ["uncannyencounters:enhanced_moth_lure", f"minecraft:{bait}"],
            "result": {"id": item, "count": 1},
        })
        write(f"{data}/advancement/recipes/misc/{name}.json", {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_material": {"trigger": "minecraft:inventory_changed", "conditions": {
                    "items": [{"items": ["uncannyencounters:enhanced_moth_lure"]}]}},
                "has_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipes": item}},
            },
            "requirements": [["has_material", "has_recipe"]], "rewards": {"recipes": [item]},
        })
        write(f"{data}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block", "pools": [{"rolls": 1,
                "entries": [{"type": "minecraft:item", "name": item}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        })
        write(f"{assets}/blockstates/{name}.json", {"variants": {
            "hanging=false": {"model": "uncannyencounters:block/enhanced_moth_lure"},
            "hanging=true": {"model": "uncannyencounters:block/enhanced_moth_lure_hanging"},
        }})
        write(f"{assets}/items/{name}.json", {"model": {
            "type": "minecraft:model", "model": "uncannyencounters:item/enhanced_moth_lure"}})
        languages["zh_cn"][f"block.uncannyencounters.{name}"] = f"强化诱饵灯（{chinese}）"
        languages["en_us"][f"block.uncannyencounters.{name}"] = f"Enhanced Lure Lantern ({english})"
    for locale, entries in languages.items():
        write(f"{assets}/lang/{locale}.json", entries)


if __name__ == "__main__":
    generate()
