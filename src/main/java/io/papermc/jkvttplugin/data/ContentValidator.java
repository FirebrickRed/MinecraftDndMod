package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.data.loader.*;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.model.*;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.data.model.enums.ToolRegistry;
import io.papermc.jkvttplugin.util.DiceRoller;
import io.papermc.jkvttplugin.util.TagRegistry;
import org.bukkit.Material;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Load-time sanity checks across all DMContent, run after every load / {@code /dm reload}.
 *
 * <p>The loaders parse leniently — a typo usually doesn't fail, it just quietly doesn't work (a shop row
 * that never appears, armor that can't be worn, a Cleric who can't use a holy symbol). This pass looks
 * for exactly those silent failures and logs one warning each. It never refuses to load anything.
 * A clean load prints nothing. See docs/authoring-items.md, authoring-entities.md, authoring-spells.md.
 */
public final class ContentValidator {
    private static final Logger LOGGER = Logger.getLogger("ContentValidator");
    private static final Pattern QTY_SUFFIX = Pattern.compile("^(.+?)\\s+x(\\d+)$");
    private static final Set<String> AOE_SHAPES = Set.of("sphere", "cone", "line", "burst");
    private static final Set<String> AOE_TARGETS = Set.of("all", "enemies", "allies");
    private static final Set<String> SAVE_EFFECTS = Set.of("half", "none");
    /** The highest amount a single merchant trade slot can hold (#94). */
    private static final int MAX_TRADE_STACK = 64;

    private int warnings = 0;
    /** Spell ids referenced by classes/races that don't exist — reported as one summary line. */
    private final Set<String> missingSpellRefs = new TreeSet<>();

    private ContentValidator() {}

    public static void validateAll() {
        ContentValidator v = new ContentValidator();
        v.checkWeapons();
        v.checkArmor();
        v.checkItems();
        v.checkSpells();
        v.checkClasses();
        v.checkRaces();
        v.checkBackgrounds();
        v.checkEntities();
        if (!v.missingSpellRefs.isEmpty()) {
            // Info, not a warning: subclasses list their spells for every level, and higher-level spells
            // simply haven't been authored yet. It becomes a real problem only for a spell a character can
            // actually get now — check this list when a domain/patron spell seems to be missing.
            LOGGER.info(v.missingSpellRefs.size() + " spell(s) referenced by classes/races aren't defined yet: "
                    + String.join(", ", v.missingSpellRefs));
        }
        if (v.warnings > 0) {
            LOGGER.warning("Content check finished with " + v.warnings + " warning(s) — see above.");
        }
    }

    private void warn(String message) {
        warnings++;
        LOGGER.warning(message);
    }

    // ==================== WEAPONS / ARMOR / ITEMS ====================

    private void checkWeapons() {
        for (DndWeapon w : WeaponLoader.getAllWeapons()) {
            String where = "Weapon '" + w.getId() + "'";
            if (blank(w.getName())) warn(where + " has no name.");
            if (!"simple".equalsIgnoreCase(w.getCategory()) && !"martial".equalsIgnoreCase(w.getCategory())) {
                warn(where + " category should be simple or martial (got '" + w.getCategory()
                        + "') — it won't join the weapon tags or proficiency groups.");
            }
            if (!w.isMelee() && !w.isRanged()) {
                warn(where + " type should be melee or ranged (got '" + w.getType() + "').");
            }
            if (blank(w.getDamage())) warn(where + " has no damage dice.");
            else if (!isDice(w.getDamage())) warn(where + " damage '" + w.getDamage() + "' isn't a dice expression like 1d8.");
            if (blank(w.getDamageType())) warn(where + " has no damage_type.");
            checkMaterialName(where, w.getMaterial());
            if (w.isRanged() && w.getNormalRange() <= 0) warn(where + " is ranged but has no range: (e.g. \"80/320\").");
            if (w.hasProperty("thrown") && w.getNormalRange() <= 0) warn(where + " is thrown but has no range: — it can only stab.");
            if (w.usesAmmunition()) {
                if (blank(w.getAmmunition())) {
                    warn(where + " has the ammunition property but no ammunition: <item id> — it fires without consuming anything.");
                } else if (!itemExists(w.getAmmunition())) {
                    warn(where + " fires ammunition '" + w.getAmmunition() + "', which isn't a defined item.");
                }
            } else if (!blank(w.getAmmunition())) {
                warn(where + " names ammunition: but lacks the \"ammunition\" property, so it's ignored.");
            }
        }
    }

