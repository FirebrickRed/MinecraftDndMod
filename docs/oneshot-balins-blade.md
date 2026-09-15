# One-Shot: *Balin's Blade* — a full-feature playtest

A single ~60-minute adventure that exercises **every major plugin system** in one sitting:
character creation, shops & currency, social/skill checks, the exploration layer (locks, traps,
hidden doors, loot), darkvision, chat spells, the full combat loop (attacks, save-spells, AoE,
conditions, reactions, death saves), class resources, and rests.

Built on content the plugin already ships — **Balin** (`balin_blacksmith`, a merchant whose stat
block hides a *stolen +2 longsword*) and the **kobolds** (`kobold`, `kobold_sorcerer`) — so there's
nothing new to author. For the fine detail of the object/check commands used below, see
[`playtest-oneshot.md`](playtest-oneshot.md) and [`exploration-and-checks.md`](exploration-and-checks.md).

> **Everything here is largely un-playtested** (combat, exploration, and the object layer especially).
> That's the point — this one-shot is the shakedown. Note what breaks.

---

## The pitch (read to players)

> Forty years ago, someone stole the finest blade Balin the dwarf ever forged. Last week a drunk
> miner swore he saw kobolds hauling "a sword that shines like moonlight" down into the flooded
> cellars beneath the old smithy. Balin can't go himself — his knees are shot — so he's hiring you.
> Bring back the blade, keep whatever else you find, and don't die in his basement.

Three acts: **the Forge** (town / shopping / social), **the Cellar** (exploration / traps / stealth),
**the Warren** (combat), then a short **reward & rest** coda.

---

## Who to bring (party that covers the most features)

Pre-alpha is all level 1. To exercise the widest surface, steer a **3–4 person** party toward:

