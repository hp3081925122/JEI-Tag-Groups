# JEI Tag Groups Wiki

[English](README.md) | [中文](zh_cn.md)

## 使用方式

JEI Tag Groups 是一个客户端模组，需要安装 JEI。配置文件位于：

`config/jei_tag_groups/`

每个 JSON 文件包含一条折叠规则。不需要某条规则时，删除对应的 JSON 文件，然后按 `F3+T` 或重启客户端。

在 JEI 中：

- 将鼠标移动到折叠配方上，按住配置的配方组按键即可展开。
- 再次按住同一个按键即可折叠。
- 修改按键绑定后，JEI 提示中的按键名称会动态更新。
- 配方轮播会定期显示折叠组中隐藏的配方。

## 配置文件

配置文件使用 UTF-8 编码的 JSON 格式。`description` 字段只用于描述规则，不会参与匹配。

### 物品规则

物品规则必须在 `item` 和 `tag` 中二选一。`icon` 是折叠后显示的代表物品，`border_color` 是六位十六进制颜色。

匹配一个物品 ID：

```json
{
  "description": "折叠附魔书物品",
  "type": "item",
  "item": "minecraft:enchanted_book",
  "icon": "minecraft:enchanted_book",
  "border_color": "#AA55FF"
}
```

匹配一个物品标签：

```json
{
  "description": "折叠原木物品",
  "type": "item",
  "tag": "minecraft:logs",
  "icon": "minecraft:oak_log",
  "border_color": "#55AA55"
}
```

同一条规则不能同时使用 `item` 和 `tag`。资源 ID 必须包含命名空间。

### 配方规则

配方规则使用 `type: "recipe"`，并且至少填写一个匹配字段。支持以下字段：

- `recipe_id`：匹配一个完全相同的配方 ID。
- `recipe_id_contains`：匹配配方 ID 中包含的文本。
- `input_item`：匹配配方中的输入物品。
- `output_item`：匹配配方中的产出物品。

同时填写多个匹配字段时，所有字段都必须匹配。物品 ID 必须包含命名空间。

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

如果要匹配完全相同的配方 ID，将 `recipe_id_contains` 替换为 `recipe_id`，例如：

```json
{
  "description": "折叠一个配方",
  "type": "recipe",
  "recipe_id": "example:my_recipe",
  "border_color": "#55AAFF"
}
```
