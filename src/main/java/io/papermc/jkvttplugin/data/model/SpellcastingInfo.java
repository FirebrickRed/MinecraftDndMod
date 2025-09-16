package io.papermc.jkvttplugin.data.model;

import java.util.List;
import java.util.Map;

public class SpellcastingInfo {
    private String type;
    private String castingAbility;
    private String preparationType;
    private boolean ritualCasting;
    private boolean spellcastingFocus;
    private String spellList;
    private int spellcastingLevel = 1;
    private List<Integer> cantripsKnownByLevel;
    private List<Integer> spellsKnownByLevel;
    private SpellsPreparedFormula spellsPreparedFormula;
    private Map<Integer, List<Integer>> spellSlotsByLevel;
    private String slotRecovery;

    public SpellcastingInfo() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCastingAbility() { return castingAbility; }
    public void setCastingAbility(String castingAbility) { this.castingAbility = castingAbility; }

    public String getPreparationType() { return preparationType; }
    public void setPreparationType(String preparationType) { this.preparationType = preparationType; }

    public boolean isRitualCasting() { return ritualCasting; }
    public void setRitualCasting(boolean ritualCasting) { this.ritualCasting = ritualCasting; }

    public boolean isSpellcastingFocus() { return spellcastingFocus; }
    public void setSpellcastingFocus(boolean spellcastingFocus) { this.spellcastingFocus = spellcastingFocus; }

    public String getSpellList() { return spellList; }
    public void setSpellList(String spellList) { this.spellList = spellList; }

    public int getSpellcastingLevel() { return spellcastingLevel; }
    public void setSpellcastingLevel(int spellcastingLevel) { this.spellcastingLevel = spellcastingLevel; }

    public List<Integer> getCantripsKnownByLevel() { return cantripsKnownByLevel; }
    public void setCantripsKnownByLevel(List<Integer> cantripsKnownByLevel) { this.cantripsKnownByLevel = cantripsKnownByLevel; }

    public List<Integer> getSpellsKnownByLevel() { return spellsKnownByLevel; }
    public void setSpellsKnownByLevel(List<Integer> spellsKnownByLevel) { this.spellsKnownByLevel = spellsKnownByLevel; }

    public SpellsPreparedFormula getSpellsPreparedFormula() { return spellsPreparedFormula; }
    public void setSpellsPreparedFormula(SpellsPreparedFormula spellsPreparedFormula) { this.spellsPreparedFormula = spellsPreparedFormula; }

    public Map<Integer, List<Integer>> getSpellSlotsByLevel() { return spellSlotsByLevel; }
    public void setSpellSlotsByLevel(Map<Integer, List<Integer>> spellSlotsByLevel) { this.spellSlotsByLevel = spellSlotsByLevel; }

    public String getSlotRecovery() { return slotRecovery; }
    public void setSlotRecovery(String slotRecovery) { this.slotRecovery = slotRecovery; }
}
