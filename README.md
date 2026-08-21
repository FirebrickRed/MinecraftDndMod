# JK VTT — Dungeons & Dragons 5e for Minecraft

A Paper/Spigot plugin that turns a Minecraft server into a **virtual tabletop for Dungeons & Dragons 5th Edition**. Build a 5e character, manage equipment and spells, and run combat encounters — all inside Minecraft, no character sheets or separate apps required.

> **Status: Pre-Alpha.** Core character and combat systems work and are playable, but data formats and features are still changing. Expect rough edges.

**Target platform:** PaperMC 1.21.3 · Java 21

---

## Why this exists

This started as a way to use Minecraft (loosely) as a virtual tabletop for D&D. Having struggled to visualize the world in a traditional 2D space — especially since all my sessions are remote — Minecraft offered a more immersive, collaborative experience.

It also grew out of a real problem at the table: some of my players (myself included) would occasionally forget how certain 5e mechanics worked. So the plugin tries to *do the rules for you* — modifiers, proficiencies, spell slots, rests, and rolls are tracked automatically, so the group can focus on playing.

---

## What it can do

### Character creation (5e rules, in-game menus)
Run `/createcharacter` — or right-click your **Character Sheet** item — to open a guided, menu-driven character builder. (Right-clicking a *filled* sheet reopens it for viewing instead.)

- **Race & subrace** selection, with racial traits applied automatically
- **Class & level-1 subclass** selection (e.g. Cleric Domains, Warlock Patrons, Sorcerer Origins)
- **Background** selection with its skills, tools, languages, and starting gear
- **Ability scores** via point-buy, standard array, or manual entry
- **Skill proficiency** choices
- **Spell selection** for spellcasters (cantrips and leveled spells, filtered to your class list)
- **Starting equipment** choices
- **Character naming** (shown as a suffix in chat and the tab list)

Characters are saved to disk and persist across restarts.

### Living character sheet
- Full stat display: HP, AC (with breakdown), speed, initiative
- Ability scores with saving-throw indicators
- **Interactive skill & ability rolling** with advantage/disadvantage — results broadcast to chat with a full breakdown
- **Spellbook** showing known spells and spell-slot tracking
- **Class resources** (Rage, Ki, Sorcery Points, etc.) with current/max tracking

### Automatic 5e mechanics
- **Racial traits:** innate spellcasting, darkvision, movement speeds, damage resistances, proficiencies, and languages
- **Proficiency system:** weapon, armor, tool, skill, and language proficiencies merged from race, class, subclass, and background
- **Rest system:** `/shortrest` and `/longrest` recover HP, spell slots, and class resources per 5e rules

### DM tools
- **DM roles:** `/dm add|remove|list` grants DM-only powers
- **Entity spawning:** `/dmentity` spawns and manages stat-block NPCs and monsters in the world
- **Item granting:** `/dmgive` hands D&D weapons, armor, and items to players
- **Hot-reload content:** `/reloadyaml` reloads all D&D data without a server restart

### Shop & economy system
- Native Minecraft merchant-GUI trading with a D&D currency system (gold/silver/copper/platinum/electrum)
- **Buy and sell:** players buy from merchants and sell items back
- Stock tracking and DM-adjustable pricing, all persisted to disk

### Combat (in active development)
- **Initiative & turn order:** `/combat` + `/rollforinitiative` start an encounter and track the turn order on a scoreboard
- **Action economy:** per-turn Action / Bonus Action / Movement tracking, active-combatant highlighting, and turn enforcement (you act on your turn; the DM runs the encounter)
- **Hidden enemies:** the DM can keep entities secret (shown as `???` to players) and reveal them mid-combat
- **Attack resolution** (rolls, hit/miss, crits, damage display) is being wired up now — see the roadmap below.

---

## Content is data-driven (YAML)

All D&D content lives in `DMContent/` as YAML — races, classes, subclasses, spells, weapons, armor, items, and backgrounds. **You can add new content without writing any Java.** Edit or add a file, run `/reloadyaml`, and it appears in-game.

```
DMContent/
  Races/        Classes/      Spells/
  Weapons/      Armor/        Items/
  Backgrounds/
```

---

## Commands

| Command | Who | What it does |
|---|---|---|
| `/createcharacter` | Player | Start character creation |
| `/closesheet` | Player | Save and close the active character sheet |
| `/shortrest` · `/longrest` | Player | Recover resources (short / long rest) |
| `/rolldice <XdY[+Z]>` | Player | Roll dice (e.g. `2d6+3`) |
| `/dm <add\|remove\|list>` | Op | Manage who is a DM |
| `/dmentity <spawn\|list\|remove\|teleport\|...>` | DM | Spawn & manage NPCs/monsters; create & run shops |
| `/dmgive <player> <item_id> [amount]` | DM | Give D&D items to a player |
| `/combat <start\|add\|remove\|nextturn\|end\|...>` | DM | Manage a combat encounter |
| `/rollforinitiative` | DM | Roll initiative for all combatants and begin combat |
| `/rest <character> <short\|long>` | DM | Trigger a rest for a character |
| `/reloadyaml` | DM/Op | Reload all YAML content |

---

## Running a server

This plugin is great for small groups (≈5–15 players). The quickest dev/playtest setup is to **self-host locally and expose it with [playit.gg](https://playit.gg)** (a tunnel — no router port-forwarding needed):

1. Download **PaperMC 1.21.3** from [papermc.io](https://papermc.io) into a fresh folder.
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

---

## Roadmap / not yet implemented

This is pre-alpha, so several things are intentionally still missing:

- Applying combat damage/healing to HP, death saves, and conditions (combat is being built out issue by issue)
- Character level-up, multiclassing, and feats (all characters are currently level 1)
- In-game equipment management (equip/unequip)
- A handful of races/backgrounds not yet entered as data

Feedback and feature ideas are welcome while the architecture is still flexible.
