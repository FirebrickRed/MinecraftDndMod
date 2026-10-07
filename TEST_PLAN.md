# Test Plan

**Before you start:** `gradlew build` (it runs the automated tests and refuses to build if one
fails), copy `build/libs/*.jar` into the server's `plugins/`, restart. Load the resource pack.

**How this is laid out**

- **One box = one thing to do and one thing to look for.** If a box needs setup, the setup is in
  the section's intro or the box above it.
- **Part 1** is everything you can do alone, as the DM, with your own character. It starts with
  **By character**: one section per character to make, rows in play order (creation → sheet →
  fight → rest), so you make each one once and work straight down. Spells live under the class
  that casts them. After that, **Any character** holds what doesn't care who you play.
- **Part 2** needs a second account that is **not** a DM (a friend, or a second account you `/deop`).
  Locks are here too: a DM walks through locks by design, so only a player can test them. So is
  anything that says **[Ask the DM]**: a DM gets [Do it anyway] or [Add it] instead, so you'd never
  see the player's side alone. A row that needs another player, or a player who isn't a DM, goes here.
- **Restart checks** are batched at the end of Part 1 so you restart once.
- A row from one ticket's work ends with its number, e.g. `(#229)`, so a failure has an obvious
  place to be reported. A new class or race row goes under its character, not a new ticket section.
- Tick a row with `[X]`. Ticked rows get deleted at the next round (git keeps the history).
- Notes go in **Playtest notes** at the bottom. They're answered in chat and turned into rows, then cleared.

---

# Paper 26.2 upgrade (#211)

Only what the upgrade changed or can plausibly break; the rest of this plan covers everything else.
The upgrade is merged: main builds for 26.2, so it's the usual jar from `build\libs\`, on a server
running **Java 25**.

**Upload the updated pack first** (the 26.2 format changed, and every player needs it): zip it, put it
where `resource-pack=` in `server.properties` points, and set `resource-pack-sha1` to the new zip's
SHA-1 (`certutil -hashfile <zip> SHA1`). These rows check what players get from the server, not your
local copy, and an old server pack loads above a local one. Steps: `docs/resource-pack.md`.

- [ ] **Resource pack:** `pack.mcmeta` has `"min_format": 88, "max_format": 88`. Joining, the server prompts
      for it, and it loads without a "made for an older version" warning.
- [ ] **Models render, none purple:** race and class tiles in `/character create`, a spawned kobold
      (its head model), and The Kindler.
- [ ] **Annotate form** (a dialog, Paper API): right-click a chest with the Annotate tool → the form
      opens; Save applies it.
- [ ] **Shop screen** opens, titled with the merchant's name; buying and selling work.
- [ ] **Item tooltips:** no vanilla "attack damage" / "Dyed" / "No Effects" lines on D&D items.
- [ ] **Combat basics:** left-click a creature to get the attack prompt; a real bow shot and loading a
      crossbow are both refused; the turn glow shows; a killed creature tips over.
- [ ] **Possession:** you go invisible and stand at the creature's size; Let go → back to your own
      size, still invisible if you were before.
- [ ] **Your datapack:** visit each custom dimension; Asteria Glade and Xegrurn keep their fixed time
      of day; Xegrurn has its ash. No datapack errors in the server log on startup.
- [ ] **Restart:** a spawned creature and a character's HP survive a restart.

---

# Part 1 — You alone, as the DM

# By character

For the fight rows, spawn a couple of goblins or kobolds to hit, and a `town_guard` or two to stand
in for allies (`/dm entity spawn town_guard`). Spawn alira as "The Kindler" for the out-of-a-fight
rows (`/dm entity spawn alira "The Kindler"`).

## Fighter

