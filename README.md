# Uncanny Encounters

面向 Minecraft 26.3 Fabric 的怪物模组。

## 洞穴垂钓者

洞穴垂钓者会附着在洞穴天花板，捕捉从下方经过的玩家。它会将猎物悬在地面上方并发出尖啸，吸引附近怪物攻击；没有怪物响应时，它会把猎物拉高后释放。

攻击舌头可以立即挣脱。舌头断裂后，洞穴垂钓者需要 15 秒才能再次捕捉目标。

## 安装

需要 Minecraft 26.3、Fabric Loader 0.19.5 或更高版本、对应版本的 Fabric API，以及 Java 25。

将模组 JAR 与 Fabric API 放入客户端和服务端的 `mods` 目录。

## 命令

```mcfunction
/give @s uncannyencounters:cave_angler_spawn_egg
/summon uncannyencounters:cave_angler ~ ~2 ~
```

洞穴垂钓者会寻找上方 32 格内可附着的坚实表面。建议测试区域从地面到天花板至少高 7 格。

## 构建

```shell
./gradlew assemble
```

Windows：

```powershell
.\gradlew.bat assemble
```

构建产物位于 `build/libs`。

## License

CC0-1.0
