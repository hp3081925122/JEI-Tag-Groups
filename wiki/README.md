# JEI Tag Groups Wiki

[English](README.md) | [中文](zh_cn.md)

## Usage

JEI Tag Groups is a client-side mod and requires JEI. Configuration files are stored in:

`config/jei_tag_groups/`

Each JSON file contains one collapse rule. To disable a rule, delete its JSON file and press `F3+T` or restart the client.

In JEI:

- Left-click a collapsed item group to expand or collapse its item list.
- Left-click a collapsed recipe to expand or collapse its recipe group.
- When the mouse is over a collapsed recipe, press the configured recipe-group key to expand or collapse it. The default key is Left Shift.
- Change the key in Minecraft's Controls menu. The key shown in the JEI hint updates automatically.
- The recipe carousel periodically shows recipes hidden in the current collapsed groups.

## Configuration

Configuration files use UTF-8 JSON. The `description` field only documents the rule and does not affect matching. Resource IDs must include their namespace, such as `minecraft:stone`.

### Item Rules

Use exactly one of `tag`, `item`, `items`, or `item_name_contains` in an item rule. `icon` is the representative item shown after collapsing, and `border_color` is a six-digit hexadecimal color in the form `#RRGGBB`.

Match one item ID. `item` is retained for compatibility and is equivalent to a one-element `items` array:

```json
{
  "description": "Collapse one item",
  "type": "item",
  "item": "minecraft:enchanted_book",
  "icon": "minecraft:enchanted_book",
  "border_color": "#AA55FF"
}
```

Match multiple exact item IDs:

```json
{
  "description": "Collapse selected potion items",
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

Match an item tag:

```json
{
  "description": "Collapse log items",
  "type": "item",
  "tag": "minecraft:logs",
  "icon": "minecraft:oak_log",
  "border_color": "#55AA55"
}
```

Match items whose translated display name contains text. The match uses the current language and is case-sensitive:

```json
{
  "description": "Collapse all spawn eggs",
  "type": "item",
  "item_name_contains": "Spawn Egg",
  "icon": "minecraft:pig_spawn_egg",
  "border_color": "#FFAA55"
}
```

### Display Name and Tooltip

`display_name` sets the name shown for the collapsed representative item. `tooltip` adds one or more extra tooltip lines. Both fields use language keys instead of literal text. `tooltip` may be a single string or an array of strings.

```json
{
  "description": "Collapse all spawn eggs",
  "type": "item",
  "item_name_contains": "Spawn Egg",
  "icon": "minecraft:pig_spawn_egg",
  "display_name": "jei_tag_groups.example.spawn_eggs.name",
  "tooltip": [
    "jei_tag_groups.example.spawn_eggs.tooltip"
  ],
  "border_color": "#FFAA55"
}
```

Define the keys in a loaded Minecraft language file, for example:

```json
{
  "jei_tag_groups.example.spawn_eggs.name": "Spawn Eggs",
  "jei_tag_groups.example.spawn_eggs.tooltip": "All spawn egg entries are collapsed"
}
```

### Recipe Rules

Use `type: "recipe"` and at least one matching field. Supported fields are:

- `recipe_id`: match one exact recipe ID.
- `recipe_id_contains`: match text contained in the recipe ID.
- `input_item`: match an input item in the recipe.
- `output_item`: match an output item in the recipe.

When multiple matching fields are provided, all of them must match. Recipe rules require `border_color` but do not use `icon`, `display_name`, or `tooltip`.

Match recipes whose ID contains text:

```json
{
  "description": "Collapse repair recipes",
  "type": "recipe",
  "recipe_id_contains": "repair.",
  "border_color": "#55AAFF"
}
```

Match recipes by input and output items:

```json
{
  "description": "Collapse recipes with a specific input and output",
  "type": "recipe",
  "input_item": "minecraft:iron_sword",
  "output_item": "minecraft:iron_sword",
  "border_color": "#FFAA55"
}
```

For an exact recipe ID, replace `recipe_id_contains` with `recipe_id`:

```json
{
  "description": "Collapse one recipe",
  "type": "recipe",
  "recipe_id": "example:my_recipe",
  "border_color": "#55AAFF"
}
```