- [ ] **Weapon switch:** start your turn holding a longsword, scroll to a handaxe and back a few times →
      nothing in chat, just a grey action-bar line. Attacking with the longsword rolls as normal. (#190)
- [ ] Scroll to the handaxe and left-click a kobold → no roll buttons, but "⚠ You started your turn holding
      Longsword. Switching to Handaxe uses your free object interaction. [Yes, I'm switching] [Ask the DM]".
      [Yes] → the roll buttons; another handaxe attack this turn doesn't ask. (#190)
- [ ] Then try a third weapon → "another switch takes your Action…" with only [Ask the DM]; your [Allow] →
      roll buttons, your [Deny] → "attack with Handaxe this turn". (#190)
- [ ] Holding the longsword, type `/combat attack <kobold> handaxe autoRoll` → the same question, nothing
      rolled. Next turn starts fresh. (#190)
- [ ] **Shortsword** in the main hand + a **dagger** in the off hand (the shield slot) at the start of the turn.
      Left-click to attack with the shortsword, then `/combat bonusAction` → an off-hand **Dagger** attack is
      listed; click it → "⚡ This attack uses your bonus action (two-weapon fighting)" and **no** "are you
      switching weapons?" question. Any class works, but both weapons must be **light**: longsword + dagger
      gets no bonus attack: "The Dagger can't be a bonus attack: … Your Longsword isn't light." (#190)
- [ ] **Defense** fighter: AC goes up by 1 when you put on chain mail (and not unarmored). The sheet's AC
      tile lists it: "Armor: 16", "Defense: +1". (#229)
- [ ] **Great Weapon Fighting**, greatsword, damage with `autoRoll` → "(Great Weapon Fighting: any 1 or 2
      was rolled again.)" A plain hit on a longsword with a shield doesn't say it. (#229)
- [ ] **Two-Weapon Fighting**, dagger in each hand: the off-hand attack's damage has `+N[DEX]`. (#229)
- [ ] BG3 order (default `combat.bonus_attack_timing: any_time`): a fighter with a dagger in each hand
      uses the off-hand button first (`… dagger bonus`), then still has their Action. Its damage has no
      DEX. Set `after_attack_action` and restart: the same button now says to attack first. (#221)
- [ ] With nothing to spend it on, the action bar shows **Bonus: —**. (#221)

## Rogue

Make a **High Elf Rogue** (Fire Bolt as the Wizard Cantrip) and a **Tiefling Rogue**.

- [ ] **High Elf Rogue:** `/character cast fire_bolt` works and uses INT.
- [ ] **Tiefling Rogue:** right-click a chest holding thieves' tools → the chest's own [Open it] /
      [Ask for a check] prompt, not a focus message. (I can't do this cuz I'm dm or op or something)
- [ ] Once the Sneak Attack damage lands, a second hit that turn offers nothing. A club (not finesse) → nothing. (#229)
- [ ] **Shield:** a Sneak Attack hit that Shield turns into a miss → the next hit that turn can still Sneak Attack. (#229)
- [ ] `on_fail: always` / `never` in config.yml behave as named.

## Wizard

- [ ] Out of a fight, `/character cast blade_ward Wolf` → "Blade Ward only targets you (range: Self)…", no
      "casts Blade Ward on Wolf". `/character cast false_life` (a wizard or sorcerer who knows it) → the dice
      are shown, you gain that many temporary hit points, and the slot is spent; `… false_life Wolf` → refused.
- [ ] `/combat cast blade_ward <someone else>` (and `/character cast` out of a fight) → "only targets you
      (range: Self). Cast it without a name and it's on you." No [Do it anyway], even for you. No name → it's on you.
- [ ] Start aiming Burning Hands, then `/combat nextturn` (or end your turn) → "Your turn is over. Your Burning
      Hands aim is cancelled: nothing spent." Your 1st-level slot is still there. (#237)
- [ ] Aim Burning Hands, then (without firing) cast another 1st-level spell with your last slot, then right-click
      → "…doesn't go off: nothing spent." (#237)
- [ ] In a fight, cast Fire Bolt and resolve it, then cast it again the same turn → "You've already used your
      Action this turn". Nothing is rolled or spent. (#235)

**Out of a fight**

- [ ] A spell with range Self that targets (not an area) at someone else → "only targets you".

**In a fight**

- [ ] After Start combat, on your first turn → "Your opening move: Fire Bolt at The Kindler [do it]".
- [ ] With a spell ready, [cancel] → left-click attacks with your weapon again. End your turn with one ready →
      next turn, left-click is your weapon (it lapses with the turn). (#179)
- [ ] Burning Hands / Thunderwave from the spellbook → the aim preview starts at once; right-click confirms.
      **The slot is spent** (spellbook shows one fewer) and so is the Action. Cancel the aim instead → nothing
      spent. (An area spell used to be free in a fight.) (#179)

## Cleric

Take the Life Domain. A couple of spawned `town_guard`s stand in for allies.

- [ ] Out of a fight, Sacred Flame at a creature, [Let it happen], cast → the cast line reads "DC 13 (8 +2[Prof]
      +3[WIS]) DEX save", and **you** get "You roll for …: DEX save, DC 13: [Roll it] [I rolled…] [My total…]". (#245)
- [ ] Roll a fail → "… fails the save", and the caster gets the damage roll buttons; the damage lands. Roll a
      success → "… saves, and resists Sacred Flame", no damage. No second click either way. (#245)
- [ ] Instead of rolling, the small grey **[failed]** / **[saved]** after the buttons rule it without a roll. (#245)
- [ ] In a fight, a save spell's cast line shows the same DC breakdown. (#245)

- [ ] **Out of a fight, Bane** at a creature, [Let it happen], it fails the save → "… is under Bane: …" and its next
      attack or save shows `-1d4[Bane]`. It saves → nothing on it. (#267)
- [ ] **Out of a fight, a save spell with a condition** (Charm Person, Cause Fear or Tasha's Hideous Laughter; any caster
      who knows one): the target fails → "… is now <condition>!" and `/dm view <them>` lists it, with no
      "/dm adjust applies it" note to you. The same cast at a creature immune to it → "… is immune to being …". (#267)
- [ ] **In a fight, the same three** (Bane, a condition, an immune creature) read exactly as before, in the same
      order: the effect line, the damage step, then the condition. (#267)
- [ ] Out of a fight, cast Sacred Flame at a creature, [Let it happen] → "…needs no attack roll (the target
      saves instead): cast it again to go. [cast it]". Guiding Bolt instead → "…is an attack: roll to hit." (#247)

**Out of a fight**

- [ ] Sacred Flame with Let it happen → the DM sees the DC, **[Roll their save]**, **[Failed: damage]** / **[Saved: …]**.
      [Roll their save] → "🎲 You roll for <creature>: Dexterity save, DC 13" with the three buttons.
- [ ] **[Let it happen]** a Sacred Flame → you get **[cast it]**, not a d20 prompt (a save spell
      doesn't roll to hit).

**In a fight**

- [ ] Cure Wounds → the dice show.
- [ ] Cure Wounds → `+3[WIS]`, not "your spellcasting modifier"; **[Roll it]** heals
      (it used to re-prompt in physical-dice mode); the table sees the roll line. (#216)
- [ ] Touch spells reach one block further than before (5 ft of slack, same as out of a fight).
- [ ] `/combat cast bless me, Town Guard` → "casts Bless on …: +1d4 to attacks and saves for 1 minute";
      your sheet's Active Effects tile shows it. Four names → "takes up to 3 targets". (#225)
- [ ] Blessed, you attack: the prompt's bonus reads `… +1d4[Bless]`, and the result shows the d4 it rolled
      (`+3[Bless 1d4]`). A save and a death save get it too; a skill check doesn't. The guard's attack (you roll
      for it) shows it too. (#225)
- [ ] Blessed, attack with **[I rolled…]** and type `manualRoll 14 3` → the result shows `+3[Bless 1d4]` (your 3). Its
      hover says to type the d20 then the d4. `manualRoll 14` alone, or `manualRoll 14 9` → a red "Bless adds 1d4 …" /
      "9 isn't a 1d4 roll" and the buttons again; nothing rolled. `autoRoll` rolls both. (#225)
- [ ] Spellbook → **Bless** → "left-click up to 3 targets (shift-click the last)". Click a guard →
      "Bless: Town Guard (1 of 3) [Cast on these] [+ me] [cancel]"; click it again → off the list; **[+ me]** adds you;
      **shift**-click a second creature → straight to "[Cast it]". Clicking three fills `/combat cast bless A, B, C`. (#179)
- [ ] `/combat cast bane Goblin, Goblin 2` → each makes a CHA save (you roll for them); the one that fails is
      "under Bane" and its next attack/save shows `-2[Bane 1d4]`. (#225)
- [ ] Cast another concentration spell, or fail a concentration save → "Bless ends on …". (#225)
- [ ] Bless runs 10 rounds and ends on its own; your ◈ goes with it. (#225)
- [ ] `/combat cast shield_of_faith me` → your AC +2 (sheet and `/combat status`), and it lasts past your
      next turn. On a creature, its AC goes up too. (#225)
- [ ] **Spell looks:** Sacred Flame → a burst of white on the target; Cure Wounds → hearts rise around the
      target; Bless → a gold ring; Bane → a dark red burst. (#230)

## Warlock

- [ ] Hex someone, then cast another concentration spell (Bless if you have it, or any) → attack the
      Hexed target: no Hex damage offered. (#238)
- [ ] A warlock without Hex: `/combat cast hex <target> strength` → refused ("doesn't know"), no slot spent. (#236)

- [ ] **Genie:** **Genie Kind** and **Genie's Vessel** now show (they never did).
      Efreeti → Burning Hands is **offered in the spell step** (not handed over). (#222)
- [ ] **Fiend:** the spell step offers Burning Hands and Command next to the warlock spells; taking one
      uses a pick (2 of 2). The sheet doesn't know the rest. The patron's tile says **Expanded Spell List
      (you may learn these)**. (A cleric still gets their domain spells for free.) (#228)
- [ ] **Hexblade:** a shield in the off hand, no "not proficient" warning.
- [ ] Hex at someone 120 ft away → refused for range (it wasn't checked before).
- [ ] Off your turn, Hellish Rebuke from the book still fills `/combat cast hellish_rebuke ` to type a name. (#179)
- [ ] Cast a 1st-level spell, `/dm rest <you> short` → "Pact Magic spell slots restored", and
      the slot is back in the spellbook. (#52)

## Races

**Halfling**
- [ ] **Stout halfling**, in a fight with something that casts Poison Spray at you (a second character, or
      `/combat cast poison_spray <the halfling>` as a caster): your CON save prompt says **2d20, keep the
      higher**. Sacred Flame at you → a plain d20. (Stout Resilience)
- [ ] **Stout halfling:** `/dm adjust <you> hp -6 type poison` → 3 taken, "resists poison".
- [ ] **Stout halfling, out of a fight:** someone casts Poison Spray at you and you [Let it happen], [Ask for their
      roll] → the save prompt says "↑ advantage: against poison" and **[Roll with advantage]** / [I also have
      disadvantage]; the roll is 2d20, keep the higher. Sacred Flame at them → the plain three buttons. (#266)
- [ ] **An unrelated save doesn't set off a waiting one:** out of a fight, Poison Spray at the stout halfling,
      [Let it happen], but **don't** click [Ask for their roll]. Now `/dm check <them> save con dc 13` → their
      prompt is a plain d20 (no "against poison"), and failing it deals **no** Poison Spray damage. Then click
      [Ask for their roll] → advantage, and failing that one deals the damage. (#272)
- [ ] **Two at once:** Poison Spray then Sacred Flame at the same player, [Ask for their roll] for both. They
      answer the **first** prompt's buttons (scroll up) → the Poison Spray result, not Sacred Flame's; then the
      second prompt → Sacred Flame. Click an already-answered prompt's button again → "That save has already
      been settled." (#272)
- [ ] `/dm trap <the halfling> constitution dc 13 1d12 type poison` → the same advantage. Without `type poison`
      → none. (#266)
- [ ] **Gnome:** `/dm trap <them> wisdom dc 13 2d6 magic` → advantage ("against magic"); the same trap as a
      `dexterity` save, or without the word `magic` → none. In a fight, Burning Hands (a DEX save) at a gnome is a
      plain d20 now; Cause Fear (WIS) still has advantage. (#266)

**Darkvision** (a dwarf, elf or tiefling, and a human) (#148)
- [ ] Drink a real night-vision potion as the human → it stays (the game only removes its own).
- [ ] As the dwarf, walk into a dark cave (or stand outside at night): after ~3 s the world beyond ~2 chunks
      fogs out. Step into torchlight or daylight → full view distance comes straight back. A human in the same
      cave keeps full distance (they just see darkness). Tell me if 2 chunks feels too tight or the switch
      too jumpy; `sight.darkvision_view_limit: false` turns it off.

---

# Any character

## Characters and gear

- [ ] New weapons: `/dm give <you> battleaxe`, `maul`, `heavy_crossbow` and `blowgun` (+ `blowgun_needle x10`) all
      arrive with their stats. In a fight, a blowgun hit's damage is one number: 1 + your DEX (it used to fail to roll).
- [ ] A creation pick of "any simple melee weapon" no longer offers the dart; "any simple ranged weapon" does.
- [ ] With two characters, delete the active one → the one left is used straight away; "no active
      character" never shows. With three, delete the active one → right-click a sheet to pick.
- [ ] Put a torch into your Explorer's Pack, in survival **and** in creative → it comes straight back
      out: "Packs don't hold other items: it's back in your inventory." (#219)

## Combat

- [ ] Damage with several dice rolled by hand: `/combat damage <t> manualRoll 1+4` and `manualRoll 1 + 4` both count
      as 5; `total 12+7` is 19. `manualRoll 14 3` on a Blessed attack is still a 14, and a 3 on the d4.
- [ ] Start a fight by casting Fire Bolt at a creature out of combat ([Start combat]), `/combat rollforinitiative` →
      the bold gold "You started this fight with …. [Do it now]" block is the **last** thing in chat, below the
      initiative order, the round banner and "you get one action…". (#152)
- [ ] On that first turn, try something else (`/combat attack <t> dagger autoRoll`) → held: "You started this
      fight with …: that's your Action this turn. [Do it now] [Something else]" (a player sees [Ask the DM], and
      your [Allow] lets them go again). [Do it now] → the roll buttons, and the cast goes through. Bonus
      actions and `/combat endturn` are never held. (#152)
- [ ] **A second cast in one turn:** on your turn cast Fire Bolt, then cast it again → "You've already used your
      Action this turn: Fire Bolt needs it. [Cast it anyway]" and nothing is rolled. This holds for you as a DM
      too; only clicking [Cast it anyway] lets the one cast through. (#235)
- [ ] Mid-fight, on someone's turn, `/combat initiative <someone else> set 30` → they move to the
      top, and the **current turn stays with whoever had it** (the green →).
- [ ] **Scoreboard:** two tied initiatives in turn order.
- [ ] Run a fight for a few turns, `/combat finished` → `plugins/jkvttplugin/CombatSessions/` is **empty** (no
      `.yml`, no `.yml.bak`). Old `.bak` files from earlier fights are gone after the next server start. (#242)

## Help, Hide and Search in a fight (#176)

- [ ] Unused, the Help is gone once the helper's next turn starts.
- [ ] `/combat action hide` → the three buttons (Stealth). Rolled → you see your total; the table sees
      "tries to Hide"; the DM sees it against each enemy's passive Perception with [Hidden] / [Not hidden].
- [ ] [Hidden] → "is hidden", and a Hidden condition tag on the scoreboard; your attack has advantage, and
      after it "is no longer hidden: the attack gave them away". Attacks against you had disadvantage.
- [ ] A creature (DM) takes `/combat action search` → the DM sees the Perception total and, for a hidden
      player it beats, **[Reveal]**.
- [ ] Help / Hide / Search spend the Action only once rolled (waiting on your die costs nothing).

## Roll prompts (one wording everywhere, #216)

Every prompt is **[Roll it] [I rolled…] [My total…]**, all three fill chat. Hover [My total…] → "roll 2d20 and keep the lower and add -1[DEX] yourself" when at disadvantage ([My total…] only when the game adds
something). Every bonus is **named by its source**, and the prompt and the result show the same
label: the hover says "the game adds +3[INT] +2[Prof]", the result `🎲 d20 [14] +3[INT] +2[Prof] = 19`
(the game rolled), `🎲 you rolled 14 +3[INT] +2[Prof] = 19`, or `🎲 your total: 19`.

- [ ] A contest with a creature side → its button labelled `+5[Deception]` (listed skill) or `+1[CHA]`.
- [ ] In a fight: initiative → `+2[DEX]`; an attack (left-click) → the weapon's breakdown; a save
      and a concentration save → `+1[CON] +2[Prof]` (proficient) or just `+1[CON]`.
- [ ] A creature's attack in a fight → to hit `+4[Scimitar]`, damage `+2[Scimitar]` (not `[ToHit]`).
- [ ] Any game-rolled dice (damage, healing, a potion) → **one** `= total`, never `[5] = 5 +3 = 8`.
- [ ] `/combat damage <t> autoRoll` → one line, `🎲 1d8 [6] +3[STR] = 9`.
- [ ] Type a roll command with no roll words (physical-dice mode): `/combat attack <t> <weapon>`,
      `/combat save`, `/combat concentration`, `/character loot investigation` → the three buttons
      on that same command, not "type 'manualRoll <n>'".
- [ ] Opportunity attack buttons → pick the attack, Enter, then the three roll buttons.
- [ ] Nowhere shows two dice icons (`🎲 … 🎲`), e.g. a shared check result or a loot roll.
- [ ] Every roll button, **[Roll it]** included, only fills chat; nothing rolls until you press Enter.
- [ ] Any d20 roll with `manualRoll 25` → asked again, not accepted.

## Checks: private rolls, groups, passive (#186)

(Private rolls and group checks with other players are in Part 2.)

- [ ] `/dm check nearby passive perception dc 14` → every character **and creature** within 60 ft of you,
      each ✔/✘ with its score; one further away isn't listed. `all` is still the party only. (#186)
- [ ] Poisoned (`/dm adjust`), click a skill on the sheet → "↯ disadvantage: Poisoned" and two buttons,
      **[Roll with disadvantage]** and **[I also have advantage]**. The first rolls 2d20 keep lower; the second
      is one d20 (they cancel). Not poisoned → the usual [Normal] [Advantage] [Disadvantage]. (#175)
- [ ] A raging barbarian's Strength check → **[Roll with advantage]** and **[I also have disadvantage]**. (#223)


## Death

- [ ] Down yourself, fail three death saves → "has DIED", turn skipped.
- [ ] `/combat finished`, new fight → still `[DEAD]`, still skipped.
- [ ] Dead: `/dm adjust <you> hp +10` refuses.
- [ ] Dead: the sheet's HP slot shows a skull "DEAD".
- [ ] `/dm revive <you>` → 1 HP, turns return.
- [ ] `/dm adjust <c> hp -<current + max HP>` → dies outright (massive damage).
- [ ] Fail one save, `/combat finished`, new fight → still 1 failure.
- [ ] `/combat deathsave` (with or without your name) → the three buttons, not a roll.
      `manualRoll 0` → "No die shows less than 1", nothing rolled. `autoRoll` → the game rolls.
- [ ] `/dm entity revive <creature>` mid-fight → its turns come back.
- [ ] A dead character leaves a tipped-over head "☠ <name>" where they fell.
- [ ] As DM, right-click the body → [Revive] and [Remove body].
- [ ] You can't punch the body or take the head.
- [ ] Walk away until the chunk unloads, `/dm revive <c>`, walk back → the body is gone.
- [ ] A stable character at 0 HP (the long rest refused them): `/dm adjust <c> hp +1` → now the long rest works.
- [ ] A DM already in spectator mode for their own reasons isn't pulled out of it by deaths.

## Sounds and music (#16)

Test your own sounds with your local pack (F3+T reloads it), no upload needed: `docs/resource-pack.md`.
The sounds are all in `DMContent/Sounds.yml`; the spell looks (rows under Wizard and Cleric) in
`DMContent/DamageTypes.yml`. Copy both into the server's DMContent. Tell me which vanilla picks sound
wrong, and swap in your own anytime.

- [ ] In a fight: a hit and a miss sound different; your turn starting → a bell (for you only); dropping to 0 →
      a heartbeat; dying → a low bell.
- [ ] **Combat music:** roll initiative → the boss music starts (Jukebox/Note Blocks slider); walk around, it
      doesn't fade; Minecraft's own music stays quiet; `/combat finished` → it stops. It loops after ~3 minutes.
- [ ] `/combat music` → what's playing; `/combat music off` → silence; `/combat music default` → back.
      A creature with `combat_music: boss` in its YAML (and a different default) → that fight uses its track.
- [ ] Restart mid-fight → the music comes back when you rejoin.
- [ ] **Sound Board** (note block, DM-mode hotbar slot 3): click **Wolf howl** → you hear it; shift-click →
      "from here (carries about 80 blocks)". **Stop sounds** cuts it. In a fight, its music row switches tracks.
- [ ] `/dm sound thunder`, `/dm sound minecraft:block.anvil.land`, `/dm sound nonsense` (→ refused), `/dm sound stop`.
- [ ] Turn the Players slider down → dice/hit sounds go quiet but music doesn't; Jukebox slider → the reverse.
- [ ] `sounds.enabled: false` in config.yml, restart → nothing plays.
- [ ] A fire hit on **you** (a creature's fire attack) → you flicker with flames for 2 seconds but lose no hearts. (#230)
- [ ] Put a typo in DamageTypes.yml (`particle: FLAMEE`) and `/dm reload` → the console names it. (#230)

---

# DM tools

## The Adjust menu & `/dm adjust`

- [ ] In a fight, an HP change updates the scoreboard and the player's open sheet.
- [ ] **Temp HP…** → closes the menu, gives a fill-in line.
- [ ] **Creature temp HP:** `/dm adjust Balin Ironforge temp 5` → "gains 5 temporary HP"; the
      Adjust header and the view card show `+5 temp`.
- [ ] Then `/dm adjust Balin Ironforge hp -3` → "Temp HP absorbed 3", his real HP unchanged.
- [ ] `temp 2` while he has 5 → stays 5 (temp HP don't stack; the higher one wins).
- [ ] In a fight, a creature's temp HP shows as `+N` on the scoreboard.
- [ ] **Set HP exactly** to a lower number while someone has temp HP → they land on exactly that
      number, temp HP gone (it used to soak part and leave them higher).
- [ ] **Drop to 0 HP** on a creature with temp HP → it dies.
- [ ] Player AC tile, shift-click → says their AC comes from armor.
- [ ] `/combat damage override` is gone.
- [ ] A trap's **[Apply damage]** fills `/dm adjust … hp -…`.
- [ ] A spawn's **[Use my own roll]** fills `/dm adjust … maxhp`.

## Surprise tool & damage approval

- [ ] The DM's own `/combat damage` lands at once, never waits.
- [ ] `combat.damage_approval: off` in config → nothing ever asks you.

## Viewing & DM notes

- [ ] View tool on your own character → race and class, concentration.
- [ ] A DM AC adjustment plus Shield → the card's AC explains both.
- [ ] A creature with its own AC → "own AC, stat block says 12".
- [ ] A downed character → "Down, dying — death saves: 1 ✔ / 2 ✖".
- [ ] Full view of a creature with YAML `dm_notes` and an added note → **one** DM notes tile: the
      YAML notes, then "Added in play:".

## Conditions outlast the fight

Add conditions with `/dm adjust <who> condition <name>` or the Adjust menu.

- [ ] Someone Dodging, `/combat finished` → Dodging ends.
- [ ] Restrained (kept from a fight): a DEX save from the sheet is at disadvantage.
- [ ] New fight with the same goblin → the scoreboard still shows Prone.
- [ ] At the start of a turn, the condition hover is wrapped.

## Entities & shops

**Multiattack** (`/dm entity spawn gnoll_fang_of_yeenoghu`, and `gnoll_pack_lord`; a fight with both and a target):
- [ ] The Fang's stat block (Attacks tile) starts "Multiattack: Bite, Claw ×2"; at the start of its turn you get
      "⚔ … · Multiattack: Bite, Claw ×2." (#253)
- [ ] Possess it and attack with **Claw** → after the roll, "Multiattack: left: Bite, Claw." Apply the damage,
      Claw again → "left: Bite." Bite → "Multiattack: done." A fourth attack → "has already used their Action". (#253)
- [ ] After the first Claw hits, attack again **before** applying its damage → "Apply the damage for …'s last hit
      first". (#253)
- [ ] After both Claws, a third Claw → "Multiattack has no Claw left. Left: Bite." (#253)
- [ ] The Pack Lord: Glaive then Longbow → both go through ("1 more: Glaive or Longbow", then "done"). On another
      turn, **Bite** first → it's the whole Action: a Glaive after it is refused. (#253)
- [ ] A plain gnoll still gets exactly one attack. End a Multiattack turn early → the next turn starts fresh. (#253)

**Limited abilities and ammunition** (`/dm entity spawn giant_spider`, and `gnoll`; a fight with both):
- [ ] The spider's stat block lists "Web (Recharge 5-6)"; the gnoll's Longbow says "Carries: 2d10 arrows" and
      Spear (Thrown) "Carries: 2d4 spears". (#256, #257)
- [ ] Attack with **Web** → after the roll, "Web: used (Recharge 5-6)." Web again this turn → "Web is spent: it
      recharges on a 5-6 at the start of its turn. **[Use it anyway]**", nothing rolled. Bite still works. (#256)
- [ ] At the start of each spider turn you (only you) get "🎲 Giant Spider · Web (Recharge 5-6), roll its
      recharge: **[Roll it] [I rolled…]**". [I rolled…] + 3 → "rolled 3, still spent (your roll)"; a 5 or 6 →
      "recharged" in green, Web works, and the prompts stop. `manualRoll 7` → "A d6 shows 1 to 6". (#256)
- [ ] Answer it, then run the same command again that turn → "isn't waiting on a recharge roll". Ignore it and
      try Web → refused with "roll its recharge first", and the buttons again. (#256)
- [ ] **Spawning a gnoll** shows its dice, like its HP: "🎲 2d10 [6, 5] = 11 arrows [Use my own roll]" and one for
      spears. Click it → fills `/dm adjust Gnoll ammo arrow `; type 14 → "Gnoll now carries 14 arrows", and the
      next shot says 13 left. Tab after `ammo` lists `arrow` and `spear`. (#257)
- [ ] [Use it anyway] → "Allowed this once. [go again]" → the attack goes through, and the next one is refused again. (#256)
- [ ] `/combat finished`, start a new fight with the same spider → Web is ready at once. (#256)
- [ ] The gnoll's **Longbow**: after each shot, "Gnoll: N arrows left." (N starts between 2 and 20, less one). Its
      thrown spear: "N spears left"; a stab with the spear doesn't lower it. (#257)
- [ ] Throw until "0 spears left" → both the thrown and the stabbing spear attacks say "Gnoll is out of spears."
      Bite still works. (#257)
- [ ] Restart the server mid-fight → the spent Web and the arrow count are as you left them. (#256, #257)
- [ ] Kill the gnoll and search the body (a second player, or `/dm` loot) → the arrows it didn't fire are in the
      loot. A gnoll killed before it ever shot still has some. (#257)
- [ ] `/dm rest "Giant Spider" short` → "took a short rest. Back: Web." `/dm rest creatures long` → every creature at
      full HP, and Tab offers `creatures` and creature names. (#256)
- [ ] In a creature's YAML add `uses: 2` to an attack, `/dm reload`, spawn a new one → refused on the third use:
      "it comes back in about 24 hours (24 hours after it was first used), or after a long rest". `/dm time add
      12h` → still refused, "about 12 hours"; another `12h` → it works. (#256)
- [ ] Set `creatures.per_day: dawn` in the server's `config.yml`, restart, repeat → the refusal says "at dawn",
      and moving the clock just past the next dawn brings it back. Set it back to `24_hours`. (#256)
- [ ] `ammunition: 3` on a bow attack → no dice shown at spawn, exactly 3 shots; `ammunition: unlimited` → no
      count at all. (#257)

**Stat-block saves and immunities** (`/dm entity spawn skeleton`, and `gnoll_fang_of_yeenoghu`):
- [ ] `/dm entity info Skeleton` opens the **same menu as** `/dm view Skeleton full` (stat tiles, DM notes, Adjust,
      what it carries). The Basic Stats tile starts "Medium undead". The old "…'s Stat Block" menu is gone.
- [ ] The skeleton's DM notes tile shows its CR and languages; possess it → it holds a shortsword / shortbow, and
      spawning it rolls its arrows (2d10). Kill it → both weapons and the unfired arrows are on the body.
- [ ] Possess a skeleton on its turn, left-click a target → **one** "Attack … as Skeleton" line, not two.
- [ ] The skeleton's stat block (`/dm entity info Skeleton`), Ability Scores tile: "Immune: poison", "Vulnerable:
      bludgeoning", "Can't be: poisoned". The Fang's shows "CON: 15 (+2)  save +4". (#252)
- [ ] `/dm adjust Skeleton hp -6 type bludgeoning` → 12 taken, "VULNERABLE to bludgeoning (doubled)".
      `… hp -6 type poison` → none, "IMMUNE to poison". A sword's slashing → normal. (#252)
- [ ] Make the skeleton Poisoned (`/dm adjust`) → "Skeleton is immune to being Poisoned", and it isn't. (#252)
- [ ] `/dm check "Gnoll Fang of Yeenoghu" save con dc 12` → the buttons add `+4[CON save]`; a STR save adds
      `+3[STR]`. A save spell on it uses the same +4. (#252)

**Mannequin trial** (look-only, nothing to click; `/dm entity mannequin clear` tidies up). For the pack
skin row, load the local `ResourcePack\jkvttresourcepack` (it has a placeholder `entity/npc/balin.png`):

- [ ] Looking at a creature, right-click your spellcasting focus → the spellbook opens.
- [ ] Dungeoneer's Pack in your inventory: click another item onto it, or it onto an item →
      nothing goes in, "Packs don't hold other items".
- [ ] Something already inside a pack from before → you can still take it out.
- [ ] `/dm check <you> insight vs Balin deception` → your roll prompt, plus the roll buttons labelled `+1[CHA]` (now **[Roll it] [I rolled…] [My total…]**).
- [ ] Same, answered → the winner with [Share].
- [ ] Same, with `autoRoll` inline.
- [ ] A guard's Perception in a contest → `+2 Perception`.

## Annotate tool: the dialog pilot

The first dialog in the plugin. Right-click = the new form; sneak + right-click = the old chat
buttons. Try both; whichever you like less gets removed.

- [ ] Fill it in, **look away**, then Save → it applies to that chest (not what you're looking at).
- [ ] A long description with line breaks → saved, and players see it.
- [ ] Opens + a key → it saves as Locked (a key means a lock).
- [ ] The buttons read short ("Opening: Locked", "Key: Brass Key", "Trap save: Dexterity"); clicking
      one cycles it. What Opening and Key mean is in the text at the top.

## Command-block traps

(`enable-command-block=true` in `server.properties`; you place and program the block as an op in creative.)

- [ ] A pressure plate wired to a command block holding `dm adjust @p hp -6 type fire` → step on it as a character:
      6 fire damage to **you**, through the usual resistances (a tiefling takes 3).
- [ ] A block holding `dm check @p save dexterity dc 13` → whoever steps on the plate gets the save prompt; the
      result comes to you (every DM online) with ✔/✘, not to the block.
- [ ] `dm adjust @p[distance=..3] hp -4` with nobody within 3 blocks → nothing happens (the block's output says
      "No player matches"). `dm adjust @a hp -1` with two players on → refused, "takes one".
- [ ] `/dm trap <a character> dexterity dc 13 2d10 type fire half name Flame jet` → you see "… sets off Flame jet: DC 13
      DEX save, 2d10 fire on a fail, half on a success"; they see "You set off Flame jet!" (no DC) and the roll
      buttons. They fail → "fails the save …: N fire damage (2d10: [4, 7] = 11)" and the HP drops, resistances
      applied. They save → half. Without `half`, a save takes nothing. (#202)
- [ ] The same line in a command block with `@p`, under a pressure plate → stepping on it does all of that.
      `… dc 13 8 type piercing` (a number, not dice) deals exactly 8 on a fail. (#202)
- [ ] **A creature's old save buttons:** `/dm trap Wolf constitution dc 12 3d6 type poison`, answer the save. Cast
      Resistance on the wolf (or give it any one-use bonus), then click the **same** roll buttons again → "That
      save has already been settled.", nothing is rolled, and the wolf still has its Resistance. (#272)
- [ ] `/dm trap Wolf constitution dc 12 3d6 type poison` → you get the creature's save buttons; answer them and the
      damage lands on the wolf. (#202)
- [ ] A chest annotated as trapped with a DC and damage: when a player pokes it, your **[Trigger]** fills
      `/dm trap <them> <save> dc <n> <dice>`. (#202)
- [ ] As the DM, typed in chat: `/dm view @p` → your own character's quick view.

## Other DM commands

- [ ] Break a spell file in the server's `DMContent` (indent one line wrong), `/dm reload` → "✗ Reload refused", naming
      the file and line; your spells are all still there. Fix it, reload → clean. (#243)
- [ ] Give a spell an unknown `material:` and reload → it reloads, with that warning listed in chat. (#243)
- [ ] A creature check with no roll → the DM gets the three roll buttons.
- [ ] `/dm check clear <your character>` → clears your held checks.
- [ ] `/dm resource restore <character> all` works.
- [ ] `/dm resource consume <character> <res> 1` works.
- [ ] Look at a chest, `/dm object key brass_key` → "The Brass Key opens the Chest".
- [ ] `/dm object key` on a sealed block refuses.

---

# Restart checks (do these together, one restart)

Set these up, `/stop`, start the server, then check:

- [ ] Before stopping: open `plugins/jkvttplugin/Saved/Characters`, pick a character file, and copy it somewhere safe. After
      the server has saved it once more (take a hit), that folder also has `<id>.yml.bak`: the version before. (#242)
- [ ] With the server stopped, break that `<id>.yml` (delete half of it). Start → the console says it "won't load",
      loaded the previous version instead, and kept the broken one as `<id>.yml.broken-…`; the character is there
      and every other character loads. (#242)
- [ ] Same, but also delete the `.bak` first → that one character is skipped, everyone else loads, the plugin
      starts normally, and the broken file is left untouched. Put your safe copy back afterwards. (#242)
- [ ] A character Poisoned before → still Poisoned after.
- [ ] A creature Prone before → start a fight after, its scoreboard tag is still there.
- [ ] High elf rogue still casts Fire Bolt with INT; astral elf still uses Wisdom.
- [ ] A dead character is still dead; their body is still there.
- [ ] `/dm adjust <c> temp 7` before → still 7 temp HP after.
- [ ] `/dm adjust Balin Ironforge temp 4` before → Balin still has 4 temp HP after.
- [ ] **A creature whose id is gone:** spawn `wolf`, stop the server, rename `id: wolf` in its YAML,
      start → the console warns once, naming `wolf` and the stand's coordinates; the wolf isn't restored.
      (Put the id back afterwards → it restores on the next start.)
- [ ] Half-orc dropped to 0 before (held at 1 by Relentless) → drop them again after, they fall.
- [ ] A note on a **character** (`/dm note <character> add …`) is still there after.
- [ ] **A fight survives** (set up: round 2, a condition on someone, a player at 0 HP; check
      `plugins/jkvttplugin/CombatSessions/` has a file): console says "Restored combat … round 2, X's turn".
- [ ] The scoreboard is back, the downed player is prone, the condition is listed.
- [ ] The current combatant can attack → damage with no errors.
- [ ] A raging barbarian is still raging (sheet, halved slashing, red tint).
- [ ] `/combat finished` → the combat file is gone.
- [ ] A wizard's prepared spells (changed before) → the same after (#218).
- [ ] A Hit Die spent before (no long rest since) → still 0/1 (#52).
- [ ] Bless on you before → still there with its rounds left; breaking your concentration still ends it (#225).

---

# Part 2 — Needs a second player (not a DM)

## Permissions

- [ ] `/roll 2d6+3` works. (#57)
- [ ] `/character create Bob` is refused.
- [ ] `/character list all` lists **their own** characters; Tab offers `all` but no player names.
- [ ] `/dm view` is refused.
- [ ] `/dm list` with every DM offline → offline ops as `[OP] name (Offline)`.

## Their character

- [ ] `/character view <your character>` → "You can only view your own characters".
- [ ] `/character view ` + Tab → only theirs.
- [ ] Right-clicking your sheet paper → "This isn't your character sheet".
- [ ] A DM note on their character → they never see it (sheet, card, anywhere).
- [ ] They `/character delete <theirs>` → "Asked the DM"; you get [Approve] / [Deny].
- [ ] Deny → kept.
- [ ] Approve → gone; the file is in `plugins/jkvttplugin/Saved/Characters/Deleted/`.
- [ ] With you offline → delete refused.
- [ ] `/character give <them> <your character>` → **[Give …]**; click → it's theirs, your paper is gone, the gear stayed with you.
- [ ] `/character give` while either of you is in a fight → refused.
- [ ] `/character give <them> <their character>` → they receive the sheet paper.

## In a fight, as a player

- [ ] `/combat add <them>` mid-fight → the game's roll shows, and they also get the roll buttons; rolling
      replaces the game's number, the tracker re-sorts, and the table sees "rolled their own initiative".
- [ ] Same, after their first turn → their `/combat initiative` refuses (the order is settled).
- [ ] **Out of reach:** they swing a sword at someone 20 ft away → **[Ask the DM]**. You get **[Allow]** /
      **[Deny]**; Allow → they get **[go again]**, and the attack goes through.
- [ ] Same with a spell (Cure Wounds from 20 ft) → [Ask the DM], then it goes through once.

## Their rogue: Sneak Attack without advantage (#229)

- [ ] The hit gives "🗡 Sneak Attack (+1d6)? No advantage, so it's the DM's call: the game sees <ally> within
      5 ft of them. [Ask the DM]" (or "no ally next to them"). Click → you get [Allow] / [Deny].
- [ ] [Allow] before the damage is rolled → "The DM allows Sneak Attack: +1d6 in that damage" and a new damage
      prompt for 1d8+1d6. On a crit it's +2d6.
- [ ] [Allow] after the damage was already applied → the 1d6 is rolled and dealt on its own, shown to the table.
- [ ] [Deny] → they're told.

## Out of a fight, as a player

- [ ] You cast Sacred Flame at **their** character out of a fight → you get **[Ask for their roll]**; click it → they
      get the roll buttons, and their result decides the damage with no further click from you. (#245)
- [ ] They `/character cast cure_wounds The Kindler` from 18 ft → refused, with **[Ask the DM]**. Click → you get
      "📏 … out of reach (about 18 ft away …)" **[Allow]** / **[Deny]**.
- [ ] [Allow] → they get **[cast it]**; it goes through once. Casting again from there refuses again.
- [ ] [Deny] → "The DM says it doesn't reach".
- [ ] Out of a fight, they left-click The Kindler with a **shortbow**, then a **light crossbow** → you get
      the same [Start combat] / [Deny] question a sword gets. Before, nothing happened. (#152)

## Private rolls and group checks (#186)

- [ ] They roll a skill from their own sheet → only they see it, with **[Show the DM]**. Click → you get it with
      [Share with players]; you (and anyone else) see nothing until then.
- [ ] `/dm check all skill stealth dc 12` → every online character gets the prompt; you see each result as it
      comes ("<name> — Stealth: … ✔ (1/2 in)"), then "Group Stealth: … The group SUCCEEDS/FAILS."
- [ ] Same with `<you>, <them>` instead of `all`; and **[Close now]** before they roll → the verdict from whoever
      did, plus "Didn't roll: …".

## Your bard inspires them (#40)

You play a bard; they're the one inspired.

- [ ] `/combat use bardic_inspiration <them>` (your bonus action) → "gives … Bardic Inspiration: +1d6 to …", one use
      spent. They're told they have it; their roll prompts don't show it.
- [ ] They **miss** an attack by 3 → "Add your Bardic Inspiration (1d6) to that attack roll (12)? [Roll it] [I rolled…]
      [Don't use it]". A 4 → "adds Bardic Inspiration … → 16" then HIT and the damage prompt; a 2 → "Still a miss".
      A hit or a natural 1 doesn't ask. `manualRoll 7` → "7 isn't a 1d6 roll" and the buttons again.
- [ ] They **fail a concentration save** → the spell stays up until they answer; enough → "keeps it going after all";
      [Don't use it] → it breaks. Same for a **death save** of 7: +3 or more → success, else the failure is marked.
- [ ] A failed spell save or a skill check → asked; using it shows the new total and "The DM decides…". A skill check
      rolled privately from their sheet shows the answer only to them.
- [ ] Out of a fight, `/character use bardic_inspiration <them>` works too, and they're asked after their next check.

## Who hears the sounds (#16)

- [ ] Out of a fight, they roll from their sheet → **only they** hear the dice; you don't.
- [ ] In a fight, they attack → you both hear the dice and the hit/miss, from where it happened.
- [ ] Their turn starts → only they hear the bell.
- [ ] Combat music: they're in the fight → they hear it; `/combat remove <them>` → it stops for them; add them
      back → it starts again. They log out and back in mid-fight → it resumes.
- [ ] Sound Board: click → they hear it wherever they are; shift-click from 60 blocks away → they hear it from
      your direction; from 200 blocks away → they don't. `/dm sound wolf_howl <them>` → only they hear it.

## Damage approval (they attack, you approve)

- [ ] They hit and `/combat damage … autoRoll` → lands at once, no DM prompt.
- [ ] They hit someone who knows Shield, the reaction window closes, they `/combat damage` → "Sent to
      the DM"; you get **[Apply]** / **[Deny]**.
- [ ] [Apply] → it lands, the tracker updates.
- [ ] [Deny] → they roll again for the same hit.
- [ ] While waiting, a second `/combat damage` → "it's with the DM".
- [ ] They end the turn while it waits → [Apply] later says the moment has passed; next turn isn't blocked.
- [ ] `/combat damage <t> manualRoll 7` off their turn → refused, with **[Ask the DM]**.
- [ ] Click [Ask the DM] → you get "X asks to deal 7 damage to Goblin (off their turn)".
- [ ] [Apply] on that → lands as typed. [Deny] → they're told.
- [ ] `combat.damage_approval: always` → every hit of theirs asks you.

## Locks, keys and thieves' tools (a DM walks through locks, so use their character)

- [ ] `/dm object lock` a chest; they click [Open it] → it stays shut and you get a ping.
- [ ] A rogue with thieves' tools → the ping has **[Thieves' tools]** and "proficient (expertise), carrying them".
- [ ] Give thieves' tools a second, homebrew sibling: copy the item in `adventuring_gear.yml` as `skeleton_keys`
      (tags `[tool, lockpick]`), `/dm reload`, carry only those → the ping's button is **[Skeleton Keys]**, and a
      failed graded check with them breaks a set. (#261)
- [ ] Creation, subclass step: cleric domains are enchanted books, warlock patrons books, sorcerer origins blaze
      powder (unchanged, but now from `subclass_material:` in the class YAML). Put `material: NETHER_STAR` on one
      domain, `/dm reload` → that tile alone changes. (#261)
- [ ] Click [Thieves' tools], add a DC → `+4[Thieves' Tools ×2]` in the breakdown.
- [ ] A character without the proficiency → just `+DEX`.
- [ ] `/dm object key brass_key` on the chest; without the key → locked, the ping says "they aren't carrying it".
- [ ] `/dm give <them> brass_key`, [Open it] → opens, "X unlocks the chest with the Brass Key".
- [ ] They keep the key, and the chest stays open for everyone.
- [ ] `key iron_key single-use` → the key is used up.
- [ ] A trapped, keyed chest → the trap still springs first.

## Study checks (#231)

Set up an enchanting table: a description, **Rolled**, History or Arcana, three tiers with text (DC 10/15/20).

- [ ] They right-click it → the description, "📖 Study the Enchanting Table, with one of:" and a roll line per
      skill. The enchanting screen doesn't open, and you get no ping. (#231)
- [ ] [Roll it] on History → "📖 History: 17 (…)" and a ✦ line per tier cleared. The server console shows
      `Study: <character> (<player>) · Enchanting Table … History 17 (game rolled) … → tier 2/3`. (#231)
- [ ] Click it again → "You've studied this already (History 17)." and the same lines, no roll. (#231)
- [ ] Another of their characters → their own roll. (#231)
- [ ] [I rolled…] → the console says `typed d20`; [My total…] → `typed total`. (#231)
- [ ] Below DC 10 → "You learn nothing more than what you see." (#231)
- [ ] With the study prompt up, roll a different skill from the sheet → a normal private roll; the study
      still waits for its own. (#231)
- [ ] Switch it to **Passive** and "Forget who has studied it" → they click: no buttons, the lines their passive
      clears just appear. The console logs it once, not on every click. (#231)
- [ ] Back to Rolled, **Send the result to me first** on, forgotten → they roll and see "The DM will tell you
      what you learn."; you get the result (hover it for the lines) with [Tell them] / [Just the basics].
      [Tell them] → they get the lines, and clicking again later repeats them. (#231)
- [ ] Same, but ignore the ping and restart the server; they click again → you're asked again. (#231)
- [ ] Add an armed trap to it → their click springs the trap instead. (#231)

## Death, from the player's side

- [ ] They die (three failed saves) → spectator mode with a message.
- [ ] `/dm revive <them>` → back in adventure mode, body gone.
- [ ] You get **[Teleport them to the body]** (no automatic teleport); click → they land at it.
- [ ] Dead, they run `/character create` → adventure mode, the creation menu works.
- [ ] They right-click someone else's body → "The body of …" + [Ask for a check].
- [ ] You get a [call a check] ping (medicine / investigation / religion suggested).

---

# Playtest notes
Write anything here, in any order. Each round Claude answers in chat, turns the notes into rows
above, and clears this section (git keeps the old notes).

---

# Known deferred

- **Upcasting can't be tested yet:** level-1 characters have no 2nd-level slots. Two rows wait for level-up (#153):
  Magic Missile from the 2nd-level page filling `… level 2` and spending that slot (#179), and upcast Cure Wounds
  keeping `level 2` on its buttons (#216).
- Racial spell **uses** (a level-3 tiefling's Hellish Rebuke) aren't saved, so a restart refills
  them. Harmless at level 1; matters once level-up (#153) lands.
- Character-sheet inventory redesign (waiting until more content lands).
- Nicer default `material:` values for spellcasting foci and packs.
