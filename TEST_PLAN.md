# Test Plan — command consolidation, icons, saves

Rebuild + deploy the jar first (`gradlew build` — it runs the automated tests too, and refuses to
build if one fails — then copy `build/libs/*.jar` to the server's
`plugins/`, restart or reload). Load the resource pack so icon checks are meaningful.

Legend: run as a **DM** (op) unless noted; for "non-DM" rows use a second account or `/deop`.
Checked-off rows are deleted once they pass — git history has them, and anything that could break
again belongs in the automated tests (`src/test/java`), not in a stale checkbox here.

## /character (player)
- [ ] `/character create` → creation menu opens.
- [ ] `/character rest short` and `/character rest long` recover as before.
- [ ] Press **Q** with the sheet or the Create Character paper in hand → it drops; nothing opens.

## /character (DM forms)
- [ ] `/character give <player> <name>` → player receives the sheet paper.
- [ ] `/character give <brother> <your character>` → you get **[Give <name> to <brother>]**; click it → the
      character is now theirs (`/character list` as them shows it, their paper opens it), your paper for it is
      gone, and you're told the gear stayed with you. Refused while either of you is in a fight. Restart → still theirs.
- [ ] `/character list all` → every saved character with `(owner)`.
- [ ] As non-DM: `/character create Bob` is refused. `/character list all` lists **your own** characters (not a refusal), same as `/character list`; Tab offers `all` but no player names.

## /roll
- [ ] As a **non-op player**: `/roll 2d6+3` works (it was op-only — an undefined permission node).

