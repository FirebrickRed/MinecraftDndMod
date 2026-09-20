# Playtest setup — interactive objects & checks

Brief, copy-pasteable steps to drop into a one-shot and exercise the exploration/checks layer
(#185/#186). You need one player acting as **DM** (an op, or added via `/dm add <you>`) and at least
one other player with an **active character** (`/character create`, then it's their active sheet).

Reload content first if you edited YAML: `/dm reload`.

---

## 1. Locked chest (the MVP)

Two ways to annotate — pick either:
- **Tool:** enter DM mode (`/dm mode`) → right-click **Exploration Tools** → **Annotate Object** —
  right-click the block for a clickable `[Opens] [Locked] [Sealed] | [Hide] [Clear] [Info]` menu.
  The first three are pick-one (the current one shows a ✔) — that's whether the block opens.
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

**The contents.** For a **real chest**, just put the items in the chest the normal Minecraft way —
`locked` only blocks the vanilla open. When the player succeeds, jump to them (click the **(x, y, z)**
in the notification) and:

```
/dm object unlock               # now right-clicking opens the real chest with its real contents
```

`/dm object loot` / `give` is for blocks with **no container** — a false wall, a statue, a body tile —
where you define what's found and hand it over:

```
/dm object loot longsword
/dm object loot gold_piece x25
/dm object give <Player>        # they receive the loot; overflow drops at their feet
```

Remove the annotation anytime: look at the block → `/dm object clear` (or `/dm object loot clear` for just the loot).

## 2. Locked door — identical, on a door

Look at a door block and `/dm object lock The iron-banded door is barred from the far side.` — same
player/DM flow. Try `athletics` instead of `sleight_of_hand` to force it.

## 2b. Sealed scenery — a chest that's just set dressing

For a prop nobody is meant to open: the supply chest in the corner, a bookshelf, a barrel. It never
opens, no roll changes that, and **you don't get pinged** — the description *is* the whole interaction.

```
/dm object seal Just food supplies for the journey — hardtack, salt pork, a wheel of cheese.
/dm object info                 # "Chest: sealed \"Just food supplies…\""
```

A player right-clicking gets the text in grey and nothing else:
> Just food supplies for the journey — hardtack, salt pork, a wheel of cheese.

Seal with no description and they get a flat *"You see a Chest. There's nothing here for you."* —
the command warns you about that, so write the flavor.

**Sealed vs locked** is about whether the party has a way in. `lock` says *try something and I'll
call a check*; `seal` says *this is furniture, move on*. `/dm object unlock` undoes either.

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
fail — `/combat damage` in a fight, or `/dm hp <player> damage 2d10 type piercing` out of one).

## 3c. Breaking an annotated block (build hygiene)

An annotation is stored against bare coordinates, so it would happily outlive its block and get
inherited by whatever you build there next. It doesn't:

- **A DM breaking one clears it** and gets told what was cleared. `/dm object restore` puts it on the
  next block you look at — that's the "I moved the chest one block over" case.
- **A player can't break one at all.** Otherwise mining a locked chest bypasses the lock for free.
- **`/dm object list`** shows every annotation in your world, nearest first, with clickable coords,
  a `[Clear]` per row, and **(block gone)** on any whose block is missing. It's the only sub that
  works while you're looking at nothing — which is exactly the situation an orphan leaves you in.
  `/dm object list all` covers every loaded world.

Worth trying: annotate a chest, break it, `/dm object list` (should be gone), then re-annotate one,
break it, and `/dm object restore` onto a different block.

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
- Sealed block: player sees only the description, the chest does NOT open, and **no DM is pinged**.
- Description shows on every path — sealed, locked, trapped and plain-openable blocks alike.
- Hidden block: players get nothing until `/dm object reveal`.
- Check results land on the **DM**, not the table, until **[Share]**.
- `adv` actually rolls 2d20 (visible in the breakdown).
- Annotations survive a `/reload` / restart (they persist to `Saved/WorldObjects.yml`).

## Not built yet (so don't expect it)

*(Updated 2026-09-17. Traps, container-less loot and the DM-mode Annotate Object tool have all
shipped since this was written.)*

- **Passive-Perception auto-reveal** and **group checks**: the DM calls each check (#185/#186).
- **Auto-open on a successful pick / auto-damage on a failed trap save**: the DM unlocks with
  `/dm object unlock` and applies the damage with `/dm hp <player> damage <dice>` (which now works
  out of combat) — the trap doesn't fire either by itself yet.
- **Contested checks against an NPC**: player vs player only. Roll the NPC's side with `/roll`.
- **A skill-picker menu** for the DM: checks are still typed (or filled from the buttons).
