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

- [ ] Cleric's Spells tab, bottom label → "You prepare these from your class's full spell list"
      (it no longer promises swapping on a long rest; that's #218).
- [ ] Item tooltips (a weapon, a potion) and condition hovers wrap at the same width as spells.
- [ ] High Elf: the Wizard Cantrip pick shows each cantrip's full description, like the Spells tab.
- [ ] Friends (or any spell with a long material component) → the card stays narrow; the component wraps.
- [ ] **Languages from two sources** (High Elf + Noble, one each): pick two, then keep picking → the
      replaced language alternates (oldest goes), not the same slot every time.

## Finished characters

Finish the combo, then look at the sheet and your inventory.

- [ ] **High Elf Rogue** with Fire Bolt as the Wizard Cantrip → `/character cast fire_bolt` works and uses INT.
- [ ] **Astral Elf** with Sacred Flame + Wisdom → `/combat cast sacred_flame <target>` uses WIS for the DC.
- [ ] **Mountain Dwarf Fighter, Guild Artisan:** the artisan's-tool pick gives the proficiency **and** the item.
- [ ] **Mountain Dwarf Fighter:** heavy armor on, no penalty.
- [ ] **Folk Hero:** the picked tool is in your kit.
- [ ] **Entertainer:** the picked instrument is in your kit.
- [ ] `/dm rest <character> short` recovers as before (the player version is gone: rests are the DM's call).
- [ ] `/dm rest <character> long` recovers as before; with no time given, the clock doesn't move.
- [ ] **Tiefling Rogue:** right-click thieves' tools with no chest in view → nothing happens, no
      "cannot use this type of focus" message.
- [ ] **Tiefling Rogue:** right-click a chest holding thieves' tools → the chest's own [Open it] /
      [Ask for a check] prompt, not a focus message.

## The sheet's skills and rolls

Armor you're not proficient with (PHB p.144) gives disadvantage on STR and DEX rolls.

- [ ] **Fighter in chain mail:** no penalty.
- [ ] **Mountain dwarf wizard:** scale mail fine, chain mail penalized.
- [ ] Skills menu, hover an ability's check tile → its breakdown (`+2[DEX]`), like the skills and saves.

## Natural 1s and 20s

Roll a few times until one comes up (or type it: `manualRoll 20` / `manualRoll 1`).


## Casting from the spellbook and `/character cast`

- [ ] `/character cast ` + Tab → your cantrips, spells and racial spells (Thaumaturgy for a tiefling).

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
- [ ] **Out of range, as DM** (possessing a creature, attacking something too far) → "… about N ft
      away" **[Do it anyway]**; click → **[go again]** fills the attack; it goes through once.
- [ ] **Out of reach in a fight, as a player:** a sword at someone 20 ft away → **[Ask the DM]**.
- [ ] The DM gets **[Allow]** / **[Deny]**; Allow → you get **[go again]**, and the attack goes through.
- [ ] Same with a spell in a fight (Cure Wounds from 20 ft) → [Ask the DM], then it goes through once.
- [ ] Hex at someone 120 ft away → refused for range (it wasn't checked before).
- [ ] A Self spell that targets (e.g. one with range Self, not an area) at someone else, out of a fight →
      "only targets you" (only checked in a fight before).
- [ ] Touch spells reach one block further than before in a fight (5 ft of slack, same as out of one).

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

## Death

- [ ] Down yourself, fail three death saves → "has DIED", turn skipped.
- [ ] `/combat finished`, new fight → still `[DEAD]`, still skipped.
- [ ] Dead: `/dm adjust <you> hp +10` refuses.
- [ ] Dead: `/dm rest <character> long` refuses.
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

- [ ] After Start combat, on the caster's first turn → "Your opening move: Fire Bolt at The Kindler [do it]".
- [ ] Sacred Flame with Let it happen → the DM sees the DC, **[Call the save]**, **[Failed: damage]** / **[Saved: …]**.
- [ ] **[Let it happen]** a Sacred Flame → you get **[cast it]**, not a d20 prompt (a save spell
      doesn't roll to hit).
- [ ] `/character cast cure_wounds The Kindler` from 18 ft → refused, with **[Ask the DM]**.
- [ ] Click it → the DM gets "📏 … out of reach (about 18 ft away …)" **[Allow]** / **[Deny]**.
- [ ] [Allow] → you get **[cast it]**; it goes through once. Casting again from there refuses again.
- [ ] [Deny] → "The DM says it doesn't reach".
- [ ] **Shocking Grasp at someone 20 ft away** → refused for range straight away; the DM is **not** asked
      to start a fight first.
- [ ] Burning Hands (or any save spell) at a creature out of combat → the DM line shows its bonus
      (`DC 13 DEX save (+1[DEX])`) and **[Call the save]**, which gives you the roll buttons graded vs the DC.
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
- [ ] `/combat condition` is gone.
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

- [ ] A long DM note (YAML `dm_notes:` or `/dm note … add`) wraps in the Full view and the stat block tile.
- [ ] View tool on your own character → race and class, concentration.
- [ ] A DM AC adjustment plus Shield → the card's AC explains both.
- [ ] A creature with its own AC → "own AC, stat block says 12".
- [ ] A downed character → "Down, dying — death saves: 1 ✔ / 2 ✖".
- [ ] A dead one → "☠ DEAD".
- [ ] Sneak + right-click a player → Full view with their real inventory; you can't take or move anything.
- [ ] Full view of a creature → what it carries, "Found with a DC 12 Investigation" / "In plain sight".
- [ ] Full view of a creature → the top row holds its stat block: stats, abilities, attacks (and the
      YAML's DM notes if it has any). No separate [Stat block] button.
- [ ] Spawn one wolf, `/dm note Wolf add hungry`, View tool on that wolf → the note is there.

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

- [ ] `/dm entity spawn kobold Meepo the Bold` → named "Meepo the Bold" (no quotes needed).
- [ ] `/dm entity spawn guard Guard 3` → "Guard 3"; `/dm entity spawn guard Guard 3 ~ ~ ~5` → "Guard 3" there.
- [ ] Two wolves (Wolf, Wolf #2): `/dm entity remove wolf #1 wolf #2` → both go, no "couldn't find" lines.
- [ ] Buttons the game fills in (loot, possession, shop prompts) say `/dm entity …` and work.
- [ ] `/dm check ` + Tab and `/dm adjust ` + Tab → the **same** list, multi-word names in quotes (`"The Kindler"`).
- [ ] `/dm check Balin Ironforge ` + Tab (unquoted) → `ability / save / skill` (no `tool` for a creature).
- [ ] `/dm check "Balin Ironforge" ` + Tab → the same.
- [ ] `/dm check Balin Ironforge save dex ` + Tab → `dc / adv / dis / autoRoll / manualRoll / total`.
- [ ] `/dm adjust The Kindler ` + Tab (unquoted) → the actions (`hp`, `temp`, …).
- [ ] `/dm entity cleanup` is gone (unknown subcommand, not in Tab).
- [ ] `/dm entity remove nobody` → "No creature called 'nobody'".
- [ ] Possess a creature → the message says its model is hidden from you and **F** shows it.
- [ ] Press F, F5 → you see the model you're possessing. Stop, possess another → still visible
      (the choice sticks); F again hides it.
- [ ] **Looking at a creature**, right-click the character sheet → the sheet opens.
- [ ] Same with your spellcasting focus → the spellbook opens.
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

- [ ] Right-click a chest with the Annotate tool → a form opens: how it opens, hidden, description,
      trap damage / save / DC / armed, key, loot. It shows the chest's current settings.
- [ ] Fill it in, **look away**, then Save → it applies to that chest (not what you're looking at).
- [ ] After Save, chat says "🔧 Saved the Chest: locked, trap 2d10 DEX DC 13 (armed), key …".
- [ ] Right-click it again → the form shows what you saved.
- [ ] A long description with line breaks → saved, and players see it.
- [ ] Trap damage `banana` → a yellow note says it isn't dice; the rest still saves.
- [ ] Sealed + a key → a note says a sealed block has no key; the key is left off.
- [ ] Opens + a key → it saves as Locked (a key means a lock).
- [ ] Loot `gold_piece x10, dagger, nonsense` → two entries saved, a note about "nonsense".
- [ ] **Clear annotation** → it's a plain chest again.
- [ ] Esc closes the form without changing anything.
- [ ] Sneak + right-click → the old chat buttons, still working.
- [ ] Which do you prefer? (Tell me in the notes.)

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

(`→` lines are Claude's status. New notes go at the top.)

**2026-09-24 (third round)**

The High Elf's spell choice doesn't show the whole spell.
  → fixed: a spell pick in the Choices tab shows the same description as the Spells tab.
Friends' components make the card huge.
  → fixed: every line of a spell card wraps now, not just the rules text. A test checks every spell.
Cycle select on languages only ever replaces the same one.
  → a bug: two one-pick sources (race + background) merged into "choose 2", and a new pick always
    replaced the first source. It now replaces whichever was picked longest ago.
Button order [Roll it] [I rolled…] [My total…].
  → done, everywhere.
[My total…] hover should say what to roll and add.
  → done: "Type your final number: roll 2d20 and keep the lower and add -1[DEX] yourself."
    Advantage/disadvantage is worked out for the prompt the same way the roll does.
[Roll it] ran the command instead of filling chat.
  → fixed: all three buttons only fill chat now, and a test fails the build if one ever runs.
    (The sheet's own [Normal] / [Advantage] / [Disadvantage] line still rolls on click in auto-roll
    mode; that's a menu pick, not a prompt. Say if that should fill chat too.)
Shocking Grasp: the DM was asked to start a fight, then it said too far away.
  → fixed: range is checked first; nobody is asked about a spell that can't reach.
Skills menu: the ability check tile has no breakdown.
  → added (`+2[DEX]`), like the skill and save tiles.
Burning Hands: add the +1 DEX to [Failed: damage] / [Saved: half], or let us roll.
  → both: the DM line shows the save bonus, and [Call the save] now works on creatures too. It
    hands you the roll buttons, graded against the DC. (It only appeared for player targets.)
"<player> casts <spell>" should make the spell hoverable.
  → done: every cast line, in and out of a fight, including plain ones like Light.
Word wrap DM notes on creatures.
  → done, in the Full view and the stat block. (A comment there said it already wrapped. It didn't.)
`/dm entity spawn kobold Meepo the Bold` named it "Meepo".
  → fixed: an unquoted name runs to the end, or up to three trailing coordinates.
`/dm entity remove wolf #1 wolf #2` removed both but complained about "#1" and "#2".
  → fixed: it matches the longest run of words that names a creature, and "#1" now means the
    unnumbered original (the first wolf is "Wolf", not "Wolf #1"), everywhere names are read.

**2026-09-24 (second round)**

`/dm entity remove "The Kindler"` → "Removed 0"; unquoted works.
  → fixed. It looked each word up on its own, so unquoted worked by luck ("Kindler" alone matched)
    and quoted matched nothing. It now reads the name like every other command, and says which
    names it couldn't find.
Stop making me invisible when I possess, or a toggle?
  → the toggle already existed (press **F** while possessing) but nothing told you. The possess
    message says so now, and your choice sticks between possessions. You staying invisible is what
    lets everyone else see the creature instead of you, so that part stays.
Spell info in creation, word wrapped.
  → done: a spell tile shows the full description (the same one as the spellbook).
Standards for wrapping, spell info, names…?
  → yes: CLAUDE.md now has a "UI standards: one helper per thing" table (roll prompts, roll
    results, wrapping, spell text, names, asking the DM). One wrap width everywhere, paragraph
    breaks kept. Tests fail the build if roll buttons get hand-built again.
No prepared spells? Ticket?
  → half: creation picks the right number, but nothing lets a Cleric/Druid change them after a long
    rest, and a Wizard can cast all 6 spellbook spells instead of INT+1 of them. Ticketed as #218.
    The Spells tab was also promising "swap them on a long rest", which isn't true yet; fixed.
Can't open my sheet or spellbook while looking at a creature.
  → fixed: right-clicking a creature is a different Minecraft event and the sheet and focus
    only listened for the other one. Looting a body still takes priority.
Second Fire Bolt at The Kindler went through without asking the DM.
  → a bug: one [Let it happen] lasted 10 minutes for that spell and target. Now it covers one cast.
Hold one request so spam doesn't pile up on the DM?
  → yes: each player has one open request. The same one again isn't re-sent ("Still waiting on
    the DM"), a different one replaces it, and the DM's old buttons say it was replaced. I'd keep it
    at "latest wins" rather than a queue: a queue fills the DM's chat with things the player
    has already given up on.
`/character cast fire_bolt manualRoll 20` (at a wall) → "No creature called 'manualRoll 20'".
  → fixed; `autoRoll 1d20` was the same bug.
Out of reach: let the DM override.
  → done: the refusal has [Ask the DM], and [Allow] hands you [cast it] for one cast. Only out of
    combat for now; in a fight the DM already has [Attack anyway] when possessing.
Three roll options, one wording, one shared function.
  → done (#216, closed): [I rolled…] [Roll it] [My total…] everywhere, results read
    `🎲 d20 [14] +5 = 19` / `🎲 you rolled 14 +5 = 19` / `🎲 your total: 19`. [My total…] only shows
    when the game would add something. See *Roll prompts*.
Put things in a Dungeoneer's Pack by accident; block it?
  → done: packs take nothing in (you can still take out what's there). Temporary until packs unpack
    into their contents; logged in #193.
Left-click with a sword neither damages nor prompts combat.
  → a bug, not a DM thing: creatures are invulnerable stands, so a survival hit never fires the
    event the prompt listened to. It now listens to the swing itself.

**2026-09-24**

Call out nat 1s and nat 20s.
  → done: every roll that shows its dice ends in **NATURAL 20!** / **NATURAL 1**. It's only a
    callout; a nat 20 on a check isn't an automatic success. See *Natural 1s and 20s*.
Gray out temp HP for entities since they can't have it?
  → there was no rule against it, we just never stored it. Creatures have temp HP now. Also fixed
    "set HP" landing too high when someone had temp HP. See *The Adjust menu*.
Ticket: standardize how every command suggests a name (`/dm check` unquoted, `/dm adjust` quoted).
  → #215. `/dm check` and `/dm adjust` already share one list (quoted) and accept both spellings;
    #215 covers the rest (mostly `/combat`).
Same for every [Roll it] / [I rolled…].
  → #216: one helper builds every roll prompt (8 wordings across 12 files today).
`/dm check Balin the Smith` only prompts adv / dc / dis.
  → a bug: Tab counted words, so a three-word name put you three slots ahead. Fixed; you now get
    ability / save / skill next. See *Entities & shops*.
`/dm check clear Balin Ironforge` only works for players.
  → by design, but the message was bad: held checks are characters' rolls kept for you, and a
    creature's check is your own roll, so a creature never has any. It now says so.
Entity or player in the usage text?
  → **creature**: `<character|creature>`, `<character>` when only characters work. `/dm check` done;
    the rest is in #215.
Full view should show the stat block instead of a separate menu.
  → done: a creature's stat block is in the Full view's top row.
What decides what goes to the console?
  → nothing on purpose; fights never reach it. A real game log is #217 (low priority).
Still need `/dm entity cleanup`?
  → removed. A creature whose YAML id you renamed now gets a console warning instead.

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
