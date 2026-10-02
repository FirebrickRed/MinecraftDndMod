# JK VTT — Dungeons & Dragons 5e for Minecraft

A Paper/Spigot plugin that turns a Minecraft server into a **virtual tabletop for Dungeons & Dragons 5th Edition**. Build a 5e character, manage equipment and spells, and run combat encounters — all inside Minecraft, no character sheets or separate apps required.

> **Status: Pre-Alpha.** Core character and combat systems work and are playable, but data formats and features are still changing. Expect rough edges.

**Target platform:** PaperMC 26.2 · Java 25 (the build downloads JDK 25 itself; the server needs Java 25 to run)

---

## Why this exists

This started as a way to use Minecraft (loosely) as a virtual tabletop for D&D. Having struggled to visualize the world in a traditional 2D space — especially since all my sessions are remote — Minecraft offered a more immersive, collaborative experience.

It also grew out of a real problem at the table: some of my players (myself included) would occasionally forget how certain 5e mechanics worked. So the plugin tries to *do the rules for you* — modifiers, proficiencies, spell slots, rests, and rolls are tracked automatically, so the group can focus on playing.

---

## What it can do

### Character creation (5e rules, in-game menus)
Run `/character create` to open a guided, menu-driven character builder. It also hands you a **Create Character** paper: right-click it to reopen the builder where you left off, so closing the menu loses nothing. When you finish, the paper becomes your **Character Sheet**, and right-clicking that opens the sheet.

- **Race & subrace** selection, with racial traits applied automatically
- **Class & level-1 subclass** selection (e.g. Cleric Domains, Warlock Patrons, Sorcerer Origins)
- **Background** selection with its skills, tools, languages, and starting gear
- **Ability scores** via point-buy, standard array, or manual entry
- **Skill proficiency** choices
- **Spell selection** for spellcasters (cantrips and leveled spells, filtered to your class list)
- **Starting equipment** choices
- **Character naming**, in a form or in chat (shown as a suffix in chat and the tab list)

Characters are saved to disk and persist across restarts.

### Living character sheet
- Full stat display: HP, AC (with breakdown), speed, initiative
- Ability scores with saving-throw indicators
- **Interactive skill, check and save rolling** with advantage/disadvantage. A roll you make from your own sheet is **private**: only you see it, with a **[Show the DM]** button; the DM can then share it with the table. A check the DM calls comes back to the DM first.
- **Roll your own dice or let the game roll:** every roll offers **[Roll it]**, **[I rolled…]** (you type the die, the game adds your bonuses) or **[My total…]**, with each bonus labelled by where it comes from (`+3[DEX] +2[Prof]`)
- **Spellbook** showing known spells and spell-slot tracking, and prepared spells for clerics, druids, paladins and wizards
- **Features & Traits** page: race traits, class and subclass features, and the ones you can use with a click
- **Class resources** (Rage, Ki, Sorcery Points, etc.) with current/max tracking

