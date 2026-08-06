# JEI Tag Groups Wiki

[English](README.md) | [中文](zh_cn.md)

## Usage

JEI Tag Groups is a client-side mod and requires JEI. Configuration files are stored in:

`config/jei_tag_groups/`

Each JSON file contains one collapse rule. To disable a rule, delete its JSON file and press `F3+T` or restart the client.

In JEI:

- Hover over a collapsed recipe and hold the configured recipe-group key to expand it.
- Hold the same key again to collapse it.
- The key shown in the JEI hint updates when the key binding changes.
- The recipe carousel periodically shows recipes hidden in collapsed groups.

## Configuration

Configuration files use UTF-8 JSON. The `description` field only describes the rule and does not affect matching.

### Item Rules

Use exactly one of `item` or `tag` in an item rule. `icon` is the representative item shown after collapsing, and `border_color` is a six-digit hexadecimal color.

Match one item ID:

```json
{
  "description": "Collapse enchanted book items",
  "type": "item",
  "item": "minecraft:enchanted_book",
  "icon": "minecraft:enchanted_book",
  "border_color": "#AA55FF"
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

`item` and `tag` cannot be used in the same rule. Resource IDs must include their namespace.

### Recipe Rules

Use `type: "recipe"` and at least one matching field. Supported fields are:

- `recipe_id`: match one exact recipe ID.
- `recipe_id_contains`: match text contained in the recipe ID.
- `input_item`: match an input item in the recipe.
- `output_item`: match an output item in the recipe.

When multiple matching fields are provided, all of them must match. Item IDs must include their namespace.

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

For an exact recipe ID, replace `recipe_id_contains` with `recipe_id`, for example:

```json
{
  "description": "Collapse one recipe",
  "type": "recipe",
  "recipe_id": "example:my_recipe",
  "border_color": "#55AAFF"
}
```
