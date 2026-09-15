# Character Creation — Playtest Guide 🎲

Thanks for helping test this! You're going to make a **Dungeons & Dragons character**
inside Minecraft. You don't need to know D&D — just poke around, have fun, and tell us
what felt good and what felt confusing. **There are no wrong answers and you can't break
anything.**

If a word you don't recognize pops up (like "cantrip" or "proficiency"), that's totally
fine — just note "I didn't know what this meant" and move on.

---

## Getting started

1. **Join the server** (your host will give you the address). If Minecraft asks to
   download a resource pack, say **yes** — that's what makes the fancy icons show up.
2. **Open character creation** one of two ways:
   - Type **`/character create`** in chat (or the short version **`/char create`**), **or**
   - Right-click the paper item named **"Create Character"** in your hotbar.

   Starting creation hands you that **"Create Character"** paper. If you close the menu, right-click
   the paper to pick up exactly where you left off — closing it never loses your progress. When you
   finish, the paper is swapped for your real **Character Sheet**.
3. A menu opens. The **top row** is your navigation — one button per step:
   **Race · Class · Background · Abilities · Choices · Spells · Name**, and a **bed** in
   the top-right corner that says **Finish**.
4. Click a top-row button to open that step; the area below fills in with your options.
   Click things to pick them. The **Finish** bed turns **green** when you've done
   everything required.

That's the whole loop: **pick a tab → make choices → move to the next tab → Finish.**

---

## Your missions

Try as many as you like — each one shows off a different part of creation. Mix and match!

### Mission 1 — The simple warrior
> *Make a **Human Fighter** (or **Barbarian**).*

- Pick the race, pick the class, pick any background.
- On **Abilities**, **left-click** a stat to raise it and **right-click** to lower it.
  Try to make a couple of numbers big.
- On **Choices**, pick your skills and your starting weapon/armor.
- Skip **Spells** (fighters don't have any — the tab should tell you so).
- Name your character and hit **Finish**.

**Watching for:** Did the ability +/- feel obvious? Did the items you got look like real
Minecraft items (a sword, armor), or like broken purple boxes?

### Mission 2 — The spellcaster
> *Make a **Wizard**, **Bard**, or **Cleric**.*

- Get to the **Spells** tab. You'll see sub-tabs like **Cantrips** and **Level 1**.
- Pick your spells — the tab shows a count like **"Cantrips 2/4"** and lists what you've
  picked when you hover it.

**Watching for:** Was it clear how many spells you were allowed to pick, and when you were
done? Could you tell which spells you'd already chosen?

### Mission 3 — The shapeshifter (flexible abilities)
> *Make a **Plasmoid** or **Half-Elf**.*

- These races let you **choose where your bonus points go**. On the **Abilities** tab
  you'll pick a "spread" (like **+2/+1**) and then click the ability icons to place them.

**Watching for:** Did you understand you needed to place the bonus? Was it clear which
ability got the bonus?

### Mission 4 — The specialist (sub-choices)
> *Make an **Elf** (pick a **subrace**) or a **Cleric** (pick a **Divine Domain**).*

- After picking Elf, a **subrace** choice appears. After picking Cleric, a **Divine
  Domain** appears. Pick one.

**Watching for:** Did you notice the extra choice appear? Was it obvious you still needed
to pick it?

---

## After you finish

- Right-click your **Character Sheet** paper (or `/character view`) to see your finished
  character. Poke around it.
- Try `/roll 2d6+3` in chat to roll some dice.

---

## What we'd love to know 💬

Jot down anything, even one-word reactions. A few prompts:

1. **First impression** — when the menu opened, did you know what to do?
2. **Getting stuck** — was there any moment you didn't know what to click, or what a step
   wanted from you?
3. **"Am I done?"** — was it clear when a step was complete, and when the whole character
   was ready to finish?
4. **Anything broken?** — purple/black boxes, weird text, buttons that did nothing,
   things that looked like placeholders.
5. **Words you didn't know** — list any D&D jargon that wasn't explained.
6. **Favorite part** / **most annoying part.**
7. **Would you want to play a game with the character you made?**

Screenshots of anything confusing or broken are gold. Thank you! 🙏

Interesting, when I shut down the server the kobolds I spawned are gone... that is supposed to haappen right?
my brother also has a handaxe, greataxe, and javelin, but only fist and javelin appear. maybe we should switch fist to unarmed.
when attacking an entity he does /combat attack Meepo he wasn't being prompted to use a weapon (javelin or unarmed)
with needing to choose a weapon we should not prompt a --roll or anything until after the weapon is chosen.

### Triage of the notes above

- **Kobolds gone after shutdown** — known, tracked in #166 (entities don't persist across shutdown).
- **Weapon choice on `/combat attack <target>`** — still open. Worth a ticket of its own: the
  command should list the attacker's usable weapons and not prompt for a roll until one is picked.
- **`--roll`** — gone. #183 replaced it with the bare keywords `autoRoll` / `manualRoll <n>` /
  `total <n>`. Any prompt still emitting `--roll` is a bug (one such prompt, for possessed
  entities, was fixed in `9ce3c05`).
- **Attacking now has a click path (#189):** on your turn, holding a weapon, **left-click** the
  enemy and the game fills in the command for you — which sidesteps the "which weapon?" problem
  entirely for the held weapon. Note this was **right-click** in the build the first playtester
  used; it changed deliberately, so say so at the table.