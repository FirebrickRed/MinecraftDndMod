# Exploration, Skill Checks & DM Annotation Layer

Design notes for the non-combat / exploration side of the VTT — locked & trapped chests, hidden
traps and doors, skill checks, and social rolls. Captured from playtest-planning discussion so we
can reference and build against it. **Nothing here is built yet** unless a linked issue says so.

## Guiding principles

1. **DM-first, then share.** The game never announces a result straight to the players. It tells the
   **DM** first (roll outcomes, revealed traps, lore text), and the DM gets a **[Share with players]**
   button to push it out. The DM stays the arbiter of what the table learns and when.
2. **No DCs in player-facing prompts.** A player sees *"You see a chest — what do you do?"* and options
   like *[Pick the lock]*, never the DC. The DM knows/sets the DC.
3. **The DM adjudicates the roll.** When a player chooses an action that needs a check, the DM is
   prompted with a ready-to-run command like `/dm check <player> dc 15` (DM fills/adjusts the DC). The
   DM stays in the loop instead of the game auto-resolving.
4. **Both command and tool.** Everything the DM does is available as a `/dm …` command *and* via an
   in-world DM-mode tool (right-click a block to annotate it). Neither is the only path.
5. **Passive where it fits.** Some things are noticed passively (passive Perception = 10 + mod) as a
   player nears them — no roll, DM-notified — very Baldur's Gate. Active searching is a rolled check.

## The reusable primitive: a DM-annotated point

A DM marks a **block or spot** with any combination of:

- **Hidden** — only DMs see it (a marker shown in DM mode). Revealed to a player by a Perception/
  Investigation check vs a DC, or by passive Perception. (Hidden traps, secret doors.)
- **Locked** — opening needs a check (Thieves' Tools / DEX to pick, STR to force). Failure may make
  noise / can't retry without a change.
- **Trapped** — a **spot** DC (Perception), a **disarm** DC (Thieves' Tools/DEX), and on trigger a
  **save** (usually DEX) + **damage/effect**.
- **Inspect text** — DM-written lore shown on interact, optionally gated by a knowledge check.
- **Loot** — a loot table (reuse the corpse-loot system) once opened.

**Player interaction:** right-clicking an annotated block cancels the vanilla action and shows a
contextual menu — *"You see a chest — [Inspect] · [Check for traps] · [Pick the lock] · [Force it]"* —
each option routing through the DM per principles 2–3.

## Scenarios to support

- **Locked / hidden doors & gates** — find with Perception/Investigation; open with Thieves' Tools/STR.
- **Traps** — pressure plates, dart traps, alarm runes: spot → disarm → or trigger (save + damage).
- **Locked / trapped chests** — the MVP; exercises the whole spine.
- **Investigate for clues** — a desk/mural/body reveals DM text or loot on an Investigation/Perception check.
- **Knowledge checks on objects** — Arcana (runes), History (heraldry), Religion (altars), Nature (tracks) → reveal DM lore.
- **Environmental** — a chasm (Athletics to climb), a river, a rickety bridge.
- **Hidden creatures / ambush** — Perception vs a lurker's Stealth; feeds combat surprise.
- **Social checks** — Intimidation / Persuasion / Deception vs a DC, or contested vs Insight (not a block — a DM-called check).
- **Group checks** — party Stealth vs enemies' passive Perception; half succeeding = success.

## DM-called checks — `/dm check`

The roller layer everything hangs off. Sketch:
- `/dm check <player…|all> [dc <n>] [<skill/ability>] [adv|dis]` — prompts the named players (or all)
  with a clickable *"roll <skill>"* button; the DM sees each roll and a **success/fail vs DC** verdict.
- **Contested:** `/dm check <A> vs <B>` (Stealth vs Perception, Deception vs Insight).
- **Passive:** compare a hidden thing's DC against players' passive Perception automatically.
- Reuses the existing sheet roller (`RollOptionsMenuHandler.resolvePhysical`) — advantage/disadvantage,
  Lucky, etc. all already work.
- Per principle 1: results surface to the DM with a **[Share]** control.

## DM-mode active-check log

In DM mode, when the DM right-clicks (to view) — or passively — the DM sees players' **active check
rolls** in chat (e.g. *"Zek rolled Stealth: 17"*), so the DM has the numbers without the players
broadcasting them to the table.

## DM-mode tooling & submenus

- A DM-mode **annotation tool** (a wand-like item) — right-click a block to annotate it (lock/trap/
  hide/desc/loot), mirroring the `/dm object …` commands.
- **Submenus / category items** — instead of 3–4 combat items cluttering the DM hotbar, one **Combat**
  item opens a combat-tools menu; likewise an **Exploration** menu, etc. Keeps DM mode tidy as it grows.

## Open questions / decisions

- Failure/retry rules per interaction (retry allowed? forcing breaks the lock / makes noise?).
- Reveal state tracking: once a hidden thing is shared, is it revealed globally or per-player?
- Tool proficiency: lockpicking/traps assume Thieves' Tools proficiency — factor into the bonus.
- Who may attempt: only the interacting player, or anyone in reach? Help action → advantage.
- Persistence: annotations save per world + coordinates across restarts (mirror the shop persistence).

## Relationship to the roadmap

This exploration/checks layer + combat (done) + character creation (done) + rests + loot is enough for
a **small playtest one-shot** that exercises most of D&D. The remaining big system after that is
**leveling up** (data-driven, tackled one class/level at a time when we're ready).
