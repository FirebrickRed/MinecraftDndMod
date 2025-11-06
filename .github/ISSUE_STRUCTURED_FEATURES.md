# Structured Class/Subclass Features System

## Overview
Currently, class and subclass features are stored as plain text descriptions in `features_by_level`. While this works for display purposes, it doesn't enable automation of feature mechanics (usage tracking, damage rolls, save DCs, etc.). This issue tracks the design and implementation of a structured features system.

## Current State
**Example from warlock.yml:**
```yaml
features_by_level:
  1:
    - "Fey Presence: Starting at 1st level... Once you use this feature, you can't use it again until you finish a short or long rest."
```

**Problems:**
- Usage limits buried in text ("once per short rest", "proficiency bonus times")
- Can't track how many uses remain
- Can't automate dice rolls or save DCs
- Action economy unclear (action, bonus action, reaction?)
- No scaling information

## Proposed Solution

### Data Model: `Feature` Class

```java
public class Feature {
    private String id;           // Unique identifier
    private String name;         // Display name
    private String description;  // Full text description

    // Mechanics
    private FeatureType type;         // ACTIVE, PASSIVE, RESOURCE_POOL
    private ActionType actionType;    // ACTION, BONUS_ACTION, REACTION, FREE_ACTION, NO_ACTION
    private UsageLimit usageLimit;    // Usage tracking (see below)
    private DiceFormula diceFormula;  // Damage/healing dice (e.g., "1d8 + CHA")
    private SaveDC saveDC;            // Save DC if applicable
    private int range;                // Range in feet (0 = touch, -1 = self)
    private TargetingInfo targeting;  // Area, number of targets, etc.
    private Duration duration;        // Instant, concentration, time-based

    // Scaling
    private List<ScalingRule> scaling;  // How feature changes with level
}
```

### Usage Limit System

```java
public class UsageLimit {
    public enum LimitType {
        UNLIMITED,           // No limit (e.g., always-on passives)
        FIXED,              // Fixed number (e.g., "3 uses")
        PROFICIENCY_BONUS,  // Scales with proficiency bonus
        ABILITY_MODIFIER,   // Based on ability score (e.g., "CHA modifier times")
        FORMULA             // Complex (e.g., "1 + warlock level")
    }

    private LimitType limitType;
    private int baseValue;              // For FIXED
    private Ability abilityModifier;    // For ABILITY_MODIFIER
    private String formula;             // For FORMULA (e.g., "1 + @warlock_level")
    private RestType recovery;          // SHORT_REST, LONG_REST, DAWN, etc.
}
```

### Example YAML (Future State)

```yaml
features_by_level:
  1:
    - id: fey_presence
      name: "Fey Presence"
      type: active
      action_type: action
      description: "Your patron bestows upon you the ability to project the beguiling and fearsome presence of the fey..."
      usage:
        limit_type: fixed
        uses: 1
        recovery: short_rest
      targeting:
        area: cube
        size: 10
        origin: self
      save:
        ability: wisdom
        dc_type: spell_save_dc
        on_fail: charmed_or_frightened  # Player chooses
      duration:
        type: until_end_of_next_turn
```

## Required Components

### 1. Core Data Models
- [ ] `Feature` class with all mechanics fields
- [ ] `UsageLimit` with scaling support
- [ ] `DiceFormula` parser (e.g., "1d8 + @CHA + @proficiency_bonus")
- [ ] `TargetingInfo` (single target, area, self, etc.)
- [ ] `Duration` (instant, concentration, rounds, minutes, hours)
- [ ] `ScalingRule` (damage increases at level X, range increases, etc.)

### 2. YAML Parsing
- [ ] Update `LoaderUtils.parseFeaturesByLevel()` to parse structured features
- [ ] Support both text-only (legacy) and structured formats
- [ ] Validation for required fields based on feature type

### 3. Feature Application System
- [ ] Track active features on `CharacterSheet`
- [ ] Resource tracking (remaining uses, recharge on rest)
- [ ] Spell slot interaction (Healing Light pool, etc.)

### 4. UI/UX
- [ ] Feature activation menu (show available features with uses)
- [ ] Usage tracking display
- [ ] Automated dice roll prompts
- [ ] Save DC calculations

### 5. Action Economy Integration
- [ ] Track actions used in combat turn
- [ ] Prevent using multiple actions when only one available
- [ ] Bonus action vs reaction distinction

## Implementation Phases

### Phase 1: Data Model & Parsing (Foundation)
- Create `Feature` class and related models
- Update YAML schema to support structured features
- Parse structured features alongside text-only features
- **No gameplay changes** - just load and store

### Phase 2: Display & Tracking (Read-Only)
- Display features in character sheet with usage info
- Show "X/Y uses remaining" based on UsageLimit
- Calculate save DCs and damage formulas
- **Still no automation** - DM enforces manually

### Phase 3: Resource Management (Basic Automation)
- Track feature usage (decrement on use)
- Recharge on short/long rest
- Prevent use when no charges remain

### Phase 4: Full Automation (Future)
- Automated dice rolls when feature used
- Apply conditions (frightened, charmed, etc.)
- Combat turn action economy tracking

## Examples to Support

### Simple Active Feature (Fixed Uses)
**Fey Presence** - 1 use per short rest, action, Wisdom save, 10ft cube

### Scaling Damage
**Tentacle of the Deep** - Bonus action, 1d8 cold (2d8 at 10th level), proficiency bonus uses per long rest

### Resource Pool
**Healing Light** - Bonus action, heal Xd6 where X ≤ CHA mod, pool of (1 + warlock level)d6 per long rest

### Ability Modifier Uses
**Form of Dread** - Proficiency bonus uses per long rest, bonus action, grants temp HP

### Complex Formula
**Genie's Wrath** - Damage = proficiency bonus, type depends on genie kind (conditional)

## Edge Cases & Challenges

1. **Conditional Features** - Genie's Wrath damage type depends on player choice
2. **Mutually Exclusive Uses** - Some features share charges with spell slots
3. **Duration Tracking** - Concentration, "until end of next turn", "1 minute"
4. **Area Targeting** - 10ft cube, 60ft cone, 30ft radius sphere
5. **Scaling Complexity** - Some features change multiple aspects (damage AND range)
6. **Multi-Mode Features** - Bottled Respite (multiple ways to use same feature)

## Out of Scope (For Now)

- Spell system integration (use existing `DndSpell` for now)
- Combat automation (initiative, HP tracking)
- Condition tracking system (charmed, frightened, etc.)
- Multi-classing feature interactions

## Success Criteria

✅ DMs can add new features to YAML without touching Java code
✅ Players see clear usage tracking (3/3 uses remaining)
✅ System automatically calculates save DCs and damage
✅ Rest buttons properly recharge features
✅ Backward compatible with text-only features

## Related Issues

- #51 - Racial Traits Implementation (similar pattern)
- #64 - Subclass Support at Level 1 (where this came up)

## Notes

This is a **large architectural change**. Recommend implementing in phases to avoid blocking character creation work. The current text-based system is functional as MVP - structured features enable better UX but aren't required for playability.

Consider whether features should be their own model or extend `InnateSpell` (racial spells use similar mechanics).