| Role | Good picks | Why it matters for the test |
|---|---|---|
| **Lock/trap specialist** | Rogue (Thieves' Tools) | Sleight of Hand + Investigation; disarming traps |
| **Class-resource martial** | Barbarian (Rage) or Monk (Ki) | Exercises `/dm resource consume/restore` + short-rest recovery |
| **Level-1-subclass caster** | Cleric / Sorcerer / Warlock | Only these pick a subclass at level 1 (#64); brings **save-spells & AoE** |
| **Nature/social face** | Druid or Ranger | Speak with Animals + Persuasion/Insight social checks |

Also steer **at least one race *without* darkvision** (human, half-orc has it, so e.g. human/plasmoid)
so light sources actually matter in Act II, and **at least one *with*** it (elf, dwarf, tiefling,
gnome, half-elf, dragonborn) to feel the difference. Use **three different ability-gen methods**
across the party (point buy / standard array / manual) to test all three.

---

## DM prep — stage it before players arrive

Be a DM first (op, or `/dm add <you>`). Reload if you edited YAML: `/dm reload`.

**Build the smithy + shop (Act I):**
```
/dmentity spawn balin_blacksmith          # his YAML already has shop.enabled: true
/dmentity shop view balin_blacksmith      # confirm stock; if empty, shop create + shop add …
```
Balin already stocks daggers, longsword, greatsword, leather/chainmail/plate, whetstones. Seed the
party with pocket money so they can actually shop:
```
/dm give <player> gold_piece 25           # item type auto-detected from the id
```

**Pre-annotate the Cellar (Act II).** Build a short corridor down to a room. Then, looking at each block:
```
# A) Locked grate at the entrance (STR or Thieves' Tools)
/dm object lock A rusted iron grate, chained from the far side.

# B) A dart trap on a treasure chest (spot / disarm / trigger)
/dm object trap 2d10 dex 13
/dm object hide
# put real loot in this chest the vanilla way, then:
/dm object lock A strongbox, its lid slick with old grease.

# C) A false wall hiding the warren (Perception/Investigation to find)
/dm object desc This stretch of wall sounds faintly hollow when knocked.
/dm object hide
```
Optionally place a **caged rat or the party's-eye-view "guard beast"** — spawn a `wolf` behind bars for
a Speak with Animals beat.

**Pre-stage the Warren (Act III) — don't spawn until the party is close** (HP/entities don't survive a
restart, and glow lingers). When ready:
```
/dmentity spawn kobold                    # ×3–4  (pack tactics; weak alone)
/dmentity spawn kobold_sorcerer           # ×1    (Fire Bolt @ +4, 1d10 fire; 27 HP)
```

**The reward.** The stolen blade is on the sorcerer. Because a slain entity's held weapon is lootable,
you can hand it over on death — or, for a dramatic non-container reveal, annotate a "hidden alcove"
block with `/dm object loot longsword` and `/dm object give <player>`.

---

## Act 0 — Character creation (~15–20 min, before the fiction)

Run this as a table warm-up. Each player: **`/character create`** — this opens the menu **and** hands
you a **"Create Character"** paper. Close the menu and you can right-click that paper any time to pick
up where you left off; when you finish, the paper is replaced by your **Character Sheet**. DMs can drive
a struggling player's menu with `/character create <player>`.

**Test as they build:**
- Race → subrace; class → **level-1 subclass** for cleric/sorcerer/warlock; background; abilities
  (use all three gen methods across the party); skills; spells; equipment choices.
- Close the menu mid-creation and **right-click the "Create Character" paper** to resume.
- `/character list`, `/character view`; once finished, right-click the character's **sheet paper** to
  open the **viewer** (a finished sheet opens the viewer, not creation).
- Click ability tiles → **Skills** drilldown; click a skill → roll with advantage/disadvantage.
- Confirm starting gear renders as **real items** (not purple boxes) and sheets saved to
  `plugins/jkvttplugin/Saved/Characters/`.

✅ Covers: creation flow, subclasses, racial traits (darkvision/innate spells show on the sheet),
skills UI, persistence.

---

## Act I — The Forge (~10–15 min) · shops, currency, social

1. **Roleplay Balin** (gruff, Scottish, secretly kind — see his `dm_notes`). He explains the job and
   **lowballs the reward** — a curt "fifty gold, take it or leave it" — and plays it off as just
   another lost bit of stock.
2. **Shopping.** `/dmentity trade balin_blacksmith` — players buy a light source, a weapon upgrade,
   maybe armor. Watch prices show in the right currency; buy until an item hits **out of stock**.
   > ⚠️ **Keep merchant prices ≤ 64.** A currency cost over 64 breaks the trade (see Known rough edges),
   > so for this playtest stick to cheap stock and don't sell the plate armor (1500 gp) in Balin's YAML.
3. **Selling.** Have a player sell starting gear back (sell price = 50% of buy). Confirm his stock
   *increases* with what they sold: `/dmentity shop view balin_blacksmith`.
4. **Haggle (social check).** A player pushes for a discount:
   ```
   /dm check <player> skill persuasion dc 13
   ```
   Or a player senses the job means more to Balin than he's letting on — and it does: **that blade
   is his own finest work**, forged decades ago and stolen from him; the gruff "fifty gold" hides a
   man who'd pay far more to get it back. Call a contested check (the DM rolls Balin's Deception):
   ```
   /dm check <player> insight vs balin deception     # contested; DM rolls Balin's side
   ```
   On a win, the player reads him — now a Persuasion check can talk the reward up (pay out in the
   coda). Results land on the **DM** with a **[Share]** button; reveal only what you choose.

✅ Covers: shop buy/sell, currency, stock tracking + persistence, tab completion, single & contested
social checks, `/dm give`.

---

## Act II — The Cellar (~15–20 min) · exploration, traps, stealth, chat spells

Descend into the dark. **Darkvision races see; others need light** — a good moment for a Light cantrip,
a torch bought in Act I, or a Light-Domain cleric.

1. **The locked grate (A).** A player right-clicks it → sees it's locked, tells you their approach.
   You get a **[call a check]** button → `/dm check <player> skill sleight_of_hand dc 15` (or
   `athletics` to force it — forcing makes noise; see step 4). On success, jump to them
   (click the coords in your notification) and `/dm object unlock`.
