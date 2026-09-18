# Authoring spells

How to add a spell to `DMContent/Spells/*.yml`. Any `.yml` in that folder is loaded; the file you
put a spell in doesn't matter (they're grouped by level only for our own sanity). After editing,
`/dm reload` — the console prints a warning for anything malformed (see **Validation** at the end).

**One spell, one id.** The top-level key is the spell id (`frostbite:`), lowercase with
underscores. A given id may appear **only once across all files** — a duplicate silently used to let
whichever loaded last win (that's how Frostbite became an attack). The loader now warns and keeps
the first, but don't rely on it: grep before adding.

---

## The shape of a spell

Every spell is one of a few **shapes**. Pick the shape first, then fill the fields for it. The engine
decides the shape from which fields are present — there's no `type:` field.

```
              has healing/temp_hp?  ─ yes ─►  HEALING
              auto_hit: true?       ─ yes ─►  AUTO-HIT (no roll, straight to damage)
              has attack_type?      ─ yes ─►  ATTACK ROLL   (+ aoe_* = attack then area, NOT YET)
              has save_type?        ─ yes ─►  SAVE          (+ aoe_* = area save)
              has social_type?      ─ yes ─►  SOCIAL (message/sending)
              none of the above     ───────►  UTILITY (no automatic resolution)
```

`aoe_shape` layers on top of ATTACK/SAVE to make it an area spell.

---

## Fields every spell has

```yaml
frostbite:                        # id — unique, lowercase_with_underscores
  name: "Frostbite"               # display name
  level: 0                        # 0 = cantrip
  school: "evocation"
  classes: [ "sorcerer", "wizard", "druid", "warlock", "artificer" ]   # [] = DM-only (see below)
  casting_time: "1 action"        # "1 action" | "1 bonus action" | "1 reaction" | "1 minute" …
  range: "60 feet"                # "Self" | "Touch" | "N feet" — used for range checks
  components: "V, S"              # free text; or verbal:/somatic:/material_* (below)
  duration: "Instantaneous"
  concentration: false            # true → casting it drops any other concentration spell
  ritual: false
  description: "…"                # shown to players; write the real rules text here
  material: "WIND_CHARGE"         # the vanilla item the spell renders as (see CLAUDE.md Icons & Materials)
  # custom_model: frostbite_icon  # optional resource-pack model
  # higher_levels: "…"            # text only for now — no automatic upcast scaling yet
```

`casting_time` containing "reaction" lets the spell be cast off-turn (spends your reaction). Range
`"Self"` and `"Touch"` are understood by the range check; anything else needs a number.

### `classes: []` means DM-only

Spell selection during character creation filters by class, so a spell with an empty `classes:` list
can't be picked by any player. That's a feature: use it for spells the DM wants available to hand out,
attach to an NPC's repertoire, or reveal through a scroll or magic item, without it showing up in the
player's list. `encode_thoughts` is authored this way.

### Two different "materials"

A spell has two unrelated things called material, and they live in different places:

| What | Where | Example |
|---|---|---|
| The **component** a caster needs (the PHB "M") | inside `components:` | `components: "V, S, M (a pinch of fine sand)"` |
| The **Minecraft item** the spell renders as | top-level `material:` | `material: "BLAZE_POWDER"` |

The component description goes in parentheses after the `M`. Only the letters *before* the `(` are
read as V/S/M, so capitals inside the description are fine. A costly or consumed component uses the
map form instead:

```yaml
components:
  verbal: true
  somatic: true
  material: true
  material_description: "a diamond worth at least 50 gp"
  material_consumed: false      # true if the spell eats it
  material_cost: 50             # gp; omit or null if it has no cost
```

A YAML comment (`components: "V, S, M" # a bit of fleece`) is **not** read. The description has to be
inside the parentheses to show up on the spell item.

### Traps that silently change how a spell resolves

- **`attack_type` makes it an attack roll — always.** Its *value* isn't read, only whether it's there.
  So `attack_type: "touch"` on Guidance, Cure Wounds or a buff means the caster has to *hit their
  ally's AC*. Touch-range buffs and utility spells get **no** `attack_type`.
- **A `Self` range means "only targets you".** Any range starting with `Self` limits a single-target
  spell to the caster. That's right for Shield or False Life and wrong for a melee spell attack
  (Primal Savagery) or a save against someone else (Lightning Lure). Give those `"Touch"` or
  `"15 feet"` and note the RAW range in a comment. Area spells are aimed, so `"Self (15-foot cone)"`
  is fine on a spell with `aoe_shape`.
- **The dice parser takes one dice group.** `"2d8+3"` works; `"2d8+1d6"` doesn't (it rolls as 0).
  Put the main dice in `damage:` and mention the extra die in a comment or the description.

---

## Shape 1 — Attack-roll spell

The caster rolls to hit vs the target's AC; on a hit, damage.

```yaml
fire_bolt:
  # …common fields…
  attack_type: "ranged_spell_attack"   # presence = "this is an attack roll"; the value is just a label
  damage: "1d10"                       # dice only — cantrips add NO ability modifier
  damage_type: "fire"
```

- `attack_type`'s **value is not inspected** — only that it's present. Use `ranged_spell_attack` or
  `melee_spell_attack` for readers.
- Cantrip damage is dice only. (Leveled attack spells: same — the spell's own dice.)

### Shape 1b — Auto-hit spell

No attack roll and no save: the spell just hits, and casting goes straight to the damage prompt.

```yaml
magic_missile:
  # …common fields…
  auto_hit: true
  damage: "3d4+3"                # all three darts at one target
  damage_type: "force"
```

Splitting darts between several targets isn't modelled — cast it at one creature, or have the DM
apply the extras. Don't combine `auto_hit` with `attack_type`/`save_type`: auto-hit wins.

## Shape 2 — Saving-throw spell

The **target** rolls a save vs your spell DC (`8 + proficiency + your spellcasting modifier`, computed
automatically). This is the shape people get wrong — the target rolls, never the caster.

```yaml
frostbite:
  # …common fields…
  save_type: "Constitution"      # the ability the TARGET rolls
  damage: "1d6"                  # ← REQUIRED if the spell deals damage. Missing this = "Apply damage (1)" bug
  damage_type: "cold"
  save_effect: "half"            # on a SUCCESSFUL save: "half" (default) or "none"
  # condition_on_fail: prone     # optional: a condition id (#103) applied on a failed save
```

- **A save spell that deals damage MUST have `damage:`.** If it only applies a condition (Hold Person),
  omit both `damage:` and `damage_type:` — then it's a condition-only save and nothing looks broken.
- `condition_on_fail` must be a condition id from `DMContent/Conditions/conditions.yml`: `blinded`,
  `charmed`, `deafened`, `frightened`, `grappled`, `incapacitated`, `invisible`, `poisoned`, `prone`,
  `restrained`, `stunned`, `paralyzed`, `unconscious`. It's applied on a failed save and stays until
  the DM removes it.
- `save_effect: half` is the Fireball rule (half on save). `none` means a successful save takes zero.

## Shape 3 — Area spell (AoE)

Add `aoe_*` to a SAVE (usual) or make an area that everyone in it saves against. No named target —
the caster aims (#173).

```yaml
burning_hands:
  # …common fields…
  save_type: "Dexterity"
  damage: "3d6"
  damage_type: "fire"
  save_effect: "half"
  aoe_shape: "cone"              # "sphere" | "cone" | "line" | "burst"
  aoe_size: 15                   # feet — radius for sphere/burst, length for cone/line
  aoe_targets: "all"            # "all" | "enemies" | "allies"
```

- `burst` is centred on the **caster** (Thunderclap, Arms of Hadar); `sphere` is centred where the
  caster **aims** (Fireball). The caster is never caught in their own area.
- There's no cube or square shape. Approximate one with a `sphere` of half the side (a 20-foot cube →
  `aoe_size: 10`) and say so in a comment.

## Shape 4 — Healing / temp HP

```yaml
cure_wounds:
  # …common fields…
  healing: "1d8"                 # spellcasting modifier IS added automatically
  # temp_hp: "1d4+4"             # temp HP granted (no modifier added); can be alone or alongside healing
```

## Shape 5 — Social (out-of-combat utility)

```yaml
message:
  social_type: "message"         # "message" (range-limited whisper) | "sending" (any range)
  word_limit: 25                 # 0 = unlimited
```

## Shape 6 — Utility / buff (no automatic resolution)

Longstrider, Invisibility, Jump, Hold Person's non-damage half, etc. Just the common fields, no
attack/save/damage/heal. The engine won't try to resolve anything — the DM narrates it. This is a
valid, complete spell, **not** an incomplete one, which is why the validator ignores it.

## Special: marked-rider spells (Hex, Hunter's Mark)

```yaml
hex:
  concentration: true
  cast_choice: "ability"         # a choice made at cast time: "ability" (Hex) | "damage_type"
  mark_damage: "1d6"             # extra dice added to the caster's hits vs the marked target
  damage_type: "necrotic"
```

---

## Shapes we canNOT represent yet

These need model + handler work (tracked in #182 / #197). **Don't try to fake them** — author the
common fields + description and leave the mechanic to the DM until the support lands. Proposed
schema for each, so we author them consistently once it's built:

- **Multi-projectile split across targets** (Scorching Ray's 3 rays, Eldritch Blast's later beams,
  Magic Missile aimed at more than one creature). Proposed: `beams: 3`, each an independent attack,
  one `damage:` per beam. **`auto_hit:` already covers Magic Missile at a single target** — what's
  missing is only rolling/splitting per beam, which the DM does by hand.
- **Attack THEN area** (Ice Knife: ranged attack for 1d10, then a Dex-save 2d6 splash).
  Proposed: a nested `secondary:` block carrying its own `save_type`/`damage`/`aoe_*`.
- **Smites and other riders** (Searing/Thunderous/Wrathful/Branding Smite, Hail of Thorns, Divine
  Favor, Zephyr Strike…). They're a bonus-action rider on your *next* weapon hit — like `mark_damage`,
  but expiring after one hit. Proposed: reuse `mark_damage` with a `mark_expires: "one_hit"`.
  **How they're authored today:** `damage:` + `damage_type:` for reference, but **no `save_type` or
  `attack_type`**, so casting just announces and the DM applies the extra damage (and calls any save)
  after the hit. Giving a smite a `save_type` would force the target's save the moment you cast it,
  before you've swung.
- **Automatic upcasting** — `higher_levels:` is text only; damage doesn't scale with slot level yet.

If you author one of these with the proposed fields now, it won't break anything — the unknown fields
are ignored — but the extra mechanic simply won't fire until the code exists.

---

## Case sensitivity & formatting

Confirmed against the code — you don't have to match any particular case:

| Field | Case? | Notes |
|---|---|---|
| `save_type` | insensitive, trimmed | Must be the **full** word — `dexterity`, not `dex`. |
| `damage_type` | insensitive | Resistance/immunity matching is case-insensitive (`cold` = `Cold`). |
| `save_effect` | insensitive | `half` / `none`. |
| `range` | insensitive, trimmed | `Self`/`self`, `Touch`/`touch`, and `60 feet` / `60 ft` / `60 Feet` all work — the first number is taken, spacing ignored. |
| `casting_time` | insensitive | Only the substring `reaction` is checked (to allow off-turn casting). |
| `classes` | insensitive **as a YAML list** | `[ "Wizard", "SORCERER" ]` is normalised to lowercase. The inline comma-string form (`classes: "Wizard, Sorcerer"`) is **not** normalised — always use the list form. Class names must be full (`wizard`). |
| `school`, `components`, `duration`, `description` | n/a | Display text only; not read mechanically. Write them however reads best. |
| `aoe_shape`, `aoe_targets`, `cast_choice`, `social_type` | insensitive | Fixed keyword sets (see each shape). |

**`cast_choice` is not a value — it's a *kind of choice*.** It only takes `ability` or `damage_type`,
and it only does something on a **mark spell** (has `mark_damage`, i.e. Hex / Hunter's Mark): it makes
the game ask the caster to pick, then imposes **disadvantage** on the marked target's checks with that
ability. So:

- `cast_choice: charisma` does **nothing** — `charisma` isn't one of the two valid values, so the
  branch is skipped. (For Friends/Animal Friendship, this would not grant advantage.)
- **There is no "grant advantage on a check" mechanic yet.** Friends (advantage on CHA checks vs a
  target) and similar can't be modelled — author them as UTILITY spells (common fields + description)
  and let the DM adjudicate, until a check-advantage effect exists (#182).

## Validation (what `/dm reload` checks)

The loader and the content check (`ContentValidator`) log a warning (they never refuse to load) when a
spell looks incomplete:

- **`damage_type:` present but no `damage:`** — almost always a forgotten dice value (the Frostbite
  bug). A condition-only save spell should have *neither*, so it isn't flagged. Mark spells (Hex) are
  fine: their dice are in `mark_damage`.
- **duplicate id** across files — kept the first, ignored the rest.
- **`material:`** that isn't a Minecraft item, or is a block that can't be held (`END_PORTAL`).
- **`save_type`** that isn't a full ability name, **`condition_on_fail`** that isn't a condition,
  **`save_effect`** other than half/none, an unknown **`aoe_shape`/`aoe_targets`**, an area with no save.
- **`damage`/`healing`/`temp_hp`/`mark_damage`** that isn't one dice group or a number.
- An attack or save spell with a **`Self` range** (it could only ever target the caster).
- Spell ids that a class, subclass or race references but that don't exist are listed on one
  **info** line (not a warning), since most are higher-level spells nobody has written yet.

Read the console after a reload; a clean load prints nothing. This is the list to work from when
filling in damage — not a manual reread of every spell.

List of spells that will need tweaking in the future:
Acid Splash
Blade ward
chill touch