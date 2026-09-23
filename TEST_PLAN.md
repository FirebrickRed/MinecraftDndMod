# Test Plan

**Before you start:** `gradlew build` (it runs the automated tests and refuses to build if one
fails), copy `build/libs/*.jar` into the server's `plugins/`, restart. Load the resource pack.

**How this is laid out**

- **Part 1** is everything you can do alone, as the DM, with your own character.
- **Part 2** needs a second account that is **not** a DM (a friend, or a second account you `/deop`).
- Inside each part, rows are grouped by area: creation, finished character, combat, entities and
  shops, DM tools. **Restart checks** are batched at the end of Part 1 so you restart once.
- Tick a row with `[X]`. Ticked rows get deleted at the next round (git keeps the history).
- Notes go in **Playtest notes** at the bottom, newest on top.

---

# Part 1 — You alone, as the DM

## Character creation

- [ ] `/character create` → the creation menu opens.
- [ ] **Selected options** (a glass pane *or* a real item like a Rapier) → the name turns
      **bold green "✔ Name"**, and the first line under it says "✔ Selected — click to deselect".
      Same on the Spells tab. Picked in another section → yellow name, "Selected in another
      section" first.
- [ ] **Already-known options** (a language you have, a cantrip you took elsewhere) show as a
      **red knowledge book**, not a gray pane, so they don't vanish into the background.
- [ ] **Paging:** Rock Gnome Artificer → Tools tab → ◀ Previous page / Page 1 of 2 / Next page ▶
      in the bottom row. Page 2 starts with the section header marked "(continued)". Switching
      sub-tab goes back to page 1.
- [ ] **Alphabetical:** skills, tools, languages and the high elf's Wizard Cantrip are A→Z.
      Equipment keeps its order.
- [ ] **Rogue Expertise** is one header pane: "Expertise — Choose 2 more". Hover it → "Only
      skills and tools you're proficient in are offered".
- [ ] **Rock Gnome + Artificer:** Tinker's Tools shows **once** under Granted ("From: Rock Gnome +
      Artificer") with a yellow "Granted twice…" note. The *Replace duplicate Tinker's Tools*
      pick offers **every** tool you don't have, including artisan's tools, instruments and
      gaming sets. The artificer's pick and an Archaeologist's tool pick stay **separate**
      sections.
- [ ] Pick the same tool in two tool sections → the second shows "Selected in another
      section"; clicking it there moves the pick.
- [ ] **High elf wizard:** pick Acid Splash as the Wizard Cantrip → on the Spells tab it's a
      knowledge book, "Already known from Wizard Cantrip". The other way round works too.
- [ ] **Astral elf** (any class): the Extra tab has "Astral Fire Cantrip: Spellcasting Ability"
      (Intelligence / Wisdom / Charisma), and Finish waits for it.
- [ ] **Backgrounds:**
  - Guild Artisan / Folk Hero → one *Artisan's Tools* pick. Entertainer → same with an instrument.
  - Outlander → instrument pick, proficiency only (no item after finishing).
  - Soldier → gaming-set proficiency pick **plus** a separate Dice / Cards gear pick.

## Finished character

Finish each of these combos, then check the sheet (Proficiency Bonus tile's tools and
languages, spells, AC) and your inventory.

- [ ] **Tiefling Rogue, Urchin**
  - `/character cast thaumaturgy` works out of combat (it used to say "doesn't know").
  - Breastplate on → casting refused. Leather back on → casts.
  - Right-click thieves' tools → "You cannot use this type of focus!"
  - A sheet skill roll for an expertise skill shows `+4[Expertise]`.
- [ ] **Rock Gnome Artificer, Sage**
  - The tool you picked as the Tinker's Tools replacement is on the sheet.
  - Right-click thieves' tools → the spell menu opens.
- [ ] **High Elf Wizard, Noble**
  - Wizard Cantrip + class cantrips, no double pick. Both languages and the gaming set on the
    sheet. Spells tab picks are in the spellbook.
