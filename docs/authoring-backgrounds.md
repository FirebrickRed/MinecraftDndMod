# Authoring backgrounds

How to add a background to `DMContent/Backgrounds/`. Any `.yml` file there is loaded, and **each
root key is a background id**, so one file can hold one background (`noble.yml`) or many
(`multibackgrounds.yml`). Run `/dm reload` after editing.

Proficiencies, languages and `player_choices` work exactly as on races and classes. See
[`authoring-character-options.md`](authoring-character-options.md) for skill/tool/language ids,
choice types, and the duplicate-proficiency rule. This page covers what's specific to backgrounds.

---

## A complete example

```yaml
archaeologist:                       # the id: permanent (saved on every character that has it)
  name: Archaeologist                # optional; defaults to the id, prettified
  description: "You have spent years digging through ruins…"
  skill_proficiencies: [history, survival]
  tool_proficiencies: []             # fixed tools, if any (tool ids / vehicles_*)
  languages: []                      # fixed languages, if any
  starting_equipment:                # item ids, "id xN" for a quantity; coin is gold_piece
    - bullseye_lantern
    - shovel
    - gold_piece x25
  player_choices:
    - id: background_tool_proficiencies
      title: Cartographer's or Navigator's Tools
      type: tool
      choose: 1
      options: [cartographers_tools, navigators_tools]
    - id: background_language
      title: Background Language
      type: language
      choose: 1
      options: []                    # any language
  feature:
    name: Historical Knowledge
    description: "When you enter a ruin or dungeon, you can correctly ascertain its original purpose…"
  links: ["https://dnd5e.wikidot.com/background:archaeologist"]   # reference only, not shown
  # custom_model: archaeologist_icon # resource-pack model for the menu tile; only if its texture exists
```

## Fields

| Key | Required | What it drives |
|---|---|---|
| *(root key)* | **yes** | The id. Saved on every character with this background and used by the creation menu, so renaming it orphans existing characters. |
| `name` | no | Display name. Defaults to the id, prettified. |
| `description` | no | Flavor text on the menu tile (first ~60 characters). |
| `skill_proficiencies` | no | Fixed skills. |
| `tool_proficiencies` | no | Fixed tools, as item ids or `vehicles_*`. |
| `languages` | no | Fixed languages. |
| `starting_equipment` | no | Gear given on creation. **Only this key is read.** `equipment:` looks right and grants nothing, and the loader warns if you use it. |
| `player_choices` | no | Skill / tool / language / equipment / custom picks. |
| `feature` | no | `{name, description}`. Shown on the menu tile (name) and the character sheet's background item (name + text). A bare string is shorthand for a name with no text. |
| `feat` | no | **2024 rules only; not applied.** See below. |
| `ability_scores` | no | **2024 rules only; not applied** (warns). See below. |
| `links` | no | Reference URLs for the author. Not displayed. |
| `custom_model` | no | Resource-pack model for the menu tile. |

Any other key is ignored. There's no `traits:` on a background. It used to be parsed and never
read; the feature is the thing to write.

### The feature is narrative

Almost every background feature is a roleplay hook: free passage on a ship, a place to stay among
the faithful, finding your way through a city. The plugin doesn't enforce them, it shows them. So
write the description for the DM who'll adjudicate it at the table, and keep it short enough to
read in a tooltip.

## Proficiency or item?

A background often gives a tool **proficiency** and separately some **gear**:

- *"Tool Proficiencies: one type of gaming set"* (Noble): a `type: tool` choice with
  `options: [gaming_set]`. The Noble's pack has no dice.
- *"Equipment: … a set of weighted dice"* (Charlatan's "tools of the con"): a `type: equipment`
  choice.

When a background gives both the proficiency and *the same* item (the Guild Artisan's artisan's
tools), you currently author two choices, and nothing forces the player to pick the same tool in
both. Linking them is a known gap (#206).

## 2024 rules: `feat:` and `ability_scores:`

The 2024 PHB moved the ability score increases from the race to the background and gave every
background an origin feat. **This plugin runs 2014 rules.** Both keys exist so content can carry
the data now, and a future config toggle (#205) can apply it. Until then:

```yaml
feat: tough                                        # shown on the tile as "(not applied yet)"; no feat system exists
ability_scores: [strength, dexterity, constitution] # the three the +2/+1 would go into; warns on load
```

Spelljammer backgrounds grant a feat too (Wildspacer → Tough), which is why `wildspacer.yml` uses
`feat:` under 2014 rules. The sheet is missing Tough's +2 HP per level until feats exist (#204).

## Shipped backgrounds

| Background | Tests |
|---|---|
| Acolyte | 2 language picks + an equipment pick (prayer book / prayer wheel) |
| Anthropologist | 2 language picks |
| Archaeologist | a limited tool pick + a language pick |
| Athlete | fixed vehicle proficiency, equipment pick |
| Charlatan | fixed tools that are also gear, equipment pick (tools of the con) |
| Noble | tool **proficiency** pick from a tag (`gaming_set`) |
| Sailor | fixed tool + vehicle. Wood elf + Sailor exercises the duplicate-Perception rule |
| Urchin | equipment pick from a tag (`keepsake`) |
| Wildspacer | `feat:` slot, `vehicles_space` |

Still missing from the PHB: Criminal, Entertainer, Folk Hero, Guild Artisan, Hermit, Outlander,
Sage, Soldier (#206).
