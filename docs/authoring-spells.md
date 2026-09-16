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
  classes: [ "sorcerer", "wizard", "druid", "warlock", "artificer" ]
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

- **Multi-projectile** (Magic Missile 3 darts, Scorching Ray 3 rays, Eldritch Blast at higher levels).
  Proposed: `beams: 3` — each an independent attack (Scorching Ray) or auto-hit (`auto_hit: true`,
  Magic Missile). One `damage:` per beam.
- **Attack THEN area** (Ice Knife: ranged attack for 1d10, then a Dex-save 2d6 splash).
  Proposed: a nested `secondary:` block carrying its own `save_type`/`damage`/`aoe_*`.
- **Smites** (Searing/Thunderous/Wrathful/Branding). They're a bonus-action rider on your *next*
  weapon hit — like `mark_damage`, but expiring after one hit. Proposed: reuse `mark_damage` with a
  `mark_expires: "one_hit"`.
- **Automatic upcasting** — `higher_levels:` is text only; damage doesn't scale with slot level yet.

If you author one of these with the proposed fields now, it won't break anything — the unknown fields
are ignored — but the extra mechanic simply won't fire until the code exists.

---

## Validation (what `/dm reload` checks)

The loader logs a warning (it never refuses to load) when a spell looks incomplete:

- **`damage_type:` present but no `damage:`** — almost always a forgotten dice value (the Frostbite
  bug). A condition-only save spell should have *neither*, so it isn't flagged.
- **duplicate id** across files — kept the first, ignored the rest.

Read the console after a reload; a clean load prints nothing. This is the list to work from when
filling in damage — not a manual reread of every spell.
