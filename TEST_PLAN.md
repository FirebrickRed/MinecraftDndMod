# Test Plan

**Before you start:** `gradlew build` (it runs the automated tests and refuses to build if one
fails), copy `build/libs/*.jar` into the server's `plugins/`, restart. Load the resource pack.

**How this is laid out**

- **One box = one thing to do and one thing to look for.** If a box needs setup, the setup is in
  the section's intro or the box above it.
- **Part 1** is everything you can do alone, as the DM, with your own character.
- **Part 2** needs a second account that is **not** a DM (a friend, or a second account you `/deop`).
  Locks are here too: a DM walks through locks by design, so only a player can test them.
- **Restart checks** are batched at the end of Part 1 so you restart once.
- A section about one ticket's work names it in the heading, e.g. "Rage's advantage (#223)", so a
  failure has an obvious place to be reported. New sections get one when a ticket exists; older
  sections aren't being retrofitted.
- Tick a row with `[X]`. Ticked rows get deleted at the next round (git keeps the history).
- Notes go in **Playtest notes** at the bottom. They're answered in chat and turned into rows, then cleared.

---

# Paper 26.2 upgrade (branch `paper-26.2`, #211)

Only what the upgrade changed or can plausibly break; the rest of this plan covers everything else.
Server on **Java 25**, the jar from `jkvttplugin-paper26\build\libs\`. When these pass, the branch
merges into main.

- [ ] **Resource pack:** `pack.mcmeta` has `"min_format": 88, "max_format": 88`. The server prompts
      for it, and it loads without a "made for an older version" warning.
- [ ] **Models render, none purple:** race and class tiles in `/character create`, a spawned kobold
      (its head model), and The Kindler.
- [ ] **Time tool** (it now uses the renamed day-cycle game rule): Stop the Clock → the sun stops;
      Start → it moves; `/dm time` says running or stopped correctly.
- [ ] **Annotate form** (a dialog, Paper API): right-click a chest with the Annotate tool → the form
      opens; Save applies it.
- [ ] **Shop screen** opens, titled with the merchant's name; buying and selling work.
- [ ] **Chat input:** the name step in creation, and a Message spell's words.
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

## Character creation

### Size, halflings and sorcerers
- [ ] **A halfling is small:** create one → your body shrinks to about half a human's height. Log out
      and back in → still small. Switch to a Medium character (`/character view` → set active) →
      back to normal. Die and respawn → still small.
- [ ] A **genasi who picks Small** is small; one who picks Medium isn't.
- [ ] Doorways and 1-block gaps feel right at that size, and the camera isn't strange (#193: the
      0.6 scale is a guess).
- [ ] **Stout halfling:** a poison save shows advantage (Stout Resilience).
- [ ] **Sorcerer origins:** Divine Soul shows a **Divine Affinity** choice (Good → Cure Wounds in the
      spellbook); Draconic Bloodline shows **Dragon Ancestor**; Lunar and Shadow show their choices.
- [ ] **Draconic Bloodline:** the AC tile says **Draconic Resilience: 13 + DEX**, and max HP is one more
      than 6 + CON. Put on armor → normal armor AC.
- [ ] **Divine Soul:** the spell step offers cleric spells (Guiding Bolt, Healing Word) next to the
      sorcerer ones; each uses a pick.

- [ ] Item tooltips (a weapon, a potion) and condition hovers wrap at the same width as spells.

### Content audit (races, classes, backgrounds vs the PHB)
- [ ] **Paladin:** a Class Skills pick (2), and Weapons / Secondary Weapon / Adventuring Gear picks.
      Finished: wearing the chain mail gives **no** "not proficient" warning.
- [ ] **Paladin:** the sheet shows Divine Sense (1 + CHA uses) and Lay on Hands (5 points).
- [ ] **Fighter:** a Fighting Style pick; no second chain mail or shield in your inventory.
- [ ] **Fighter, "Any Martial Weapon + Any Martial Weapon":** the list says "Pick 1 of 2", pick a longsword →
      it stays open saying "Pick 2 of 2 … (so far: Longsword + Any Martial Weapon)", pick a warhammer →
      the tile reads "✔ Longsword + Warhammer", and you get both.
- [ ] Same, pick one then press Back → the tile says "So far: Longsword + …" and Finish still wants it;
      click it → you carry on with the second pick.
- [ ] **Ranger:** Favored Enemy, a language for it, and Natural Explorer picks; two simple melee weapons
      when you take that option; 20 arrows in one stack.
- [ ] **Artificer:** at level 1 it prepares INT-modifier spells (one fewer than before); two simple
      weapon picks.
- [ ] **Hexblade warlock:** a shield in the off hand, no "not proficient" warning.
- [ ] **Bard** with CHA 10: Bardic Inspiration shows 1 use (it used to be missing).

## Finished characters

Finish the combo, then look at the sheet and your inventory.

- [ ] **High Elf Rogue** with Fire Bolt as the Wizard Cantrip → `/character cast fire_bolt` works and uses INT.
- [ ] **Astral Elf** with Sacred Flame + Wisdom → `/combat cast sacred_flame <target>` uses WIS for the DC.
- [ ] **Folk Hero:** the picked tool is in your kit.
- [ ] **Entertainer:** the picked instrument is in your kit.
- [ ] `/dm rest <character> short` recovers as before (the player version is gone: rests are the DM's call).
- [ ] `/dm rest <character> long` recovers as before; with no time given, the clock doesn't move.
- [ ] **Tiefling Rogue:** right-click thieves' tools with no chest in view → nothing happens, no
      "cannot use this type of focus" message.
- [ ] **Tiefling Rogue:** right-click a chest holding thieves' tools → the chest's own [Open it] /
      [Ask for a check] prompt, not a focus message.

## Casting from the spellbook and `/character cast`

- [ ] `/character cast ` + Tab → your cantrips, spells and racial spells (Thaumaturgy for a tiefling).

## Combat

- [ ] **One prompt per click (#167):** on your turn, left-click a kobold directly → exactly **one** attack
      prompt. Then aim at it from a few blocks away and left-click the air → one prompt. With a bow,
      left-click at a distant one → one prompt.
- [ ] Same, after a hit: exactly one damage prompt (to you, and one to the DM). Applying it twice →
      the second says "No attack hit to apply damage for".
- [ ] **Wizard in chain mail:** a weapon attack shows "↯ disadvantage" with an "Armor (you)" reminder.
- [ ] **Wizard in chain mail:** initiative rolls with disadvantage.
- [ ] **Wizard in chain mail:** `/combat cast` refuses.
- [ ] `/combat add <a player>` mid-fight → the game's roll shows, and they also get the roll
      buttons; rolling replaces the game's number, the tracker re-sorts, and the table sees
      "rolled their own initiative".
- [ ] Same, after their first turn → `/combat initiative` refuses (the order is settled).
- [ ] Mid-fight, on someone's turn, `/combat initiative <someone else> set 30` → they move to the
      top, and the **current turn stays with whoever had it** (the green →).
- [ ] `/combat attack <t> <weapon> manualRoll abc` → "'abc' isn't a number", then buttons that fill
      `/combat attack <t> <weapon> manualRoll ` (no `abc` left in it).
- [ ] `/combat rollforinitiative` with someone in unproficient armor → their line shows disadvantage.
- [ ] `/combat heal <t> autoRoll 2d4` → the dice show.
- [ ] A healing potion with auto-roll → the dice show.
- [ ] Cure Wounds → the dice show.
- [ ] **Scoreboard, fight:** players' HP green / yellow / red below ½ and ¼.
- [ ] **Scoreboard, fight:** temp HP shows as `+N` in aqua.
- [ ] **Scoreboard, fight:** **[S]** on someone surprised.
- [ ] **Scoreboard, fight:** downed → red ☠ and green/red death-save dots.
- [ ] **Scoreboard, fight:** pink ✦N while channelling a ritual.
- [ ] **Scoreboard, fight:** two tied initiatives in turn order.
- [ ] **Out of reach in a fight, as a player:** a sword at someone 20 ft away → **[Ask the DM]**.
- [ ] The DM gets **[Allow]** / **[Deny]**; Allow → you get **[go again]**, and the attack goes through.
- [ ] Same with a spell in a fight (Cure Wounds from 20 ft) → [Ask the DM], then it goes through once.
- [ ] Hex at someone 120 ft away → refused for range (it wasn't checked before).
- [ ] A Self spell that targets (e.g. one with range Self, not an area) at someone else, out of a fight →
      "only targets you" (only checked in a fight before).
- [ ] Touch spells reach one block further than before in a fight (5 ft of slack, same as out of one).

### After the deprecation clean-up (replaced Paper APIs)
- [ ] **Rage** still tints red (the Strength effect) and it goes when the rage ends.
- [ ] **Bow in combat:** draw and release a real bow shot → refused, nothing fires. Count your
      arrows before and after: does a refused draw cost one? (The old "give it back" call never
      worked, per Paper, so this is to find out, not a regression.)

### Rage's advantage (#223)
- [ ] A barbarian rages (`/combat use rage`), then rolls Athletics from the sheet: the line shows
      **↑ advantage: Rage** and the roll is 2d20 keep-higher. A STR save in the fight too.
- [ ] Not raging, or a DEX check while raging: a normal roll.

### Choices that grant things (#222)
- [ ] Create a **fire genasi**: the Extra tab has **Spellcasting Ability** (Intelligence / Wisdom /
      Charisma). Pick Charisma → the spellbook's Produce Flame uses CHA.
- [ ] Create a **Genie warlock**: **Genie Kind** and **Genie's Vessel** now show (they never did).
      Efreeti → Burning Hands is **offered in the spell step** (not handed over: see #228 below).
- [ ] **Warlock patron spells (#228):** a Fiend warlock's spell step offers Burning Hands and Command
      next to the warlock spells; taking one uses a pick (2 of 2). Their sheet doesn't know the rest.
      The patron's tile says **Expanded Spell List (you may learn these)**. A cleric still gets their
      domain spells for free.
- [ ] A **red dragonborn** still resists fire, and its breath weapon is still a fire cone.

### Monk and two-weapon fighting (#220, #221)
- [ ] A monk with no armor: the sheet's AC tile says **Unarmored Defense: 10 + DEX + WIS**. Put on
      leather armor (chestplate slot) → the AC drops to leather's; take it off → back.
- [ ] A monk's unarmed strike (`/combat attack <target> unarmed`) rolls **1d4 + DEX**, labelled [DEX].
- [ ] **The game picks the cost.** A monk attacks unarmed twice with the same `/combat attack <target> unarmed`
      (or left-click twice): the first uses the Action, the second says **"That used your bonus action
      (Martial Arts: bonus unarmed strike)"**. A third is refused with the reason.
- [ ] The action bar: before attacking, **Bonus: READY: Martial Arts…**; a fighter with nothing to
      spend it on shows **Bonus: —**.
- [ ] `/combat attack` with nothing after it lists attacks under **With your Action** and **With your
      bonus action**.
- [ ] BG3 order (default `combat.bonus_attack_timing: any_time`): a fighter with a dagger in each hand
      uses the off-hand button first (`… dagger bonus`), then still has their Action. Its damage has no
      DEX. Set `after_attack_action` and restart: the same button now says to attack first.

### Level-1 class features (#229)
- [ ] **Archery** fighter, longbow: the to-hit breakdown ends with `+2[Archery]`; a sword attack doesn't.
- [ ] **Defense** fighter: AC goes up by 1 when you put on chain mail (and not unarmored).
- [ ] **Dueling** fighter, longsword + shield: damage breakdown has `+2[Dueling]`. A second weapon in the
      off hand → no Dueling.
- [ ] **Great Weapon Fighting**, greatsword, damage with `autoRoll` → "(Great Weapon Fighting: any 1 or 2
      was rolled again.)" A plain hit on a longsword with a shield doesn't say it.
- [ ] **Two-Weapon Fighting**, dagger in each hand: the off-hand attack's damage has `+N[DEX]`.
- [ ] **Second Wind:** `/combat use second_wind` → the three roll buttons; answer → heals 1d10 + 1, the
      bonus action is spent, a second use says no uses left. After the fight, `/character use second_wind`
      once it's back (a short rest).
- [ ] **Rage** now spends the bonus action: rage, then `/combat bonusAction` has nothing left.
- [ ] **Sneak Attack with advantage** (the goblin is prone, or restrained): a rapier hit says "🗡 Sneak
      Attack: +1d6 in that damage (advantage)" and the damage prompt rolls 1d8+1d6. No DM needed.
- [ ] **Sneak Attack without advantage**, a player's rogue: the hit gives "🗡 Sneak Attack (+1d6)? No
      advantage, so it's the DM's call: the game sees Borin within 5 ft of them. [Ask the DM]" (or "no
      ally next to them"). Click → the DM gets [Allow] / [Deny].
- [ ] [Allow] before the damage is rolled → "The DM allows Sneak Attack: +1d6 in that damage" and a new
      damage prompt for 1d8+1d6. On a crit it's +2d6.
- [ ] [Allow] after the damage was already applied → the 1d6 is rolled and dealt on its own, shown to the table.
- [ ] [Deny] → the player is told. A DM running a rogue gets [Add it] instead of [Ask the DM].
- [ ] Once the Sneak Attack damage lands, a second hit that turn offers nothing. A club (not finesse) → nothing.
- [ ] **Shield:** a Sneak Attack hit that Shield turns into a miss → the next hit that turn can still Sneak Attack.
- [ ] **Lay on Hands:** `/combat use lay_on_hands <ally> 3` next to them → heals 3, pool 5 → 2. From 20 ft
      → "You need to touch them". On a skeleton → "no effect on Skeleton: it's an undead".
- [ ] **Divine Sense** with `/dm entity spawn skeleton` nearby: "an undead, about 25 ft to the
      north-east". The DM sees the same; other players only see that you used it. With nothing near →
      "Nothing within 60 ft".

## Death

- [ ] Down yourself, fail three death saves → "has DIED", turn skipped.
- [ ] `/combat finished`, new fight → still `[DEAD]`, still skipped.
- [ ] Dead: `/dm adjust <you> hp +10` refuses.
- [ ] Dead: the sheet's HP slot shows a skull "DEAD".
- [ ] `/dm revive <you>` → 1 HP, turns return.
- [ ] `/dm adjust <c> hp -<current + max HP>` → dies outright (massive damage).
- [ ] Fail one save, `/combat finished`, new fight → still 1 failure.
- [ ] `/dm entity revive <creature>` mid-fight → its turns come back.
- [ ] A dead character leaves a tipped-over head "☠ <name>" where they fell.
- [ ] As DM, right-click the body → [Revive] and [Remove body].
- [ ] You can't punch the body or take the head.
- [ ] Walk away until the chunk unloads, `/dm revive <c>`, walk back → the body is gone.
- [ ] A stable character at 0 HP (the long rest refused them): `/dm adjust <c> hp +1` → now the long rest works.
- [ ] A DM already in spectator mode for their own reasons isn't pulled out of it by deaths.

## Attacks outside a fight

- [ ] After Start combat, on the caster's first turn → "Your opening move: Fire Bolt at The Kindler [do it]".
- [ ] Sacred Flame with Let it happen → the DM sees the DC, **[Roll their save]**, **[Failed: damage]** / **[Saved: …]**.
      [Roll their save] → "🎲 You roll for <creature>: Dexterity save, DC 13" with the three buttons.
- [ ] **[Let it happen]** a Sacred Flame → you get **[cast it]**, not a d20 prompt (a save spell
      doesn't roll to hit).
- [ ] `/character cast cure_wounds The Kindler` from 18 ft → refused, with **[Ask the DM]**.
- [ ] Click it → the DM gets "📏 … out of reach (about 18 ft away …)" **[Allow]** / **[Deny]**.
- [ ] [Allow] → you get **[cast it]**; it goes through once. Casting again from there refuses again.
- [ ] [Deny] → "The DM says it doesn't reach".
- [ ] **Shocking Grasp at someone 20 ft away** → refused for range straight away; the DM is **not** asked
      to start a fight first.
- [ ] Burning Hands (or any save spell) at a creature out of combat → the DM line shows its bonus
      (`DC 13 DEX save (+1[DEX])`) and **[Roll their save]**, which gives you the roll buttons graded vs the DC.
- [ ] Any "✨ Zek casts Magic Missile at …" line (in or out of a fight, and a plain `/character cast`
      like Light) → hover the spell name → its description.

## Roll prompts (one wording everywhere, #216)

Every prompt is **[Roll it] [I rolled…] [My total…]**, all three fill chat. Hover [My total…] → "roll 2d20 and keep the lower and add -1[DEX] yourself" when at disadvantage ([My total…] only when the game adds
something). Every bonus is **named by its source**, and the prompt and the result show the same
label: the hover says "the game adds +3[INT] +2[Prof]", the result `🎲 d20 [14] +3[INT] +2[Prof] = 19`
(the game rolled), `🎲 you rolled 14 +3[INT] +2[Prof] = 19`, or `🎲 your total: 19`.

- [ ] A contest with a creature side → its button labelled `+5[Deception]` (listed skill) or `+1[CHA]`.
- [ ] In a fight: initiative → `+2[DEX]`; an attack (left-click) → the weapon's breakdown; a save
      and a concentration save → `+1[CON] +2[Prof]` (proficient) or just `+1[CON]`.
- [ ] Spell attack in a fight → `+3[INT] +2[Prof]`, not `+5[Spell]`.
- [ ] Cure Wounds in a fight → `+3[WIS]`, not "your spellcasting modifier"; **[Roll it]** heals
      (it used to re-prompt in physical-dice mode); the table sees the roll line.
- [ ] Upcast Cure Wounds with no roll (`/combat cast cure_wounds <t> level 2`) → the buttons keep `level 2`.
- [ ] A creature's attack in a fight → to hit `+4[Scimitar]`, damage `+2[Scimitar]` (not `[ToHit]`).
- [ ] Any game-rolled dice (damage, healing, a potion) → **one** `= total`, never `[5] = 5 +3 = 8`.
- [ ] `/combat damage <t> autoRoll` → one line, `🎲 1d8 [6] +3[STR] = 9`.
- [ ] Type a roll command with no roll words (physical-dice mode): `/combat attack <t> <weapon>`,
      `/combat save`, `/combat concentration`, `/character loot investigation` → the three buttons
      on that same command, not "type 'manualRoll <n>'".
- [ ] Downed → the player gets **💀 Roll your death save** with [Roll it] [I rolled…]; the result
      reads `… makes a death saving throw: 🎲 you rolled 14` → SUCCESS.
- [ ] A Halfling rolling a 1 on a death save → Lucky rerolls it (it didn't before).
- [ ] `/combat rollforinitiative` (DM) → each line `🎲 d20 [14] +2[DEX] = 16`.
- [ ] Opportunity attack buttons → pick the attack, Enter, then the three roll buttons.
- [ ] Nowhere shows two dice icons (`🎲 … 🎲`), e.g. a shared check result or a loot roll.
- [ ] Every roll button, **[Roll it]** included, only fills chat; nothing rolls until you press Enter.
- [ ] Advantage, game-rolled → `🎲 d20 [9, 15] advantage +3[DEX] = 18` (both dice, one line).
- [ ] Sheet [Normal] / [Advantage] / [Disadvantage] → never rolls on the click, even in auto-roll mode: you get the three roll buttons.
- [ ] Then [Roll it] + Enter, as a Halfling rolling a 1 → Lucky rerolls it.
- [ ] `/combat action attack` → says "left-click your target" (it said right-click).

## Fifth-round fixes

- [ ] **Divine Soul:** hover each affinity → "✦ Always known: Cure Wounds (1st level, doesn't use a pick)" etc.
- [ ] **Draconic Bloodline:** hover each dragon → its damage type and what it does from 6th level.
- [ ] **Fighter:** hover a Fighting Style → "✦ Archery: +2 to hit (ranged)" and so on.
- [ ] **Paladin:** pick "Martial Weapon + Shield" as a longsword → only that tile lights up.
- [ ] **Ranger:** Favored Enemy options read "Dragons (learn Draconic)"; no separate language pick. Finished →
      Draconic is on the sheet. Beasts → no extra language.
- [ ] **Wizard with nothing prepared:** open the spellbook → "📖 You have no spells prepared…" [Prepare spells];
      click a 1st-level spell → "isn't prepared".
- [ ] **Death save:** `/combat deathsave` (with or without your name) → the three buttons, not a roll.
      `manualRoll 0` → "No die shows less than 1", nothing rolled. `autoRoll` → the game rolls.
- [ ] Any d20 roll with `manualRoll 25` → asked again, not accepted.
- [ ] **[I'm done]** after a rest → "Rest finished"; the DM gets "✓ <name> is done with their long rest";
      the rest options (Hit Dice, prepare) are closed.
- [ ] **Creation, wizard spell step:** the label says "This is your spellbook. Each day you prepare some of it".

## Help, Hide and Search in a fight (#176)

- [ ] `/combat action help` → a row of your allies; pick one → the table sees "🤝 … helps …". Their next attack
      shows "↑ advantage" with "Helped by …"; the attack after that doesn't. A sheet check instead of an
      attack also takes the advantage ("↑ Advantage: helped by …").
- [ ] Unused, the Help is gone once the helper's next turn starts.
- [ ] `/combat action hide` → the three buttons (Stealth). Rolled → you see your total; the table sees
      "tries to Hide"; the DM sees it against each enemy's passive Perception with [Hidden] / [Not hidden].
- [ ] [Hidden] → "is hidden", and a Hidden condition tag on the scoreboard; your attack has advantage, and
      after it "is no longer hidden: the attack gave them away". Attacks against you had disadvantage.
- [ ] A creature (DM) takes `/combat action search` → the DM sees the Perception total and, for a hidden
      player it beats, **[Reveal]**.
- [ ] Help / Hide / Search spend the Action only once rolled (waiting on your die costs nothing).

## Checks: private rolls, groups, passive (#186)

- [ ] A player rolls a skill from their own sheet → only they see it, with **[Show the DM]**. Click →
      the DM gets it with [Share with players]; nobody else sees anything until then.
- [ ] `/dm check all skill stealth dc 12` → every online character gets the prompt; you see each result as it
      comes ("Zek — Stealth: … ✔ (1/3 in)"), then "Group Stealth: 2 of 3 succeed. The group SUCCEEDS."
- [ ] Same with `Zek, Borin` instead of `all`; and **[Close now]** before everyone rolls → the verdict from
      whoever did, plus "Didn't roll: …".
- [ ] `/dm check all passive perception dc 14` → no prompts; you see each passive score and who notices.
      On a creature (`/dm check Goblin passive perception`) too.
- [ ] `/dm check Zek skill athletics|acrobatics dc 13` → Zek gets both prompts ("pick how you go about
      it"); the one he rolls comes back to you, graded.

## Features & Traits page (#65)

- [ ] Character sheet → **Features & Traits** (nether star, slot 9). Top row: your race's traits, darkvision
      and speeds; your class's features up to your level with their text (a fighter: Fighting Style, Second
      Wind); your subclass's, names in proper case; "Your picks" (fighting style, dragon ancestor…).
- [ ] A dragonborn: **Breath Weapon** tile shows the area, your save DC and the damage for your ancestry,
      and "Uses 1/1". Click it outside a fight → "is used in a fight"; in a fight on your turn → a chat
      button that fills `/combat use breath_weapon`.
- [ ] A fighter clicks **Second Wind** out of a fight → fills `/character use second_wind`. Passive tiles
      (Sneak Attack, Archery) aren't clickable. ← goes back to the sheet.

## Possessed creatures attack by left-click (#179)

- [ ] Possess a creature with attacks (`skeleton`): the hotbar item says "left-click a target". Hold the
      shortbow, left-click a player → the prompt lists **Shortbow first** and fills
      `/combat attack <them> Shortbow ` (you pick autoRoll/manualRoll after).

## Darkvision is night vision (#148)

- [ ] Make a dwarf/elf/tiefling active → night vision (no icon, no swirl); a cave at night is lit. Switch to
      a human → it goes. Rejoin and die/respawn → it comes back for the dwarf.
- [ ] Drink a real night-vision potion as the human → it stays (the game only removes its own).
- [ ] As a DM, possess `skeleton` (darkvision 60) → you see in the dark; possess `wolf` → you don't; let
      go → back to your own character's sight (none if you have no character).
- [ ] As the dwarf, walk into a dark cave (or stand outside at night): after ~3 s the world beyond ~2 chunks
      fogs out. Step into torchlight or daylight → full view distance comes straight back. A human in the same
      cave keeps full distance (they just see darkness). Tell me if 2 chunks feels too tight or the switch
      too jumpy; `sight.darkvision_view_limit: false` turns it off.

## Prepared spells (#218)

- [ ] **Wizard spellbook:** an unprepared spell reads "In your spellbook, not prepared" and clicking it does
      nothing; an unprepared **ritual** (e.g. Detect Magic, Find Familiar) says "Ritual: click to cast it as one".
- [ ] `/character cast <unprepared non-ritual>` → "…in your spellbook but not prepared…". An unprepared
      ritual without `ritual` → the same, plus **[cast it as a ritual]**; with `ritual` → "casts … as a ritual
      (10 extra minutes, no spell slot)" and no slot is spent.
- [ ] In a fight, `/combat cast <unprepared ritual> --ritual` works for the wizard.
- [ ] **Cleric:** the menu lists the whole cleric list at 1st level, domain spells last as "✦ Always
      prepared (Life Domain)". Swap one after a long rest; the new one casts, the old one says "isn't prepared".
- [ ] **Cleric:** an unprepared ritual can't be cast as one ("only a ritual they have prepared").
- [ ] **The window:** `/dm rest <you> long` → the summary says "During this rest you can: 📖 Change your
      prepared spells (3/4) [Prepare spells]". After a short rest, or once you join a fight, the menu is
      view-only and says "after a long rest".
- [ ] **Arcane Recovery:** wizard casts a 1st-level spell, `/dm rest <you> short` → the summary offers
      "📖 Arcane Recovery… [Recover]"; use it → a level 1 slot back. Again → "used today". In a fight → refused.
- [ ] Restart after preparing → the same spells are still prepared.

## Rests and Hit Dice (#52)

- [ ] The sheet's HP tile says "Hit Dice: 1/1 (1d10)" for a fighter.
- [ ] Take some damage, `/dm rest <you> short` → the summary lists Hit Dice, then
      **💚 Spend a Hit Die (1 of 1 left, HP 5/12): [Roll it] [I rolled…] [My total…]**.
- [ ] Answer it → heals the die + CON, HP goes up on the sheet, "Hit Dice left: 0 of 1".
- [ ] `/character hitdice autoRoll` again → "No Hit Dice left. A long rest brings back half of them."
- [ ] `/character hitdice` with no rest → "spent at the end of a short rest".
- [ ] After a short rest, join a fight, then finish it → `/character hitdice` refuses (the fight ended the rest).
- [ ] `/dm rest <you> long` → Hit Dice back to 1/1; Second Wind back too (it used to stay spent overnight).
- [ ] **Warlock:** cast a 1st-level spell, `/dm rest <you> short` → "Pact Magic spell slots restored", and
      the slot is back in the spellbook.
- [ ] Restart after spending a Hit Die (before a long rest) → still 0/1.

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

- [ ] Buttons the game fills in (loot, possession, shop prompts) say `/dm entity …` and work.
- [ ] Looking at a creature, right-click your spellcasting focus → the spellbook opens.
- [ ] Right-clicking a **dead** creature with the sheet in hand → still loots (the body wins).
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
- [ ] The Trap DC slider goes to 40.

## DM tools

Spawn alira as "The Kindler" first (`/dm entity spawn alira "The Kindler"`).

- [ ] A creature check with no roll → the DM gets the three roll buttons.
- [ ] `/dm check clear Balin Ironforge` → "is a creature. Only characters have held checks…".
- [ ] `/dm check clear ` + Tab → only characters, no creatures.
- [ ] `/dm check clear <your character>` → clears your held checks.
- [ ] `/dm resource restore "Balin Ironforge" rage` works.
- [ ] `/dm rest <character> long` works.
- [ ] `/dm resource restore <character> all` works.
- [ ] `/dm resource consume <character> <res> 1` works.
- [ ] Look at a chest, `/dm object key brass_key` → "The Brass Key opens the Chest".
- [ ] `/dm object key` on a sealed block refuses.
- [ ] **Thieves' tools break on a fail:** carry 2 sets, `/dm check <you> tool thieves_tools dc 25`, fail → one set gone, you're told.
- [ ] DC 5, pass → nothing breaks.
- [ ] No DC → never breaks.
- [ ] `on_fail: always` / `never` in config.yml behave as named.

## Restart checks (do these together, one restart)

Set these up, `/stop`, start the server, then check:

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