    private void checkArmor() {
        for (DndArmor a : ArmorLoader.getAllArmors()) {
            String where = "Armor '" + a.getId() + "'";
            if (blank(a.getName())) warn(where + " has no name.");
            String cat = a.getCategory() == null ? "" : a.getCategory().toLowerCase();
            if (!Set.of("light", "medium", "heavy", "shield").contains(cat)) {
                warn(where + " category should be light, medium, heavy or shield (got '" + a.getCategory() + "').");
            }
            if (a.getBaseAC() <= 0) warn(where + " has no ac:.");
            Material m = a.getMaterial();
            boolean wearable = "shield".equals(cat) ? m == Material.SHIELD : m.name().endsWith("_CHESTPLATE");
            if (!wearable) {
                warn(where + " material " + m + " can't be worn in the " + ("shield".equals(cat) ? "off-hand (use SHIELD)"
                        : "chestplate slot (use e.g. LEATHER_CHESTPLATE)") + ", so it will never count toward AC.");
            }
        }
    }

    private void checkItems() {
        Set<String> classFocusTypes = new HashSet<>();
        for (DndClass c : ClassLoader.getAllClasses()) {
            if (c.getSpellcastingInfo() != null && !blank(c.getSpellcastingInfo().getSpellcastingFocusType())) {
                classFocusTypes.add(c.getSpellcastingInfo().getSpellcastingFocusType().toLowerCase());
            }
        }
        Set<String> itemFocusTypes = new HashSet<>();
        for (DndItem i : ItemLoader.getAllItems()) {
            String where = "Item '" + i.getId() + "'";
            if (blank(i.getName())) warn(where + " has no name.");
            checkMaterialName(where, i.getMaterial());
            boolean isFocus = "spellcasting_focus".equals(i.getType());
            if (isFocus && blank(i.getFocusType())) {
                warn(where + " is a spellcasting_focus with no focus_type — no class can use it.");
            } else if (!isFocus && !blank(i.getFocusType())) {
                warn(where + " has focus_type but its type isn't spellcasting_focus, so right-clicking it does nothing.");
            }
            checkAmount(where, "healing", i.getHealing());   // a potion's dice must be rollable
            if (isFocus && !blank(i.getFocusType())) {
                String ft = i.getFocusType().toLowerCase();
                itemFocusTypes.add(ft);
                if (!"component".equals(ft) && !classFocusTypes.contains(ft)) {
                    warn(where + " focus_type '" + i.getFocusType() + "' matches no class's spellcasting_focus_type "
                            + classFocusTypes + " — nobody can cast with it.");
                }
            }
        }
        for (DndClass c : ClassLoader.getAllClasses()) {
            if (c.getSpellcastingInfo() == null || blank(c.getSpellcastingInfo().getSpellcastingFocusType())) continue;
            String ft = c.getSpellcastingInfo().getSpellcastingFocusType().toLowerCase();
            if (!itemFocusTypes.contains(ft)) {
                warn("Class '" + c.getId() + "' casts with focus type '" + ft + "', but no item has that focus_type.");
            }
        }
    }

    // ==================== SPELLS ====================