## /dm
- [ ] `/dm rest <character> long`; `/dm resource restore <character> all`; `/dm resource consume <character> <res> 1`.
- [ ] **Death (#101):** in combat, down a player and fail three death saves → "has DIED", turn skipped.
      `/combat finished`, start a new fight with them → still `[DEAD]`, still skipped. `/dm hp <c> heal 10`,
      `/character rest long` and `/dm rest <c> long` all refuse. Character sheet HP slot shows a skull "DEAD".
      Restart the server → still dead. `/dm revive <c>` → back at 1 HP; mid-fight their turns return.
- [ ] Massive damage: `/dm hp <c> damage <current HP + max HP>` → dies outright, no saves.
- [ ] Dying carries over: fail one save, `/combat finished`, new fight → tally still shows 1 failure.
- [ ] `/dm entity revive <creature>` mid-fight → the creature's turns come back.
- [ ] **The body:** a dead character leaves a tipped-over head named "☠ <name>" where they fell. Right-click it
      as another player → "The body of …" + [Ask for a check] → DM gets a [call a check] ping (medicine /
      investigation / religion suggested). As DM → also [Revive] and [Remove body]. Can't punch it or take the head.
      Restart → body still there.
- [ ] **Spectator:** the dead character's player (in adventure mode) is put in spectator mode with a message.
      `/dm revive <c>` → back in **adventure**, body gone, and the DM gets **[Teleport them to the body]**
      (no automatic teleport). Click it → the player lands at the body.
- [ ] Dead player runs `/character create` → back in adventure mode, creation menu opens and works.
- [ ] A DM already in spectator mode for their own reasons isn't pulled out of it by any of this.
- [ ] Walk far away (chunk unloads), `/dm revive <c>`, walk back → the stale body is gone when the chunk loads.
- [ ] **Sheets are private:** as a non-DM, `/character view <someone else's character>` → "You can only view your
      own characters"; Tab only suggests your own. `/character view <your other character>` opens it. Right-click
      someone else's sheet paper → "This isn't your character sheet" (and it doesn't become your active character).
- [ ] **Deleting needs the DM:** as a player, `/character delete <yours>` → "Asked the DM"; DM gets [Approve] / [Deny].
      Deny → player told, character kept. Approve → gone from `/character list`, and the file is in
      `plugins/jkvttplugin/Saved/Characters/Deleted/` (not erased). No DM online → refused.
      As DM, `/character delete <name>` deletes straight away (still archived).
- [ ] **Long rest at 0 HP** (stable, not dead) → refused, "needs at least 1 HP". `/dm hp <c> heal 1` → now works.
- [ ] **Temp HP & Relentless Endurance survive a restart:** `/dm hp <c> temp 7`, restart → still 7 temp HP.
      Half-orc drops to 0 → held at 1 by Relentless; restart; drop them again → this time they fall.
- [ ] Tab: `/dm ` shows add/remove/list + give/check/rest/resource/reload; `/dm resource ` shows restore/consume.

## Choices pane
- [ ] A **selected** option (glass pane *or* a real item like a Rapier) → its name turns **bold green "✔ <name>"**
      and the line right under the name says "✔ Selected — click to deselect" (it used to sit at the bottom of the
      tooltip). Same on the Spells tab. Selected in another section → yellow name, "Selected in another section" first.
- [ ] A long tool list (rock gnome artificer → Tools) → **◀ Previous page / Page 1 of 2 / Next page ▶** in the bottom
      row. Page 2 opens with the section's header marked "(continued)". Switching sub-tab goes back to page 1.
- [ ] Options are **alphabetical** (skills, tools, languages, and the high elf's Wizard Cantrip). Equipment keeps its order.
- [ ] **Rogue Expertise** header is one pane: "Expertise — Choose 2 more"; hover it → "Only skills and tools you're
      proficient in are offered". No separate gray pane under it.

## Backgrounds, tools & duplicate proficiencies (2026-09-21)
- [ ] **Rock gnome + Artificer** → Tinker's Tools shows **once** under Granted, "From: Rock Gnome + Artificer", with a
      yellow "Granted twice…" note. The *Replace duplicate Tinker's Tools* pick offers **every** tool you don't have
      (artisan's tools, instruments, gaming sets, vehicles), paging onto page 2. It used to show only the 3 vehicles:
      the pane ran out of slots. The artificer's artisan's-tool pick and the archaeologist's tool pick (if taken) are
      **separate sections**, not one merged "choose 3".
- [ ] Pick the same tool in two tool sections → the second shows light green "Selected in another section"; clicking moves it.
- [ ] **Restart the server** → the same character still lists its chosen languages and tools (they used to vanish).
- [ ] **Guild Artisan / Folk Hero** → one *Artisan's Tools* pick; finish the character → you're proficient with that tool **and** the tool item is in your starting kit. **Entertainer** → same with an instrument.
- [ ] **Outlander** → instrument pick gives proficiency but **no** instrument item. **Soldier** → gaming-set proficiency pick *plus* a separate Dice / Cards gear pick.
- [ ] **Contested vs an NPC**: `/dm entity spawn balin_blacksmith`, then `/dm check <player> insight vs Balin deception` → the player gets their roll prompt, you get **[Roll it] / [I rolled…]** labelled "+1 CHA". Winner comes back with [Share]. Also try it inline with `autoRoll`, and a guard's Perception (`+2 Perception`, listed skill).
- [ ] **Rogue expertise**: rogue + Sage → the Expertise pick offers only skills you're proficient in (plus Thieves' Tools). Pick Athletics as a class skill and as expertise, then un-pick Athletics from class skills → the expertise pick is dropped too, and chat says "Expertise in Athletics removed: you're no longer proficient in it". Sheet skill roll for an expertise skill shows `+4[Expertise]`.
- [ ] **Thieves' tools on a lock**: `/dm object lock …` a chest, player clicks [Open it] → your ping has **[Thieves' tools]** and a status line ("proficient (expertise), carrying them"). Click it, add a DC → the roll shows `+4[Thieves' Tools ×2]` for that rogue, just `+DEX` for a non-proficient character.
- [ ] **Thieves' tools break on a fail** (default `objects.thieves_tools_break: on_fail`): give a player 2 sets, call `tool thieves_tools dc 25` and fail → one set gone from the inventory, player and DMs told. Pass a DC 5 → nothing breaks. No DC → never breaks. Try `always` and `never` in config.yml (restart).
- [ ] **Armor proficiency (#209)**: a wizard puts on chain mail → a warning in chat, and the sheet's AC tile says "⚠ not proficient with Chain Mail". Then: a STR/DEX skill roll from the sheet says "(disadvantage)" and rolls 2d20-keep-lower; `/dm check <wizard> save dex` does too; a WIS check doesn't. In combat, a weapon attack gets "↯ disadvantage" with an "Armor (you)" reminder, initiative rolls with disadvantage, and `/combat cast` / `/character cast` refuse. Take it off → all normal. A fighter in the same armor → no penalty. A mountain dwarf wizard in scale mail (medium) → fine; chain mail (heavy) → penalty.
- [ ] **Combat survives a restart (#165)**: start a fight with a player and a kobold, roll initiative, get into round 2, give someone a condition, drop a player to 0. `/stop`, start the server. Check `plugins/jkvttplugin/CombatSessions/` has a file before restarting. On boot the console says "Restored combat … round 2, X's turn". Rejoin: the DM gets "Combat is still on: round 2, X's turn"; players see the initiative scoreboard again; the downed player is prone again; the condition is still listed. The current combatant can act (attack → damage works, no errors). A barbarian who was raging still is (sheet shows it, slashing damage is halved, red tint back). `/combat finished` → the file is gone.
- [ ] **Sheet adv/dis in physical-dice mode**: click a skill → "Roll with advantage" → the filled command has `adv` in it and the result shows two d20s (this used to roll normal silently).
- [ ] **Keys (#200)**: look at a chest, `/dm object key brass_key` → "The Brass Key opens the Chest (locked it)". A player without the key clicks [Open it] → locked, and your ping adds "Opens with: Brass Key — they aren't carrying it". `/dm give <player> brass_key`, they click [Open it] → it opens, you and anyone nearby see "X unlocks the chest with the Brass Key", they keep the key, and the chest opens normally for everyone after. Repeat with `key iron_key single-use` → the key is gone from their inventory. A trapped chest with a key still springs its trap first. `key` on a sealed block refuses.
- [ ] **Tiefling** → Thaumaturgy is castable (it used to count as a 0-use leveled spell). Same for forest gnome's Minor Illusion.
      **Tiefling rogue** (not a caster class): in leather armor, `/character cast thaumaturgy` works. It used to say
      "Teef doesn't know Thaumaturgy", because the cast paths only checked class spell lists and skipped racial
      spells. In combat, `/combat cast thaumaturgy <target>` works too and uses CHA.

## Playtest fixes (2026-09-22)
- [ ] **Bows, in combat:** on your turn, left-click toward a distant enemy (not touching them) → you get the
      filled-in `/combat attack` prompt. (Clicks at open sky used to be ignored.)
- [ ] **Checks show the work:** `/dm check <player> save dex` → both you and the player see
      `d20(16) +3[DEX] +2[Prof] = 21`, not just 21. [Share] shares the same line. Contested checks show it too.
- [ ] **Contested, multi-word names:** `/dm check <player> insight vs Balin Ironforge deception` works (and with
      the name in quotes). `/dm check <player> insight vs Balin deception` still works.
- [ ] **`/dm list` with every DM offline** → lists the offline ops as `[OP] name (Offline)` instead of "No DMs".
- [ ] `/combat add <someone>` mid-fight → the table sees their initiative roll, not just a new row.
- [ ] `/dm resource restore "Balin Ironforge" rage` and `/character delete "Balin Ironforge"` → quoted names work.

## Entities move under /dm (2026-09-22)
- [ ] Buttons the game fills in (loot, possession, shop prompts) all say `/dm entity …` and work when clicked.

## Every game roll shows its dice (2026-09-22)
- [ ] `/combat damage <t> autoRoll 2d6` → "🎲 2d6: [4, 3] = 7" before the damage line; `/combat heal <t> autoRoll 2d4` same.
- [ ] `/dm hp <who> damage 2d10` → shows the dice. A flat `/dm hp <who> damage 7` doesn't pretend to roll.
- [ ] Drink a healing potion with auto-roll on → the dice show. Cure Wounds cast with auto-roll → the dice show.
- [ ] `/combat rollforinitiative` → each line shows `[d20] +N (DEX) = total`; a character in unproficient
      armor shows `[disadvantage: a/b]` (this path used to skip the armor rule entirely).

## Playtest fixes, round 2 (2026-09-22)
- [ ] `/dm entity trade ` + Tab → only merchants (Balin), not every creature.
- [ ] `/dm entity list` → each line ends "in world" / "in world_nether"; a creature in another dimension than
      you is yellow "(not your world)".
- [ ] `/dm entity spawn <creature with hit_dice>` → the dice line has **[Use my own roll]**; click → chat fills
      `/dm entity maxhp <name> `; type 15 → "max HP 11 → 15 (now 15/15)". `/dm entity list` agrees. On a hurt
      creature, current HP stays put (capped at the new max).
- [ ] `/roll ` + Tab → no player names.
- [ ] Thieves' tools tooltip says "Spellcasting focus for: Artificer" / "(Artificer only)", not a bare "Spellcasting Focus".
- [ ] **High elf wizard** → pick Acid Splash as the Wizard Cantrip (Choices) → on the Spells tab Acid Splash is a gray
      pane "Already known from Wizard Cantrip". The other way round: pick it on the Spells tab first → the Wizard
      Cantrip pick shows it gray "Already known".
- [ ] Actually **finish** a character (so far everything was checked inside creation) and look at the sheet.

## Playtest notes
(`→` lines are Claude's status for each note. Add new notes at the bottom.)

`/dm entity trade` autofills every entity's name.
  → fixed, merchants only. There's no use for it on a non-merchant; it just fails with "is not a merchant".
`/dm entity list` doesn't say which world/dimension.
  → fixed, see "round 2".
A button to override auto-rolled entity HP with a real roll.
  → added [Use my own roll] → `/dm entity maxhp`, see "round 2".
`/roll` autofills player names.
  → fixed (Bukkit suggests players when a command has no tab completer).
Selected item's text should change too; move "selected" under the name; both green.
  → done, see "Choices pane". Real items stay real items (not glass panes); the name + status line carry it.
Rogue: one pane for "choose 2 more" + the proficient-only note.
  → done, the note is the header's hover text.
Is thieves' tools supposed to be a spellcasting focus?
  → yes, for the **Artificer** only (TCoE p.10: they cast through thieves' or artisan's tools). The tooltip
    now says so instead of implying everyone can.
Rock gnome + artificer: Tinker's Tools twice in Granted, plus a prompt to pick another; only the 3 vehicles offered.
  → the extra pick is correct: PHB p.125 says the same proficiency from two sources lets you choose a different
    one instead. Showing it twice wasn't: it's now one tile naming both sources. Only vehicles showed because the
    pane has 27 slots and that pick came last, so it got cut off after 3 options. Added paging.
Haven't actually finished a character yet.
  → added a row in "round 2".
Alphabetize the high elf's wizard cantrip list.
  → done, and every non-equipment choice list.
Acid Splash selectable as both the high elf cantrip and a wizard cantrip.
  → fixed both ways, see "round 2". (Knowing a cantrip twice does nothing in 5e, so the second pick would be wasted.)
Expertise stays after un-picking the proficiency under it.
  → fixed, the expertise drops with it (it used to only block Finish).
Tiefling rogue: "Teef doesn't know Thaumaturgy".
  → fixed. Cast paths only checked class spell lists, never racial spells. Same fix covers `/combat cast`.

## Known deferred (not in this build)
- A high elf **non-wizard** casting their racial wizard cantrip uses their class's ability (or none, for a
  rogue) instead of INT: the pick is stored with class cantrips, so its source is lost. Needs the pick to
  become an innate spell with `casting_ability: intelligence`.
- Character-sheet inventory redesign (waiting until more content lands).
- Unify the class-resource nested `icon:` (a sheet-display Material) into the `material:` naming.
- Give spellcasting foci / packs nicer default `material:` values.

