# Authoring character options — the shared pieces

Races, classes and backgrounds are three files of the same idea: things a character **gets
automatically** plus things the player **chooses**. They share one set of fields and one code path,
so this page covers those once. The per-type guides link back here:

- [`authoring-races.md`](authoring-races.md) — races and subraces
- [`authoring-classes.md`](authoring-classes.md) — classes and subclasses
- [`authoring-backgrounds.md`](authoring-backgrounds.md) — backgrounds

The rules are **2014 PHB**. (2024-rules support would be a config toggle, #205; see *2024 rules* in the
backgrounds guide for the slots already reserved.)

> **Read the console after `/dm reload`.** The content check (`ContentValidator`) names unknown
> skills, tools, languages and item ids in all three. A clean load prints nothing. An unknown
> **language** is the one hard failure: it stops that race / class / background loading, with the
> language named.

---

## Automatic grants

These keys work the same on a race, subrace, class, subclass or background (backgrounds spell
skills `skill_proficiencies:`; classes spell fixed skills `skills:` because `skill_proficiencies`
there would read like the class's *choice* list):

```yaml
skill_proficiencies: [perception, sleight_of_hand]   # skill ids (below)
tool_proficiencies:  [thieves_tools, vehicles_water] # tool ids (below)
languages:           [Common, Elvish]                # language names or ids
weapon_proficiencies: [simple_weapons, longsword]
armor_proficiencies:  [light_armor, shields]
```

Everything here shows under **Automatic Traits** in character creation and lands on the sheet.

### Skill ids

The 18 skills, lowercase with underscores: `acrobatics`, `animal_handling`, `arcana`,
`athletics`, `deception`, `history`, `insight`, `intimidation`, `investigation`, `medicine`,
`nature`, `perception`, `performance`, `persuasion`, `religion`, `sleight_of_hand`, `stealth`,
`survival`. A typo is a content-check warning and grants nothing.

### Tool ids — a tool proficiency *is* an item

Tools are gear, so **a tool proficiency names an item id**. `tool_proficiencies: [smiths_tools]`
means proficiency with the `smiths_tools` item in `Items/tools.yml`. An item counts as a tool
when it carries one of these tags:

| Tag | What it is | Where they live |
|---|---|---|
| `artisan_tool` | Smith's Tools, Cook's Utensils, … (17) | `Items/tools.yml` |
| `musical_instrument` | Lute, Flute, … (10) | `Items/musical_instruments.yml` |
| `gaming_set` | Dice Set, Playing Card Set, Dragonchess, Three-Dragon Ante | `Items/gaming_sets.yml` |
| `tool` | the other kits: Thieves' Tools, Disguise / Forgery / Herbalism / Poisoner's Kit, Navigator's Tools | `Items/adventuring_gear.yml`, `Items/tools.yml` |

**Vehicles** are the only tools that aren't items: `vehicles_land`, `vehicles_water`,
`vehicles_air`, `vehicles_space`.

**Spelling doesn't matter.** `"Navigator's Tools"`, `navigators_tools` and `Navigator's tools`
are the same id, and so are `vehicles(land)`, `"Vehicles (Land)"` and `vehicles_land`. The loader
folds them all to one id, so nothing downstream ever compares two spellings. **Write the
`vehicles_water` form** in new content. It's the id, so it's what the console and saved sheets show.

**Homebrew tool:** add an item with the tag. It appears in every "choose an artisan's tool" pick,
and the content check accepts proficiencies that name it. No code.

The **Healer's Kit is not a tool** in 5e (anyone can use it), so it has no tool tag.

### Languages

The PHB standard and exotic languages are built in: Common, Dwarvish, Elvish, Giant, Gnomish,
Goblin, Halfling, Orc, Abyssal, Celestial, Draconic, Deep Speech, Infernal, Primordial, Sylvan,
Undercommon. Add homebrew ones (Thieves' Cant, Druidic, Gith, …) in **`DMContent/Languages.yml`**,
a plain list:

```yaml
- Thieves' Cant
- Druidic
```

Like tools, case and spacing don't matter (`Deep Speech` = `deep_speech`).

---

## `player_choices` — what the player picks

A list. Every entry has `id`, `title`, `type`, `choose` and (usually) `options`:

```yaml
player_choices:
  - id: background_language      # unique within this race/class/background
    title: Background Language   # shown on the choice header
    type: language               # skill | tool | language | equipment | spell | custom
    choose: 2
    options: []                  # empty = "any" (see per-type below)
```

| `type` | `options:` holds | Empty `options: []` means |
|---|---|---|
| `skill` | skill ids | any of the 18 skills |
| `tool` | tool ids **or a category tag** (`artisan_tool`, `musical_instrument`, `gaming_set`, `vehicle`, `tool`) | any tool |
| `language` | language names | any language |
| `equipment` | item ids, tags, `id xN`, or `give: [a, b]` bundles (CLAUDE.md → *Equipment choices*) | nothing (the choice is dropped) |
| `spell` | spell ids, **or** `spell_list: wizard` + `spell_level: 0` instead of options | — |
| `custom` | free strings (draconic ancestry, a size) | nothing |

A choice with `choose: 0` or no usable options is dropped silently.

**Proficiency vs item.** "Proficiency with one gaming set" is a `type: tool` choice with
`options: [gaming_set]`. "A gaming set" in your pack is a `type: equipment` choice with the same
option. They look alike and do different things, and the Noble used to get the wrong one.

### How choices are grouped in the menu

The creation menu merges choices of the same kind **only when they offer the same options**. Two
"any language" picks (a high elf's and a Noble's) become one "choose 2" pool. An artificer's
"one artisan's tool" and an archaeologist's "cartographer's or navigator's tools" stay separate,
because merging them would let you take two artisan's tools and skip the other pick. Skills are
grouped by source (race / class / background) instead.

Anything already granted shows as gray **Already known** and can't be picked. Something picked in
another section of the same kind shows light green, and clicking it **moves** the pick there, so
you can't spend two picks on the same thing.

---

## Duplicate proficiencies (PHB p.125)

> "If a character would gain the same proficiency from two different sources, he or she can
> choose a different proficiency of the same kind (skill or tool) instead."

The game handles this automatically. A wood elf (Keen Senses: Perception) who takes the Sailor
background (Perception) gets a **"Replace duplicate Perception (Elf + Sailor)"** pick for any other
skill. A rock gnome (tinker's tools) artificer (tinker's tools) gets one for any other tool. You
don't author anything. It only fires for **fixed** grants, because a player's own pick can't choose
something already granted.

Languages aren't proficiencies, so RAW doesn't cover them. A language granted twice is just known
once.

---

## Persistence

Tool, language, weapon and armor **grants** aren't saved on the character. They're re-derived from
the race/class/background on load, so fixing a typo in YAML fixes existing characters too. The
player's tool and language **choices** are saved (`chosenTools`, `chosenLanguages` in
`Saved/Characters/<id>.yml`), because they exist nowhere else.

**Skills are saved as the full list**, grants and picks together. So a skill added to a race later
does reach existing characters on load, but one *removed* from a background stays on characters
already made with it.

---

## Gotchas

| You wrote | What happens | Fix |
|---|---|---|
| a language not in the list | that race/class/background **doesn't load** (console names it) | add it to `DMContent/Languages.yml` |
| a tool id with no matching item | content-check warning; the proficiency names nothing | fix the id, or add the item with a tool tag |
| `type: equipment` for a proficiency | the player gets the item, not the proficiency | `type: tool` |
| `options: []` on an equipment choice | the choice silently disappears | list the options |
| a key this page doesn't list | ignored silently (the loaders read only known keys) | check the per-type guide |
