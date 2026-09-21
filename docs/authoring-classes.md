# Authoring classes & subclasses

How to add a class to `DMContent/Classes/`. **One class per file**, and the file's root *is* the
class. Subclasses live inside it under `subclasses:`. Run `/dm reload` after editing. `warlock.yml`
is the spellcaster template, `barbarian.yml` the martial one (with a usable feature and a resource).

Proficiencies, languages and `player_choices` work exactly as on races and backgrounds. See
[`authoring-character-options.md`](authoring-character-options.md) for skill/tool/language ids,
choice types, and the duplicate-proficiency rule. Spells themselves are in
[`authoring-spells.md`](authoring-spells.md).

> **Every character is level 1** until level-up lands (#153). Fields indexed by level
> (`*_by_level`) are read at index 0 today. Fill all 20 anyway so nothing needs revisiting.

---

## Core fields

```yaml
name: Warlock                  # REQUIRED — and it IS the id ("Warlock" → warlock). Don't rename later.
hit_die: 8                     # level-1 HP = hit_die + CON modifier
primary_abilities: [Charisma]  # highlighted in the ability-score step
saving_throws: [Wisdom, Charisma]
armor_proficiencies: [light_armor]
weapon_proficiencies: [simple_weapons]
tool_proficiencies: []         # tool ids (item ids / vehicles_*)
languages: []                  # rare: Druidic, Thieves' Cant (add them to DMContent/Languages.yml)
skills: []                     # FIXED skills (rare). The usual "choose 2 from…" is a player_choice
starting_equipment: [leather_armor, dagger, dagger]
player_choices:
  - id: class_skills
    title: Class Skills
    type: skill
    choose: 2
    options: [arcana, deception, history, intimidation, investigation, nature, religion]
  - id: class_equipment_1
    title: Class Equipment 1
    type: equipment
    choose: 1
    options:
      - give: [light_crossbow, bolt x20]
      - simple_weapon            # a tag: expands to every simple weapon
custom_model: warlock_icon       # menu tile model; only if its texture exists
```

| Key | Default | Notes |
|---|---|---|
| `name` | "Unknown" | Display name **and** id. |
| `hit_die` | 6 | A number. |
| `saving_throws` | — | Full ability names. Drives save proficiency. |
| `primary_abilities` | — | Hint only. |
| `armor_proficiencies` / `weapon_proficiencies` | — | Shown on the sheet. Armor proficiency isn't enforced yet: no penalty for wearing armor you lack it for. |
| `tool_proficiencies`, `languages`, `skills` | — | Automatic grants ([shared guide](authoring-character-options.md#automatic-grants)). |
| `starting_equipment` | — | Flat list of item ids / `id xN`. |
| `player_choices` | — | Skills, tools, equipment. |
| `proficiency:` | *ignored* | The proficiency bonus is derived from level. The key in shipped files does nothing. |
| `asi_levels`, `multiclass_requirements`, `allow_feats` | — | Parsed for level-up / multiclassing / feats, none of which exist yet. |

## Spellcasting

```yaml
spellcasting:
  casting_ability: Charisma
  preparation_type: known          # known | prepared
  ritual_casting: false            # optional, default false
  spellcasting_focus_type: arcane_focus   # which items count as a focus (an item's focus_type)
  spellcasting_level: 1            # class level spellcasting starts
  cantrips_known_by_level: [2,2,2,2,3,…]   # 20 entries
  spells_known_by_level:   [2,2,3,3,4,…]   # for `known` casters
  spells_prepared_formula:         # for `prepared` casters instead
    type: ability_plus_level       # ability_plus_level | flat | custom
    ability: wisdom
    level_type: full               # full | half | third
    minimum: 1
  spell_slots_by_level:            # spell level → 20 entries (slots at each class level)
    1: [1,2,2,2,2,…]
  slot_recovery: short_rest        # warlock pact magic; default long rest
```

**Which spells a class can learn isn't set here.** Each spell lists its classes in its own YAML
(`classes: [warlock, wizard]`). `spell_list:` and `pact_slot_level_by_level:` in shipped files
aren't read.

## Class resources (Rage, Ki, Bardic Inspiration)

```yaml
class_resources:
  - name: Rage
    max_by_level: [2,2,3,3,3,4,…]   # OR:
    # uses_ability: charisma        #   max = that ability's modifier (Bardic Inspiration)
    recovery: long_rest             # short_rest | long_rest | dawn | none
    material: red_dye               # the item it shows as on the sheet (`icon:` is the old, deprecated spelling)
```

A resource whose max works out to 0 at the character's level isn't created (Action Surge before
level 2). `/character rest short|long` recovers them.

## Usable features (Effect Engine, #70)

`features:` makes a feature *do* something, for example Rage as a bonus action that costs a Rage use
and grants resistance plus bonus damage, activated with `/combat use rage`. The format has its own
depth (costs, durations, effects, AoE actions). Copy from `barbarian.yml` (Rage) or `dragonborn.yml`
(Breath Weapon) until it gets a guide of its own.

`features_by_level:` is **display text only**: the level-by-level feature list on the class tile.
Its `type:`, `uses:` and `damage:` keys are descriptive and drive nothing. Put mechanics in `features:`.

## Subclasses

```yaml
subclass_level: 1                        # when it's chosen; default 3
subclass_type_name: Otherworldly Patron  # the label in menus
subclasses:
  fiend:                                 # subclass id (the key)
    name: The Fiend
    description: "You have made a pact with a fiend…"
    bonus_spells: [burning_hands, command]      # always known/prepared; don't count against the limit
    additional_spells: [light]                  # bonus cantrips
    skill_proficiencies: []
    armor_proficiencies: [heavy_armor]
    weapon_proficiencies: []
    tool_proficiencies: [smiths_tools]
    languages: []
    darkvision: 0                               # upgrades the race's if larger
    swimming_speed: 0
    player_choices: []                          # e.g. Knowledge Domain's skills/languages, Nature Domain's cantrip
    conditional_advantages: []                  # saving_throw + condition is applied (as on races)
    conditional_bonus_spells: {}                # per-choice spells (Genie kind): parsed, not applied yet
    features_by_level: { 1: ["Dark One's Blessing. …"] }
    custom_model: fiend_icon
```

**Only `subclass_level: 1` subclasses are picked at creation** (Cleric, Warlock, Sorcerer). A level-2
or level-3 subclass (Wizard, everyone else) waits for level-up, and nothing from it applies yet.

## Gotchas

| You wrote | What happens |
|---|---|
| renamed `name:` | new id; existing characters with the old class fail to load their class |
| `hit_die: d8` | the whole class fails to load (it must be a number) |
| a spell in `bonus_spells` that isn't authored | listed in the console's "spells referenced but not defined" line; the character just doesn't get it |
| mechanics in `features_by_level` | shown, never applied. Use `features:` |
| a level-3 subclass expecting creation-time effects | nothing applies until level-up |