### Automatic 5e mechanics
- **Racial traits:** innate spellcasting, darkvision, movement speeds, damage resistances, proficiencies, and languages
- **Proficiency system:** weapon, armor, tool, skill, and language proficiencies merged from race, class, subclass, and background
- **Rest system:** the DM's `/dm rest <character|all> <short|long> [time passed]` recovers HP, spell slots, and class resources per 5e rules; a short rest opens a window to spend Hit Dice
- **Armor proficiency** is enforced (disadvantage and no spellcasting in armor you aren't trained for), and **equipped armor and shields** are read from your real armor and off-hand slots

### DM tools
- **DM roles:** `/dm add|remove|list` grants DM-only powers
- **Entity spawning:** `/dm entity` spawns and manages stat-block NPCs and monsters in the world
- **Item granting:** `/dm give` hands D&D weapons, armor, and items to players
- **Checks:** `/dm check` calls a check, save or tool check on one player, a group or everyone, graded against a DC, plus passive and contested checks
- **The DM's override:** `/dm adjust` changes HP, AC and conditions, in or out of a fight
- **Annotating the world:** `/dm object` (and an in-world tool) marks blocks as locked, trapped, hidden, sealed, keyed or holding loot, and gives them lore a character can study, with text that depends on the roll
- **DM mode:** `/dm mode` swaps your inventory for a toolbar of DM tools (spawn, annotate, adjust, time, the Sound Board, possessing a creature to play it)
- **Hot-reload content:** `/dm reload` reloads all D&D data without a server restart. A file with a YAML syntax error refuses the reload and keeps what's loaded, and the result is reported honestly: a clean load, or the warnings

### Shop & economy system
- Native Minecraft merchant-GUI trading with a D&D currency system (gold/silver/copper/platinum/electrum)
- **Buy and sell:** players buy from merchants and sell items back
- Stock tracking and DM-adjustable pricing, all persisted to disk

### Combat (largely built, still being playtested)
- **Initiative & turn order:** `/combat start`, `/combat add`, then `/combat rollforinitiative`; the order is on a scoreboard with everyone's HP
- **Action economy:** Action, Bonus Action, Reaction and Movement per turn; you act on your turn, the DM runs the encounter
- **Attacks:** on your turn, left-click an enemy with your weapon to get the attack filled in. Hit, miss and crits are worked out, then damage, with resistances applied
- **Spells:** attack, save and area spells (with an aim preview), spell slots and upcasting, concentration (and its saves), rituals, and spells that last (Bless, Hex, Shield)
- **Reactions:** a hit can be answered before damage lands (Shield turns it into a miss), and walking out of an enemy's reach offers it an opportunity attack
- **Downed and dying:** death saves, stabilizing, and death; a dead character leaves a body until revived
- **Conditions** with their advantage and disadvantage, and class features (Rage, Sneak Attack, Second Wind, Lay on Hands, Bardic Inspiration…)
- **Hidden enemies:** the DM can keep entities secret (shown as `???` to players) and reveal them mid-combat
- **Survives a restart:** a fight in progress is saved on shutdown and picks up where it left off

---

## Content is data-driven (YAML)

All D&D content lives in `DMContent/` as YAML — races, classes, subclasses, spells, weapons, armor, items, backgrounds, creatures and conditions. **You can add new content without writing any Java.** Edit or add a file, run `/dm reload`, and it appears in-game. The authoring guides in [`docs/`](docs) cover every field (`authoring-spells.md`, `authoring-items.md`, `authoring-entities.md`, `authoring-races.md`, `authoring-classes.md`, `authoring-backgrounds.md`).

```
DMContent/
  Races/        Classes/      Spells/       Backgrounds/
  Weapons/      Armor/        Items/        Entities/      Conditions/
  Sounds.yml   DamageTypes.yml   Languages.yml (optional: homebrew languages; the PHB ones are built in)
```

Every load runs a content check that warns about typos the loaders would otherwise swallow (an unknown item id in a shop, armor that can't be worn, a spell with no damage dice). A clean load prints nothing.

---

## Commands

Commands are consolidated under four roots: `/character`, `/roll`, `/combat`, `/dm`.

| Command | Who | What it does |
|---|---|---|
| `/character create` | Player | Start character creation |
| `/character view [name]` | Player | View one of your character sheets |
| `/character cast <spell> [target]` | Player | Cast a spell outside a fight (the DM decides whether a harmful one starts a fight) |
| `/roll <XdY[+Z]>` | Player | Roll dice (e.g. `2d6+3`, or several groups: `1d8+1d6+3`) |
| `/combat <attack\|cast\|damage\|action\|bonusAction\|endturn\|...>` | Player (own turn) / DM | Act in a fight; usually filled in for you by a click |
| `/combat <start\|add\|rollforinitiative\|nextturn\|finished\|...>` | DM | Run a combat encounter |
| `/dm <add\|remove\|list>` | Op | Manage who is a DM |
| `/dm check <who> <skill\|save\|tool\|passive> ...` | DM | Call a check, graded against a DC |
| `/dm entity <spawn\|list\|remove\|rename\|trade\|shop\|...>` | DM | Spawn & manage NPCs/monsters and their shops |
| `/dm give <player> <item_id> [amount]` | DM | Give D&D items to a player |
| `/dm rest <character\|all> <short\|long> [time]` | DM | Run a rest |
| `/dm mode` | DM | The DM toolbar |
| `/dm reload` | DM | Reload all YAML content |

See **COMMANDS.md** for the full, grouped command reference.

---

## Running a server

This plugin is great for small groups (≈5–15 players). The quickest dev/playtest setup is to **self-host locally and expose it with [playit.gg](https://playit.gg)** (a tunnel — no router port-forwarding needed):

1. Install **Java 25**, and download **PaperMC 26.2** from [papermc.io](https://papermc.io) into a fresh folder.
2. Build this plugin (`gradlew build`) and copy `build/libs/jkvttplugin-*.jar` into the server's `plugins/` folder.
3. Copy the **`DMContent/`** folder into the server root — *the plugin needs it for all D&D content.*
4. Run the server once, set `eula=true` in `eula.txt`, then start it again.
5. Run the **playit.gg** agent, create a **Minecraft Java** tunnel, and share the address it gives you.
6. `/whitelist on`, add your players, and you're live.

> Tip: keep an eye on the server console — the plugin logs content loading and errors there, which is the fastest way to debug while iterating.

---

## Building from source

```bash
# Windows
gradlew build

# Clean build
gradlew clean build
```

The compiled JAR lands in `build/libs/`.

### Tests

`gradlew build` also runs the test suite (`gradlew test` runs it alone). The tests load the real
`DMContent/` folder, so **a broken YAML file or a content-check warning fails the build**: no jar
until it's fixed. The console names the failing test and what it expected. Reports land in
`build/reports/tests/test/index.html`. Anything that needs a live server (items in inventories,
chat prompts) isn't covered; that's what [`TEST_PLAN.md`](TEST_PLAN.md) is for.

---

## Roadmap / not yet implemented

This is pre-alpha, so several things are intentionally still missing:

- Character level-up, multiclassing, and feats (all characters are currently level 1)
- Each character carrying their own gear: today a player's characters share one inventory
- Magic items beyond magic weapons, and attunement
- Animated creature models, and player-shaped NPCs with their own skins (being trialled)
- A handful of races/backgrounds not yet entered as data
- Much of combat is built but only lightly playtested; [`TEST_PLAN.md`](TEST_PLAN.md) is the checklist

Feedback and feature ideas are welcome while the architecture is still flexible.