    private void checkSpells() {
        for (DndSpell s : SpellLoader.getAllSpells()) {
            String where = "Spell '" + s.getId() + "'";
            if (s.getMaterial() != null && !isItem(s.getMaterial())) {
                warn(where + " material " + s.getMaterial() + " is a block, not an item — it can't render.");
            }
            if (s.isSaveSpell() && parseAbility(s.getSaveType()) == null) {
                warn(where + " save_type '" + s.getSaveType() + "' isn't a full ability name (e.g. Dexterity).");
            }
            if (!blank(s.getConditionOnFail()) && ConditionLoader.get(s.getConditionOnFail()) == null) {
                warn(where + " condition_on_fail '" + s.getConditionOnFail() + "' isn't a condition in Conditions/.");
            }
            if (!blank(s.getSaveEffect()) && !SAVE_EFFECTS.contains(s.getSaveEffect().toLowerCase())) {
                warn(where + " save_effect '" + s.getSaveEffect() + "' should be half or none.");
            }
            if (s.isAoe()) {
                if (!AOE_SHAPES.contains(s.getAoeShape().toLowerCase())) {
                    warn(where + " aoe_shape '" + s.getAoeShape() + "' should be sphere, cone, line or burst.");
                }
                if (s.getAoeSize() <= 0) warn(where + " has aoe_shape but no aoe_size.");
                if (!AOE_TARGETS.contains(String.valueOf(s.getAoeTargets()).toLowerCase())) {
                    warn(where + " aoe_targets '" + s.getAoeTargets() + "' should be all, enemies or allies.");
                }
                if (!s.isSaveSpell() && !s.isAttackRoll()) {
                    warn(where + " has an area but no save_type — the area resolves nothing.");
                }
            }
            if (s.isAutoHit() && blank(s.getDamage())) {
                warn(where + " is auto_hit but has no damage: — the caster gets an empty damage prompt.");
            }
            if (s.isAutoHit() && (s.isAttackRoll() || s.isSaveSpell())) {
                warn(where + " is auto_hit and also has an attack/save — auto_hit wins, the roll is skipped.");
            }
            checkAmount(where, "damage", s.getDamage());
            checkAmount(where, "healing", s.getHealing());
            checkAmount(where, "temp_hp", s.getTempHp());
            checkAmount(where, "mark_damage", s.getMarkDamage());
            boolean targeted = (s.isAttackRoll() || s.isSaveSpell()) && !s.isAoe() && !s.isMarkSpell();
            if (targeted && s.getRange() != null && s.getRange().trim().toLowerCase().startsWith("self")) {
                warn(where + " is an attack/save spell with range '" + s.getRange() + "' — a Self range can only"
                        + " target the caster. Use \"Touch\" or \"N feet\".");
            }
        }
    }

    private void checkAmount(String where, String field, String value) {
        if (blank(value)) return;
        String v = value.trim();
        if (isDice(v)) return;
        try { Integer.parseInt(v); } catch (NumberFormatException e) {
            warn(where + " " + field + " '" + value + "' isn't a single dice group or number (e.g. 2d6+3) — it rolls as 0.");
        }
    }

    // ==================== CLASSES / RACES / BACKGROUNDS ====================

    private void checkClasses() {
        for (DndClass c : ClassLoader.getAllClasses()) {
            String where = "Class '" + c.getId() + "'";
            checkEquipmentList(where + " starting_equipment", c.getStartingEquipment());
            checkSkillNames(where + " skills", c.getSkills());
            checkToolIds(where + " tool_proficiencies", c.getToolProficiencies());
            checkChoices(where, c.getPlayerChoices());
            if (c.getSubclasses() == null) continue;
            for (DndSubClass sub : c.getSubclasses().values()) {
                String subWhere = where + " subclass '" + sub.getId() + "'";
                referenceSpells(sub.getBonusSpells());
                referenceSpells(sub.getAdditionalSpells());
                if (sub.getConditionalBonusSpells() != null) {
                    sub.getConditionalBonusSpells().values().forEach(this::referenceSpells);
                }
                checkSkillNames(subWhere + " skill_proficiencies", sub.getSkillProficiencies());
                checkToolIds(subWhere + " tool_proficiencies", sub.getToolProficiencies());
                checkChoices(subWhere, sub.getPlayerChoices());
            }
        }
    }

    private void checkRaces() {
        for (DndRace r : RaceLoader.getAllRaces()) {
            String where = "Race '" + r.getId() + "'";
            referenceInnate(r.getInnateSpells());
            checkSkillNames(where + " skill_proficiencies", r.getSkillProficiencies());
            checkToolIds(where + " tool_proficiencies", r.getToolProficiencies());
            checkChoices(where, r.getPlayerChoices());
            if (r.getSubraces() == null) continue;
            for (DndSubRace sub : r.getSubraces().values()) {
                String subWhere = where + " subrace '" + sub.getId() + "'";
                referenceInnate(sub.getInnateSpells());
                checkSkillNames(subWhere + " skill_proficiencies", sub.getSkillProficiencies());
                checkToolIds(subWhere + " tool_proficiencies", sub.getToolProficiencies());
                checkChoices(subWhere, sub.getPlayerChoices());
            }
        }
    }