- [ ] **High Elf Rogue** (any background)
  - Pick Fire Bolt as the Wizard Cantrip → `/character cast fire_bolt` works and uses INT.
- [ ] **Astral Elf** (any class): pick Sacred Flame + Wisdom → `/combat cast sacred_flame <target>`
      uses your WIS for the save DC.
- [ ] **Mountain Dwarf Fighter, Guild Artisan**
  - The artisan's-tool pick gives the proficiency **and** the tool item.
  - Heavy armor, no penalty.
- [ ] **Guild Artisan / Folk Hero / Entertainer:** the picked tool or instrument is in your kit.
- [ ] `/character rest short` and `/character rest long` recover as before.
- [ ] Press **Q** holding the sheet or the Create Character paper → it drops; nothing opens.
- [ ] **Armor proficiency:** a wizard in chain mail →
  - a chat warning, and the sheet's AC tile says "⚠ not proficient with Chain Mail";
  - STR/DEX skill rolls from the sheet say "(disadvantage)"; `/dm check <wizard> save dex` too;
    a WIS check doesn't;
  - take it off → all normal. A fighter in it → no penalty. Mountain dwarf wizard: scale mail
    fine, chain mail penalized.
- [ ] **Sheet adv/dis with physical dice:** click a skill → "Roll with advantage" → the filled
      command has `adv`, and the result shows two d20s.

## Combat

- [ ] **Bows:** on your turn, left-click toward a distant enemy (not touching them) → you get the
      filled-in `/combat attack`.
- [ ] **Armor penalty in a fight** (wizard in chain mail): a weapon attack shows "↯ disadvantage"
      with an "Armor (you)" reminder; initiative rolls with disadvantage; `/combat cast` refuses.
- [ ] `/combat add <someone>` mid-fight → the table sees their initiative roll.
- [ ] `/combat rollforinitiative` → each line shows `[d20] +N (DEX) = total`. Unproficient armor →
      `[disadvantage: a/b]`.
- [ ] **Dice show:** `/combat damage <t> autoRoll 2d6` → "🎲 2d6: [4, 3] = 7" before the damage.
      Same for `/combat heal <t> autoRoll 2d4`, a healing potion with auto-roll, Cure Wounds.
- [ ] `/combat damage` / `/combat heal` with `manualRoll 7` and with a bad value (`manualRoll abc`)
      → sensible messages.
- [ ] **Scoreboard, setup:** combatants in the order added, no numbers on the right, "Add
      combatants..." at the bottom. Two creatures with the same name → two lines.
- [ ] **Scoreboard, in the fight:**
  - green **→** on the current turn; initiative as the red number;
  - players' HP green / yellow / red below ½ and ¼; `+N` temp HP in aqua;
  - **[S]** surprised; downed → red ☠ and green/red death-save dots; **[DEAD]**;
  - purple condition tag; pink ✦N while channelling a ritual;
  - two tied initiatives in turn order; "Round: N" at the bottom with no number.
- [ ] **Death:** down yourself, fail three death saves → "has DIED", turn skipped.
  - `/combat finished`, new fight → still `[DEAD]`, still skipped.
  - `/dm adjust <you> hp +10`, `/character rest long`, `/dm rest <you> long` all refuse.
  - The sheet's HP slot shows a skull "DEAD". `/dm revive <you>` → 1 HP, turns return.
- [ ] **Massive damage:** `/dm adjust <c> hp -<current + max HP>` → dies outright.
- [ ] **Dying carries over:** fail one save, `/combat finished`, new fight → still 1 failure.
- [ ] `/dm entity revive <creature>` mid-fight → its turns come back.
- [ ] **The body:** a dead character leaves a tipped-over head "☠ <name>" where they fell.
      As DM, right-click it → [Revive] and [Remove body]. Can't punch it or take the head.
