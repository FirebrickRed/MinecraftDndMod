# JK VTT — Command Reference

One-page cheat sheet for every command in the plugin. Source of truth is
`src/main/resources/plugin.yml` (top-level commands) plus the sub-command switches
in the handler classes. In-game, typing a command with no args (e.g. `/combat`)
prints that command's own help.

> **DM authorization:** "DM" = a server op, a player with the `jkvtt.dm` permission
> node, or a player added via `/dm add`. Op is required only for `/dm add`/`/dm remove`.

---

## 👤 Player commands (anyone)
| Command | What it does |
|---|---|
| `/character create` | Start character creation (or right-click your **Character Sheet** paper) |
| `/character view [name]` | Open your character sheet (or view one by name) |
| `/character list` | List your characters |
| `/character close` | Save & close the active character sheet |
| `/character rest short` | Short rest — recover short-rest resources |
| `/character rest long` | Long rest — full HP, spell slots, resources |
| `/character loot <check> <d20>` | Search a body you right-clicked (usually filled by the prompt) |
| `/character check <type> <value> [--roll n]` | Resolve a skill/ability/save roll (usually filled by the sheet prompt) |
| `/character cast <spell> [target] [message…]` | Cast a chat/social spell — Message, Speak with Animals (#151) |
| `/character reply <message…>` | Free reply to the last Message/Sending you received (usually the **[reply]** button) |
| `/roll <XdY[+Z]>` | Roll dice, e.g. `/roll 2d6+3` |

DM extras: `/character create <player>` opens creation for another player; `/character give <player> <name>` hands them their sheet. The old per-action commands (`/createcharacter`, `/viewsheet`, `/shortrest`, `/dmgive`, `/reloadyaml`, …) have been **removed** — everything lives under the five roots below.

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
| `cast <spell> [target] [--roll <d20>]` | Cast a combat spell — attack-roll or save; AoE spells aim (no target) (#123, #149) |
| `cast <ritual_spell> --ritual` · `cast cancel` | Channel a ritual over several turns / cancel it (#156) |
| `save [target] [--roll <d20>]` | Roll a saving throw vs a spell (you for yourself; DM for others) |
| `condition <target> [add\|remove <cond>]` · `condition list` | DM: tag/clear conditions on a combatant (#103, #150) |
| `reactions` | List reactions — a player sees their own; the **DM sees a whole-table roster** (#147) |
| `reactions [<reactor>] <attack\|pass>` | Take/pass a provoked opportunity attack (usually the ⚡ end-of-turn buttons) (#147) |
| `finished` | **End combat & clean up** (clears glow, scoreboards, prone) — named distinctly from `endturn`, so no confirm needed |

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
| `revive <name> [hp]` | Bring a dead entity back (default full HP) |
| `teleport <name>` | Teleport an entity to you |
| `info <name>` | Show an entity's stat block |
| `trade <name>` | Open a merchant's trade GUI |
| `shop create <name>` | Turn an entity into a merchant |
| `shop add <name> <item_id> <amount> <price> <currency>` | Add stock |
| `shop restock <name> <item_id> <amount>` | Restock an item |
| `shop view <name>` | View a merchant's inventory |
| `cleanup` | Remove orphaned spawned entities the plugin lost track of (post-crash) |

### DM admin — `/dm <subcommand>`
| Subcommand | What it does |
|---|---|
| `add\|remove\|list` | Manage who is a DM (`add`/`remove` op only) |
| `give <player> <item_id> [amount]` | Give a D&D item |
| `promptcheck <player> <ability\|save\|skill> <name> [adv\|dis]` | Prompt a player to roll a check |
| `lootprompt <player> <check>` | Call a loot check for a player searching a body (usually clicked, not typed) |
| `animalreply <player> <message…>` | Voice the animals' reply to a Speak with Animals caster (usually clicked) |
| `rest <character> <short\|long>` | Force a rest |
| `resource restore <character> <name\|all>` | Restore a class resource |
| `resource consume <character> <name> [amount]` | Spend a class resource |
| `reload` | Reload all `DMContent/` YAML without a restart |

Viewing/giving character sheets is under `/character`: `/character view <name>`, `/character view player <p>`, `/character give <player> <name>`.

---

## 🛠️ Op / admin
DM role management lives under `/dm add\|remove\|list` (see above). `/dm add`/`remove` are op-only.

---

## ⚠️ Operational notes

**There is no combat crash-recovery yet.** If the server stops mid-encounter, the
combat session, HP changes, and spawned-entity stats are lost, and glowing NPCs stay
glowing. **Before stopping the server:**
1. `/combat finished` (clears glow, scoreboards, prone)
2. `/dmentity remove <name>` for NPCs you don't want lingering
3. `/character rest long` or `/character close` to persist player HP (no auto-save on shutdown)

Tracked in issues #105 (combat auto-save), #89 (entity persistence), #31 (character auto-save).
