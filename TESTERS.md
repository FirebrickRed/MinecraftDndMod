# Playtest Checklist 🎲

Thanks for testing! Make **one** character, then try a **short fight**. Jot down anything
confusing or broken — one-word reactions are great. You can't break anything.

## 1. Make a character
1. Type **`/character create`** (or right-click the **Character Sheet** paper).
2. Work across the **top-row tabs** (Race → Class → Background → Abilities → Choices →
   Spells → Name), then hit the **Finish** bed (turns green when you're done).
   - **Abilities:** left-click a stat to raise it, right-click to lower it.
3. **Watch for:** Did you know what to do? Any **purple/black boxes**, weird text, or
   buttons that did nothing? Was it clear when each step was complete?
4. `/character view` — does your sheet look right (HP, AC, your items)?

## 2. A short fight (your DM sets this up)
5. On your turn, **walk around** — you should see your HP + Action/Move budget.
6. Attack, two ways:
   - **Right-click your weapon** while looking at the enemy → click the chat prompt → type
     your **d20 roll** (the game adds your modifiers). *Or:*
   - **`/combat attack <target> <weapon>`** (Tab-completes the weapon; `unarmed` works too),
     then apply damage when prompted: **`/combat damage <target>`**.
7. **Watch for:** Could you read the **scoreboard** (your HP, whose turn)? Did the attack
   respect **range** (can't hit something far away with a sword)?
8. If you drop to 0 HP, do **death saves** kick in?

## 3. Tell us (quick)
- Biggest moment of confusion?
- Anything broken or placeholder-looking?
- Favorite part / most annoying part?
- Would you want to play a game with the character you made?

Screenshots of anything weird are gold. Thank you! 🙏

---

# 🔧 Recent changes to verify (for the host)

Newest first. Tick these off after a build to confirm they work in-game.

### Combat
- [ ] **Right-click a weapon on your turn** → clickable attack prompt (aim at target, or
      right-click the enemy directly); fills `/combat attack … --roll`, plus a `[--total]`
      option if you did the math yourself.
- [ ] **Range gate** — melee refuses a far target; bows/crossbows use their range.
- [ ] **Weapon required** — `/combat attack <target>` alone asks for a weapon; `unarmed` works.
- [ ] **/combat damage** only applies once per attack hit (no spamming); `/combat override` bypasses.
- [ ] **Dead things** are skipped on their turn and can't be targeted.
- [ ] **Combat end** — when one side is wiped (or one combatant is left standing), the DM gets a
      one-click **[finish combat]** prompt; `/combat finished` reports **standing vs defeated**.
- [ ] **/combat finished** ends combat (no `confirm` needed).
- [ ] **HP at a glance** — your HP on the scoreboard/action bar; **enemy HP hidden** from players; DM sees all via `/combat status`.
- [ ] **NPC turn** — DM sees the entity's action/move bar + its **attack list**.
- [ ] Boats/mounts are **frozen** when it's not your turn.
- [ ] **Undo movement** works for **players too** (not just entities): `/combat movement undo` on your
      turn snaps you back to where the turn started.
- [ ] **Range rings** (#133): on a combatant's turn — **green** ring = how far it can move (centered
      where the turn began, with a **gold "started here" pillar**); **orange** ring = its attack reach
      (follows it). Waiting players each see a **faint** green ring of their own move range (only they
      see it) to plan. (If you see nothing, check your client's Particles isn't on Minimal.)

### Character creation
- [ ] **Prepared casters** (Cleric/Druid) prepare leveled spells (count = mod + level); changing abilities **resets** prepared spells.
- [ ] **Finish** stays locked until every required step is done.
- [ ] Class/ability/race **icons** show (resource pack on).
- [ ] Clear labels: **Starting Equipment**, **Skill/Tool Proficiencies**.
- [ ] Starting gear shows **real items** (no purple boxes); handaxe/greataxe/scimitar/etc. exist.

### DM tools
- [ ] **`/dm mode`** enters DM mode — your items are stashed and swapped for a **View** tool + **Exit** item.
- [ ] **View tool**: right-click a **player** → their character sheet; right-click an **entity** → its stat block (DM-only).
- [ ] **Possess tool**: right-click an entity → you go invisible, it follows you, your hotbar becomes its kit; **sneak** to stop.
- [ ] **Combat-setup tools** (#130): **Start Combat** (iron sword) begins an encounter; **Add/Remove**
      (book) right-click a player/entity to add (they glow), right-click again to remove; **Roll for
      Initiative** (clock) starts turns. Whole encounter set up without typing commands.
- [ ] **Move tool** (#107, leather boots): right-click entities to **select** them (they glow), then
      right-click the ground to send them there. Works **out of combat** (free) and **in combat**
      (only on that entity's turn, counts vs its speed; `/combat movement undo` reverses). Multi-select
      several before moving.
- [ ] **Exit item** (or `/dm mode` again) restores your real inventory exactly.
- [ ] **Crash-safe:** enter DM mode, then disconnect/kill the server → on rejoin you're OUT of DM mode with your real inventory back.

### Commands & misc
- [ ] Five roots only: `/character` `/roll` `/combat` `/dmentity` `/dm` (old commands gone).
- [ ] **`/character delete <name>`** removes a character (yours; DM removes any).
- [ ] **Entities survive a server restart** (spawn kobolds, damage one, restart → still there with same HP).
- [ ] **`/character view <name>`** works for the DM on any character (no crash).

---

# 📝 Notes / findings

_(Jot bugs, confusions, and ideas here as you test — I'll turn them into fixes/tickets.)_

- 
- 
- 