- [ ] Walk away until the chunk unloads, `/dm revive <c>`, walk back → the body is gone.
- [ ] **Long rest at 0 HP** (stable, not dead) → refused, "needs at least 1 HP". `/dm adjust <c> hp +1`
      → now works.

## Attacks outside a fight (new)

- [ ] **Fire Bolt at a creature, out of combat:** `/character cast fire_bolt The Kindler` (or leave
      the name off and look at her). You're told "asking the DM"; you get **[Start combat]**,
      **[Let it happen]**, **[Deny]**. No slot is spent yet.
  - **Deny** → the caster is told; nothing happens.
  - **Let it happen** → the caster gets roll buttons; the attack roll shows `vs AC`; on a hit,
    roll damage (`/character damage`), and her HP drops. A miss ends it.
  - **Start combat** → a fight opens in setup with both of you. Add someone else, mark someone
    surprised, roll initiative. On the caster's first turn: "Your opening move: Fire Bolt at The
    Kindler [do it]".
- [ ] **A save spell** (Sacred Flame) with Let it happen → the DM sees the DC, **[Call the save]**
      (characters), **[Failed: damage]** / **[Saved: …]**.
- [ ] **At a thing:** look at a torch or a wall, `/character cast fire_bolt` with no name → "You're
      not aiming at a creature. Cast it anyway?" → **[Cast it]** → roll → the DM sees "N to hit"
      and "they're looking at the wall torch" (plus its annotation, if it has one) and **[Ask for
      damage]**. Damage rolled at a thing hurts nobody.
- [ ] **Healing:** `/character cast cure_wounds <someone>` → roll prompt → they heal. No DM
      prompt. Too far away → "about N ft away" refusal.
- [ ] **A weapon:** left-click a creature with a sword out of combat → the DM gets **[Start
      combat]** / **[Deny]** (no one-off for weapons). The creature takes no damage from the click.
- [ ] **Utility spells** (Light, Thaumaturgy) still just announce.
- [ ] The spellbook, out of combat, fills `/character cast …`, and its hover explains the above.

## The Adjust menu & `/dm adjust` (new)

- [ ] `/dm mode` → the **Adjust** tool (blaze rod) is on the main toolbar and both pages. Right-click
      a creature or a player → "Adjust: <name>". Right-clicking nothing (or a cow) → an action-bar hint.
- [ ] **HP tile:** click → +1, right-click → −1, shift → ±5. In a fight, the scoreboard and the
      player's open sheet update. **Set HP exactly…** / **Temp HP…** / **Max HP…** (creatures)
      close the menu and give you a [click to type the number] line.
- [ ] **Drop to 0 HP:** a creature dies; a character goes down and starts death saves. On a dead one,
      **Revive (1 HP)** appears and works.
- [ ] **AC, temporary:** click the AC tile with no adjustment yet → "for how long?" (next turn only
      in a fight). Pick "until a long rest" → AC +1, and the sheet's AC tile says "DM: +1 (until a
      long rest)". Click again → +2 (keeps the duration). Right-click → back down. **Clear** removes it.
      A long rest ends it; "until you remove it" survives rests.
- [ ] **AC, permanent (creatures):** shift-click → the creature's own AC goes up by 1 for good, and
      the tile says "Own AC 13 (stat block says 12)". **Back to the stat block's AC** undoes it. On a
      player, shift-click says their AC comes from armor.
- [ ] **Conditions:** click Poisoned → it glows, "✔ Poisoned", the table (or the player and you) is
      told. Click again → removed. Blinded on a player → they get the blindness effect.
- [ ] **Typed:** `/dm adjust The Kindler hp 12` (set), `hp +5`, `hp -2d6 type fire`, `full`,
      `temp 5`, `maxhp 30`, `ac +1 until short_rest`, `ac +1` (asks how long), `ac set 16`,
      `ac reset`, `condition poisoned` (toggle), `condition add prone`, `down`, `revive`.
      Tab completes each step.
