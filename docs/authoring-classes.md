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
| `armor_proficiencies` / `weapon_proficiencies` | — | Shown on the sheet. Armor you lack proficiency with gives disadvantage on STR/DEX checks, saves and attacks, and blocks spellcasting (PHB p.144). Categories are `light_armor`, `medium_armor`, `heavy_armor`, `shields`; a specific armor id works too. |
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

**Prepared casters (#218).** `preparation_type: prepared` with **no** `spells_known_by_level` (cleric,
druid, paladin, artificer) prepares from the whole class list, up to their highest slot: their
prepared spells are their known ones. **With** `spells_known_by_level` (wizard) that's the spellbook's
size, and they prepare `spells_prepared_formula` of the book each day. Either way they change them
in the Prepare Spells menu after a long rest (a new character starts rested). A subclass's
`bonus_spells` are always prepared and don't count. `ritual_casting: true` lets them cast a ritual
spell as a ritual (no slot): a wizard from the book, prepared or not; everyone else only if prepared.

**Which spells a class can learn isn't set here.** Each spell lists its classes in its own YAML
(`classes: [warlock, wizard]`). `spell_list:` and `pact_slot_level_by_level:` in shipped files
aren't read.

## Class resources (Rage, Ki, Bardic Inspiration)

```yaml
class_resources:
  - name: Rage
    max_by_level: [2,2,3,3,3,4,…]   # OR:
    # uses_ability: charisma        #   max = that ability's modifier (Bardic Inspiration)
    # plus: 1                       #   added to the max (Divine Sense: 1 + CHA)
    # minimum: 1                    #   the max is never lower ("a minimum of once")
    recovery: long_rest             # short_rest | long_rest | dawn | none
    material: red_dye               # the item it shows as on the sheet
```

A pool of points is a resource too: Lay on Hands is `max_by_level: [5, 10, 15, …]`, and using it
spends as many points as it heals. A feature's `cost: { resource: … }` can name a resource by its
name or its id spelling (`second_wind` finds "Second Wind").

