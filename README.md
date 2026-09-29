# Uncanny Encounters

面向 Minecraft 26.3 / Fabric 的生物模组，当前版本为 **1.2.2**。包含洞穴垂钓者、僵尸玩家与水晶青蛙，提供简体中文和英文名称。

## 洞穴垂钓者

洞穴垂钓者会附着在洞穴天花板，捕捉从下方经过的生存或冒险模式玩家。它会将猎物悬在地面上方一格并发出尖啸，招引附近能攻击猎物的怪物；没有怪物响应时，它会把猎物拉高后释放，造成正常摔落伤害。其他生物只有在攻击它或将它作为攻击目标时才会被捕捉。

攻击舌头可以立即挣脱，舌头会短暂回缩；断舌后需要 15 秒才能再次捕捉目标。失去天花板支撑也会释放猎物。

- 24 点生命值（12 颗心），固定位置伏击。
- 在主世界 Y < 48 的黑暗、无露天视野且有合适天花板与下方空间的位置自然生成，和平难度不生成。
- 死亡掉落 1–3 个滴水石锥；经验基础值为 8，遵循原版经验掉落条件。
- 死亡时生成向下坠落的钟乳石，可能伤害下方生物。

## 僵尸玩家

生存或冒险模式玩家死亡约 3 秒后，模组会在死亡点 8 格内寻找安全位置，生成带有死者皮肤、惯用手和名字的僵尸玩家。客户端会将皮肤处理为腐化外观。

- 每位玩家最多保留一只由死亡事件生成的僵尸；它仍存在时，再次死亡不会追加生成。
- 存在时间为 10 分钟游戏时间，记录会保存；区块卸载不会释放名额，重新加载时检查是否到期。
- 20 点生命值（10 颗心），随机 10–20 点护甲，不会被阳光点燃，仍具有亡灵特性。
- 主动攻击生存或冒险模式玩家，并反击攻击者；能够寻路、开门、挖掘、搭桥、搭柱及游泳。
- 挖掘和搭建遵循 `minecraft:mob_griefing`。挖掘不直接掉落方块，原方块通常在 60 秒后恢复；搭建的僵尸方块在 30 秒后消失。实体占位会延迟恢复，玩家放置的方块占位会改为按规则掉落原方块。
- 只在主手为空时拾取武器；拾取能力在生成时按简单 / 普通 / 困难的 25% / 50% / 75% 概率决定，拾取仍受原版规则限制。支持近战武器、弓和弩，死亡时返还捡到的武器。
- 半血及以下会尝试吃腐肉，获得短暂再生和伤害吸收，进食冷却为 15 秒。

默认情况下，同一玩家的死亡生成僵尸被玩家击败累计 3 次后，后续生成的僵尸会进化。进化版使用更快的挖掘、疾跑与跳劈、战斗走位、投射物应对及逐块建造的障碍或防御墙。增强操作具有转向、视线、反应时间和放置频率限制。

死亡生成记录、进化计数和临时地形修改均保存到世界数据中。复杂地形下的寻路、战斗表现和多人体验需要结合游戏内反馈调整。

## 水晶青蛙

水晶青蛙（`crystal_frog`）是可驯服的中立生物，以连续短跳在陆地移动，在水中游泳。移动节奏快于原版青蛙，背部水晶可反制长矛冲刺与重锤下砸。

- **反伤**：玩家直接近战命中时，实际水平速度达到约 5.6 格/秒，或向下速度达到同一阈值，则免疫该次伤害，将原始伤害的 100% 返还给玩家。反伤量不随难度缩放，玩家的护甲、保护效果及原版受伤冷却照常生效；伴有水晶声音与紫色粒子。
- 依据命中时服务端已知速度判断，骑乘与滑行达到阈值也适用；仅向上运动不触发。普通低速近战、箭、投掷物和环境伤害正常生效。长矛的独立击退流程保留，由自身击退抗性减弱。
- **反击与逃离**：平时不主动攻击。受到攻击或触发反伤后，剩余生命值 ≥ 50% 时反击攻击者，低于 50% 时逃离。反击不会召集其他青蛙；目标死亡、离开 24 格或约 10 秒未再受击后结束追击。环境伤害无法提供攻击目标时采用逃生行为。
- **属性**：20 点生命值（10 颗心）、3 点基础攻击伤害、2 点攻击击退属性、80% 自身击退抗性。实际反击伤害、击退距离仍受原版难度、护甲、地形与目标抗性影响。
- 在主世界 Y < 48、无露天视野、可站立且附近各坐标方向 4 格内有紫水晶方块或晶芽的位置稀少生成，每组 1–2 只。使用原版被动生物生成额度，其他动物会影响生成机会。
- 死亡掉落 **1–2 个粘稠紫水晶**；玩家击杀时掉落 **5 点基础经验**，遵循原版掉落规则。

