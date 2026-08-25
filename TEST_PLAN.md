# Test Plan — command consolidation, icons, saves

Rebuild + deploy the jar first (`gradlew build`, copy `build/libs/*.jar` to the server's
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
- [ ] Each choice shows a "— Choose N more —" divider; groups are visually separated.
- [ ] **Automatic grants** (e.g. Common under Languages, background skills) appear as locked cyan tiles under "— Granted — automatic —". ⚠️ If Common does NOT appear, tell me — it means the language grant isn't being populated into the session (a data step, not the UI).

## Save location
- [ ] New characters save under **`plugins/jkvttplugin/Saved/Characters/`**.
- [ ] Shops save under **`plugins/jkvttplugin/Saved/Shops/`**.
- [ ] No stray `<server-root>/DMContent/` folder is recreated.

## Deprecated aliases (should all still work)
- [ ] `/createcharacter`, `/viewsheet`, `/closesheet`, `/givesheet`, `/shortrest`, `/longrest`, `/rolldice`, `/dmgive`, `/check`, `/rest`, `/restoreresource`, `/consumeresource`, `/reloadyaml`.

## Known deferred (not in this build)
- Character-sheet inventory redesign (waiting until more content lands).
- Unify the class-resource nested `icon:` (a sheet-display Material) into the `material:` naming.
- Give spellcasting foci / packs nicer default `material:` values.
