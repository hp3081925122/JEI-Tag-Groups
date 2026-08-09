# JEI Tag Groups Wiki

[English](README.md) | [中文](zh_cn.md)

## 使用方式

JEI Tag Groups 是一个客户端模组，需要安装 JEI。配置文件位于：

`config/jei_tag_groups/`

每个 JSON 文件包含一条折叠规则。不需要某条规则时，删除对应的 JSON 文件，然后按 `F3+T` 或重启客户端。

在 JEI 中：

- 左键点击折叠的物品组，可以展开或折叠物品列表。
- 左键点击折叠的配方，可以展开或折叠配方组。
- 鼠标移动到折叠配方上时，按一次配置的配方组按键即可展开或折叠，默认按键是左 Shift。
- 可以在 Minecraft 的控制菜单中修改按键，JEI 提示中的按键名称会自动动态更新。
- 配方轮播会定期显示当前折叠组中隐藏的配方。

## 配置文件

配置文件使用 UTF-8 编码的 JSON 格式。`description` 字段只用于描述规则，不会参与匹配。资源 ID 必须包含命名空间，例如 `minecraft:stone`。

### 物品规则

物品规则必须在 `tag`、`item`、`items`、`item_name_contains` 中选择一个。`icon` 是折叠后显示的代表物品，`border_color` 是 `#RRGGBB` 格式的六位十六进制颜色。

匹配一个物品 ID。`item` 为兼容旧配置保留，作用等同于只有一个元素的 `items` 数组：

```json
{
  "description": "折叠一个物品",
  "type": "item",
  "item": "minecraft:enchanted_book",
  "icon": "minecraft:enchanted_book",
  "border_color": "#AA55FF"
}
```

匹配多个指定物品 ID：

```json
{
  "description": "折叠指定的药水物品",
  "type": "item",
  "items": [
    "minecraft:potion",
    "minecraft:splash_potion",
    "minecraft:lingering_potion"
  ],
  "icon": "minecraft:potion",
  "border_color": "#55AAFF"
}
```

匹配物品标签：

```json
{
  "description": "折叠原木物品",
  "type": "item",
  "tag": "minecraft:logs",
  "icon": "minecraft:oak_log",
  "border_color": "#55AA55"
}
```

根据当前语言下的物品显示名称包含文本进行匹配。匹配区分大小写：

```json
{
  "description": "折叠所有刷怪蛋",
  "type": "item",
  "item_name_contains": "刷怪蛋",
  "icon": "minecraft:pig_spawn_egg",
  "border_color": "#FFAA55"
}
```

切换到英文时，应将匹配文本改为英文，例如 `Spawn Egg`。

### 显示名称和 Tooltip

`display_name` 用于设置折叠后代表物品显示的名称。`tooltip` 用于添加一行或多行额外 Tooltip。两个字段都填写语言键，而不是直接填写显示文本。`tooltip` 可以是单个字符串，也可以是字符串数组。

```json
{
  "description": "折叠所有刷怪蛋",
  "type": "item",
  "item_name_contains": "刷怪蛋",
  "icon": "minecraft:pig_spawn_egg",
  "display_name": "jei_tag_groups.example.spawn_eggs.name",
  "tooltip": [
    "jei_tag_groups.example.spawn_eggs.tooltip"
  ],
  "border_color": "#FFAA55"
}
```

在已加载的 Minecraft 语言文件中定义这些语言键，例如：

```json
{
  "jei_tag_groups.example.spawn_eggs.name": "刷怪蛋",
  "jei_tag_groups.example.spawn_eggs.tooltip": "所有刷怪蛋条目已折叠"
}
```

### 配方规则

配方规则使用 `type: "recipe"`，并且至少填写一个匹配字段。支持以下字段：

- `recipe_id`：匹配一个完全相同的配方 ID。
- `recipe_id_contains`：匹配配方 ID 中包含的文本。
- `input_item`：匹配配方中的输入物品。
- `output_item`：匹配配方中的产出物品。

同时填写多个匹配字段时，所有字段都必须匹配。配方规则必须填写 `border_color`，不能使用 `icon`、`display_name` 或 `tooltip`。

匹配配方 ID 中包含的文本：

```json
{
  "description": "折叠修复配方",
  "type": "recipe",
  "recipe_id_contains": "repair.",
  "border_color": "#55AAFF"
}
```

根据输入和产出物品匹配配方：

```json
{
  "description": "折叠指定输入和产出的配方",
  "type": "recipe",
  "input_item": "minecraft:iron_sword",
  "output_item": "minecraft:iron_sword",
  "border_color": "#FFAA55"
}
```

如果要匹配完全相同的配方 ID，将 `recipe_id_contains` 替换为 `recipe_id`：

```json
{
  "description": "折叠一个配方",
  "type": "recipe",
  "recipe_id": "example:my_recipe",
  "border_color": "#55AAFF"
}
```
