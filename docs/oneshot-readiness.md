# One-shot readiness — where we are (2026-09-17)

A snapshot of what's ready for a one-shot playtest, what needs doing first, and what can wait.
It comes from a full pass over the open issues, the code, and the two playtest sessions
([`playtest-2026-09-15.md`](playtest-2026-09-15.md)). The adventure script is
[`oneshot-balins-blade.md`](oneshot-balins-blade.md).

**Bottom line:** the core loop works end to end: character creation → shopping → DM-called checks →
exploration objects → combat with attacks, spells, conditions and death saves → loot → rest. Most of
it was used for real in the two September sessions. **Nothing is blocking a level-1 one-shot.**

What *will* hurt at the table is a short list of gaps. Needs 1–7 are now done: content, plus HP
changes outside combat. What's left is two DM workarounds — **reactions** (#195) and **boats**
(#198) — neither of which blocks play. Everything else can wait.

The build is green (`gradlew build`, 2026-09-17). There are no automated tests (#14).

---

## ✅ Have — ready to use

| Area | What works | Confidence |
|---|---|---|
| **Character creation** | Race/subrace, class + level-1 subclass, background, 3 ability methods, skills, spells, equipment choices with real item previews, resumable "Create Character" paper, delete clears gear | Played ✅ |
| **Character sheet** | HP/AC breakdown, speed, race/class/background, abilities → skills → roll, spellbook with slots + concentration, class resources | Played ✅ |
| **Persistence** | Characters (HP, slots, resources, equipped armor) save on every change; entities + corpses survive restart (bd929da); combat sessions snapshot per turn | Characters ✅ · entities fixed, not retested · combat ❓ (#165) |
| **Shops** | Villager trade GUI, buy/sell, stock tracking + persistence, price adjust/discount/markup | Earlier testing ✅ (content bugs below) |
| **DM checks** | `/dm check` with a private DC, adv/dis (real 2d20), contested (player vs player), held values, DM-first + **[Share]** | Built, lightly played |
| **Exploration** | `/dm object` lock/hide/reveal/desc/trap/disarm/loot/give; the DM gets **[call a check]** / trap buttons; persists | Chest open ✅ (after `spawn-protection=0`) · traps not played |
| **DM mode** | `/dm mode` hotbar: View, Possess, Move, Add/Remove Combatant, Spawn Entity, Exploration tools | Possession + Fire Bolt ✅ |
| **Combat core** | Start/add/surprise/hidden, initiative, turn order + freeze, action economy, movement tracking, `/combat finished` cleanup | Played ✅ |
| **Attacks** | Left-click prompt, weapon picker, adv/dis shown before the roll, `autoRoll`/`manualRoll`/`total`, crits, auto damage type, correct-target damage | Played ✅ |
| **Ranged & thrown** | Ammo consumed and recovered, vanilla arrows blocked, reach weapons, stab-or-throw | Ammo ✅ · thrown new (#192) |
| **Spells in combat** | Attack spells, save spells, AoE aim + confirm, healing, temp HP, conditions on fail, rituals, reaction-time spells, Hex, spellbook → `/combat cast` | Fire Bolt ✅ · Frostbite fixed · rest lightly played |
| **Chat spells** | Message / Sending / Speak with Animals + DM `animalreply` | Built |
| **Conditions** | Advantage/disadvantage engine, save adv/dis, racial conditional advantages, mechanical enforcement | Built |
| **Effect Engine** | Rage, Dragonborn breath, Lucky, Relentless Endurance, Savage Attacks, racial resistances | Built |
| **Death & dying** | 0 HP → downed + prone pose, can't jump, death saves, stabilize, heal back up | Prone ✅ |
| **Loot** | DM-mediated corpse search, lootable held weapons, chest loot | Built |
| **Rests** | `/character rest short\|long`, `/dm rest <character>`; slots, resources, innate spells | Built |

---

## 🔴 Need — fix before the one-shot

Ordered by how likely each is to bite, with a cost estimate. None needs new systems.

| # | Problem | Why it bites | Cheapest fix | Cost |
|---|---|---|---|---|
| 1 | ~~HP can't change outside combat~~ | | **Fixed properly:** `DamageHandler` now takes a nullable session, so damage and healing run the same path everywhere — resistances, downing, death saves, persistence. New `/dm hp <who> <damage\|heal\|temp\|set> <amount> [type <t>]` (dice allowed) and drinkable potions (`healing:` on an item → click fills `/character drink`). | done |
| 2 | ~~Balin's shop lists ids that don't exist~~ | | **Fixed:** `chain_mail`, a real `whetstone` item; plate is commented out until #94. | done |
| 3 | ~~Shop prices over 64~~ (#94) | | **Worked around in content:** chain mail is 8 platinum. The content check now flags any price over 64. The code fix is still #94. | done |
| 4 | ~~Holy Symbol is unusable~~ | | **Fixed:** `focus_type: holy_symbol`. The bard (instruments) and artificer (thieves' tools) focuses now exist too. | done |
| 5 | ~~No adventuring gear~~ | | **Fixed:** `Items/adventuring_gear.yml` (tools, all class packs, torch, rope, rations, potion of healing, clothing, background keepsakes) and `Items/musical_instruments.yml`. Backgrounds used `equipment:`, which is ignored, so they gave **no gear at all**. Now `starting_equipment:` with `gold_piece x15`-style quantities. A potion still needs #1 to heal. | done |
| 6 | ~~Level-1 damaging spells with no `damage:`~~ (#197) | | **Fixed:** all 1st-level spells and cantrips (PHB/XGE/TCE) are authored alphabetically, with dice. Magic Missile, Ice Knife's splash, multi-ray and smites are still DM-applied (#182). | done |
| 7 | **Contested check vs an NPC doesn't work.** `/dm check A insight vs balin deception` → "Both players must be online". | The one-shot script (Act I) uses exactly this. | Doc fix (done in the script): run the player's Insight as an ungraded check and roll Balin's Deception with `/roll 1d20+1`. | done |
| 8 | **Reactions are unreliable** (#195): the offer doesn't reach the DM, they don't use roll keywords, and damage reactions may time out. | Opportunity attacks get missed. | For the one-shot, the DM watches for provoking moves. If the ⚡ prompt appears, use it (`/combat reactions <reactor> attack` only works while one is pending). If not, roll the attack with `/roll 1d20+N` and apply a hit with the DM-only `/combat override <target> <amount>`. A real fix is a half-day pass. | 0 now / ~4 h |
| 9 | **Boats bypass the turn freeze** (#198). Likely root cause: teleporting a vehicle with passengers silently fails in Paper. | A player skips turn order. | Don't build the arena near water (free), or cancel `VehicleEnterEvent` during combat (~15 lines). | 0 / 30 min |

**Server config** (already known, easy to forget on a fresh server): `allow-flight=true`,
`spawn-protection=0`.

---

## 🟠 Retest — fixed or built, but never confirmed at a table

Put these on the playtest checklist rather than treating them as open work:

- **Combat restore after a restart** (#165). Steps are on the issue. If it fails, the rule is simply "don't restart mid-fight".
- **Entities survive a restart** (bd929da fixed the shutdown deletion; #166 closed).
- **Exactly one attack prompt per click** (#167). Click the kobold directly *and* aim-then-click.
- **DM Add tool** double-toggle fix, **miss scatter** (±0.12), **downed-can't-jump**. All from session 2's fix round.
- **Traps end to end**: spot / disarm / trigger → DEX save (damage needs Need #1).
- **Thrown weapons** (#192): throw a handaxe, see it leave your hand, pick it up.
- **`autoRoll autoRoll`** (session-1 #8): couldn't reproduce. Note the exact text if it recurs.
- **Ammo playtest notice** (#193 §1): remove it once the ammo visuals are confirmed.
- **HP outside combat (2026-09-17)**: `/dm hp Zek damage 2d10 type piercing` with no fight running,
  then `/dm hp Zek heal 5`; drop someone to 0 out of combat (they should fall unconscious and go
  prone); drink a Potion of Healing in and out of combat (in combat it should cost the Action).
- **New content (2026-09-17)**: build one character per background and check the gear arrives
  (coins as `gold_piece` stacks, no "Unknown item" papers). Right-click a holy symbol as a Cleric and
  an instrument as a Bard. Cast one new spell of each shape: Burning Hands (cone), Guiding Bolt
  (attack), Tasha's Hideous Laughter (save → prone), False Life (temp HP).
- **The console after `/dm reload`**: the new content check should print nothing but one info line
  (higher-level spells not written yet).

---

## 🟡 Should — noticeably better, not required

| Item | Why | Cost |
|---|---|---|
| ~~Content validation at load~~ | **Done:** `ContentValidator` checks kits, shops, loot, materials, focuses and spell fields on every reload. | done |
| **Hit dice** (#52) | Short rests heal nothing today. A DM can hand-wave it with Need #1. | ~half day |
| **Out-of-combat casting** (#152) | Only chat spells cast outside combat. Light / Guidance / Cure Wounds are DM-narrated. | ~half day for "announce + spend slot" |
| **`/combat bonus` lists bonus actions** (#176) | Today it just marks the bonus action used. | ~2 h basic |
| **Racial traits on the sheet** (#65) | Darkvision, resistances and innate spells aren't shown. Players ask "do I have darkvision?". | ~1–2 h |
| **Spell item right-click casts** (#179) | Spellbook and focus work; a bare spell item does nothing. | ~2 h |
| **Contested checks vs NPCs** (#186) | Removes the Need #7 workaround. | ~2 h |

---

## 🟢 Nice to have — after the one-shot

Magic items & attunement (#188, next big epic) · level-up (#153) · per-instance entity edits and the
alias/reveal model (#194) · spawn groups (#79) · passive Perception and group checks (#186) ·
stealth/hide (#163) · darkvision → night vision (#148) · random loot tables (#164) · currency
conversion (#91/#92) · the turn glow hugging the model (#134) · Bless/Bane/Guidance-style roll
buffs (#182, #70) · Ready action (#157) · factions (#155) · `/roll` expressions (#57) ·
op-is-not-DM (#116) · unit tests (#14) · codebase restructure (#95).

---

## Issue triage — 2026-09-17

**Closed (17)** as done or superseded, each with a comment explaining why:
#26 spell slots (level-1 done, progression → #153) · #39 Rage · #43, #45 rest state machines
(superseded by the #90 decision) · #58 private rolls and #61 DM-prompted checks (→ `/dm check`, #186) ·
#71 player nameplates (not possible in Paper) · #82 entity HP · #83 entity initiative · #85 DM wand
(→ DM mode) · #102 enemy visibility (→ #194 + possession) · #110 DM entity control epic (done, groups
→ #79) · #124 weapon tab tooltips (→ weapon picker) · #129 checks on objects (duplicate of #185) ·
#160 rest-requirement traits (data done; #90 removes enforcement) · #166 entities not persisting
(fixed bd929da).

**Status comments added:** #165 (retest steps) · #174 (only subrace advantages left) · #186
(what shipped / what's left) · #194 (per-viewer broadcast note moved from #102) · #197 (current
36-spell list, one-shot picks first) · #198 (likely root cause + fix).

**Noticed, not ticketed:** commit `7f885c0` "DM-mode Spawn Entity tool" is tagged `(#20)`, but #20 is
the far-future character-import ticket. The number came from item 20 in the playtest notes, so
the spawn tool has no ticket of its own.
