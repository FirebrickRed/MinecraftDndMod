# JK VTT — Manual Test Plan

A living checklist of things to verify by hand, updated as each feature lands. Mirrors and expands GitHub issue #106 (multiplayer checklist). Check items off during playtests; note bugs as GitHub issues.

**Legend:** `[ ]` untested · `[x]` passing · `[!]` known broken (link issue)

---

## ⭐ Priority for the 2-player session (DM + brother)

The highest-value checks — multiplayer-only behavior and things changed in the last review pass.
Detailed versions live in the sections below.

**Must-hit:**
- [ ] **Freeze:** on his turn he moves freely; when it's NOT his turn he's frozen but can still look around; **DM is never frozen**
- [ ] **No puppeting monsters:** on a *monster's* turn, brother's `/combat attack` and `/combat action` are **refused**
- [ ] **No death cheating:** while downed, `/combat deathsave --roll 20` **ignores his roll**; a 2nd `/combat deathsave` that turn is **rejected**
- [ ] **…but legit rolls work:** on his own turn while downed, plain `/combat deathsave` **does** roll
- [ ] **Sync:** you both see identical initiative order, HP changes, and death-save pips live
- [ ] **Turn permissions:** his `/combat attack|bonus|endturn|deathsave` work on his turn; DM-only cmds (`add`, `nextturn`, `damage`) are blocked for him
- [ ] **Full down loop with HIM dropping:** damage him to 0 → unconscious/prone/auto-prompt → he rolls saves → heal to revive; then damage-while-down = auto-fail, nat-1 = two failures
- [ ] **Nameplate:** each of you sees the OTHER's character name as the chat/tab suffix (#71)

**🔁 Re-verify after the round-2 fixes (rebuild first):**
- [ ] Non-DM CANNOT possess an armor stand (right-click does nothing), in AND out of combat
- [ ] Non-DM CANNOT `/combat endturn` on a **monster's** turn (refused)
- [ ] On-hit apply-damage prompt runs clean: flat-damage weapon → `/combat damage <t> 6`; dice weapon → `/combat damage <t> --roll 2d4+2`
- [ ] `/combat attack <t> --total 20` shows "Roll: 20 (provided total)"
- [ ] `/combat heal <deadEntity>` says "is dead and cannot be healed"
- [ ] A downed player is genuinely movement-locked (Slowness) on their death-save turn
- [ ] Core auth suite with him **non-op & non-DM**: can't puppet the wolf's attack/action/endturn; CAN do his own attack→damage on his turn

**Setup tips:**
- Use a resistance-capable character (tiefling=fire, dwarf=poison) if testing `--type` damage halving — else it shows nothing.
- Don't restart the server mid-encounter (no crash recovery yet) — `/combat end` first.

