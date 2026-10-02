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
- [ ] **Time tool** (it now uses the renamed day-cycle game rule): Stop the Clock → the sun stops;
      Start → it moves; `/dm time` says running or stopped correctly.
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

- [ ] A fighter's sheet has **no Spellbook tile**; a tiefling fighter's does (Thaumaturgy). (#247)
- [ ] Features & Traits: click **Second Wind** out of a fight → fills `/character use second_wind`. Passive tiles
      (Sneak Attack, Archery) aren't clickable. ← goes back to the sheet. (#65)
- [ ] Creation: the Fighting Style tiles are just the name ("Archery", "Great Weapon Fighting"); hover →
      what it does and the ✦ effect line, wrapped. Protection reads the same way. (#229)
- [ ] **Weapon switch:** start your turn holding a longsword, scroll to a handaxe and back a few times →
      nothing in chat, just a grey action-bar line. Attacking with the longsword rolls as normal. (#190)
- [ ] Scroll to the handaxe and left-click a kobold → no roll buttons, but "⚠ You started your turn holding
      Longsword. Switching to Handaxe uses your free object interaction. [Yes, I'm switching] [Ask the DM]".
      [Yes] → the roll buttons; another handaxe attack this turn doesn't ask. (#190)
- [ ] Then try a third weapon → "another switch takes your Action…" with only [Ask the DM]; your [Allow] →
      roll buttons, your [Deny] → "attack with Handaxe this turn". (#190)
- [ ] Holding the longsword, type `/combat attack <kobold> handaxe autoRoll` → the same question, nothing
      rolled. Next turn starts fresh. (#190)
- [ ] Longsword + a dagger in the off hand at the start of the turn: the dagger's bonus attack doesn't ask. (#190)
- [ ] **Defense** fighter: AC goes up by 1 when you put on chain mail (and not unarmored). (#229)
- [ ] **Dueling** fighter, longsword + shield: damage breakdown has `+2[Dueling]`. A second weapon in the
      off hand → no Dueling. (#229)
- [ ] **Great Weapon Fighting**, greatsword, damage with `autoRoll` → "(Great Weapon Fighting: any 1 or 2
      was rolled again.)" A plain hit on a longsword with a shield doesn't say it. (#229)
- [ ] **Two-Weapon Fighting**, dagger in each hand: the off-hand attack's damage has `+N[DEX]`. (#229)
- [ ] BG3 order (default `combat.bonus_attack_timing: any_time`): a fighter with a dagger in each hand
      uses the off-hand button first (`… dagger bonus`), then still has their Action. Its damage has no
      DEX. Set `after_attack_action` and restart: the same button now says to attack first. (#221)
- [ ] With nothing to spend it on, the action bar shows **Bonus: —**. (#221)
- [ ] **Second Wind:** `/combat use second_wind` → the three roll buttons; answer → heals 1d10 + 1, the
      bonus action is spent, a second use says no uses left. After the fight, `/character use second_wind`
      once it's back (a short rest). (#229)
- [ ] Take some damage, `/dm rest <you> short` → the summary lists Hit Dice, then
      **💚 Spend a Hit Die (1 of 1 left, HP 5/12): [Roll it] [I rolled…] [My total…]**. (#52)
- [ ] Answer it → heals the die + CON, HP goes up on the sheet, "Hit Dice left: 0 of 1". (#52)
- [ ] `/character hitdice autoRoll` again → "No Hit Dice left. A long rest brings back half of them." (#52)
- [ ] `/character hitdice` with no rest → "spent at the end of a short rest". (#52)
- [ ] After a short rest, join a fight, then finish it → `/character hitdice` refuses (the fight ended the rest). (#52)
- [ ] `/dm rest <you> long` → Hit Dice back to 1/1; Second Wind back too (it used to stay spent overnight). (#52)

## Rogue

Make a **High Elf Rogue** (Fire Bolt as the Wizard Cantrip) and a **Tiefling Rogue**.

- [ ] **High Elf Rogue:** `/character cast fire_bolt` works and uses INT.
- [ ] **Tiefling Rogue:** `/character cast ` + Tab → your cantrips, spells and racial spells (Thaumaturgy).
- [ ] **Tiefling Rogue:** right-click thieves' tools with no chest in view → nothing happens, no
      "cannot use this type of focus" message.
- [ ] **Tiefling Rogue:** right-click a chest holding thieves' tools → the chest's own [Open it] /
      [Ask for a check] prompt, not a focus message.
- [ ] **Sneak Attack with advantage** (the goblin is prone, or restrained): a rapier hit says "🗡 Sneak
      Attack: +1d6 in that damage (advantage)" and the damage prompt rolls 1d8+1d6. No DM needed. (#229)
- [ ] **Sneak Attack without advantage** (you're the DM): the hit offers **[Add it]** (a player's rogue
      gets [Ask the DM]: Part 2). Click → +1d6 in the damage. (#229)
- [ ] Once the Sneak Attack damage lands, a second hit that turn offers nothing. A club (not finesse) → nothing. (#229)
- [ ] **Shield:** a Sneak Attack hit that Shield turns into a miss → the next hit that turn can still Sneak Attack. (#229)
- [ ] **Thieves' tools break on a fail:** carry 2 sets, `/dm check <you> tool thieves_tools dc 25`, fail → one set gone, you're told.
- [ ] DC 5, pass → nothing breaks.
- [ ] No DC → never breaks.
- [ ] `on_fail: always` / `never` in config.yml behave as named.

## Barbarian

- [ ] **Rage:** a ring of red dust circles you (and others see it on you); it stops when the rage ends.
      The Strength icon is only in the inventory: that was never the tint. (#247)

## Monk

- [ ] No armor: the sheet's AC tile says **Unarmored Defense: 10 + DEX + WIS**. Put on leather armor
      (chestplate slot) → the AC drops to leather's; take it off → back. (#220)
- [ ] Unarmed strike (`/combat attack <target> unarmed`) rolls **1d4 + DEX**, labelled [DEX]. (#220)
- [ ] **The game picks the cost.** Attack unarmed twice with the same `/combat attack <target> unarmed`
      (or left-click twice): the first uses the Action, the second says **"That used your bonus action
      (Martial Arts: bonus unarmed strike)"**. A third is refused with the reason. (#221)
- [ ] The action bar, before attacking: **Bonus: READY: Martial Arts…**. (#221)
- [ ] `/combat attack` with nothing after it lists attacks under **With your Action** and **With your
      bonus action**. (#221)

## Paladin

- [ ] **Lay on Hands** on someone 24 ft away → "You need to touch them…" with **[Do it anyway]** (you're the DM).
      Click it → it fills the command again, Enter → it heals. Next time it asks again. (#247)

## Wizard

- [ ] Start aiming Burning Hands, then `/combat nextturn` (or end your turn) → "Your turn is over. Your Burning
      Hands aim is cancelled: nothing spent." Your 1st-level slot is still there. (#237)
- [ ] Aim Burning Hands, then (without firing) cast another 1st-level spell with your last slot, then right-click
      → "…doesn't go off: nothing spent." (#237)
- [ ] In a fight, cast Fire Bolt and resolve it, then cast it again the same turn → "You've already used your
      Action this turn". Nothing is rolled or spent. (#235)
- [ ] `/roll 1d6+2147483647d6` and `/roll 99999999999d6` → refused as invalid, and the server doesn't stall. (#234)

**Creation and the spellbook**

- [ ] Creation, spell step: the label says "This is your spellbook. Each day you prepare some of it".
- [ ] **Nothing prepared:** open the spellbook → "📖 You have no spells prepared…" [Prepare spells];
      click a 1st-level spell → "isn't prepared".
- [ ] An unprepared spell reads "In your spellbook, not prepared" and clicking it does
      nothing; an unprepared **ritual** (e.g. Detect Magic, Find Familiar) says "Ritual: click to cast it as one". (#218)
- [ ] `/character cast <unprepared non-ritual>` → "…in your spellbook but not prepared…". An unprepared
      ritual without `ritual` → the same, plus **[cast it as a ritual]**; with `ritual` → "casts … as a ritual
      (10 extra minutes, no spell slot)" and no slot is spent. (#218)
- [ ] **The window:** `/dm rest <you> long` → the summary says "During this rest you can: 📖 Change your
      prepared spells (3/4) [Prepare spells]". After a short rest, or once you join a fight, the menu is
      view-only and says "after a long rest". (#218)
- [ ] **Arcane Recovery:** cast a 1st-level spell, `/dm rest <you> short` → the summary offers
      "📖 Arcane Recovery… [Recover]"; use it → a level 1 slot back. Again → "used today". In a fight → refused. (#218)

**Out of a fight**

- [ ] **Shocking Grasp at someone 20 ft away** → refused for range straight away; the DM is **not** asked
      to start a fight first.
- [ ] Burning Hands (or any save spell) at a creature → the DM line shows its bonus
      (`DC 13 DEX save (+1[DEX])`) and **[Roll their save]**, which gives you the roll buttons graded vs the DC.
- [ ] A spell with range Self that targets (not an area) at someone else → "only targets you".
- [ ] Any "✨ Zek casts Magic Missile at …" line (in or out of a fight, and a plain `/character cast`
      like Light) → hover the spell name → its description.
- [ ] `/character cast fire_bolt` at The Kindler (after the DM lets it happen) → a line of flames flies to it. (#230)

**Chain mail** (put it on for these, take it off after)

- [ ] `/combat add <you>` before initiative → above the roll buttons, "↯ You have disadvantage on initiative."
      and "• Armor (you): disadvantage, …". Same when added mid-fight.
- [ ] `/combat rollforinitiative` → your line shows disadvantage.

**In a fight**

- [ ] After Start combat, on your first turn → "Your opening move: Fire Bolt at The Kindler [do it]".
- [ ] Spell attack → `+3[INT] +2[Prof]`, not `+5[Spell]`. (#216)
- [ ] On your turn: right-click your focus → spellbook → **Fire Bolt** → "Fire Bolt is ready: left-click
      your target". Left-click a goblin → "✨ Fire Bolt at Goblin:" with [Roll it] [I rolled…] [My total…]; the
      roll goes through as `/combat cast fire_bolt Goblin …`. Left-clicking a different creature first re-aims it. (#179)
- [ ] A save spell (Hold Person) or Magic Missile the same way → one **[Cast it]** button instead of roll buttons. (#179)
- [ ] Magic Missile from the **2nd-level** page → the click fills `… Goblin level 2 ` and spends a 2nd-level slot. (#179)
- [ ] With a spell ready, [cancel] → left-click attacks with your weapon again. End your turn with one ready →
      next turn, left-click is your weapon (it lapses with the turn). (#179)
- [ ] Burning Hands / Thunderwave from the spellbook → the aim preview starts at once; right-click confirms.
      **The slot is spent** (spellbook shows one fewer) and so is the Action. Cancel the aim instead → nothing
      spent. (An area spell used to be free in a fight.) (#179)
- [ ] **A Self spell needs no name:** `/combat cast false_life` → you gain the temp HP (it used to say
      "Usage: … <target>"). Picking it from the spellbook fills the same, with nothing to type.
- [ ] **Rituals are one word everywhere:** `/combat cast detect_magic ` + Tab offers `ritual`;
      `/combat cast detect_magic ritual` starts channelling it (`--ritual` is gone).
- [ ] Spellbook → an unprepared ritual (Detect Magic) in a fight, on your turn → fills
      `/combat cast detect_magic ritual`, not `/character`.
- [ ] `/character cast detect_magic ritual` in a fight → "You're in combat — cast it with
      /combat cast detect_magic ritual", and clicking it fills that. (#218)
- [ ] **Spell looks:** Fire Bolt at a goblin → a line of flames flies to it and pops; Ray of Frost → snowflakes;
      Burning Hands → only its aim preview, as before. (#230)

## Cleric

Take the Life Domain. A couple of spawned `town_guard`s stand in for allies.

- [ ] Creation, Spells tab, Level 1: Bless and Cure Wounds are gold **✦ Always prepared (Life Domain)** tiles,
      not picks: clicking does nothing and they don't count toward your number. (#247)
- [ ] Out of a fight, cast Sacred Flame at a creature, [Let it happen] → "…needs no attack roll (the target
      saves instead): cast it again to go. [cast it]". Guiding Bolt instead → "…is an attack: roll to hit." (#247)

**Prepared spells**


**Out of a fight**

- [ ] Sacred Flame with Let it happen → the DM sees the DC, **[Roll their save]**, **[Failed: damage]** / **[Saved: …]**.
      [Roll their save] → "🎲 You roll for <creature>: Dexterity save, DC 13" with the three buttons.
- [ ] **[Let it happen]** a Sacred Flame → you get **[cast it]**, not a d20 prompt (a save spell
      doesn't roll to hit).

**In a fight**

- [ ] Cure Wounds → the dice show.
- [ ] Cure Wounds → `+3[WIS]`, not "your spellcasting modifier"; **[Roll it]** heals
      (it used to re-prompt in physical-dice mode); the table sees the roll line. (#216)
- [ ] Upcast Cure Wounds with no roll (`/combat cast cure_wounds <t> level 2`) → the buttons keep `level 2`. (#216)
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

## Bard

- [ ] With CHA 10: Bardic Inspiration shows 1 use (it used to be missing).
- [ ] Bardic Inspiration on yourself → refused; on a creature → it's added to the creature's next roll
      (the DM rolls for it, so it isn't asked); 80 ft away → **[Do it anyway]**; out of uses → "No uses". The
      Features page's tile fills in the command. (A player being asked after their roll is in Part 2.) (#40)

## Sorcerer

- [ ] **Origins:** Divine Soul shows a **Divine Affinity** choice (Good → Cure Wounds in the
      spellbook); Draconic Bloodline shows **Dragon Ancestor**; Lunar and Shadow show their choices.
- [ ] **Divine Soul:** hover each affinity → "✦ Always known: Cure Wounds (1st level, doesn't use a pick)" etc.
- [ ] **Divine Soul:** the spell step offers cleric spells (Guiding Bolt, Healing Word) next to the
      sorcerer ones; each uses a pick.
- [ ] **Draconic Bloodline:** hover each dragon → its damage type and what it does from 6th level.
- [ ] **Draconic Bloodline:** the AC tile says **Draconic Resilience: 13 + DEX**, and max HP is one more
      than 6 + CON. Put on armor → normal armor AC.

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

## Artificer

- [ ] At level 1 it prepares INT-modifier spells (one fewer than before); two simple weapon picks.

## Races

**Halfling**
- [ ] **Stout halfling:** a poison save shows advantage (Stout Resilience).

**Genasi**

**Dragonborn** (red)
- [ ] Still resists fire, and its breath weapon is still a fire cone. (#222)
- [ ] Aim the breath weapon, let the turn pass → the aim cancels itself, and the use is still there. (#237)

**Astral Elf**

**Darkvision** (a dwarf, elf or tiefling, and a human) (#148)
- [ ] Drink a real night-vision potion as the human → it stays (the game only removes its own).
- [ ] As the dwarf, walk into a dark cave (or stand outside at night): after ~3 s the world beyond ~2 chunks
      fogs out. Step into torchlight or daylight → full view distance comes straight back. A human in the same
      cave keeps full distance (they just see darkness). Tell me if 2 chunks feels too tight or the switch
      too jumpy; `sight.darkvision_view_limit: false` turns it off.

**Heavy armor and Strength** (#34)
- [ ] A human with STR 10 puts on chain mail (`/dm give <you> chain_mail`, wear it) → "⚠ Chain Mail needs Strength 13:
      your speed drops by 10 ft (now 20 ft)". The sheet's Speed tile says 20 ft with the reason; AC is 16, not 10.
      In a fight the movement bar allows 20 ft. Take it off → 30 ft.
- [ ] A dwarf with STR 10 in plate → no warning, speed stays 25.

---

# Any character

## Characters and gear

- [ ] With two characters, delete the active one → the one left is used straight away; "no active
      character" never shows. With three, delete the active one → right-click a sheet to pick.
- [ ] Put a torch into your Explorer's Pack, in survival **and** in creative → it comes straight back
      out: "Packs don't hold other items: it's back in your inventory." (#219)

## Combat

- [ ] Mid-fight, on someone's turn, `/combat initiative <someone else> set 30` → they move to the
      top, and the **current turn stays with whoever had it** (the green →).
- [ ] A healing potion with auto-roll → the dice show.
- [ ] **Scoreboard:** **[S]** on someone surprised.
- [ ] **Scoreboard:** two tied initiatives in turn order.

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
- [ ] Advantage, game-rolled → `🎲 d20 [9, 15] advantage +3[DEX] = 18` (both dice, one line).
- [ ] Sheet [Normal] / [Advantage] / [Disadvantage] → never rolls on the click, even in auto-roll mode: you get the three roll buttons.
- [ ] `/combat action attack` → says "left-click your target" (it said right-click).
- [ ] Any d20 roll with `manualRoll 25` → asked again, not accepted.

## Checks: private rolls, groups, passive (#186)

(Private rolls and group checks with other players are in Part 2.)

- [ ] `/dm check all passive perception dc 14` → no prompts; you see each passive score and who notices.
      On a creature (`/dm check Goblin passive perception`) too.
- [ ] `/dm check <you> skill athletics|acrobatics dc 13` → you get both prompts ("pick how you go about
      it"); the one you roll comes back graded.

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

## Rests

- [ ] `/dm rest <character> short` recovers as before (the player version is gone: rests are the DM's call).
- [ ] `/dm rest <character> long` recovers as before; with no time given, the clock doesn't move.
- [ ] **[I'm done]** after a rest → "Rest finished"; the DM gets "✓ <name> is done with their long rest";
      the rest options (Hit Dice, prepare) are closed.

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

## Possession (#179)

- [ ] Possess a creature with attacks (`skeleton`): the hotbar item says "left-click a target". Hold the
      shortbow, left-click a player → the prompt lists **Shortbow first** and fills
      `/combat attack <them> Shortbow ` (you pick autoRoll/manualRoll after).
- [ ] Possess `skeleton` (darkvision 60) → you see in the dark; possess `wolf` → you don't; let
      go → back to your own character's sight (none if you have no character). (#148)

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

- [ ] With no fight → "Start a fight first".
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
- [ ] A Poisoned player, `/combat finished` → "X is still Poisoned after the fight".
- [ ] A Prone goblin, `/combat finished` → "Goblin is still Prone after the fight".
- [ ] Still Poisoned: a skill check from the sheet says "↯ Disadvantage: Poisoned" and rolls two d20s.
- [ ] Still Poisoned: a saving throw doesn't.
- [ ] Restrained (kept from a fight): a DEX save from the sheet is at disadvantage.
- [ ] New fight with the same goblin → the scoreboard still shows Prone.
- [ ] At the start of a turn, the condition hover is wrapped.

## Entities & shops

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

## Other DM commands

- [ ] A creature check with no roll → the DM gets the three roll buttons.
- [ ] `/dm check clear Balin Ironforge` → "is a creature. Only characters have held checks…".
- [ ] `/dm check clear ` + Tab → only characters, no creatures.
- [ ] `/dm check clear <your character>` → clears your held checks.
- [ ] `/dm rest <character> long` works.
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
- [ ] A fight in progress (your character in it) → after the restart, `/combat nextturn` works and
      `/combat finished` ends it; your character is out of combat and character commands work. (#165)
- [ ] A character Poisoned before → still Poisoned after.
- [ ] A creature Prone before → start a fight after, its scoreboard tag is still there.
- [ ] Your finished characters still list their chosen languages, tools and racial spell picks.
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
- [ ] On join → "Combat is still on".
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

- [ ] `/roll 2d6+3` works.
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

- Racial spell **uses** (a level-3 tiefling's Hellish Rebuke) aren't saved, so a restart refills
  them. Harmless at level 1; matters once level-up (#153) lands.
- Character-sheet inventory redesign (waiting until more content lands).
- Nicer default `material:` values for spellcasting foci and packs.
