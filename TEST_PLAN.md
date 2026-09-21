# Test Plan — command consolidation, icons, saves

Rebuild + deploy the jar first (`gradlew build` — it runs the automated tests too, and refuses to
build if one fails — then copy `build/libs/*.jar` to the server's
`plugins/`, restart or reload). Load the resource pack so icon checks are meaningful.

Legend: run as a **DM** (op) unless noted; for "non-DM" rows use a second account or `/deop`.

## Resource pack / icons
- [ ] Server hands the pack on join (or load it client-side). Accept the prompt.
- [ ] `/character create` → **Class** tab shows custom class art (incl. barbarian).
- [ ] **Abilities** tab: the six tiles show `str_icon … cha_icon`; stack count = the score.
- [ ] Plasmoid or half-elf → racial bonus row shows the ability icons; the **assigned** one shimmers (enchant glint).
- [ ] Race / Background tabs stay on vanilla items (no purple boxes) — expected until those textures exist.
- [ ] Selected race/class tile shimmers (enchant glint) without hovering.

## /character (player)
- [ ] `/character` and `/char` → usage list; Tab cycles create/view/list/close/rest (+give if DM).
- [ ] `/character create` → creation menu opens.
- [ ] Finish a character → `/character list` shows its name.
- [ ] `/character view` opens your sheet; `/character view <name>` opens that one.
- [ ] `/character rest short` and `/character rest long` recover as before.
- [ ] `/character close` saves & closes.
- [ ] Right-click the **Character Sheet** paper → opens the sheet.

## /character (DM forms)
- [ ] `/character create <onlinePlayer>` → creation opens **for that player**.
- [ ] `/character give <player> <name>` → player receives the sheet paper.
- [ ] `/character list all` → every saved character with `(owner)`.
- [ ] As non-DM: `/character create Bob` and `/character list all` are refused; `/character list` still lists your own.

## /roll
- [ ] `/roll 2d6+3` works. `/rolldice 1d20` still works (deprecated alias).

## /dm
- [ ] `/dm` → help shows role verbs + DM tools.
- [ ] `/dm list`; `/dm give <player> <item_id> 1`; `/dm check <player> save dexterity`.
- [ ] `/dm rest <character> long`; `/dm resource restore <character> all`; `/dm resource consume <character> <res> 1`.
- [ ] `/dm reload` reloads YAML.
- [ ] Tab: `/dm ` shows add/remove/list + give/check/rest/resource/reload; `/dm resource ` shows restore/consume.
- [ ] As non-DM: `/dm give`, `/dm rest`, `/dm reload` refused.
- [ ] As non-op DM (added via `/dm add`): DM tools work, but `/dm add`/`remove` refused (op-only).

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
- [ ] **Wood elf + Sailor** → a *Replace duplicate Perception (Elf + Sailor)* skill pick; Perception shows as "Already known" in every skill list.
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
- [ ] **Contested vs an NPC**: `/dmentity spawn balin_blacksmith`, then `/dm check <player> insight vs Balin deception` → the player gets their roll prompt, you get **[Roll it] / [I rolled…]** labelled "+1 CHA". Winner comes back with [Share]. Also try it inline with `autoRoll`, and a guard's Perception (`+2 Perception`, listed skill).
- [ ] **Rogue expertise**: rogue + Sage → the Expertise pick offers only skills you're proficient in (plus Thieves' Tools). Pick Stealth, then un-pick Stealth from class skills → finishing says "Expertise (not proficient in Stealth)". Sheet skill roll for Stealth shows `+4[Expertise]`.
- [ ] **Thieves' tools on a lock**: `/dm object lock …` a chest, player clicks [Open it] → your ping has **[Thieves' tools]** and a status line ("proficient (expertise), carrying them"). Click it, add a DC → the roll shows `+4[Thieves' Tools ×2]` for that rogue, just `+DEX` for a non-proficient character.
- [ ] **Thieves' tools break on a fail** (default `objects.thieves_tools_break: on_fail`): give a player 2 sets, call `tool thieves_tools dc 25` and fail → one set gone from the inventory, player and DMs told. Pass a DC 5 → nothing breaks. No DC → never breaks. Try `always` and `never` in config.yml (restart).
- [ ] **Armor proficiency (#209)**: a wizard puts on chain mail → a warning in chat, and the sheet's AC tile says "⚠ not proficient with Chain Mail". Then: a STR/DEX skill roll from the sheet says "(disadvantage)" and rolls 2d20-keep-lower; `/dm check <wizard> save dex` does too; a WIS check doesn't. In combat, a weapon attack gets "↯ disadvantage" with an "Armor (you)" reminder, initiative rolls with disadvantage, and `/combat cast` / `/character cast` refuse. Take it off → all normal. A fighter in the same armor → no penalty. A mountain dwarf wizard in scale mail (medium) → fine; chain mail (heavy) → penalty.
- [ ] **Combat survives a restart (#165)**: start a fight with a player and a kobold, roll initiative, get into round 2, give someone a condition, drop a player to 0. `/stop`, start the server. Check `plugins/jkvttplugin/CombatSessions/` has a file before restarting. On boot the console says "Restored combat … round 2, X's turn". Rejoin: the DM gets "Combat is still on: round 2, X's turn"; players see the initiative scoreboard again; the downed player is prone again; the condition is still listed. The current combatant can act (attack → damage works, no errors). A barbarian who was raging still is (sheet shows it, slashing damage is halved, red tint back). `/combat finished` → the file is gone.
- [ ] **Sheet adv/dis in physical-dice mode**: click a skill → "Roll with advantage" → the filled command has `adv` in it and the result shows two d20s (this used to roll normal silently).
- [ ] **Keys (#200)**: look at a chest, `/dm object key brass_key` → "The Brass Key opens the Chest (locked it)". A player without the key clicks [Open it] → locked, and your ping adds "Opens with: Brass Key — they aren't carrying it". `/dm give <player> brass_key`, they click [Open it] → it opens, you and anyone nearby see "X unlocks the chest with the Brass Key", they keep the key, and the chest opens normally for everyone after. Repeat with `key iron_key single-use` → the key is gone from their inventory. A trapped chest with a key still springs its trap first. `key` on a sealed block refuses.
- [ ] `/dm check <player> tool smiths_tools` with no ability → asks which ability; `… tool smiths_tools int dc 12` works.
- [ ] **Tiefling** → Thaumaturgy is castable (it used to count as a 0-use leveled spell). Same for forest gnome's Minor Illusion.

## Save location
- [ ] New characters save under **`plugins/jkvttplugin/Saved/Characters/`**.
- [ ] Shops save under **`plugins/jkvttplugin/Saved/Shops/`**.
- [ ] No stray `<server-root>/DMContent/` folder is recreated.

## Old commands removed (should NOT exist)
- [ ] `/createcharacter`, `/viewsheet`, `/closesheet`, `/givesheet`, `/shortrest`, `/longrest`, `/rolldice`, `/dmgive`, `/check`, `/rest`, `/restoreresource`, `/consumeresource`, `/reloadyaml` — each should be "Unknown command". Only the five roots (`/character`, `/roll`, `/combat`, `/dmentity`, `/dm`) exist.

## Known deferred (not in this build)
- Character-sheet inventory redesign (waiting until more content lands).
- Unify the class-resource nested `icon:` (a sheet-display Material) into the `material:` naming.
- Give spellcasting foci / packs nicer default `material:` values.