**Expected — NOT bugs, don't chase:**
- Hidden entities show `???` to *everyone* incl. the DM in attack logs (stopgap; real per-viewer names = #102)
- Stabilized players aren't auto-skipped (DM `/combat nextturn` past them)
- Only *resistance* works (vulnerability/immunity have no data yet)
- A typo'd weapon name silently becomes an unarmed strike (#109)

---

## Setup smoke test (do this first on any new server)
- [ ] Server starts; console shows `D&D Plugin has been enabled!`
- [ ] Console shows D&D content loading (races, classes, spells, weapons, etc.) with no stack traces
- [ ] `DMContent/` folder is present at server root (no "0 loaded" warnings)
- [ ] `/createcharacter` opens the creation menu
- [ ] A finished character's sheet item, right-clicked, reopens the view menu
- [ ] `/dm add <you>` grants DM commands; non-DMs are correctly blocked
- [ ] A **non-op** player added via `/dm add` CAN use `/dmentity`, `/dmgive`, `/combat`, `/rollforinitiative`, `/check`, `/givesheet` (permission-gate fix)
- [ ] A non-op, non-DM player is refused those DM commands (internal isDM check still enforces)
- [ ] A non-op player in combat CAN use `/combat attack|endturn|bonus|deathsave` on their own turn

---

## Combat — Initiative & Session (Issue #97) ✅ committed
### Solo
- [ ] `/combat start` creates a session in setup phase
- [ ] `/combat add <player>` and entity adds work
- [ ] `/rollforinitiative` rolls all combatants and orders the scoreboard correctly
- [ ] `/combat status` shows the current order
- [ ] `/combat end` tears the session down cleanly
### Multiplayer
- [ ] All players see the initiative scoreboard with the same order
- [ ] Hidden entities show `???` to non-DM players but real names to the DM
- [ ] `/combat surprise <name>` and `/combat reveal <name>` update displays for everyone

## Combat — Turns & Action Economy (Issue #98) ✅ committed
### Solo
- [ ] Active combatant glows
- [ ] `/combat action`, `/combat bonus`, `/combat endturn` work on your turn
- [ ] Action bar shows remaining action/bonus/movement for the active player
- [ ] `/combat movement undo` teleports back correctly
### Multiplayer
- [ ] Non-active players are frozen (can't move) but can still look around
- [ ] DM is never frozen
- [ ] "Not your turn" error when acting out of turn
- [ ] Non-DMs cannot use DM-only subcommands (add/remove/nextturn)
- [ ] Players move freely during setup (before initiative)

## Combat — Attack Rolls (Issue #99) 🚧 uncommitted WIP
### Solo
- [ ] `/combat attack <target>` auto-rolls d20 + mods, compares to AC, announces HIT/MISS
- [ ] `/combat attack <target> --showmods` shows the modifier breakdown without attacking
- [ ] `/combat attack <target> --roll <n>` uses a player-provided d20 + mods
- [ ] `/combat attack <target> --total <n>` compares a final total directly to AC
- [ ] Attack shows **HIT/MISS only** — no damage number
- [ ] On a HIT, the attacker (and DM) get a private "→ Apply damage: `/combat damage <target> --roll <formula>`" prompt, pre-filled with the weapon's damage
- [ ] Natural 20 → CRITICAL HIT; the apply-damage prompt notes dice are doubled
- [ ] Natural 1 → CRITICAL MISS (auto-miss)
- [ ] Finesse weapon uses the better of STR/DEX; proficiency added only when proficient
- [ ] Unarmed strike works with no weapon equipped
- [ ] Entity attack uses its YAML `to_hit`/`damage` (DM acts on entity's turn)
- [ ] `--weapon`/positional weapon name selects a specific inventory weapon
- [ ] Attack itself does **NOT** change HP — HP only changes via the follow-up `/combat damage`
- [ ] Tab completion: targets after `attack`, weapons/flags after the target
### Multiplayer
- [ ] Attack results broadcast to all combatants in the session
- [ ] Hidden attacker/target names show as `???` to non-DM players
- [ ] A non-DM can only attack on their own turn
- [ ] On a **monster's** turn a non-DM player CANNOT `/combat attack` (can't drive the monster)
- [ ] `/combat attack <target> --roll 30` is rejected (d20 must be 1–20)

## Combat — Damage & Healing (Issue #100) 🚧 uncommitted WIP
### Damage
- [ ] `/combat damage <target> 14` applies 14 flat; HP drops and broadcasts before → after
- [ ] `/combat damage <target> --roll 2d6+3` rolls, shows the roll, applies it
- [ ] `/combat damage <target> --total 17` applies 17 directly
- [ ] `--type fire` on a resistant target halves damage (rounded down) with a RESISTANT note
- [ ] `--type <t>` on a vulnerable target doubles (VULNERABLE note) — needs data to test
- [ ] Immunity zeroes damage (IMMUNE note) — needs data to test
- [ ] Multi-word target works: `/combat damage "Goblin Chief" 8` and `/combat damage Goblin Chief 8`
- [ ] Tab completion: targets after `damage`, `--type` suggests damage types, flags suggested
### Temp HP
- [ ] `/combat temphp <player> 10` grants 10 temp HP
- [ ] Next damage is absorbed by temp HP first (temp → 0), remainder hits real HP
- [ ] Temp HP does not stack — granting a lower value keeps the higher
- [ ] `/combat temphp <entity> 10` reports entities can't gain temp HP
### Healing
- [ ] `/combat heal <target> 10` restores HP, never exceeding max
- [ ] `/combat heal <target> --roll 2d8+3` rolls and heals
- [ ] Overheal caps at max HP (shows the actual amount healed)
### Death triggers (handoff to #101)
- [ ] A player reduced to 0 HP broadcasts "falls unconscious!" and resets death saves
- [ ] An entity reduced to 0 HP broadcasts "is defeated!" and is marked dead
- [ ] Damage to an already-unconscious player = automatic death save failure (2 with `--crit`)
- [ ] Healing an unconscious player above 0 restores consciousness and resets death saves
### Player-applied damage & DM override (new flow)
- [ ] A **player** can `/combat damage <target> <amt>` on their OWN turn (applies the damage they rolled)
- [ ] A player is refused `/combat damage` when it is NOT their turn
- [ ] `/combat override <target> <amt>` (DM-only) applies corrective damage anytime; a player is refused it
### Multiplayer
- [ ] All combatants see damage/heal broadcasts
- [ ] Non-DM players cannot run heal/temphp (DM-only); damage IS allowed on their own turn

## Combat — Death Saves (Issue #101) 🚧 uncommitted WIP
### Going down & rolling
- [ ] A player dropped to 0 HP falls unconscious, gets Slowness (prone), and death saves reset
- [ ] On the downed player's turn, combat prompts "must make a death saving throw / Type /combat deathsave"
- [ ] `/combat deathsave` (self) auto-rolls d20: ≥10 SUCCESS, <10 FAILURE, with a pip tally
- [ ] `/combat deathsave --roll 8` uses a provided d20 result
- [ ] DM can roll for a player: `/combat deathsave <player>`
- [ ] A non-DM can only roll for themselves
### Special results
- [ ] Natural 20 → regain 1 HP + consciousness, prone removed, saves reset
- [ ] Natural 1 → counts as TWO failures
- [ ] 3 successes → STABILIZED (skips turn, no more prompts)
- [ ] 3 failures → DIED
### Interactions with #100
- [ ] Damage while down = automatic failure (2 with `--crit`)
- [ ] Healing above 0 while down = regain consciousness, prone removed, saves reset
### Display / cleanup
- [ ] Scoreboard shows ☠ skull + success/failure pips for downed players, [DEAD] when dead
- [ ] Ending combat removes the prone/Slowness effect from any downed player
### Multiplayer
- [ ] All players see death-save rolls and outcomes broadcast
- [ ] Downed player can still look around (prone = slowness, not a freeze)
- [ ] A downed player CANNOT self-revive with `/combat deathsave --roll 20` (player-supplied roll ignored)
- [ ] Only one death save per turn; a second `/combat deathsave` that turn is rejected
- [ ] A death save is only allowed on the downed player's own turn
- [ ] A player who *dies* has their prone/Slowness removed

## Character Sheet Commands (Issue #47) 🚧 uncommitted WIP
- [ ] `/viewsheet` opens your own active character
- [ ] `/viewsheet` with no character tells you to `/createcharacter`
- [ ] `/viewsheet <characterName>` (DM) opens any saved character by name
- [ ] `/viewsheet "Two Word Name"` handles spaces via quotes
- [ ] `/viewsheet player <playerName>` (DM) opens that player's character
- [ ] Non-DM using `/viewsheet <name>` or `player <name>` is refused
- [ ] `/givesheet <player> <characterName>` (DM) puts the sheet paper in their inventory
- [ ] Full inventory → sheet drops at the player's feet with a warning
- [ ] The given paper right-clicks open to the correct character
- [ ] Tab completion: character names + `player`; player names after `player`

## DM Checks with Advantage (Issue #61 MVP) 🚧 uncommitted WIP
- [ ] `/check <player> ability STR` rolls a STR check for that player and broadcasts it
- [ ] `/check <player> save DEX` rolls a DEX save
- [ ] `/check <player> skill stealth` rolls Stealth (multi-word skills use underscores, e.g. `animal_handling`)
- [ ] `adv` rolls 2d20 keep highest; `dis` rolls 2d20 keep lowest (shown in the breakdown)
- [ ] Ability accepts both `STR` and `strength`
- [ ] Unknown ability/skill/type gives a clear error
- [ ] Offline player or player with no active character gives a clear error
- [ ] Non-DM cannot use `/check`
- [ ] Output format matches the character-sheet roll menu
- [ ] _Deferred (needs party system #42): clickable prompts, `@party` targeting_

---

## 🎲 Scripted Practice Encounter — "Wolves at the Gate"

A guided run that exercises the whole combat chain in ~10 minutes. A DM can do most of
it solo by controlling one test "Hero" character and driving the monsters; a second
person lets you also check the multiplayer/freeze items. Legend: `→` = expected result.

**Tip:** pass `--roll <n>` / `--total <n>` on attacks, damage, and death saves so you
control the dice and can hit each branch on purpose.

### 0. Prep
```
/dm add <you>                     → you can run DM commands (non-op is fine)
```
Have a **Hero** character created (yours or a test player's), standing near you.

### 1. Set the stage
```
/dmentity spawn wolf              → a Wolf armor stand appears (HP 11, AC 13, Bite +4, 2d4+2)
/dmentity spawn wolf              → a second Wolf ("Wolf #1" / "Wolf #2" once both are in combat)
/combat start                     → session begins in setup phase
/combat add --radius 20           → Hero + both Wolves pulled into combat
/rollforinitiative                → scoreboard shows the initiative order; Round 1 begins
```
Read the scoreboard for the exact combatant names (e.g. `Wolf #1`).

### 2. Act I — the Hero fights (attacks + damage + entity death)
On the Hero's turn:
```
/combat attack Wolf --showmods    → shows attack bonus breakdown, no attack made
/combat attack "Wolf #1" --roll 18 → HIT/MISS vs AC 13; damage dice shown (NOT applied)
/combat attack "Wolf #1" --total 25 → auto-HIT
/combat attack "Wolf #1"          → auto-rolls d20; a nat 20 → CRITICAL HIT, doubled dice
```
Now the DM applies the damage the roll showed:
```
/combat damage "Wolf #1" 11       → HP 11 → 0; "Wolf #1 is defeated!"
/combat damage "Wolf #1" --roll 2d4+2 → (already dead) confirm no crash / sane message
```

### 3. Act II — the Hero goes down (unconscious + death saves)
Advance to a Wolf's turn (`/combat nextturn` as needed), then attack the Hero and drop them:
```
/combat attack <HeroName>         → Wolf attacks Hero (entity attack, Bite +4)
/combat damage <HeroName> --roll 2d4+2   → repeat until Hero hits 0 HP
                                  → "Hero falls unconscious!", gets Slowness (prone),
                                     death saves reset
```
On the Hero's next turn:
```
                                  → auto-prompt: "UNCONSCIOUS ... Type /combat deathsave"
/combat deathsave <HeroName> --roll 15   → SUCCESS  (●○○ successes)
/combat deathsave <HeroName> --roll 8    → FAILURE  (●○○ failures)
/combat deathsave <HeroName> --roll 20   → NAT 20 → regain 1 HP + consciousness, prone removed
```
Knock them down again, then test the lethal + auto-fail branches:
```
/combat damage <HeroName> 50      → unconscious again
/combat damage <HeroName> 5       → damage while down = automatic death save FAILURE
/combat damage <HeroName> 5 --crit → crit while down = TWO failures
/combat deathsave <HeroName> --roll 1 → NAT 1 = two failures (→ DIED if it reaches 3)
```
Then verify healing revives (knock down once more if needed):
```
/combat heal <HeroName> 5         → regains consciousness, prone removed, saves reset
```

### 4. Act III — temp HP, resistance, hidden enemy
```
/combat temphp <HeroName> 10      → gains 10 temporary HP
/combat damage <HeroName> 6       → temp HP absorbs 6 (10 → 4), real HP unchanged
/combat damage <HeroName> 8 --type fire → if Hero resists fire (e.g. tiefling): "RESISTANT", halved
```
Hidden enemy (needs the second person to confirm the `???` view):
```
/dmentity spawn kobold            → a Kobold (random name, e.g. "Skrix")
/combat add <koboldName> --hidden → added hidden; players see "???", DM sees the name
/combat reveal <koboldName>       → name revealed to everyone
```

### 5. Wrap — sheet + check commands
```
/viewsheet                        → opens your own sheet
/viewsheet <HeroName>             → (DM) opens the Hero's sheet by name
/givesheet <player> <HeroName>    → hands the Hero's sheet paper to a player
/check <player> save DEX advantage → rolls a DEX save w/ advantage, broadcast to all
/check <player> skill stealth     → rolls Stealth
```

### 6. Teardown
```
/combat end                       → session ends, scoreboards reset, prone/Slowness cleared
```

> As you run this, tick the matching boxes in the sections above. Anything that misbehaves → note it as a GitHub issue with the exact command and what happened.

---

## Non-combat regression checks
- [ ] `/shortrest` / `/longrest` only affect the running player and restore correctly
- [ ] Skill/ability rolls broadcast with a correct breakdown
- [ ] Shop: two players can trade the same merchant; stock syncs (#75)
- [ ] Characters persist across a server restart


Notes for stuff that broke:
When creating a character he choose a race, class, and background, but was not prompted to check ability scores and was able to complete... not sure if we want that or not. I think he may have also chosen a race that needs a sub class chosen. He also didn't get to choose his equipment because he clicked finished
if a race gives plus two to an ability can intellegence (example) go above 20?
For some reason he is prompted to select a level 1 spell but gets angry because it's saying he gets 0 level one spells or something, I believe he chose the cleric class and dark elf race so it might just be showing the level 1 spells by default even though it just wants him to select cantrips
the nameplate also isn't changing which I think we can't change a players name but thats fine, I need to confirm if scoreboard one works or not.
he named his character "YO MAMA" and I tried doing /combat add "YO MAMA" and it didn't work are we accounting for quotes? His account/user name worked fine for adding him though
Lol, I can't figure out how to mark people as hidden smh
Did we incourporate pc's being able to roll their own initiative? I also don't like how the command is /rollforinitiative I think it should be more like /combat rollforinitiative
their movement speed is 30ft but they aren't getting the warning of going to far
probably should allow the pc to use their weapon in combat as their action
bruh, it is saying it's "YO MAMA" turn in the score board but it's not letting them take their turn at all
the pc isn't glowing at all either
lol he can take control of my wolf npc
we need to also make sure that even tho it's a server op they aren't neccissarily the dm or given dm permissions.
how can we check health and remaining health and etc.
I removed him as op and it's his turn again and it's showing his movement.
it's not letting him do /combat on his turn. 
his health also isn't going down. 
and when I tried /combat attack YO MAMA bite --roll as a wolf 
we have /combat attack which does both "do you hit" and "how much damage do you do" which needs to be seperated out so that when a player goes to attack they can see if they hit and then see how much damage they do.
I like the idea of a player holding a weapon and they can click on an entity to attempt to attack but I also like the command for ranged characters.
------

brother is able to possess entity outside of combat... probably don't want that
to be clear he is not dm or op and in survival
and can still possess in combat
can't jump in combat when not turn, and can't fall down holes...
can we stop giving the player paper when possessing an entity
when in a boat he can move around the map...
same with pig
but then dismounting is then stuck in that spot
bro he ended the wolf's turn on the wolf's turn >.>
he then hit the wolf and was prompted to put in the command /combat damage Wolf --roll 6 --type bludgeoning but then when using that command gave him an error that said invalid dice expression: 6
which not sure what the 6 is
I ended his turn and made it the wolfs turn and tried to heal the wolf with /combat heal Wolf 20 but it still showed 0/11 as it's health
I did /combat attack YO MAMA bite --total 20 and in the attack roll had Roll: (total provided) we should still print the number provided there
the apply damage message did have a --roll 2d4 + 2 so might just be a character sheet pc issue
he does take the correct number of damage in the character sheet
lol it shows the wolf as dead but I can still do commands as it... I don't mind because of testing purposes
I brought his health down to 0 and it said he  needs to make death saving throws
he was able to make death saving throws is there a way to make him prone when he is downed?
is there anything else I should test?

1. /createcharacter → new single-pane menu. (Right-clicking a blank sheet should also open it.)
2. Race → Elf → subrace appears nested; Race tab ⚠ then ✓.
3. Class → Cleric → domain (subclass) appears nested.
4. Abilities → left-click +1, right-click −1; number shows as the item's stack count.
5. Choices / Spells → open existing menus, then land back on the new menu.
6. Name → test both buttons (anvil and chat).
7. Finish → locked until complete (lists missing), then creates the character + gives the sheet item.