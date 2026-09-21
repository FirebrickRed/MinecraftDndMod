# Authoring races & subraces

How to add a race to `DMContent/Races/`. **One race per file.** The file's root *is* the race (unlike
backgrounds, where each root key is one). Subraces live inside it under `subraces:`. Run `/dm reload`
after editing.

Proficiencies, languages and `player_choices` work exactly as on classes and backgrounds. See
[`authoring-character-options.md`](authoring-character-options.md) for skill/tool/language ids,
choice types, and the duplicate-proficiency rule. This page covers what's specific to races.

---

## A trimmed example

```yaml
name: Elf                           # REQUIRED — and it IS the id: "Elf" → elf, "Half-Orc" → half_orc
creature_type: Humanoid
description: Elves are a magical people of otherworldly grace…
size: Medium                        # Tiny | Small | Medium | Large | Huge | Gargantuan
speed: 30
darkvision: 60
ability_scores:
  fixed:
    Dexterity: 2
traits: [Darkvision, Fey Ancestry, Trance, Keen Senses]   # display names on the menu tile
languages: [Common, Elvish]
skill_proficiencies: [perception]
conditional_advantages:
  - type: saving_throw
    condition: charmed
    description: "You have advantage on saving throws against being charmed."
rest_requirements:
  long_rest_hours: 4
  sleep_required: false
custom_model: elf_icon              # only if the texture exists (CLAUDE.md → Icons & Materials)

subraces:
  wood_elf:                         # subrace id (the key)
    name: Wood Elf
    ability_scores: { fixed: { Wisdom: 1 } }
    speed: 35                       # overrides the race's 30
    weapon_proficiencies: [longsword, shortsword, shortbow, longbow]
```

**The race id comes from `name:`**, normalized, not from the file name. Renaming a race changes its
id and orphans every character saved with it. Pick the name first.

## Race fields

| Key | Default | What it drives |
|---|---|---|
| `name` | "Unknown" | Display name **and** id. |
| `description` | — | Menu tile flavor text. |
| `creature_type` | Humanoid | Matters for spells/effects that check creature type. |
| `size` | Medium | Size category. For a choose-your-size race, see *Size* below. |
| `speed` | 30 | Walking speed. **Must be a number**: `speed: 30ft` fails the whole race. |
| `swimming_speed` / `flying_speed` / `climbing_speed` / `burrowing_speed` | 0 | 0 = none (swim/climb fall back to half walking speed). |
| `darkvision` | none | Range in feet. A number, not "60 ft". |
| `ability_scores` | none | `fixed: {Ability: bonus}` and/or `choice: {distributions: [[2,1],[1,1,1]]}` (player splits the bonus). Ability names in full: `Dexterity`, not `DEX`. |
| `traits` | — | Names shown under "Traits:" on the tile. **Display only.** A trait does nothing unless another field implements it. |
| `languages`, `skill_proficiencies`, `tool_proficiencies`, `weapon_proficiencies`, `armor_proficiencies` | — | Automatic grants ([shared guide](authoring-character-options.md#automatic-grants)). |
| `damage_resistances` | — | Damage types always resisted (`[poison]`). |
| `damage_resistance` | — | A resistance **linked to a custom choice**, for dragonborn ancestry. See below. |
| `innate_spells` | — | See below. |
| `conditional_advantages` | — | `type: saving_throw` + `condition:` is applied: advantage on saves against that condition/tag (Fey Ancestry, Dwarven Resilience). Other types are display only. |
| `features` | — | Usable features for the Effect Engine (#70), same format as a class's `features:`. See `dragonborn.yml` (Breath Weapon). |
| `rest_requirements` | 8h / sleeps | `long_rest_hours`, `sleep_required`. Recorded, not enforced yet (#45). |
| `player_choices` | — | [Shared guide](authoring-character-options.md#player_choices--what-the-player-picks). |
| `custom_model` | vanilla | Menu tile model. |
| `source_url` | — | Reference only. |
| `subraces` | — | Map of subrace id → subrace. |

## Subrace fields

A subrace **adds to** its race. `name`, `description`, `ability_scores`, `traits`, `languages`, all
five proficiency lists, `damage_resistances`, `innate_spells`, `player_choices` and `custom_model`
work as on the race. Movement and vision **override** the race when set: `speed` and the other
speeds apply when above 0, and `darkvision` replaces the race's value.

A subrace has **no** `features`, `conditional_advantages` or linked `damage_resistance`. Put those on
the race, or they're silently ignored.

## Innate spells

```yaml
innate_spells:
  - spell_id: thaumaturgy         # REQUIRED; must be a spell in DMContent/Spells
    level_requirement: 1          # character level it unlocks at (default 1)
    casting_ability: charisma
  - spell_id: hellish_rebuke
    level_requirement: 3
    spell_level: 2                # cast at this slot level (default: the spell's own level)
    uses: 1                       # per recovery, or "proficiency_bonus"
    recovery: long_rest           # long_rest | short_rest
    casting_ability: charisma
```

**Cantrip or not comes from the spell itself.** A level-0 spell is unlimited and `uses` is ignored.
You don't need an `is_cantrip:` flag; it's only read for a spell id that isn't authored yet. (Before
this, a `type: cantrip` line was silently ignored, and the tiefling's Thaumaturgy could never be cast.)

## Linked resistance (dragonborn)

```yaml
player_choices:
  - id: draconic_ancestry
    type: custom
    choose: 1
    options: ["Red (Fire, 15 ft. cone, DEX save)", "Blue (Lightning, 5x30 ft. line, DEX save)"]
damage_resistance:
  source_choice: draconic_ancestry           # the choice id above
  mapping:
    "Red (Fire, 15 ft. cone, DEX save)": fire   # option text → damage type (text must match exactly)
    "Blue (Lightning, 5x30 ft. line, DEX save)": lightning
```

## Size

A fixed size is `size: Small`. For a race that lets the player pick (plasmoid), use a `custom`
choice **whose id contains `size`** so it lands on the Extra tab:

```yaml
player_choices:
  - id: race_size
    title: Size
    type: custom
    choose: 1
    options: [small, medium]
```

(The older `size: {players_choice: …}` form is parsed and then thrown away. Use the choice.)

## Keys you'll see in shipped YAML that do nothing

Some race files carry notes for mechanics that aren't built yet: `condition_immunities`,
`sunlight_sensitivity`, `special_hide_rules`, `rest_requirements.description`. They're ignored. They
record intent for #63, and the trait name in `traits:` is what the player actually sees.