2. **The strongbox trap (B).** Someone reaches for the chest. Every DM gets the 🪤 prompt with
   `[Perception] [Disarm] [Trigger]`. Play it out: did they **spot** it (Perception)? Try to **disarm**
   it (Thieves' Tools/DEX → `/dm object disarm`)? Or **trigger** it — the button calls the victim's
   **DEX save**; apply the 2d10 (out of combat, narrate it; or `/combat damage` if you'd rather).
   Then `/dm object unlock` for the real loot inside.
3. **The caged beast (chat spell).** A Druid/Ranger casts Speak with Animals on the wolf:
   `/character cast speak_with_animals` → you voice it back with `/dm animalreply <player> …`. It
   warns them the "little scaly ones" are just past the hollow wall. Split party? `/character cast
   message <ally> psst, found a secret door` tests the Message cantrip + **[reply]**.
4. **The false wall (C).** Nothing happens on right-click until found. Call it when they search:
   `/dm check <player> skill perception dc 13` (Help from a second PC = advantage; Guidance = +1d4).
   On success: `/dm object reveal`, then narrate the passage grinding open into the warren.
5. **Set up the ambush.** Before they push through, call a **group Stealth** roll to sneak up on the
   kobolds — hold each PC's number (`/dm check <player> skill stealth`) to compare against kobold
   passive Perception next act.

✅ Covers: darkvision, locked objects, traps (spot/disarm/trigger), **saving throws**, hidden doors,
Perception/Investigation, Help/Guidance, chat spells (Speak with Animals, Message), held checks.

---

## Act III — The Warren (~20–30 min) · full combat

Spawn the kobolds (see prep). Reward good Act-II Stealth: add sneaky PCs' targets as **surprised**,
or add a kobold `--hidden` for an enemy ambush if they were loud.

```
/combat start
/combat add --radius 30                     # grab everyone nearby
/combat add kobold_sorcerer --hidden        # (optional) lurking caster
/combat surprise <kobold_or_PC>             # from the stealth outcome
/combat rollforinitiative
/combat status
```

**Run the loop** and deliberately touch each mechanic at least once:

- **Attacks & roll modes.** On your turn, **left-click the kobold** while holding your weapon and the
  game hands you the filled-in command — or type `/combat attack <kobold>` yourself. Either way you
  get HIT/MISS/CRIT, then run the `/combat damage` it prompts. Cycle the input styles across the
  fight: `autoRoll`, `manualRoll <n>`, `total <n>`.
  *(Left-click replaced right-click in #189 — right-click is now "use" only. Worth saying out loud
  at the table if anyone played the earlier build.)*
- **Save-spell + AoE.** Have the caster drop a Burning Hands / Thunderwave-type spell with **no target**
  (`/combat cast <spell> autoRoll` → aim preview); the kobolds `/combat save` vs it. And an
  **attack-roll spell** from the enemy: the sorcerer's `/combat cast fire_bolt <PC> autoRoll`.
- **Conditions ± adv/dis.** Knock a kobold **prone** or **frightened**: `/combat condition <kobold>
  add prone` and watch attack advantage/disadvantage apply on subsequent rolls.
- **Reactions / opportunity attacks.** A bloodied kobold flees an adjacent PC → the ⚡ end-of-turn
  prompt / `/combat reactions <reactor> attack`. DM sees the whole-table reaction roster.
- **Class resources.** Barbarian rages / Monk spends Ki: track via the sheet and
  `/dm resource consume <character> <resource> 1` (restore later with `restore`).
- **Healing, temp HP, death saves.** Drop a PC to 0 → `/combat deathsave autoRoll` on their turn;
  the cleric heals (`/combat heal <PC> autoRoll <dice>`) or grants `/combat temphp <PC> <n>`.

End cleanly: `/combat finished` (clears glow, scoreboards, prone).

✅ Covers: initiative, surprise/hidden combatants, action economy, attack + damage roll modes,
attack-roll spells, save-spells, AoE aim, conditions + advantage/disadvantage, reactions/opportunity
attacks, class resources, healing, temp HP, death saves.

---

## Coda — Reward & Rest (~5 min) · loot, rests, closing

1. **The blade.** Loot Balin's stolen +2 longsword off the sorcerer (held-weapon loot), or reveal it in
   the annotated alcove: `/dm object give <player>`.
2. **Rest.** `/character rest short` — recover short-rest resources & hit dice (confirm the Barbarian's
   Rage / Monk's Ki come back). Do a `/character rest long` to confirm full HP + spell-slot restore.
3. **Return to Balin.** Turn in the blade → he pays up. Base reward `/dm give <player> gold_piece 50`;
   if a player read him in Act I and talked it up, add a second stack (a single `_piece` stack caps at
   64, so hand out large sums in multiple gives, or in platinum). Sell dungeon loot back at his shop.
   `/character close` to save & close sheets.

**Before you shut the server down.** Combat state, entity HP, and corpses now persist and restore on
restart (#89/#105/#31) — but the *in-progress turn* resets and player HP only autosaves every ~5 min,
so it's still tidiest to `/combat finished`, `/dmentity remove <name>` unwanted NPCs, and
`/character rest long` or `close` before stopping. See Known rough edges.

✅ Covers: loot handoff, short/long rest recovery, resource restore, shop sell, save-on-close.

---

## Timing dial

| Budget | Run |
|---|---|
| **~30 min** | Skip Act 0 (pre-build characters), one lock + the trap in Act II, straight to the kobold fight. |
| **~60 min** | The full three acts as written. |
| **~90 min** | Add the contested Insight, the Speak-with-Animals beat, group Stealth, a second kobold wave (`/dmentity spawn kobold` mid-fight), and both rests. |

---

## Feature-coverage checklist

Tick these off during the run — this is the "test everything" contract.

**Creation & data**
- [ ] Race + subrace, class + **level-1 subclass**, background, all 3 ability-gen methods
- [ ] Skill selection, spell selection, equipment choices; sheet saves & reloads
- [ ] Racial traits visible (darkvision, innate spells, resistances, speeds)

**Shops & economy**
- [ ] Buy from merchant (stock decreases, out-of-stock message)
- [ ] Sell to merchant (inventory increases; 50% price)
- [ ] Currency renders correctly; shop persists (`Saved/Shops/…`)

**Checks & social**
- [ ] Single graded check (DC), ungraded check, advantage (2d20)
- [ ] Contested check (PC vs NPC, DM rolls the NPC)
- [ ] Help (advantage) / Guidance (+1d4) folded in; **[Share]** gate works

**Exploration layer**
- [ ] Locked object (pick vs force); DM `[call a check]`; vanilla open blocked
- [ ] Trap: spot / disarm / trigger; **DEX save** on trigger
- [ ] Hidden door revealed by check; annotations survive a reload
- [ ] Loot on a container-less block (`/dm object loot` + `give`)

**Spells (chat & combat)**
- [ ] Speak with Animals + `/dm animalreply`; Message + [reply]
- [ ] Attack-roll spell (Fire Bolt); save-spell; **AoE aim** (no target)

**Combat**
- [ ] Initiative, surprise/hidden, action economy (action/bonus/movement)
- [ ] Attack HIT/MISS/CRIT → damage; `autoRoll` / `manualRoll` / `total` all used
- [ ] Conditions apply advantage/disadvantage; reactions / opportunity attacks
- [ ] Healing, temp HP, death saves; `/combat finished` cleans up

**Resources & rests**
- [ ] Class resource consume + restore; short-rest recovery; long-rest full restore

---

## Known rough edges to expect (don't file these twice)

- **Shop prices cap at 64.** A currency cost > 64 is silently clamped to 64 by the merchant trade
  (`ShopGuiUtil.createCurrencyItem` sets a single `_piece` stack, and a vanilla trade ingredient maxes
  at 64) — no denomination conversion or coin-pouch yet. Keep playtest prices ≤ 64; big sums need
  higher denominations or multiple stacks. *(Worth an issue if it isn't one.)*
- **Combat crash recovery mostly works** (not "none"): sessions persist to `CombatSessions/*.yml`
  (initiative, round, turn index, conditions, death saves — #105); entity HP + corpse state live on
  the armor-stand PDC (#89); player HP autosaves every ~5 min (#31). **Lost on a hard crash:** the
  in-progress turn (resets), player HP since the last autosave, and any stray turn-glow (no startup
  scrub). *(The "no crash recovery" line in `COMMANDS.md`/`CLAUDE.md` is stale — flag if you'd like
  me to correct those too.)*
- Conditional spells (Genie/Lunar) and conditional advantages are parsed but **not applied**.
- No level-up, multiclassing, feats, or in-game equip/unequip yet — everyone's level 1.
- Player-chosen tool/language proficiencies don't persist across reload yet (#17).