    private void checkBackgrounds() {
        for (DndBackground b : BackgroundLoader.getAllBackgrounds()) {
            String where = "Background '" + b.getId() + "'";
            checkEquipmentList(where + " starting_equipment", b.getStartingEquipment());
            checkSkillNames(where + " skill_proficiencies", b.getSkills());
            checkToolIds(where + " tool_proficiencies", b.getTools());
            checkChoices(where, b.getPlayerChoices());
            // 2024-rules slot: parsed, never applied (2014 ability increases come from the race).
            if (!b.getAbilityScoreOptions().isEmpty()) {
                warn(where + " sets ability_scores:, which is 2024-rules only and isn't applied yet — "
                        + "under 2014 rules the race gives the ability score increases.");
            }
        }
    }

    /**
     * A tool proficiency must be a registered tool: an item tagged artisan_tool / musical_instrument
     * / gaming_set / tool, or a built-in vehicle. Anything else is a typo or a missing item — the
     * character is "proficient" in something no menu or check will ever name.
     */
    private void checkToolIds(String where, List<String> tools) {
        if (tools == null) return;
        for (String t : tools) {
            if (!blank(t) && !ToolRegistry.isRegistered(t)) {
                warn(where + " names tool '" + t + "', which isn't a registered tool — give its item a tool tag"
                        + " (artisan_tool, musical_instrument, gaming_set or tool), or fix the id.");
            }
        }
    }

    private void checkChoices(String where, List<ChoiceEntry> choices) {
        if (choices == null) return;
        for (ChoiceEntry choice : choices) {
            if (choice.pc() == null || choice.pc().getOptions() == null) continue;
            String choiceWhere = where + " choice '" + choice.id() + "'";
            if (choice.type() == PlayersChoice.ChoiceType.EQUIPMENT) {
                for (Object opt : choice.pc().getOptions()) {
                    if (opt instanceof EquipmentOption eo) checkEquipmentOption(choiceWhere, eo);
                }
            } else if (choice.type() == PlayersChoice.ChoiceType.SKILL) {
                List<String> names = new ArrayList<>();
                for (Object opt : choice.pc().getOptions()) if (opt instanceof String s) names.add(s);
                checkSkillNames(choiceWhere, names);
            } else if (choice.type() == PlayersChoice.ChoiceType.TOOL) {
                List<String> ids = new ArrayList<>();
                for (Object opt : choice.pc().getOptions()) if (opt instanceof String s) ids.add(s);
                checkToolIds(choiceWhere, ids);
            } else if (choice.type() == PlayersChoice.ChoiceType.LANGUAGE) {
                for (Object opt : choice.pc().getOptions()) {
                    if (opt instanceof String s && !LanguageRegistry.isRegistered(s)) {
                        warn(choiceWhere + " offers language '" + s + "', which isn't registered"
                                + " (add it to DMContent/Languages.yml if it's homebrew).");
                    }
                }
            }
        }
    }

    private void checkEquipmentOption(String where, EquipmentOption opt) {
        switch (opt.getKind()) {
            case ITEM -> { if (!itemExists(opt.getIdOrTag())) warn(where + " offers '" + opt.getIdOrTag() + "', which isn't a defined weapon/armor/item."); }
            case TAG -> { if (TagRegistry.itemsFor(opt.getIdOrTag()).isEmpty()) warn(where + " offers tag '" + opt.getIdOrTag() + "', which has no items."); }
            case BUNDLE -> opt.getParts().forEach(p -> checkEquipmentOption(where, p));
        }
    }

