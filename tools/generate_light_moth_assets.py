"""Author small block/item assets and data for the light moth. No preview is rendered."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/uncannyencounters'
DATA=ROOT/'src/main/resources/data/uncannyencounters'
def write(base,path,data):
    p=base/path;p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def png(path,draw):
    p=ASSETS/path;p.parent.mkdir(parents=True,exist_ok=True)
    image=Image.new('RGBA',(16,16));draw(ImageDraw.Draw(image));image.save(p)

for name,parent in [('dimmed_torch','template_torch'),('dimmed_wall_torch','template_torch_wall')]:
    write(ASSETS,f'models/block/{name}.json',dict(parent=f'minecraft:block/{parent}',textures={'torch':'minecraft:block/redstone_torch_off'}))
write(ASSETS,'blockstates/dimmed_torch.json',{'variants':{'':{'model':'uncannyencounters:block/dimmed_torch'}}})
write(ASSETS,'blockstates/dimmed_wall_torch.json',{'variants':{
    f'facing={direction}':{'model':'uncannyencounters:block/dimmed_wall_torch',**({'y':angle} if angle else {})}
    for direction,angle in [('east',0),('north',270),('south',90),('west',180)]}})

def lantern_texture(draw,inner,metal):
    draw.rectangle((0,2,5,8),fill=metal)
    draw.rectangle((1,3,4,7),fill=inner)
    draw.line((2,3,2,7),fill=metal)
    draw.rectangle((1,0,4,1),fill=metal)
    draw.rectangle((0,9,5,14),fill=metal)
    draw.line((0,9,5,9),fill=tuple(min(255,c+25) for c in metal[:3])+(255,))
    draw.rectangle((11,1,13,2),fill=metal)
    draw.rectangle((11,10,13,11),fill=metal)
for name,inner,metal in [('dimmed_lantern',(42,34,26,255),(70,66,62,255)),
                          ('moth_lure',(240,222,153,255),(95,78,125,255)),
                          ('enhanced_moth_lure',(248,244,203,255),(140,109,59,255))]:
    png(f'textures/block/{name}.png',lambda d,i=inner,m=metal:lantern_texture(d,i,m))
    for suffix,parent in [('', 'template_lantern'),('_hanging','template_hanging_lantern')]:
        write(ASSETS,f'models/block/{name}{suffix}.json',dict(parent=f'minecraft:block/{parent}',textures={'lantern':f'uncannyencounters:block/{name}'}))
    write(ASSETS,f'blockstates/{name}.json',{'variants':{
        'hanging=false':{'model':f'uncannyencounters:block/{name}'},
        'hanging=true':{'model':f'uncannyencounters:block/{name}_hanging'}}})

def egg(draw):
    draw.polygon([(6,1),(9,1),(12,5),(13,10),(11,14),(4,14),(2,10),(3,5)],fill='#e6d8af',outline='#766b52')
    for x,y in [(5,4),(9,6),(4,9),(8,11),(11,10)]:draw.rectangle((x,y,x+1,y+1),fill='#594e38')
def dust(draw):
    draw.polygon([(2,11),(4,8),(6,8),(7,6),(10,7),(12,10),(14,12),(12,14),(3,14)],fill='#9b8251',outline='#594b31')
    for x,y in [(5,9),(7,8),(10,10),(8,12),(4,12),(11,12),(4,4),(11,3)]:draw.rectangle((x,y,x+1,y+1),fill='#eee3b4')
def lure_icon(draw,enhanced):
    metal='#a2844d' if enhanced else '#70578c'
    draw.rectangle((6,1,9,3),outline=metal)
    draw.rectangle((5,4,10,5),fill=metal)
    draw.rectangle((3,6,12,13),fill=metal)
    draw.rectangle((4,7,11,12),fill='#fff7ce' if enhanced else '#ecd98b')
    draw.line((7,7,7,12),fill=metal)
    draw.line((3,10,12,10),fill=metal)
    draw.rectangle((4,14,11,14),fill=metal)
icons={'light_moth_spawn_egg':egg,'moth_scale_dust':dust,
       'moth_lure':lambda d:lure_icon(d,False),'enhanced_moth_lure':lambda d:lure_icon(d,True)}
for name,draw in icons.items():
    png(f'textures/item/{name}.png',draw)
    write(ASSETS,f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'uncannyencounters:item/{name}'}})
    write(ASSETS,f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'uncannyencounters:item/{name}'}})

write(DATA,'loot_table/entities/light_moth.json',{'type':'minecraft:entity','pools':[{'rolls':1,'entries':[{
    'type':'minecraft:item','name':'uncannyencounters:moth_scale_dust','functions':[{'function':'minecraft:set_count',
    'count':{'type':'minecraft:uniform','min':1,'max':2}}]}]}]})
for name in ['moth_lure','enhanced_moth_lure']:
    write(DATA,f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,
        'entries':[{'type':'minecraft:item','name':f'uncannyencounters:{name}'}],
        'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
recipes={'moth_lure':['minecraft:lantern','minecraft:glowstone_dust','minecraft:amethyst_shard'],
         'enhanced_moth_lure':['uncannyencounters:moth_lure','uncannyencounters:moth_scale_dust']}
for name,ingredients in recipes.items():
    write(DATA,f'recipe/{name}.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':ingredients,
        'result':{'id':f'uncannyencounters:{name}','count':1}})
    ingredient='minecraft:lantern' if name=='moth_lure' else 'uncannyencounters:moth_scale_dust'
    write(DATA,f'advancement/recipes/misc/{name}.json',{'parent':'minecraft:recipes/root','criteria':{
        'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[ingredient]}]}},
        'has_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipes':f'uncannyencounters:{name}'}}},
        'requirements':[['has_material','has_recipe']],'rewards':{'recipes':[f'uncannyencounters:{name}']}})
for language,values in [('zh_cn',{
    'entity.uncannyencounters.light_moth':'夺光蛾','item.uncannyencounters.light_moth_spawn_egg':'夺光蛾刷怪蛋',
    'item.uncannyencounters.moth_scale_dust':'夺光鳞粉','block.uncannyencounters.moth_lure':'诱饵灯',
    'block.uncannyencounters.enhanced_moth_lure':'强化诱饵灯','block.uncannyencounters.dimmed_torch':'熄灭的火把',
    'block.uncannyencounters.dimmed_wall_torch':'熄灭的火把','block.uncannyencounters.dimmed_lantern':'熄灭的灯笼'}),
    ('en_us',{'entity.uncannyencounters.light_moth':'Light Moth','item.uncannyencounters.light_moth_spawn_egg':'Light Moth Spawn Egg',
    'item.uncannyencounters.moth_scale_dust':'Moth Scale Dust','block.uncannyencounters.moth_lure':'Lure Lantern',
    'block.uncannyencounters.enhanced_moth_lure':'Enhanced Lure Lantern','block.uncannyencounters.dimmed_torch':'Extinguished Torch',
    'block.uncannyencounters.dimmed_wall_torch':'Extinguished Torch','block.uncannyencounters.dimmed_lantern':'Extinguished Lantern'})]:
    path=ASSETS/f'lang/{language}.json';current=json.loads(path.read_text(encoding='utf-8'));current.update(values)
    write(ASSETS,f'lang/{language}.json',current)
print('Light moth block/item models, authored pixel textures, loot, recipes and names generated.')