- [ ] **Gone:** `/dm hp`, `/combat damage override`, `/combat condition`, `/dm entity maxhp` and
      `--force` are unknown now. A trap's **[Apply damage]** fills `/dm adjust … hp -…`. A spawn's
      **[Use my own roll]** fills `/dm adjust … maxhp`.
- [ ] **Out of range, as DM** (possessing a creature, `/combat attack` at something too far) →
      "... out of range [Attack anyway]" → click → the attack goes ahead.

## Surprise tool & damage approval (new)

- [ ] DM combat toolbar → **Surprised (ambush)** (firework star) in slot 6; hover it → what Surprised
      means and when to use it. Start a fight, add a goblin, right-click it with the tool → "Goblin is
      Surprised: …", **[S]** on the tracker. Right-click again → no longer surprised. On someone not in
      the fight → "Add them first". With no fight → "Start a fight first".
- [ ] Out-of-combat Fire Bolt → [Start combat] → the hint mentions the Surprise tool.
- [ ] **Damage approval (default on):** as a player, hit and `/combat damage … autoRoll` → "Sent to
      the DM: 7 fire damage to Goblin." Nothing changes yet. The DM gets **[Apply]** / **[Deny]**.
  - Apply → the goblin takes it, the tracker updates, the hit can't be applied twice.
  - Deny → the player is told to roll again; `/combat damage` works again for the same hit.
  - While it's waiting, a second `/combat damage` says it's with the DM.
  - End the turn with it still waiting → clicking Apply later says the moment has passed, and next turn
    the player's damage isn't blocked.
  - The DM's own `/combat damage` (a creature's hit) applies straight away.
  - `combat.damage_needs_dm_approval: false` in config.yml (restart) → players' damage applies at once.

## Viewing & DM notes (new)

- [ ] **View tool, right-click** a creature → a chat card: name (size, type), HP, AC, speed,
      conditions (hover for rules), notes, and [Full view] / [Adjust] / [Add a note]. On a player →
      the same for their character (race and class, concentration). Right-clicking with it no longer
      opens the sheet directly; the Full view has a button for that.
- [ ] Give someone a DM AC adjustment and (in a fight) Shield → the card's AC explains both:
      "(+1 (DM, until a long rest); +5 Shield, until their turn)". A creature with its own AC says
      "own AC, stat block says 12".
- [ ] A downed character → "Down, dying — death saves: 1 ✔ / 2 ✖". A dead one → "☠ DEAD".
- [ ] **Sneak + right-click** (or `/dm view <who> full`) → the Full view menu:
  - a player: their real inventory in the same layout as theirs (backpack, hotbar row, armor, off-hand),
    and you can't take or move anything;
  - a creature: what it carries, each with "Found with a DC 12 Investigation" or "In plain sight";
  - [Character sheet] / [Stat block] opens it; [Adjust] opens the Adjust menu; [Add a note…] gives
    you a fill-in line.
- [ ] `/dm note Balin add owes the party a favour` → shows in Balin's card and Full view, **after**
      his YAML `dm_notes`. `/dm note Balin clear` removes only yours. `/dm note Balin` lists them.
- [ ] Notes on a **character** survive a restart. The player never sees them: not on their sheet,
      not anywhere.
- [ ] `/dm view The Kindler` (unquoted) works; `/dm view` as a non-DM is refused.

## Conditions outlast the fight (new)

Add conditions with `/dm adjust <who> condition <name>` or the Adjust menu.

- [ ] In a fight, make a player **Poisoned** and a creature **Prone**; also have someone **Dodge**.
      `/combat finished` → Dodging ends, but the table sees "X is still Poisoned after the fight"
      and "Goblin is still Prone after the fight".
- [ ] Still poisoned, out of combat: a skill or ability check from the sheet says "↯ Disadvantage:
      Poisoned" and rolls 2d20-keep-lower. A saving throw doesn't.