### 合成与驯服

**1 个粘稠紫水晶 + 1 个烈焰粉 → 1 个活化紫水晶**，无序合成，玩家自身的 2×2 合成栏也可使用。获得粘稠紫水晶后解锁配方。

手持活化紫水晶右键野生水晶青蛙，每次消耗 1 个，有 **1/3 概率**驯服；创造模式遵循原版物品不消耗规则。成功时出现爱心并原地坐下，胸前显示小水晶标记。主人空手右键切换坐下 / 跟随；距离较远时使用原版宠物的安全传送规则追上主人。身份和坐下状态会保存，不提供繁殖。

跟随状态下，生命值 ≥50% 的水晶青蛙会**攻击主人新近攻击的目标，也会反击伤害主人的攻击者**。不协助攻击主人、自身、同主宠物或友军，玩家间战斗遵循原版友伤限制。坐下或低于半血时不主动参战，重新站起不会重放坐下期间的旧战斗；自身受击时仍优先执行反击或逃离规则。协助追击也遵循 24 格、约 10 秒的范围与时限。

**主人也按普通攻击规则处理**：高速攻击会被反伤，低速攻击会伤害青蛙，并按剩余血量触发反击或逃离。受击会解除坐下命令。

模型由本地 `reference/水晶青蛙.zip` 中的原始 OBJ 直接减面，保留原模型轮廓、晶体斜面、眼睛和腹部纹理。运行时使用约 6,000 个三角面及 1024×1024 原图缩小贴图，通过 Minecraft 原生模型接口绘制，身体、头部和四肢分别控制。在参考网格基础上等比放大 15% 后，模型约宽 0.83 格、高 0.52 格；碰撞箱约宽 0.83 格、高 0.53 格。模型、碰撞箱、视线高度与阴影共用 `CrystalFrog.SIZE_SCALE`，缩放时脚底位置保持不变。

运行时网格位于 `assets/uncannyencounters/geometry/crystal_frog.json`，可编辑的 Blockbench 网格位于 `reference/low_poly/crystal_frog.bbmodel`。重新从原始素材生成需要本地参考 ZIP 与 Blender，运行以下命令；游戏运行时不需要 Blender：

```shell
blender --background --factory-startup --python tools/generate_crystal_frog_model.py
```

## 安装

需要 Minecraft 26.3、Fabric Loader 0.19.5 或更高版本、对应版本的 Fabric API，以及 Java 25 或更高版本。

将模组 JAR 与 Fabric API 放入客户端的 `mods` 目录；多人游戏时服务端也需要安装。构建产物中的 `-sources.jar` 是源码包，不用于安装。

## 命令

```mcfunction
/give @s uncannyencounters:cave_angler_spawn_egg
/summon uncannyencounters:cave_angler ~ ~2 ~
```

洞穴垂钓者会寻找上方 32 格内可附着的坚实表面。建议测试区域从地面到天花板至少高 7 格。

僵尸玩家测试：

```mcfunction
/give @s uncannyencounters:zombie_player_spawn_egg
/summon uncannyencounters:zombie_player ~ ~ ~
/summon uncannyencounters:zombie_player ~ ~ ~ {Evolved:1b}
```

玩家使用刷怪蛋时会继承使用者皮肤，并按其进化计数决定版本；测试生成的僵尸不占死亡生成名额。直接使用 `/summon` 时没有玩家身份，使用默认皮肤。

游戏规则与管理员命令：

```mcfunction
/gamerule uncannyencounters:zombie_player_spawning false
/gamerule uncannyencounters:zombie_player_evolution true
/gamerule uncannyencounters:zombie_player_evolution_defeats 3
/zombieplayer defeats <玩家名>
/zombieplayer reset <玩家名>
```

生成和进化规则默认均开启，进化阈值最小为 1。`defeats` 查询计数，`reset` 清空计数；进化规则与计数决定后续生成版本。

