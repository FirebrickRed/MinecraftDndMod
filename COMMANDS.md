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
| `/character view [name]` | Open your character sheet, or another of **your** characters by name. Other people's sheets are DM-only |
| `/character list [all]` | List your characters (`all` is the same for a player; for a DM it lists the whole table with owners) |
| `/character delete <name>` | **Ask the DM** to delete one of your characters ([Approve]/[Deny]; a DM deletes directly). The file is archived to `Saved/Characters/Deleted/`, never erased. Also removes that character's own gear; DM-given items stay |
| `/character rest short` | Short rest — recover short-rest resources |
| `/character rest long` | Long rest — full HP, spell slots, resources |
| `/character loot <check> <d20>` | Search a body you right-clicked (usually filled by the prompt) |
| `/character check <type> <value> [manualRoll <n> \| autoRoll]` | Resolve a skill/ability/save roll (usually filled by the sheet prompt) |
| `/character cast <spell> [target] [message…]` | Cast a chat/social spell — Message, Speak with Animals (#151) |
| `/character cast <spell> [target] [level <n>] [autoRoll \| manualRoll <n> \| total <n>]` | **Out of combat** (#152). Leave the target blank to aim at what you're looking at. **At a creature or character:** the DM gets **[Start combat]** (a fight with both of you; the DM adds others and marks surprise; your spell comes back on your first turn), **[Let it happen]** (attack roll, then damage, no fight) or **[Deny]**. **At a thing** (a torch on the wall): "Cast it anyway?", you roll to hit, and the DM sees the roll and what you aimed at, with **[Ask for damage]**. **Healing** rolls and applies. Anything else announces. The slot is spent only when the spell goes off. Clicking a spell in your spellbook fills this in |
| `/character damage <autoRoll \| manualRoll <n> \| total <n>>` | Roll the damage for an out-of-combat hit (the prompt fills it in). In a fight it's `/combat damage` |
| `/character drink <item_id> [autoRoll \| manualRoll <n> \| total <n>]` | Drink a healing item. Clicking the potion fills this in for you; in combat it costs your Action |
| `/character reply <message…>` | Free reply to the last Message/Sending you received (usually the **[reply]** button) |
| `/roll <XdY[+Z]>` | Roll dice and show every die, e.g. `/roll 2d6+3` → `[4, 3] +3 = 10` |

DM extras: `/character create <player>` opens creation for another player; `/character give <player> <name>` hands them their sheet; if the character belongs to someone else, a **[Give … to …]** confirm transfers the character to them (its gear stays put — hand it over in game). The old per-action commands (`/createcharacter`, `/viewsheet`, `/shortrest`, `/dmgive`, `/reloadyaml`, …) have been **removed** — everything lives under the four roots below.

**In combat, on your own turn only:**
`/combat action` · `/combat bonusAction` · `/combat attack <target>` · `/combat damage <target>` · `/combat cast <spell>` · `/combat use <feature>` · `/combat endturn` · `/combat deathsave`
Any time you're in the fight: `/combat save` (answer a spell's save) · `/combat reactions …` (your ⚡ opportunity attack, or answering a held attack)

---

## 🎲 DM commands

### Combat — `/combat <subcommand>`
| Subcommand | What it does |
|---|---|
| `start` | Begin a combat session (setup phase) |
| `add <player\|entity> [--hidden]` · `add --radius <blocks> [--hidden]` | Add combatant(s) |
| `remove <name>` | Remove a combatant |
| `surprise <name>` | **Toggle** Surprised on a combatant, before initiative: they didn't see it coming (an ambush, or a friendly chat that turns into a Fire Bolt), so they can't move, act or react on their first turn. Shows **[S]**. Also the **Surprised (ambush)** tool on the DM combat toolbar |
| `initiative <name> <n>` | Manually set initiative |
| `nextturn` · `turn <name>` · `endturn` | Advance / jump / end a turn |
| `status` | Show the initiative order |
| `reveal <name>` · `hide <name>` | Toggle hidden-entity visibility |
| `action` · `action <dash|dodge|disengage|help|hide|ready|…>` | No argument: **what this character can spend their Action on** — the weapon in their hands, Action-cost spells with their slots, Action-activated features — then the generic row (Dodge, Disengage, Help, Hide, Ready, Search). With a name: take that action (#176) |
| `bonusAction` | No argument: **what this character can do with it** — bonus-action spells (with slots left), features like Rage, an off-hand attack when dual-wielding. Everything fills a command rather than firing it (#176) |
| `bonusAction used [target]` | Mark the Bonus Action spent, for anything the engine doesn't model |
| `use <feature>` | Activate a class/racial feature, e.g. `use rage` (Effect Engine, #70) |
| `movement [undo]` | Check / undo movement this turn |
| `attack <target> [weapon] [stab\|throw] [flags]` | **Hit check only**: resolves HIT/MISS/CRIT, and on a hit prompts you with the `/combat damage` command to run. With no weapon you get clickable weapon buttons. Thrown weapons default by distance (#192) |
| `damage <target> [amount] [flags]` | Apply damage — a **player on their own turn**, or the **DM** anytime. With `combat.damage_needs_dm_approval: true` (the default) a player's damage goes to the DM as **[Apply]** / **[Deny]** first; Deny lets them roll again |
| `heal <target> [amount \| manualRoll <n> \| autoRoll <dice> \| total <n>]` | Restore HP |
| `temphp <target> <amount>` | Grant temporary HP |
| `deathsave [<player>] [manualRoll <d20> \| autoRoll]` | Roll a death save (DM may roll for a downed player). Three failures and the character is **dead**, on the sheet, so it outlasts the fight: see `/dm revive` |
| `cast <spell> [target] [level <n>] [manualRoll <d20> | autoRoll | total <n>]` | Cast a combat spell — attack-roll or save; AoE spells aim (no target) (#123, #149). `level <n>` casts from a higher slot; it must come **last**, after the target |
| `cast <ritual_spell> --ritual` · `cast cancel` | Channel a ritual over several turns / cancel it (#156) |
| `save [target] [manualRoll <d20> | autoRoll]` | Roll a saving throw vs a spell (you for yourself; DM for others) |
| `concentration [target] [autoRoll | manualRoll <d20> | total <n>]` | The CON save to keep a concentration spell (or a channelled ritual) going after taking damage. The prompt says what you add before you roll |
| `reactions` | List reactions — a player sees their own; the **DM sees a whole-table roster** (#147) |
| `reactions [<reactor>] <attack|pass>` | Take/pass a provoked opportunity attack (usually the ⚡ end-of-turn buttons) (#147) |
| `reactions pass` | Decline a **held** reaction — this is what releases the attacker's damage (#195) |
| `reactions skip <who|all>` | **DM-only:** answer for a reactor who isn't answering, and let the attack resolve (#195) |
| `finished` | **End combat & clean up** (clears glow, scoreboards, prone) — named distinctly from `endturn`, so no confirm needed |

Initiative is rolled with **`/combat rollforinitiative`** (rolls for all combatants, starts Round 1).

> **Left-click to attack (#189).** On your turn, holding a weapon: **left-click the enemy**, or
> left-click while looking at them, and the game hands you the filled-in `/combat attack` command.
> The click only *prompts* — you still choose your roll mode. Right-click means "use" (spell focus,
> area-effect confirm), never attack.

> **Opportunity attacks hold the mover's turn (#147/#195).** Walk out of an enemy's melee reach and
> nothing happens mid-step (you might step back). A second after you **stop moving**, the whole table
> sees a window: everyone whose reach you left may swing, and **your turn waits** — you can't attack,
> cast, use a feature or end your turn until they've swung or passed. A strike that lands after you've
> already acted isn't an interruption, and it may drop you before you act at all. Disengage still
> means nothing provokes.

> **Reactions hold the attack (#195).** When a hit lands on someone who could react — a character
> with their reaction in hand who knows a spell cast as a reaction, like Shield — the whole table
> sees a **reaction window** open and the attacker's `/combat damage` is **held** until they answer.
> They cast it (`/combat cast shield me`) or decline it (`/combat reactions pass`); the DM can answer
> for them with `/combat reactions skip <who|all>`. If the reaction raised their AC, the attack is
> re-checked against the new number and may become a miss — a crit still lands. Ending the turn is
> blocked while a window is open, because the held damage would go with it. Hitting a creature that
> can't react opens no window at all.

> **Concentration.** Casting a `concentration: true` spell announces it and shows **◈ <spell>** on your
> action bar. Take damage and you're asked for a **CON save, DC 10 or half the damage, whichever is
> higher** — with the modifier spelled out ("you add +3 (+1 CON, +2 proficiency)") so you know what to
> add before picking up the die. **The game never rolls it for you**; you pick `autoRoll`,
> `manualRoll <n>` or `total <n>` like any other d20, and the DM rolls for a creature. Casting a second
> concentration spell drops the first; being knocked out, killed or incapacitated ends it with no save.
> Your turn won't continue until the save is answered.

**Roll input** (attack / cast / save / initiative / deathsave — a d20 action). Pick one, as a bare keyword (no `--`):
- `autoRoll` — the game rolls your d20 for you, applying any advantage/disadvantage (rolls 2d20 and keeps the right one). Works even in physical-dice mode.
- `manualRoll <n>` — you physically rolled `n` (1–20); the game adds your modifiers.
- `total <n>` — a final total you already worked out; nothing is added.
- Give nothing in physical-dice mode and you get a clickable prompt; in auto-roll mode the game rolls.

**Damage roll** (`/combat damage`, `heal`): `manualRoll <n>` (the number you rolled on the damage dice) · `autoRoll <dice>` (the game rolls those dice, e.g. `autoRoll 2d6`) · `total <n>` · a bare `<amount>` for flat damage. The **damage type is automatic** (taken from the hit); add `type <slashing|fire|…>` only to override it. Crit carries over from the attack — no flag.

**Other attack options:** `showModifiers` (show your to-hit breakdown without attacking). Out of range, a DM gets an **[Attack anyway]** button (there is no `--force`).

### Entities & items — `/dm entity <subcommand>`
| Subcommand | What it does |
|---|---|
| `spawn <entityId> [name] [x y z]` | Spawn an entity (from `DMContent/Entities/`), optionally named / placed. Also the DM-mode **Spawn Entity** tool |
| `spawngroup <groupId>` | ⚠️ **Not implemented** — prints a notice (#79) |
| `list` | List spawned entities with their coordinates and world (flagged when it isn't yours) |
| `remove <name>` · `remove all\|dead` · `remove type <creature_type>` · `remove radius <blocks>` | Despawn one or many entities |
| `rename <current> <new>` | Rename a spawned entity, keeping its HP, shop stock and loot. Quote names with spaces — an ambiguous unquoted split is refused, not guessed |
| `revive <name> [hp]` | Bring a dead entity back (default full HP). Same path as `/dm revive`, so it rejoins a fight in progress |
| `teleport <name> [x y z]` | Teleport an entity to you (or to coordinates) |
| `info <name>` | Show an entity's stat block |
| `trade <name>` | Open a merchant's trade GUI (Tab offers merchants only) |
| `shop view <name>` | View a merchant's stock |
| `shop add <name> <item_id> <price> <currency> [stock]` | Add an item (stock `-1` = unlimited). Only works on an entity whose YAML has a `shop:` section; there's no `shop create` |
| `shop restock <name> [item_id] [amount]` | Restock one item, or everything back to the YAML defaults |
| `shop adjust <name> <item_id> <price>` · `shop reset <name> [item_id]` | Override / reset an item's price |
| `shop discount <name> <percent>` · `shop markup <name> <percent>` | Shop-wide price change |
| `shop setfunds <name> <amount> <currency>` · `shop setmultiplier <name> <buy\|sell> <x>` | Merchant money / buy-sell multipliers |
| `cleanup` | Remove orphaned spawned entities the plugin lost track of (post-crash) |

### DM admin — `/dm <subcommand>`
| Subcommand | What it does |
|---|---|
| `add\|remove\|list` | Manage who is a DM (`add`/`remove` op only) |
| `give <player> <item_id> [amount]` | Give a D&D item. The player is required and first (use your own name for yourself) |
| `adjust <character\|creature>` | **Opens the Adjust menu** (#175), the DM's hands on one creature or character, in or out of a fight. Also the **Adjust** tool (blaze rod) in DM mode: right-click someone. HP (click ±1, shift ±5, set exactly, full, temp, max HP for creatures, drop to 0, revive), AC (click ±1 **temporary**, you pick how long: next turn / short rest / long rest / until removed; shift-click ±1 **permanent**, creatures only), conditions (click to toggle), hide/reveal in a fight |
| `adjust <who> hp <n> \| +<n> \| -<n or dice> [type <t>]` | Set HP / heal / damage (`-2d6 type fire` rolls and shows the dice). Same HP path as a hit: resistances, downing, death |
| `adjust <who> full` · `temp <n>` · `maxhp <n>` · `down` · `revive [hp]` | Full HP · temp HP · a creature's max HP (e.g. hit dice you rolled; the spawn message's **[Use my own roll]** fills it in) · drop to 0 · back from the dead |
| `adjust <who> ac +1 [until next_turn\|short_rest\|long_rest\|removed]` · `ac clear` · `ac set <n>` · `ac reset` | Temporary AC change (asks how long if you don't say) · clear it · a creature's own AC for good · back to its stat block |
| `adjust <who> condition <name>` · `condition add\|remove <name>` | Toggle / add / remove a condition. Conditions live on the character or creature: they work out of combat and outlast a fight (only Dodging-type ones end with it) |
| `view <character\|creature>` | **Quick look in chat** (#175): HP and temp HP, AC with every change and where it came from (DM adjustment and how long, a creature's own AC, a spell's bonus), speed, conditions (rules on hover), concentration, death saves, the first DM note, and [Full view] / [Adjust] / [Add a note]. The DM-mode **View** tool does this on right-click |
| `view <who> full` | **Full view** menu, read-only: summary, a button to their sheet / stat block, all DM notes, [Add a note], [Adjust]; a character's actual inventory (backpack, hotbar, armor, off-hand; while online) or what a creature carries and drops, with the check to find each item. The View tool does this on **sneak** + right-click |
| `note <who> add <text…>` · `note <who> clear` · `note <who>` | **DM-only notes** on a character or a spawned creature, saved with them. Players never see them. A creature's YAML `dm_notes:` show alongside and `clear` doesn't touch them |
| `revive <character\|creature> [hp]` | **The only way back from death** (#101), standing in for Revivify / Raise Dead. Default 1 HP. Healing, rests and the fight ending never revive anyone. Damage at 0 HP is a failed death save (two on a crit); massive damage (left over past 0 HP ≥ max HP) kills outright |
| `mode` | Enter/exit **DM mode**: your inventory is swapped for the DM toolbar (View, Adjust, Surprised, Possess, Move, Add/Remove Combatant, Spawn Entity, Exploration tools) and restored on exit |
| `check <player> <ability\|save\|skill> <name> [dc <n>] [adv\|dis]` | Prompt a player to roll. The result comes to **you** (graded vs the DC if given) with a **[Share]** button. `promptcheck` is an alias (#186) |
| `check <player> tool <tool> [ability] [dc <n>] [adv\|dis]` | A check **with a tool** (#207): the ability modifier + proficiency if they have the tool, **doubled with expertise**. The ability defaults to the item's `check_ability:` (thieves' tools → DEX); name it for other tools (`tool smiths_tools int dc 12`). You're told first whether they're proficient and whether they're carrying the tools. Picking a lock is `tool thieves_tools`, not Sleight of Hand (PHB p.154). A **failed** graded thieves' tools check breaks one set by default (BG3-style); set `objects.thieves_tools_break` to `on_fail`, `always` or `never` in config.yml (#210). |
| `check <A> <skill> vs <B> <skill> [autoRoll \| manualRoll <n> \| total <n>]` | Contested check. Either side can be a character (rolls their own die) or a **spawned creature** (the DM rolls: add the roll words inline, or click **[Roll it]** / **[I rolled…]**). The creature uses its stat-block `skills:` bonus, else its ability modifier. The winner comes back to the DM with **[Share]**; a tie changes nothing (PHB p.174). |
| `check active <player>` · `check clear <player> [skill\|all]` | See / clear held check values (e.g. an ongoing Stealth) |
| `object <hide\|reveal\|desc <text>\|clear\|info>` | Annotate the block you're looking at (#185). See `docs/playtest-oneshot.md` |
| `object lock [text]` · `object unlock` · `object seal [text]` | Whether it opens — pick one. `lock` = DM gets a [call a check] ping; `seal` = scenery that never opens and pings no one; `unlock` = back to opening normally |
| `object key <item_id> [single-use]` · `object key none` | The item that opens this lock (#200). A player **carrying** it clicks [Open it] and it opens with no roll, then stays open; nearby players and the DMs see "X unlocks the chest with the Brass Key". Locks the block if it wasn't. `single-use` takes the key from them when it's turned. Ships `iron_key`, `brass_key`, `silver_key`, `ornate_key`; add your own item with `tags: [key]` for a key that fits one lock |
| `object trap <damage> [save] [dc]` · `object disarm` · `object arm` | Trap the block, e.g. `object trap 2d10 dex 13` |
| `object loot <item_id> [xN]` · `object loot clear` · `object give <player>` | Loot on a block with no container, then hand it over |
| `object list [all]` | Every annotation in your world (or all worlds) — clickable coords, a `[Clear]` per row, and **(block gone)** on orphans. Works without looking at anything |
| `object restore` | Put the annotation you just broke onto the block you're looking at |
| `tp <world> <x> <y> <z>` | Teleport (usually clicked from the coordinates in a DM notification) |
| `lootprompt <player> <check>` | Call a loot check for a player searching a body (usually clicked, not typed) |
| `animalreply <player> <message…>` | Voice the animals' reply to a Speak with Animals caster (usually clicked) |
| `rest <character> <short\|long>` | Force a rest (refused for a dead character; a long rest also needs at least 1 HP, PHB p.186) |
| `resource restore <character> <name\|all>` | Restore a class resource |
| `resource consume <character> <name> [amount]` | Spend a class resource |
| `reload` | Reload all `DMContent/` YAML without a restart |

Viewing/giving character sheets is under `/character`: `/character view <name>`, `/character view player <p>`, `/character give <player> <name>`.

---

## 🛠️ Op / admin
DM role management lives under `/dm add\|remove\|list` (see above). `/dm add`/`remove` are op-only.

---

## ⚠️ Operational notes

**Combat, entities, and character HP persist across a restart.** Combat sessions snapshot to
`Saved/CombatSessions/*.yml` and restore on boot (#105); spawned-entity HP and dead/corpse state
ride on the armor stand's persistent data (#89); and character HP, temp HP, spell slots, and
resources save to disk **on every change** and at **each combat turn advance** — no timed autosave
(#31). What a hard crash still loses: the **in-progress combat turn** (it resets fresh), and any
stray turn-indicator glow on armor stands. So before a planned stop it's tidiest to:
1. `/combat finished` (clears glow, scoreboards, prone)
2. `/dm entity remove <name>` for NPCs you don't want lingering

Issues #105 (combat auto-save), #89 (entity persistence), #31 (character auto-save) are done;
the remaining gap is mid-turn state and startup glow-scrub.