- [ ] Restrained (added in a fight, kept after): a DEX save from the sheet is at disadvantage.
- [ ] Start a **new** fight with the same creature → the scoreboard still shows its condition tag.

## Entities & shops

- [ ] Buttons the game fills in (loot, possession, shop prompts) say `/dm entity …` and work.
- [ ] **Contested vs an NPC:** `/dm entity spawn balin_blacksmith`, then
      `/dm check <you> insight vs Balin deception` → your roll prompt, plus **[Roll it] /
      [I rolled…]** labelled "+1 CHA". The winner comes back with [Share]. Also inline with
      `autoRoll`, and a guard's Perception (`+2 Perception`).

## DM tools

- [ ] **Names with spaces, one reader for all** (`/dm entity spawn alira "The Kindler"`, or rename
      something to "The Kindler"):
  - `/dm adjust "The Kindler" hp -5` and `/dm adjust The Kindler hp -5` both work;
  - `/dm check Balin Ironforge save dex` (and quoted) works; so does `/dm check clear Balin Ironforge`;
  - `/dm check <you> insight vs Balin Ironforge deception` works, quoted or not;
  - `/dm resource restore "Balin Ironforge" rage` and `/character delete "Balin Ironforge"` work.
- [ ] **Ambiguous names:** spawn two goblins → `/dm adjust Goblin hp -1` says "'Goblin' could be
      Goblin #1, Goblin #2 — name the one you mean" (it used to hit whichever came first).
      `/dm adjust Goblin 2 hp -1` hits #2 (the `#` is optional).
- [ ] **Checks show the work:** `/dm check <you> save dex` → `d20(16) +3[DEX] +2[Prof] = 21`.
      [Share] shares the same line.
- [ ] `/dm adjust <who> hp -2d10` shows the dice. A flat `hp -7` doesn't pretend to roll.
- [ ] `/dm rest <character> long`; `/dm resource restore <character> all`;
      `/dm resource consume <character> <res> 1`.
- [ ] Tab: `/dm ` shows add/remove/list + give/check/rest/resource/reload; `/dm resource ` shows
      restore/consume.