水晶青蛙测试：

```mcfunction
/give @s uncannyencounters:crystal_frog_spawn_egg
/summon uncannyencounters:crystal_frog ~ ~ ~
/give @s uncannyencounters:viscous_amethyst 8
/give @s uncannyencounters:activated_amethyst 16
```

建议在生存模式依次测试普通挥击、疾跑近战、长矛冲刺、垂直下落重锤与射箭，观察双方血量。再分别将青蛙剩余生命控制在 10 点及以下，检查反击 / 逃离切换和击退；最后测试合成、驯服、空手坐下 / 跟随、主人攻击，以及保存重进后的状态。视觉、跳跃碰撞、游泳上岸和自然生成密度需游戏内验收。

## 构建

项目使用 Gradle Wrapper，构建前请确认 `JAVA_HOME` 指向 JDK 25 或更新版本；无需单独安装 Gradle。当前依赖配置：

| 组件 | 版本 |
| --- | --- |
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Fabric Loom | 1.18-SNAPSHOT |
| Gradle Wrapper | 9.7.1 |
| Java 编译目标 | 25 |

```shell
./gradlew assemble
```

Windows：

```powershell
.\gradlew.bat assemble
```

构建产物位于 `build/libs`。

仅在改动涉及现有游戏测试代码或需要与 CI 一致的编译检查时，使用：

```powershell
.\gradlew.bat assemble compileGametestJava
```

该命令打包模组并编译 GameTest 代码，不启动游戏或执行 GameTest。仓库 CI 使用相同任务。游戏内交互、视觉和端到端验收由维护者手动完成，编译成功不代表这些行为已验证。

## 项目结构与开发约定

| 路径 | 用途 |
| --- | --- |
| `src/main/java/.../UncannyEncounters.java` | 公共初始化入口 |
| `src/main/java/.../entity/` | 实体、属性、刷怪蛋与生成注册 |
| `src/main/java/.../entity/zombieplayer/` | 死亡生成、持久化、自定义寻路、移动、战斗与临时方块恢复 |
| `src/main/java/.../entity/crystalfrog/` | 水晶青蛙的移动、反击、逃离、跟随和速度判定 |
| `src/main/java/.../item/` | 粘稠紫水晶与活化紫水晶注册 |
| `src/main/java/.../block/` | 僵尸搭建方块，无对应物品 |
| `src/client/java/.../client/` | 客户端初始化、模型、渲染器与皮肤处理 |
| `src/main/resources/assets/uncannyencounters/` | 中英文名称、模型、贴图和物品外观定义 |
| `src/main/resources/data/` | 战利品表、合成配方、配方解锁、伤害类型与标签 |
| `src/gametest/` | 洞穴垂钓者游戏测试与水晶青蛙伤害 / 反击规则测试 |
| `tools/` | 模型生成、预览脚本，以及僵尸玩家与水晶青蛙离线逻辑检查 |
| `reference/low_poly/` | 已纳入版本控制的 Blockbench 模型与几何数据 |

表中的 Java 路径省略了包目录 `com/ignilumen/uncannyencounters`。原始参考素材和 `docs/` 下的本地设计记录被 Git 忽略，克隆仓库后不一定存在。

- 公共逻辑和客户端代码采用独立 source set。伤害、AI、生成与持久化在服务端处理，客户端负责同步状态的显示和动画。
- 使用 Minecraft 原生 Java 实体模型与渲染器，当前没有第三方动画库或 Mixin 配置。
- **修改前先核对实际 Minecraft 26.3 / Fabric 依赖源码。** 不直接套用旧版本教程中的 API 名称和签名。本项目已使用 `Identifier`、`EntityTypes`、`ValueInput` / `ValueOutput`、`SubmitNodeCollector`、`CreativeModeTabEvents` 和 `ModelLayerRegistry` 等接口。
- Loom 缓存中的 `*-sources.jar` 可用于核对游戏源码；如本机尚未生成，可运行 `genSources`。新增生物还需同步处理注册、属性、客户端渲染、语言、刷怪蛋及掉落资源。
- 只执行与当前改动直接相关的检查；只有获得维护者授权后才创建 Git 提交。

## License

LGPL-3.0-only。许可证全文见 `COPYING.LESSER`；LGPL 是在 GPL-3.0 基础上附加的许可，GPL-3.0 全文见 `COPYING`。
