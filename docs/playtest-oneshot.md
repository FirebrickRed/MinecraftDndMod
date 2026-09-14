# Playtest setup — interactive objects & checks

Brief, copy-pasteable steps to drop into a one-shot and exercise the exploration/checks layer
(#185/#186). You need one player acting as **DM** (an op, or added via `/dm add <you>`) and at least
one other player with an **active character** (`/character create`, then it's their active sheet).

Reload content first if you edited YAML: `/dm reload`.

---

## 1. Locked chest (the MVP)

Two ways to annotate — pick either:
- **Tool:** enter DM mode (`/dm mode`) → right-click **Exploration Tools** → **Annotate Object** —
  right-click the block for a clickable `[Lock] [Hide] [Reveal] [Clear] [Info]` menu.
- **Commands:** the `/dm object …` forms below (work whether or not you're in DM mode).

As the DM, place a chest and look at it, then:

```
/dm object lock A rusty iron chest, its lock scarred from old attempts
/dm object info                 # confirm: "Chest: locked \"A rusty iron chest…\""
```

Now a **player** right-clicks the chest. They see:
> 🔒 You see a Chest — A rusty iron chest… It's locked.
> Tell the DM how you'd like to open it.

The **DM** gets:
> 🔒 <Player> is trying to open a locked Chest (x, y, z) — **[call a check]**

Click **[call a check]** → it fills `/dm check <Player> skill ` — finish it, e.g.:

```
/dm check <Player> skill sleight_of_hand dc 15
```

The player is prompted to roll (they click **[type your d20]** or **[let the game roll]**). The result
comes back to **you** privately with SUCCESS/FAIL vs the DC and a **[Share with players]** button.
Click it to announce the outcome, then narrate the chest opening (or not).

Remove the annotation anytime: look at the block → `/dm object clear`.

## 2. Locked door — identical, on a door

Look at a door block and `/dm object lock The iron-banded door is barred from the far side.` — same
player/DM flow. Try `athletics` instead of `sleight_of_hand` to force it.

## 3. False wall / hidden passage

Place a normal-looking wall block, look at it, then:

```
/dm object desc A section of wall that sounds faintly hollow.
/dm object hide                 # players get NOTHING when they right-click it yet
```

Players right-clicking feel nothing (it's a plain wall). When someone searches, call a Perception
check:

```
/dm check <Player> skill perception dc 13
```

On a success, reveal it so they can interact:

```
/dm object reveal
/dm object lock                 # (optional) now it's a discovered-but-stuck passage
```

## 3b. Trap (dart trap, hidden)

Look at the block that hides the trap (a pressure plate, a chest, a wall panel) and:

```
/dm object trap 2d10 dex 13     # 2d10 damage, DEX save, reference DC 13
/dm object hide                 # players don't see it coming (optional)
/dm object info                 # "…: hidden trap[2d10 dex DC 13, armed]"
```

When a player right-clicks it, they feel nothing special — but every DM gets:
> 🪤 <Player> is at a trapped Chest (x,y,z) — fires 2d10 on a failed dexterity save (DC 13).
> `[Perception]` `[Disarm]` `[Trigger]`

You decide: `[Perception]` (did they notice?), `[Disarm]` (they try — then `/dm object disarm` on a
success), or `[Trigger]` (it goes off — the button calls the player's DEX save; apply the 2d10 on a
fail — via `/combat damage` if you're in combat, or narrate it out of combat for now).

## 4. Skill checks on their own (no object)

```
/dm check <Player> skill stealth dc 15         # simple, graded
/dm check <Player> skill perception            # ungraded — you just get the number
/dm check <Player> skill insight adv dc 12      # advantage → the game rolls 2d20
/dm check active <Player>                       # see held values (e.g. Stealth)
/dm check clear <Player> stealth                # clear a lingering value
```

## 5. Contested (social)

Two online players:

```
/dm check <A> insight vs <B> deception
```

Both roll their own skill; when both are in, **you** see the winner with a **[Share]** button.

---

## What to watch for (this is all untested in-game)

- Locked block: player right-click shows the lock + DM gets the [call a check] button; the vanilla
  chest/door does NOT open.
- Hidden block: players get nothing until `/dm object reveal`.
- Check results land on the **DM**, not the table, until **[Share]**.
- `adv` actually rolls 2d20 (visible in the breakdown).
- Annotations survive a `/reload` / restart (they persist to `Saved/WorldObjects.yml`).

## Not built yet (so don't expect it)

- Traps (spot/disarm/trigger), loot tables on chests, passive-Perception auto-reveal — later slices of
  #185. The lock outcome is DM-narrated for now (no auto-open/loot).
- A DM tool + skill-picker menu (#187) — for now the DM types the check.
