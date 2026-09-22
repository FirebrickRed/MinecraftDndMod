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
- [ ] As non-DM: `/character create Bob` and `/character list all` are refused; `/character list` still lists your own.

## /roll
- [ ] As a **non-op player**: `/roll 2d6+3` works (it was op-only — an undefined permission node).
- [ ] `/roll 2d6+3` shows each die: `🎲 2d6+3: [4, 3] +3 = 10`. `/roll 1d0` → "Invalid dice format", no error.

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

## Equipment items (starting gear)
- [ ] Create a character; the granted items (Rapier, Dagger, Leather Armor, Flute, Entertainer's Pack, Dice Set) show **real vanilla items**, NOT purple/black boxes.
- [ ] Weapons look like a sword (ranged = bow) by default; set `material:` in a weapon's YAML to change the base item (e.g. `material: GOLD_INGOT`).
- [ ] Currency shows as ingots/nuggets; items with no real material fall back to paper (set `material:` to improve).

## Choices pane
- [ ] Skills/Tools/Languages/Equipment sub-tabs show a count ("Skills 1/2"), green ✓ when done.
- [ ] Hovering a sub-tab (e.g. Languages) lists what you have: granted (aqua), chosen (green) — e.g. Common (granted), Elvish.
- [ ] Each choice shows a "— Choose N more —" divider; groups are visually separated.
- [ ] A **selected** option reads as a **bold green "✔ <name>"** (obvious even if the glint is faint).
- [ ] **Automatic grants** (e.g. Common under Languages, background skills) appear as locked cyan tiles under "— Granted — automatic —". ⚠️ If Common does NOT appear, tell me — it means the language grant isn't being populated into the session (a data step, not the UI).

## Backgrounds, tools & duplicate proficiencies (2026-09-21)
- [ ] `/dm reload` → console shows **no** content-check warnings (the "N spells referenced but not defined" info line is expected).
- [ ] **Archaeologist** → a *Cartographer's or Navigator's Tools* pick appears under Tools (it used to be silently dropped).
- [ ] **Noble** → a *Gaming Set Proficiency* pick (4 sets) under **Tools**, not a dice item under Equipment.
- [ ] **Acolyte** → Prayer Book / Prayer Wheel pick. **Charlatan** → Tools of the Con pick, labelled with real names ("Ten Stoppered Bottles").
- [ ] **Rock gnome + Artificer** → *Replace duplicate Tinker's Tools* pick. The artificer's artisan's-tool pick and the archaeologist's tool pick (if taken) are **separate sections**, not one merged "choose 3".
- [ ] Pick the same tool in two tool sections → the second shows light green "Selected in another section"; clicking moves it.
- [ ] **High elf** still gets its *Wizard Cantrip* pick; its extra language + a background language merge into one "choose 2".
- [ ] Finish a character with chosen languages/tools → sheet's **Proficiency Bonus** tile lists Armor / Weapons / Tools / Languages with proper names ("Navigator's Tools", "Vehicles (Water)").
- [ ] **Restart the server** → the same character still lists its chosen languages and tools (they used to vanish).
- [ ] Background tile → shows *Choices:* and *Feature:*. Wildspacer shows *Feat: Tough (not applied yet…)*. Sheet's background item shows the feature text.
- [ ] **Guild Artisan / Folk Hero** → one *Artisan's Tools* pick; finish the character → you're proficient with that tool **and** the tool item is in your starting kit. **Entertainer** → same with an instrument.
- [ ] **Outlander** → instrument pick gives proficiency but **no** instrument item. **Soldier** → gaming-set proficiency pick *plus* a separate Dice / Cards gear pick.
- [ ] **Haunted One** → skill pick offers only Arcana / Investigation / Religion / Survival (choose 2); language pick offers only the 8 exotic languages.
- [ ] **Druid + Hermit** → *Replace duplicate Herbalism Kit* pick.
- [ ] **Dwarf + Guild Artisan** → pick Smith's Tools in the dwarf's tool section, then click it in the Guild Artisan section → it **moves** (light green "Selected in another section") instead of taking it twice.
- [ ] **Contested vs an NPC**: `/dm entity spawn balin_blacksmith`, then `/dm check <player> insight vs Balin deception` → the player gets their roll prompt, you get **[Roll it] / [I rolled…]** labelled "+1 CHA". Winner comes back with [Share]. Also try it inline with `autoRoll`, and a guard's Perception (`+2 Perception`, listed skill).
- [ ] **Rogue expertise**: rogue + Sage → the Expertise pick offers only skills you're proficient in (plus Thieves' Tools). Pick Stealth, then un-pick Stealth from class skills → finishing says "Expertise (not proficient in Stealth)". Sheet skill roll for Stealth shows `+4[Expertise]`.
- [ ] **Thieves' tools on a lock**: `/dm object lock …` a chest, player clicks [Open it] → your ping has **[Thieves' tools]** and a status line ("proficient (expertise), carrying them"). Click it, add a DC → the roll shows `+4[Thieves' Tools ×2]` for that rogue, just `+DEX` for a non-proficient character.
- [ ] **Thieves' tools break on a fail** (default `objects.thieves_tools_break: on_fail`): give a player 2 sets, call `tool thieves_tools dc 25` and fail → one set gone from the inventory, player and DMs told. Pass a DC 5 → nothing breaks. No DC → never breaks. Try `always` and `never` in config.yml (restart).
- [ ] **Armor proficiency (#209)**: a wizard puts on chain mail → a warning in chat, and the sheet's AC tile says "⚠ not proficient with Chain Mail". Then: a STR/DEX skill roll from the sheet says "(disadvantage)" and rolls 2d20-keep-lower; `/dm check <wizard> save dex` does too; a WIS check doesn't. In combat, a weapon attack gets "↯ disadvantage" with an "Armor (you)" reminder, initiative rolls with disadvantage, and `/combat cast` / `/character cast` refuse. Take it off → all normal. A fighter in the same armor → no penalty. A mountain dwarf wizard in scale mail (medium) → fine; chain mail (heavy) → penalty.
- [ ] **Combat survives a restart (#165)**: start a fight with a player and a kobold, roll initiative, get into round 2, give someone a condition, drop a player to 0. `/stop`, start the server. Check `plugins/jkvttplugin/CombatSessions/` has a file before restarting. On boot the console says "Restored combat … round 2, X's turn". Rejoin: the DM gets "Combat is still on: round 2, X's turn"; players see the initiative scoreboard again; the downed player is prone again; the condition is still listed. The current combatant can act (attack → damage works, no errors). A barbarian who was raging still is (sheet shows it, slashing damage is halved, red tint back). `/combat finished` → the file is gone.
- [ ] **Sheet adv/dis in physical-dice mode**: click a skill → "Roll with advantage" → the filled command has `adv` in it and the result shows two d20s (this used to roll normal silently).
- [ ] **Keys (#200)**: look at a chest, `/dm object key brass_key` → "The Brass Key opens the Chest (locked it)". A player without the key clicks [Open it] → locked, and your ping adds "Opens with: Brass Key — they aren't carrying it". `/dm give <player> brass_key`, they click [Open it] → it opens, you and anyone nearby see "X unlocks the chest with the Brass Key", they keep the key, and the chest opens normally for everyone after. Repeat with `key iron_key single-use` → the key is gone from their inventory. A trapped chest with a key still springs its trap first. `key` on a sealed block refuses.
- [ ] **Tiefling** → Thaumaturgy is castable (it used to count as a 0-use leveled spell). Same for forest gnome's Minor Illusion.

## Playtest fixes (2026-09-22)
- [ ] **Menus:** in character creation, double-click an option → it toggles **once** (it used to select and
      straight back off). Double-click / drag a glass pane → nothing ends up on your cursor, not even for a flicker.
- [ ] **Bows, out of combat:** right-click with a shortbow at open sky → it doesn't draw, no arrow fires, the arrow
      stays in your inventory, and the action bar says it's for combat **right away**. Same aiming at a block.
- [ ] **Bows, in combat:** on your turn, left-click toward a distant enemy (not touching them) → you get the
      filled-in `/combat attack` prompt. (Clicks at open sky used to be ignored.)
- [ ] **Content items keep their vanilla hands off:** right-click the *Map of Your Home City* → it stays that item
      (it used to turn into a random filled map). Same for flint and steel (no fire), a potion item (not drunk —
      healing potions still go through their prompt), a bottle (doesn't fill). Chests still open with one in hand.
      Armor still equips by right-click; a shield still raises.
- [ ] **Magic weapon tooltip:** `/dm give <you> longsword_plus_2` → the yellow description wraps across lines
      instead of running off the screen. Same for long armor/item descriptions.
- [ ] **`/dm give` order:** Tab after `/dm give ` → players only; next → items; next → amounts. `/dm give longsword`
      (no player) → usage, not "gave it to you". Full inventory → the rest drops at their feet.
- [ ] **Checks show the work:** `/dm check <player> save dex` → both you and the player see
      `d20(16) +3[DEX] +2[Prof] = 21`, not just 21. [Share] shares the same line. Contested checks show it too.
- [ ] **Contested, multi-word names:** `/dm check <player> insight vs Balin Ironforge deception` works (and with
      the name in quotes). `/dm check <player> insight vs Balin deception` still works.
- [ ] **`/dm list` with every DM offline** → lists the offline ops as `[OP] name (Offline)` instead of "No DMs".
- [ ] `/dm entity spawn <creature with hit_dice>` → the DM sees the HP dice it rolled.
- [ ] `/combat add <someone>` mid-fight → the table sees their initiative roll, not just a new row.
- [ ] `/dm resource restore "Balin Ironforge" rage` and `/character delete "Balin Ironforge"` → quoted names work.

## Entities move under /dm (2026-09-22)
- [ ] `/dmentity` → "Unknown command". `/dm ` + Tab no longer autocompletes into it.
- [ ] `/dm entity spawn balin_blacksmith`, `/dm entity list`, `/dm entity info Balin`, `/dm entity trade Balin`,
      `/dm entity shop view Balin`, `/dm entity remove Balin` all work as before, with Tab at each step.
- [ ] `/dm entity spawn town_guard "Marcus the Brave" ~ ~ ~` → named Marcus the Brave, spawned at you.
- [ ] `/dm entity rename "Marcus the Brave" "Marcus the Bold"` → renamed; an unquoted two-word rename still
      refuses rather than guessing.
- [ ] Buttons the game fills in (loot, possession, shop prompts) all say `/dm entity …` and work when clicked.

## Every game roll shows its dice (2026-09-22)
- [ ] `/combat damage <t> autoRoll 2d6` → "🎲 2d6: [4, 3] = 7" before the damage line; `/combat heal <t> autoRoll 2d4` same.
- [ ] `/dm hp <who> damage 2d10` → shows the dice. A flat `/dm hp <who> damage 7` doesn't pretend to roll.
- [ ] Drink a healing potion with auto-roll on → the dice show. Cure Wounds cast with auto-roll → the dice show.
- [ ] `/combat rollforinitiative` → each line shows `[d20] +N (DEX) = total`; a character in unproficient
      armor shows `[disadvantage: a/b]` (this path used to skip the armor rule entirely).

## Save location
- [ ] New characters save under **`plugins/jkvttplugin/Saved/Characters/`**.

## Known deferred (not in this build)
- Character-sheet inventory redesign (waiting until more content lands).
- Unify the class-resource nested `icon:` (a sheet-display Material) into the `material:` naming.
- Give spellcasting foci / packs nicer default `material:` values.

## playtest notes:
(`→` lines are Claude's status for each note.)

My player when in the character creation menu keeps trying to steal the glass panes, it doesn't let him keep the glass panes but is there a way to prevent them from double clicking the glass panes
  → fixed, see "Playtest fixes → Menus".
bows used out of combat still use ammo and the command prompt doesn't show until after the arrow is used. 
  → fixed (a click at open sky skipped the bow guard), see "Playtest fixes → Bows".
we should also have a path where we can remove character creation paper from a player if the command get's run accidently.
  → the real bug was Q opening creation; fixed, see "/character (player)".
/character give does not work, well at least I can't give my character to my brother. but if I give him one of his character sheet's it works. 
  → now a DM-confirmed hand-over, see "/character (DM forms)".
I'm not quite sure what /close is supposed to do it says it ran but it doesn't look like anything happens
  → it only saved, and saving is automatic now; removed.
also not sure how we want to handle one player having 2 character sheets. like the player get's both sets of equipment and not sure which goes to which. 
  → ticketed: #213 (per-character inventories).
/character list all only works for dm, as a non dm /character list sends an error. 
  → that message is `/character list all` being DM-only, by design; the wording now points at `/character list`.
    The real find next to it: `/roll` was op-only (an undefined permission node), now fixed.
/roll 2d6+3 works but can we have the message show not just the end result of 10 (what I got) Can we show what each dice rolled then a +3 and then a = 10
  → fixed, see "/roll".
do we want to move /dmentity to /dm entity? give me your thoughts on this, I only get annoyed because I go to type /dm and it wants to finish autofilling it to /dmentity
  → done: /dmentity is gone, entities live under /dm entity.
/dm give autofills item and player on the next input, maybe we make player neccissary next? then item
  → fixed, player is required and first.
can we also word wrap the yellow text on magical sword. 
  → fixed (weapons, armor and items).
my player right clicked a map and it turned it into a different map
  → fixed for every content item, see "Content items keep their vanilla hands off".
I prompted for a dex save and can we show the work of the save not just 21. if that makes sense. 
  → fixed, see "Checks show the work".
/dm list for a player when all dm's are offline shows no dm's currently assigned (not sure if this is expected behavior)
  → fixed, offline ops are listed.
My dmcontent/saved/characters is empty when there should be characters in there... 
  → expected: characters moved to plugins/jkvttplugin/Saved/Characters/ (see "Save location"). The old folder is stale.
this command breaks with spaces in the name, the name being "Balin Ironforge" instead of "balin" /dm check <player> insight vs Balin deception
  → fixed, see "Contested, multi-word names".