- [ ] **Thieves' tools on a lock:** `/dm object lock` a chest, click [Open it] as your rogue →
      your ping has **[Thieves' tools]** and "proficient (expertise), carrying them". Click it,
      add a DC → `+4[Thieves' Tools ×2]`. A non-proficient character → just `+DEX`.
- [ ] **Thieves' tools break on a fail** (default `on_fail`): carry 2 sets, `tool thieves_tools dc 25`
      and fail → one set gone, you're told. DC 5 pass → nothing breaks. No DC → never breaks.
      Try `always` and `never` in config.yml.
- [ ] **Keys:** look at a chest, `/dm object key brass_key` → "The Brass Key opens the Chest".
  - Without the key, [Open it] → locked; the ping says "they aren't carrying it".
  - `/dm give <you> brass_key`, [Open it] → opens, "X unlocks the chest with the Brass Key",
    you keep the key, and it stays open for everyone.
  - `key iron_key single-use` → the key is used up. A trapped chest still springs first.
    `key` on a sealed block refuses.
- [ ] A DM already in spectator mode for their own reasons isn't pulled out of it by deaths.

## Restart checks (do these together, one restart)

Set these up, `/stop`, start the server, then check:

- [ ] A character still Poisoned before the restart is still Poisoned after; so is a creature (start
      a fight after the restart and look at its scoreboard tag).
- [ ] Your finished characters still list their chosen languages, tools and racial spell picks
      (high elf rogue still casts Fire Bolt with INT; astral elf still uses Wisdom).
- [ ] A dead character is still dead; their body is still there.
- [ ] `/dm adjust <c> temp 7` before → still 7 temp HP after.
- [ ] Half-orc dropped to 0 before (held at 1 by Relentless) → drop them again after, they fall.
- [ ] **A fight survives:** before, get into round 2 with a condition on someone and a player at
      0 HP (check `plugins/jkvttplugin/CombatSessions/` has a file). After:
  - console says "Restored combat … round 2, X's turn"; on join you get "Combat is still on";
  - the scoreboard is back, the downed player is prone, the condition is listed;
  - the current combatant can attack → damage with no errors;
  - a raging barbarian is still raging (sheet, halved slashing, red tint);
  - `/combat finished` → the file is gone.

---

# Part 2 — Needs a second player (not a DM)

## Permissions

- [ ] `/roll 2d6+3` works as a non-op player.
- [ ] `/character create Bob` is refused. `/character list all` lists **their own** characters;
      Tab offers `all` but no player names.
- [ ] **`/dm list` with every DM offline** → lists offline ops as `[OP] name (Offline)`.

## Their character

- [ ] **Sheets are private:** `/character view <your character>` → "You can only view your own
      characters"; Tab only suggests theirs. Right-clicking your sheet paper → "This isn't your
      character sheet".
- [ ] **Deleting needs you:** they run `/character delete <theirs>` → "Asked the DM"; you get
      [Approve] / [Deny]. Deny → kept. Approve → gone, and the file is in
      `plugins/jkvttplugin/Saved/Characters/Deleted/`. With you offline → refused.
- [ ] **Giving a character:** `/character give <them> <your character>` → **[Give …]**; click →
      it's theirs (their `/character list`, their paper opens it), your paper is gone, the gear
      stayed with you. Refused while either of you is in a fight.
- [ ] `/character give <them> <their character>` → they receive the sheet paper.

## Death, from the player's side

- [ ] They die (three failed saves) → spectator mode with a message.
  - `/dm revive <them>` → back in adventure mode, body gone, you get **[Teleport them to the
    body]** (no automatic teleport). Click → they land at the body.
  - Dead, they run `/character create` → adventure mode, creation menu works.
- [ ] They right-click someone else's body → "The body of …" + [Ask for a check]; you get a
      [call a check] ping (medicine / investigation / religion suggested).

---

# Playtest notes

(`→` lines are Claude's status. New notes go at the top.)

**2026-09-22 (evening)**

Already-known spell (high elf) is a gray pane and blends in.
  → now a knowledge book, for every already-known tile. See *Character creation*.
`/dm hp "The Kindler"` reads the quote as part of the name.
  → fixed. See *DM tools → Names with spaces*.
Standardize names: one place for creature names, one for player names.
  → done. One reader (`NameUtil.readName`) and one finder per kind: creatures
    (`DndEntityInstance.findByName`), combatants (`CombatSession.getCombatantByName`), characters
    and players (`CharacterResolver`). A test now fails the build if a command hands a raw word to
    a finder. Ambiguous names ask "which one?" instead of guessing.
Fire Bolt on an NPC out of combat skips the attack roll and fills in `/dm hp`.
  → fixed (#152): the DM decides (start combat / let it happen / deny), the attack roll comes
    first, then damage. See *Attacks outside a fight*.
The Fire Bolt prompt let me change the target name.
  → gone: out-of-combat spells no longer hand you a `/dm hp`.
`/dm hp` should use autoRoll / manualRoll / total. Why damage, heal *and* hp?
  → done: `/dm hp`, `/combat damage override`, `/combat condition`, `/dm entity maxhp` and `--force`
    are replaced by `/dm adjust` (a menu + a command + a DM-mode tool). See *The Adjust menu*.
Use the `/combat` commands out of combat, with "you're not in combat, OK?" for the DM.
  → done for attacks and spells: the DM gets [Start combat] / [Let it happen] / [Deny].

---

# Known deferred

- Racial spell **uses** (a level-3 tiefling's Hellish Rebuke) aren't saved, so a restart refills
  them. Harmless at level 1; matters once level-up (#153) lands.
- Character-sheet inventory redesign (waiting until more content lands).
- Nicer default `material:` values for spellcasting foci and packs.
