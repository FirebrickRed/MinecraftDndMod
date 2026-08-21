# JK VTT — Command Reference

One-page cheat sheet for every command in the plugin. Source of truth is
`src/main/resources/plugin.yml` (top-level commands) plus the sub-command switches
in the handler classes. In-game, typing a command with no args (e.g. `/combat`)
prints that command's own help.

> **DM authorization:** "DM" = a server op, a player with the `jkvtt.dm` permission
> node, or a player added via `/dm add`. Op is required only for `/dm` itself and `/reloadyaml`.

---

## 👤 Player commands (anyone)
| Command | What it does |
|---|---|
| `/createcharacter` | Start character creation (or right-click your **Character Sheet** paper) |
| `/viewsheet` | Open your own character sheet |
| `/closesheet` | Save & close the active character sheet |
| `/shortrest` | Short rest — recover short-rest resources |
| `/longrest` | Long rest — full HP, spell slots, resources |
| `/rolldice <XdY[+Z]>` | Roll dice, e.g. `/rolldice 2d6+3` |

**In combat, on your own turn only:**
`/combat action` · `/combat bonus` · `/combat attack <target>` · `/combat damage <target>` · `/combat endturn` · `/combat deathsave`

---

## 🎲 DM commands

### Combat — `/combat <subcommand>`
| Subcommand | What it does |
|---|---|
| `start` | Begin a combat session (setup phase) |
| `add <player\|entity> [--hidden]` · `add --radius <blocks> [--hidden]` | Add combatant(s) |
| `remove <name>` | Remove a combatant |
| `surprise <name>` | Mark a combatant surprised |
| `initiative <name> <n>` | Manually set initiative |
| `nextturn` · `turn <name>` · `endturn` | Advance / jump / end a turn |
| `status` | Show the initiative order |
| `reveal <name>` · `hide <name>` | Toggle hidden-entity visibility |
| `action [target]` · `bonus [target]` | Mark Action / Bonus Action used |
| `movement [undo]` | Check / undo movement this turn |
| `attack <target> [weapon] [flags]` | **Hit check only** — resolves HIT/MISS/CRIT; on a hit it prompts you with the `/combat damage` command to run |
| `damage <target> [amount] [flags]` | Apply damage — a **player on their own turn**, or the **DM** anytime |
| `override <target> [amount] [flags]` | **DM-only:** apply corrective/extra damage anytime (e.g. a forgotten modifier) |
| `heal <target> [amount] [--roll <dice>] [--total <n>]` | Restore HP |
| `temphp <target> <amount>` | Grant temporary HP |
| `deathsave [<player>] [--roll <d20>]` | Roll a death save (DM may roll for a downed player) |
| `end confirm` | **End combat & clean up** (clears glow, scoreboards, prone) — requires `confirm` so it's not confused with `endturn` |

Initiative is rolled with **`/combat rollforinitiative`** (rolls for all combatants, starts Round 1).

**Attack flags:** `--showmods` (show modifiers, don't attack) · `--roll <1-20>` (your physical d20 face) · `--total <n>` (final number, mods already added)
**Damage flags:** `--roll <dice>` (e.g. `2d6+3`) · `--total <n>` · `--type <slashing|fire|…>` · `--crit`

### Entities & items — `/dmentity <subcommand>`
| Subcommand | What it does |
|---|---|
| `spawn <entityId>` | Spawn an entity (from `DMContent/Entities/`) |
| `spawngroup <groupId>` | Spawn a predefined group |
| `list` | List spawned entities |
| `remove <name>` | Despawn an entity |
| `teleport <name>` | Teleport an entity to you |
| `info <name>` | Show an entity's stat block |
| `trade <name>` | Open a merchant's trade GUI |
| `shop create <name>` | Turn an entity into a merchant |
| `shop add <name> <item_id> <amount> <price> <currency>` | Add stock |
| `shop restock <name> <item_id> <amount>` | Restock an item |
| `shop view <name>` | View a merchant's inventory |

### Character & resource management
| Command | What it does |
|---|---|
| `/dmgive <player> <item_id> [amount]` | Give a D&D item |
| `/viewsheet <characterName>` | View any character by name |
| `/viewsheet player <playerName>` | View a player's character |
| `/givesheet <player> <characterName>` | Give a player their sheet paper (if lost) |
| `/check <player> <ability\|save\|skill> <name> [adv\|dis]` | Roll a check for a player |
| `/rest <character> <short\|long>` | Force a rest |
| `/restoreresource <character> <name\|all>` | Restore a class resource |
| `/consumeresource <character> <name> [amount]` | Spend a class resource |

---

## 🛠️ Op / admin
| Command | What it does |
|---|---|
| `/dm <add\|remove\|list>` | Manage who is a DM (op only) |
| `/reloadyaml` | Reload all `DMContent/` YAML without a restart |

---

## ⚠️ Operational notes

**There is no combat crash-recovery yet.** If the server stops mid-encounter, the
combat session, HP changes, and spawned-entity stats are lost, and glowing NPCs stay
glowing. **Before stopping the server:**
1. `/combat end` (clears glow, scoreboards, prone)
2. `/dmentity remove <name>` for NPCs you don't want lingering
3. `/longrest` or `/closesheet` to persist player HP (no auto-save on shutdown)

Tracked in issues #105 (combat auto-save), #89 (entity persistence), #31 (character auto-save).