A resource whose max works out to 0 at the character's level isn't created (Action Surge before
level 2). `/dm rest <character|all> short|long` recovers them (rests are the DM's call).

## Usable features (Effect Engine, #70)

`features:` makes a feature *do* something, for example Rage as a bonus action that costs a Rage use
and grants resistance plus bonus damage, activated with `/combat use rage`. The format has its own
depth (costs, durations, effects, AoE actions). Copy from `barbarian.yml` (Rage) or `dragonborn.yml`
(Breath Weapon) until it gets a guide of its own.

### A feature for someone else (Bardic Inspiration, #40)

```yaml
features:
  - id: bardic_inspiration
    activation: bonus_action
    target: other_creature        # its apply: goes on the creature named when it's used
    range: 60                     # feet; out of reach offers [Ask the DM]
    cost: { resource: bardic_inspiration, amount: 1 }
    apply:
      duration: { rounds: 100, until_used: true, held: true }
      effects:
        roll_bonus: { dice: 1d6, to: [attacks, saves, checks] }
```

Used as `/combat use bardic_inspiration Zek` or `/character use bardic_inspiration Zek`; never on
yourself. `roll_bonus` puts a die on the holder's rolls (the same primitive as Bless, see
`authoring-spells.md` → Shape 5c; write `to:`, not `on:`). `held: true` keeps it off every roll by
itself: after each attack, save or check the holder rolls, they're asked whether to add it
(**[Roll it] [I rolled…] [Don't use it]**, answered by `/character inspiration`), as the PHB allows
(p.54). A missed attack is re-checked against the AC; a failed concentration or death save waits for
the answer; anything else is the DM's call. A creature holding one has it added to its rolls at
once, since the DM rolls for it. A second one replaces the first.

### Advantage and disadvantage (`advantage_on`, `disadvantage_on`, #223)

An effect lists the rolls it affects by **roll tag**. Rage's is `advantage_on: [str_checks, str_saves]`.

| Tag | Rolls |
|---|---|
| `str_checks` … `cha_checks` | that ability's checks, including its skills and tool checks |
| `checks` | every ability check |
| `str_saves` … `cha_saves` | that ability's saves |
| `saves` | every save |
| `attacks` | attack rolls |
| `initiative` | initiative (a DEX check, so `dex_checks` covers it too) |

It works on a passive feature (always on) and on a live effect (only while it lasts). The roll says
where it came from ("↑ Advantage: Rage"), and advantage plus disadvantage cancel as usual. A
misspelt tag is a console warning on `/dm reload`.

### Another way to work out AC (`armor_class`, #220)

Unarmored Defense, natural armor and the like are a passive feature with an `armor_class:` effect:

```yaml
features:
  - id: unarmored_defense
    name: Unarmored Defense
    activation: passive
    apply:
      effects:
        armor_class: { base: 10, add: [dexterity, wisdom], requires: [no_armor, no_shield] }
```

| Key | Default | Meaning |
|---|---|---|
| `base` | 10 | The flat part (13 for natural armor, 17 for a tortle's shell). |
| `add` | none | Ability modifiers added, **full names** (`dexterity`, not `dex`). |
| `requires` | none | `no_armor`, `no_shield`, and (for `unarmed_strike`, `weapon_ability` and an `attack:` block) `only_monk_weapons`: nothing in either hand but weapons the character's own `weapon_ability` covers, which is the monk's "unarmed or wielding only monk weapons" (#259). An AC formula ignores that one. Leave `no_shield` off and a shield adds its +2 on top (a barbarian's does; a monk's doesn't). |

The character's AC is the **best** of the normal calculation (armor or 10 + DEX, plus a shield) and
every formula that applies, so a formula can only help. The sheet's AC tile names the one that won.
It works on races' `features:` too (natural armor), and a live effect with a duration can carry one
(Mage Armor). A misspelt ability or requirement is a console warning on `/dm reload`, not a guess.

### Extra max HP (`max_hp_per_level`)

`max_hp_per_level: 1` in a passive feature's `effects:` adds that much max HP per character level
(Draconic Resilience, Dwarven Toughness). It's worked out when the character is created.

### Unarmed strikes and monk weapons (`unarmed_strike`, `weapon_ability`, #221)

Martial Arts in `monk.yml` is the worked example. Two passive effects and one bonus-action attack:

```yaml
  - id: martial_arts
    activation: passive
    apply:
      effects:
        unarmed_strike:
          damage_by_level: [1d4, 1d4, 1d4, 1d4, 1d6, …]   # 20 entries; past the end, the last holds
          ability: [strength, dexterity]                  # one, or a list: the better one is used
          requires: [no_armor, no_shield, only_monk_weapons]
        weapon_ability:
          weapons: [shortsword, simple_melee_weapon]      # weapon ids and/or weapon tags
          exclude_properties: [two_handed, heavy]
          ability: [strength, dexterity]
          min_die: unarmed                                # roll the unarmed die when it's bigger
          requires: [no_armor, no_shield, only_monk_weapons]
  - id: martial_arts_strike
    name: "Martial Arts: bonus unarmed strike"
    activation: bonus_action
    attack: { weapon: unarmed, requires: [no_armor, no_shield, only_monk_weapons], after_attack_with: [unarmed, monk_weapon] }
```

- Without an `unarmed_strike`, an unarmed strike is 1 + STR (PHB p.195).
- `weapon_ability` only ever **helps**: the listed ability is used when its modifier beats the
  weapon's own (STR, or the better of STR/DEX for finesse). A magic weapon counts as its `base:`.
- A feature with an **`attack:`** block *is* an attack. `/combat use` and `/combat bonusAction` fill
  `/combat attack <target> <weapon> bonus`. By default it follows `combat.bonus_attack_timing`, like
  two-weapon fighting's off-hand attack. **`after_attack_with: [unarmed, monk_weapon]`** ties it to the
  Attack action instead: it's only allowed after an Attack action made with an unarmed strike
  (`unarmed`) or a weapon the character's `weapon_ability` covers (`monk_weapon`), whatever the timing
  setting says (#259).
- The attack prompt says what these rules changed ("Martial Arts: 1d4 + DEX instead of 1 + STR", "The
  Quarterstaff's own 1d6 beats the Martial Arts 1d4") or why they're off ("Martial Arts is off: you're
  holding a Longsword"), named after the feature, so a homebrew feature gets the same lines.
- `requires` works as on `armor_class`. Misspelt keys are console warnings on `/dm reload`.

### Attack, AC and damage bonuses (fighting styles, #229)

| Effect | Example | Meaning |
|---|---|---|
| `attack_bonus: { amount: 2, when: ranged }` | Archery | Added to hit, and named in the roll ("+2[Archery]"). `when`: `ranged` or `melee` |
| `ac_bonus: { amount: 1, requires: [armor] }` | Defense | Added on top of whichever AC won. `requires: [armor]` = only while wearing body armor |
| `bonus_damage: { amount: 2, when: melee_one_handed }` | Dueling | Added to damage. `when`: `melee_str` (a STR melee swing, Rage) or `melee_one_handed` (a melee weapon that isn't two-handed, with no weapon in the other hand; a shield is fine) |
| `aura: red` | Rage | A ring of coloured dust around whoever has the effect, every half second, seen by everyone nearby. `red`, `orange`, `yellow`, `green`, `blue`, `purple`, `white`, `gold`, or `#rrggbb`; anything else warns at load. (`minecraft_effect:` only adds a potion icon in the inventory: it shows nothing on screen) |
| `reroll_low_damage: true` | Great Weapon Fighting | 1s and 2s on the damage dice are rolled again when the game rolls, for a two-handed melee weapon (or versatile with the off hand empty). A player rolling their own dice is reminded |
| `offhand_ability_damage: true` | Two-Weapon Fighting | The off-hand attack adds its ability modifier to damage |
| `sneak_attack: { dice_by_level: [1d6, 1d6, 2d6, …] }` | Sneak Attack | Once per turn, a finesse or ranged weapon, no disadvantage. **With advantage it applies on its own**; otherwise the hit offers the player [Ask the DM] (tables rule "an ally next to it" differently), with what the game noticed. The dice join the hit's own (a crit doubles them). It's spent when the damage lands, so a hit Shield turns into a miss doesn't use it |

All of these work on a passive feature and on one a choice grants (see
[`authoring-character-options.md`](authoring-character-options.md), *Options that grant things*):
that's how the fighter's Fighting Style is written.

### Healing and sensing (`heal:`, `sense:`, #229)

```yaml
features:
  - id: second_wind
    name: Second Wind
    activation: bonus_action
    cost: { resource: second_wind, amount: 1 }
    heal: { dice: 1d10, add_level: true }          # on yourself: 1d10 + your level
  - id: lay_on_hands
    name: Lay on Hands
    activation: action
    cost: { resource: lay_on_hands }
    heal: { from_pool: true, range: 5, not: [undead, construct] }   # spend points on someone you touch
  - id: divine_sense
    name: Divine Sense
    activation: action
    cost: { resource: divine_sense, amount: 1 }
    sense: { creature_types: [celestial, fiend, undead], range: 60 }  # lists them by type and direction
```

`recover_slots: { max_slot_level: 5 }` with `activation: short_rest` is Arcane Recovery (#218): during a
short rest, spent slots adding up to half the character's level (rounded up) come back, none above
that level. The short rest's summary offers it.

These run from `/combat use <id>` on your turn (spending the action or bonus action) and from
`/character use <id>` outside a fight. A rolled heal gets the usual three roll buttons. Nothing is
spent until the feature actually happens.

**Every activated feature spends what its `activation` says**: `action` or `bonus_action`. It's refused
if that's already used this turn. (Rage used to be free.)

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
    expanded_spells: [burning_hands, command]   # a patron's list: may be LEARNED, each costs a pick
    expanded_spell_lists: []                    # whole class lists to learn from (Divine Soul: [cleric])
    features: []                                # usable features, as on a class (Draconic Resilience, #224)
    bonus_spells: []                            # a domain's/oath's: known or prepared for FREE
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
    features_by_level: { 1: ["Dark One's Blessing. …"] }
    custom_model: fiend_icon
```

**Two spell rules, two keys (#228).** `bonus_spells` are simply known (or always prepared) and don't
use up a pick: Cleric domains, Paladin oaths, Aberrant Mind / Clockwork Soul sorcerers. `expanded_spells`
are added to the list the character **picks from** and cost a pick like any other: every warlock
patron (PHB p.108, "choose from an expanded list"). Read the subclass's text: "you always have" or
"you learn" → `bonus_spells`; "you can choose from" → `expanded_spells`.

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