    private void checkEquipmentList(String where, List<String> entries) {
        if (entries == null) return;
        for (String entry : entries) {
            String id = entry == null ? "" : entry.trim();
            Matcher m = QTY_SUFFIX.matcher(id);
            if (m.matches()) id = m.group(1).trim();
            if (TagRegistry.isTag(id)) continue;
            if (!itemExists(id)) {
                warn(where + " gives '" + entry + "', which isn't a defined weapon/armor/item — it arrives as an \"Unknown item\" paper.");
            }
        }
    }

    private void checkSkillNames(String where, List<String> skills) {
        if (skills == null) return;
        for (String s : skills) {
            if (!blank(s) && Skill.fromString(s) == null) warn(where + " names skill '" + s + "', which isn't a skill.");
        }
    }

    private void referenceSpells(List<String> ids) {
        if (ids == null) return;
        for (String id : ids) if (!blank(id) && SpellLoader.getSpell(id) == null) missingSpellRefs.add(id);
    }

    private void referenceInnate(List<InnateSpell> innate) {
        if (innate == null) return;
        for (InnateSpell i : innate) if (!blank(i.getSpellId()) && SpellLoader.getSpell(i.getSpellId()) == null) missingSpellRefs.add(i.getSpellId());
    }

    // ==================== ENTITIES ====================

    private void checkEntities() {
        for (DndEntity e : EntityLoader.getAllEntities()) {
            String where = "Entity '" + e.getId() + "'";
            if (e.getAttacks() != null) {
                for (DndAttack a : e.getAttacks()) {
                    String aw = where + " attack '" + a.getName() + "'";
                    if (!blank(a.getItem()) && !itemExists(a.getItem())) {
                        warn(aw + " item '" + a.getItem() + "' isn't a defined weapon — no hotbar weapon or loot for it.");
                    }
                    if (blank(a.getDamage())) warn(aw + " has no damage.");
                    else checkAmount(aw, "damage", a.getDamage());
                }
            }
            checkLoot(where + " inventory", e.getInventoryLoot());
            checkLoot(where + " loot", e.getLoot());
            ShopConfig shop = e.getShop();
            if (shop == null) continue;
            if (shop.getItems() != null) {
                for (ShopItem si : shop.getItems()) {
                    if (!itemExists(si.getItemId())) {
                        warn(where + " shop sells '" + si.getItemId() + "', which isn't a defined item — that row won't appear.");
                    }
                    Cost price = si.getPrice();
                    if (price != null && price.getAmount() > MAX_TRADE_STACK) {
                        warn(where + " shop prices '" + si.getItemId() + "' at " + price.getAmount() + " " + price.getCurrency()
                                + " — a trade slot holds at most " + MAX_TRADE_STACK + " coins (#94). Use a bigger coin.");
                    }
                }
            }
            if (shop.getAccepts() != null) {
                for (String id : shop.getAccepts()) {
                    if (!itemExists(id)) warn(where + " shop accepts '" + id + "', which isn't a defined item.");
                }
            }
        }
    }

    private void checkLoot(String where, List<LootEntry> entries) {
        if (entries == null) return;
        for (LootEntry l : entries) {
            if (!itemExists(l.getItemId())) warn(where + " lists '" + l.getItemId() + "', which isn't a defined item.");
        }
    }

    // ==================== HELPERS ====================

    private static boolean itemExists(String id) {
        if (blank(id)) return false;
        return WeaponLoader.getWeapon(id) != null || ArmorLoader.getArmor(id) != null || ItemLoader.getItem(id) != null;
    }

    private void checkMaterialName(String where, String name) {
        if (blank(name)) {
            warn(where + " has no material: — it renders as paper.");
            return;
        }
        Material m = Material.matchMaterial(name.trim());
        if (m == null) warn(where + " material '" + name + "' isn't a Minecraft item — it renders as paper.");
        else if (!isItem(m)) warn(where + " material " + m + " is a block, not an item — it renders as paper.");
    }

    /** Material.isItem() needs the server's registries; without them (offline tooling) assume yes. */
    private static boolean isItem(Material m) {
        try {
            return m.isItem();
        } catch (Throwable noRegistry) {
            return true;
        }
    }

    private static boolean isDice(String s) {
        return DiceRoller.parseDiceRoll(s.trim()).isPresent();
    }

    private static Ability parseAbility(String s) {
        if (s == null) return null;
        try { return Ability.valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
