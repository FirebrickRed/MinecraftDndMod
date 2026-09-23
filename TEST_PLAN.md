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
- Tick a row with `[X]`. Ticked rows get deleted at the next round (git keeps the history).
- Notes go in **Playtest notes** at the bottom, newest on top.

---

# Part 1 — You alone, as the DM

## Character creation

- [ ] Human Monk Entertainer → the choice headers say where each pick comes from: "Monk: Artisan's
      Tools or Musical Instrument — …" and "Entertainer: Musical Instrument — …".
- [ ] A pick two sources share (Rock Gnome + Artificer's duplicate) → its header names both,
      "Rock Gnome + Artificer: …".

## Finished characters

Finish the combo, then look at the sheet and your inventory.

- [ ] **Rock Gnome Artificer, Sage:** the tool you picked as the Tinker's Tools replacement is on the sheet.
- [ ] **Rock Gnome Artificer:** looking at the sky, right-click thieves' tools → the spell menu opens.
- [ ] **High Elf Wizard, Noble:** Wizard Cantrip and class cantrips are both there, no double pick.
- [ ] **High Elf Wizard, Noble:** both languages and the gaming set are on the sheet.
- [ ] **High Elf Wizard, Noble:** your Spells-tab picks are in the spellbook.
- [ ] **High Elf Rogue** with Fire Bolt as the Wizard Cantrip → `/character cast fire_bolt` works and uses INT.
- [ ] **Astral Elf** with Sacred Flame + Wisdom → `/combat cast sacred_flame <target>` uses WIS for the DC.
- [ ] **Mountain Dwarf Fighter, Guild Artisan:** the artisan's-tool pick gives the proficiency **and** the item.
- [ ] **Mountain Dwarf Fighter:** heavy armor on, no penalty.
- [ ] **Folk Hero:** the picked tool is in your kit.
- [ ] **Entertainer:** the picked instrument is in your kit.
- [ ] `/character rest short` recovers as before.
- [ ] `/character rest long` recovers as before.
- [ ] Press **Q** holding the sheet → it drops; nothing opens.
- [ ] Press **Q** holding the Create Character paper → it drops; nothing opens.
- [ ] **Tiefling Rogue:** right-click thieves' tools with no chest in view → nothing happens, no
      "cannot use this type of focus" message.
- [ ] **Tiefling Rogue:** right-click a chest holding thieves' tools → the chest's own [Open it] /
      [Ask for a check] prompt, not a focus message.

## The sheet's skills and rolls

On a Tiefling Rogue with Expertise in Persuasion (or any expertise skill):

- [ ] Skills menu, hover the expertise skill → "✦ Expertise (proficiency ×2)", not "Proficient".
- [ ] Same hover → the bonus broken down, e.g. `+4[CHA] +4[Prof ×2]`.
- [ ] Hover a plain proficient skill → e.g. `+1[WIS] +2[Prof]`.
- [ ] Hover a saving throw → its breakdown too.
- [ ] Click a skill → no menu opens; chat shows the bonus and **[Normal] [Advantage] [Disadvantage]**.
- [ ] Click **[Advantage]** → the roll shows both dice and "advantage", e.g. `[9, 15] advantage`.
- [ ] Click **[Disadvantage]** → both dice, keeps the lower.
- [ ] The buttons still work after a couple of rolls (they don't go dead after one click).
- [ ] `/character check skill persuasion adv autoRoll` → both d20s shown, not just one.
- [ ] **Wizard in chain mail:** the chat line for a DEX skill says it's at disadvantage (armor).
- [ ] **Wizard in chain mail:** a WIS check doesn't.
- [ ] **Wizard in chain mail:** the sheet's AC tile says "⚠ not proficient with Chain Mail".
- [ ] **Wizard in chain mail:** `/dm check <wizard> save dex` is at disadvantage.
- [ ] **Wizard in chain mail:** take it off → all normal again.
- [ ] **Fighter in chain mail:** no penalty.
- [ ] **Mountain dwarf wizard:** scale mail fine, chain mail penalized.

## Casting from the spellbook and `/character cast`

- [ ] `/character cast ` + Tab → your cantrips, spells and racial spells (Thaumaturgy for a tiefling).
- [ ] `/character cast fire_bolt ` + Tab → creature and player names.
- [ ] Click Thaumaturgy in the spellbook → the chat line's spell name is underlined; hover it →
      level, school, casting time, range, duration and what it does.

## Combat

- [ ] **Bows:** on your turn, left-click toward a distant enemy (not touching them) → the filled-in `/combat attack`.
- [ ] **Wizard in chain mail:** a weapon attack shows "↯ disadvantage" with an "Armor (you)" reminder.
- [ ] **Wizard in chain mail:** initiative rolls with disadvantage.
- [ ] **Wizard in chain mail:** `/combat cast` refuses.
- [ ] `/combat add <someone>` mid-fight → the table sees their initiative roll.
- [ ] `/combat rollforinitiative` → each line shows `[d20] +N (DEX) = total`.
- [ ] Same, in unproficient armor → `[disadvantage: a/b]`.
- [ ] `/combat damage <t> autoRoll 2d6` → "🎲 2d6: [4, 3] = 7" before the damage.
- [ ] `/combat heal <t> autoRoll 2d4` → the dice show.
- [ ] A healing potion with auto-roll → the dice show.
- [ ] Cure Wounds → the dice show.
- [ ] `/combat damage <t> manualRoll 7` → 7 damage.
- [ ] `/combat damage <t> manualRoll abc` → a sensible error.
- [ ] **Scoreboard, setup:** combatants in the order added, no numbers on the right, "Add combatants..." at the bottom.
- [ ] **Scoreboard, setup:** two creatures with the same name → two lines.
- [ ] **Scoreboard, fight:** green **→** on the current turn; initiative is the red number.
- [ ] **Scoreboard, fight:** players' HP green / yellow / red below ½ and ¼.
- [ ] **Scoreboard, fight:** temp HP shows as `+N` in aqua.
- [ ] **Scoreboard, fight:** **[S]** on someone surprised.
- [ ] **Scoreboard, fight:** downed → red ☠ and green/red death-save dots.
- [ ] **Scoreboard, fight:** dead → **[DEAD]**.
- [ ] **Scoreboard, fight:** a purple condition tag.
- [ ] **Scoreboard, fight:** pink ✦N while channelling a ritual.
- [ ] **Scoreboard, fight:** two tied initiatives in turn order.
- [ ] **Scoreboard, fight:** "Round: N" at the bottom with no number on the right.
- [ ] **Out of range, as DM** (possessing a creature, attacking something too far) → "... out of
      range [Attack anyway]"; click → the attack goes ahead.

## Death

- [ ] Down yourself, fail three death saves → "has DIED", turn skipped.
- [ ] `/combat finished`, new fight → still `[DEAD]`, still skipped.
- [ ] Dead: `/dm adjust <you> hp +10` refuses.
- [ ] Dead: `/character rest long` refuses.
- [ ] Dead: `/dm rest <you> long` refuses.
- [ ] Dead: the sheet's HP slot shows a skull "DEAD".
- [ ] `/dm revive <you>` → 1 HP, turns return.
- [ ] `/dm adjust <c> hp -<current + max HP>` → dies outright (massive damage).
- [ ] Fail one save, `/combat finished`, new fight → still 1 failure.
- [ ] `/dm entity revive <creature>` mid-fight → its turns come back.
- [ ] A dead character leaves a tipped-over head "☠ <name>" where they fell.
- [ ] As DM, right-click the body → [Revive] and [Remove body].
- [ ] You can't punch the body or take the head.
- [ ] Walk away until the chunk unloads, `/dm revive <c>`, walk back → the body is gone.
- [ ] **Long rest at 0 HP** (stable, not dead) → refused, "needs at least 1 HP".
- [ ] Then `/dm adjust <c> hp +1` → the long rest works.
- [ ] A DM already in spectator mode for their own reasons isn't pulled out of it by deaths.

## Attacks outside a fight

- [ ] `/character cast fire_bolt The Kindler` out of combat → "asking the DM"; you get **[Start
      combat]** / **[Let it happen]** / **[Deny]**. No slot spent yet.
- [ ] Same, leaving the name off and looking at her → same prompt.
- [ ] **[Deny]** → the caster is told; nothing happens.
- [ ] **[Let it happen]** → the caster gets roll buttons; the attack roll shows `vs AC`.
- [ ] Let it happen, a hit → roll damage (`/character damage`), her HP drops.
- [ ] Let it happen, a miss → it ends there.
- [ ] **[Start combat]** → a fight opens in setup with both of you, and the hint mentions the Surprise tool.
- [ ] After Start combat, on the caster's first turn → "Your opening move: Fire Bolt at The Kindler [do it]".
- [ ] Sacred Flame with Let it happen → the DM sees the DC, **[Call the save]**, **[Failed: damage]** / **[Saved: …]**.
- [ ] Look at a wall, `/character cast fire_bolt` → "You're not aiming at a creature. Cast it anyway?" [Cast it].
- [ ] Cast it → the DM sees "N to hit", "they're looking at the wall torch" and **[Ask for damage]**.
- [ ] Damage rolled at a thing hurts nobody.
- [ ] `/character cast cure_wounds <someone>` → roll prompt → they heal, no DM prompt.
- [ ] Same, too far away → "about N ft away" refusal.
- [ ] Left-click a creature with a sword out of combat → the DM gets **[Start combat]** / **[Deny]**.
- [ ] That click doesn't damage the creature.
- [ ] The spellbook, out of combat, fills `/character cast …`, and its hover explains the above.

## The Adjust menu & `/dm adjust`

- [ ] HP tile: click → +1; right-click → −1; shift → ±5.
- [ ] In a fight, an HP change updates the scoreboard and the player's open sheet.
- [ ] **Set HP exactly…** → closes the menu, gives a [click to type the number] line.
- [ ] **Temp HP…** → same.
- [ ] **Max HP…** (creatures) → same.
- [ ] Creature AC tile, shift-click → own AC +1, "Own AC 13 (stat block says 12)".
- [ ] **Back to the stat block's AC** undoes it.
- [ ] Player AC tile, shift-click → says their AC comes from armor.
- [ ] The rules text on a condition tile is wrapped, not one long line.
- [ ] `/dm hp` is an unknown command.
- [ ] `/combat damage override` is gone.
- [ ] `/combat condition` is gone.
- [ ] `/dm entity maxhp` is gone.
- [ ] `--force` is gone.
- [ ] A trap's **[Apply damage]** fills `/dm adjust … hp -…`.
- [ ] A spawn's **[Use my own roll]** fills `/dm adjust … maxhp`.

## Surprise tool & damage approval

- [ ] DM combat toolbar → **Surprised (ambush)** (firework star) in slot 6; its hover explains Surprised.
- [ ] In a fight, right-click a goblin with it → "Goblin is Surprised: …", **[S]** on the tracker.
- [ ] Right-click again → no longer surprised.
- [ ] On someone not in the fight → "Add them first".
- [ ] With no fight → "Start a fight first".
- [ ] The DM's own `/combat damage` lands at once, never waits.
- [ ] `combat.damage_approval: off` in config → nothing ever asks you.

## Viewing & DM notes

- [ ] View tool, right-click a creature → chat card: name (size, type), HP, AC, speed, conditions,
      notes, [Full view] / [Adjust] / [Add a note].
- [ ] Hover a condition on the card → its rules, wrapped.
- [ ] View tool on your own character → race and class, concentration.
- [ ] Right-clicking with the View tool no longer opens the sheet directly; Full view has a button for it.
- [ ] A DM AC adjustment plus Shield → the card's AC explains both.
- [ ] A creature with its own AC → "own AC, stat block says 12".
- [ ] A downed character → "Down, dying — death saves: 1 ✔ / 2 ✖".
- [ ] A dead one → "☠ DEAD".
- [ ] Sneak + right-click a player → Full view with their real inventory; you can't take or move anything.
- [ ] Full view of a creature → what it carries, "Found with a DC 12 Investigation" / "In plain sight".
- [ ] Full view → [Character sheet] / [Stat block] opens it.
- [ ] Full view → [Adjust] opens the Adjust menu.
- [ ] Full view → [Add a note…] gives a fill-in line.
- [ ] `/dm view <who> full` opens the same Full view.
- [ ] `/dm note Balin add owes the party a favour` → shows on Balin's card, **after** his YAML `dm_notes`.
- [ ] `/dm note Balin` lists the notes.
- [ ] `/dm note Balin clear` removes only yours.
- [ ] Spawn one wolf, `/dm note Wolf add hungry`, View tool on that wolf → the note is there.
- [ ] `/dm view The Kindler` (unquoted) works.

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
- [ ] Spawn `wolf` twice → they're named "Wolf" and "Wolf #2".
- [ ] `/dm entity spawn kobold Meepo` twice → "Meepo" and "Meepo #2".
- [ ] `/dm entity rename ` + Tab → **every** creature (The Kindler, Alira, …), not just Balin.
- [ ] `/dm entity teleport ` + Tab → every creature.
- [ ] `/dm entity info ` + Tab → every creature.
- [ ] `/dm entity trade ` + Tab → only merchants.
- [ ] `/dm entity revive ` + Tab → only dead creatures.
- [ ] Names with spaces tab-complete in quotes (`"The Kindler"`).
- [ ] `/dm check <you> insight vs Balin deception` → your roll prompt, plus **[Roll it] / [I rolled…]** labelled "+1 CHA".
- [ ] Same, answered → the winner with [Share].
- [ ] Same, with `autoRoll` inline.
- [ ] A guard's Perception in a contest → `+2 Perception`.

## DM tools

Spawn alira as "The Kindler" first (`/dm entity spawn alira "The Kindler"`).

- [ ] `/dm adjust "The Kindler" hp -5` works.
- [ ] `/dm adjust The Kindler hp -5` works.
- [ ] `/dm check Balin Ironforge save dex` works (a creature now, not an error).
- [ ] `/dm check "Balin Ironforge" save dex` works.
- [ ] A creature check with no roll → the DM gets [Roll it] / [I rolled…].
- [ ] `/dm check Balin Ironforge save dex dc 12 autoRoll` → the result graded against the DC, with [Share with players].
- [ ] `/dm check Balin Ironforge skill perception` uses his Perception bonus.
- [ ] `/dm check clear Balin Ironforge` works.
- [ ] `/dm check <you> insight vs Balin Ironforge deception` works, quoted or not.
- [ ] `/dm resource restore "Balin Ironforge" rage` works.
- [ ] `/character delete "Balin Ironforge"` works.
- [ ] Spawn two goblins, rename both to "Snik" → `/dm adjust Snik hp -1` says "'Snik' could be … — name the one you mean".
- [ ] Two spawned goblins (Goblin, Goblin #2) → `/dm adjust Goblin 2 hp -1` hits #2; `Goblin` hits the first.
- [ ] `/dm rest <character> long` works.
- [ ] `/dm resource restore <character> all` works.
- [ ] `/dm resource consume <character> <res> 1` works.
- [ ] Annotate tool on a chest → the menu has **[Describe…]**, **[Trap…]**, **[Key…]**.
- [ ] Click **[Describe…]** → the chat bar holds `/dm object desc ` with the current description.
- [ ] Click **[Trap…]** → the chat bar holds `/dm object trap `.
- [ ] Click **[Key…]** → the chat bar holds `/dm object key `.
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

(`→` lines are Claude's status. New notes go at the top.)

**2026-09-23**

Human Monk Entertainer asks for "artisan tool or musical instrument" — correct?
  → yes. The PHB monk picks one artisan's tool or instrument, and the Entertainer picks an
    instrument too, so there are two picks.
Note which source gives each choice (I didn't know the monk gave stuff).
  → done: every choice header now starts with its source, "Monk: …", "Entertainer: …".
`/character cast thaumaturgy` doesn't tab-complete.
  → done: Tab offers the spells you know, then targets.
Expertise skills say "Proficient".
  → fixed: "✦ Expertise (proficiency ×2)".
Skill click: [normal] [advantage] [disadvantage] instead of a new menu.
  → done: clicking a skill, check or save puts those three buttons in chat. The menu is gone.
Skill hover should list the sources (Perception: +1 WIS, +2 prof).
  → done: the tile shows the breakdown, so the "show modifier" chat line is gone.
Put these in the character sheet redesign, or baby chunks?
  → baby chunks, as they come up; the three above are the first.
Persuasion says +4[CHA] +4[Expertise].
  → that's right: expertise doubles your +2 proficiency. Relabelled `+4[Prof ×2]` so it reads that way.
`/character check skill persuasion adv autoRoll` doesn't look like advantage.
  → it was rolling two d20s, but only showed the kept one. Now shows both, `[9, 15] advantage`.
Word-wrap condition texts.
  → done: Adjust menu, view card and turn-start hovers.
`/dm entity rename` only suggests Balin; Alira doesn't autofill.
  → fixed: rename/teleport/info were sharing the merchants-only list. Now every creature.
`/dm check Balin the Smith save dex` → "no character or player named…".
  → creatures now work: the DM gets [Roll it] / [I rolled…] with the creature's own bonus.
Two Meepos / two Wolves: the command hit the first, no "which one?".
  → fixed two ways: a new spawn with a taken name gets numbered ("Wolf #2"), and two identical
    names (after a rename) now ask instead of guessing.
Annotate tool: trap and desc clickable with the command filled in.
  → done: [Describe…] / [Trap…] / [Key…].
I can still open a locked chest (probably because I'm DM).
  → yes, by design. Lock tests moved to Part 2.
Right-click thieves' tools at a chest says "cannot use this type of focus".
  → fixed: the focus leaves chests and doors alone, and says nothing if you can't cast with it.
DM note on a wolf didn't show in the View tool.
  → almost certainly the other wolf (both were named "Wolf", and the note went to the first one).
    Numbered names fix it; there's a row in *Viewing & DM notes* to confirm.
Spellbook: make the spell name in chat hoverable with what it does.
  → done.

**2026-09-22 (evening)**

Already-known spell (high elf) is a gray pane and blends in.
  → now a knowledge book, for every already-known tile.
`/dm hp "The Kindler"` reads the quote as part of the name.
  → fixed. See *DM tools*.
Standardize names: one place for creature names, one for player names.
  → done. One reader (`NameUtil.readName`) and one finder per kind. A test fails the build if a
    command hands a raw word to a finder.
Fire Bolt on an NPC out of combat skips the attack roll and fills in `/dm hp`.
  → fixed (#152). See *Attacks outside a fight*.
`/dm hp` should use autoRoll / manualRoll / total. Why damage, heal *and* hp?
  → replaced by `/dm adjust`. See *The Adjust menu*.
Use the `/combat` commands out of combat, with "you're not in combat, OK?" for the DM.
  → done for attacks and spells.

---

# Known deferred

- Racial spell **uses** (a level-3 tiefling's Hellish Rebuke) aren't saved, so a restart refills
  them. Harmless at level 1; matters once level-up (#153) lands.
- Character-sheet inventory redesign (waiting until more content lands).
- Nicer default `material:` values for spellcasting foci and packs.
