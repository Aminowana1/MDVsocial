package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Native Bedrock adapters for the three MMOCore inventories used by MDVSocial:
 * profile/player-stats, attributes and root class selection.
 *
 * Java players are untouched. The bridge intentionally talks to MMOCore through
 * reflection so MDVSocial keeps MMOCore as a soft-dependency and can still boot
 * if MMOCore is missing or updated.
 */
final class MMOCoreBedrockMenuManager {

    private static final DecimalFormat NUMBER = new DecimalFormat("0.##");

    private final MDVSocialPlugin plugin;

    MMOCoreBedrockMenuManager(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    boolean openProfile(Player player) {
        if (!ready(player))
            return false;
        try {
            Object data = playerData(player);
            if (data == null)
                return unavailable(player, null);

            YamlConfiguration ui = ui("mmocore_perfil");
            Map<String, String> tokens = baseTokens(data, player);
            addProfileAttributeTokens(data, tokens);

            SimpleForm.Builder form = SimpleForm.builder()
                    .title(text(ui, "title", "&8&lTu Personaje", player, tokens))
                    .content(lines(ui, "content", List.of(
                            "&e&l● &7&lProgreso",
                            "&7Nivel actual: &e{level}",
                            "&7EXP: &e{exp} &7/ &e{next_level}",
                            "",
                            "&e&l● &7&lIdentidad",
                            "&7Raza actual: &c{class}",
                            "&7Puntos de atributo: &a{attribute_points}",
                            "&7Puntos de clase: &c{class_points}"), player, tokens));

            List<Runnable> actions = new ArrayList<>();
            if (ui.getBoolean("buttons.attributes.enabled", true)) {
                addButton(form, ui, "buttons.attributes",
                        text(ui, "buttons.attributes.text", "&d&lAtributos\n&r&8Gestiona tus puntos de atributo.", player, tokens));
                actions.add(() -> openAttributes(player));
            }
            if (ui.getBoolean("buttons.classes.enabled", true)) {
                addButton(form, ui, "buttons.classes",
                        text(ui, "buttons.classes.text", "&a&lRazas\n&r&8Consulta o cambia tu raza.", player, tokens));
                actions.add(() -> openClasses(player));
            }
            if (ui.getBoolean("buttons.party.enabled", true)) {
                addButton(form, ui, "buttons.party",
                        text(ui, "buttons.party.text", "&d&lGrupo de Aventura\n&r&8Consulta tu party de MMOCore.", player, tokens));
                actions.add(() -> plugin.openBedrockPartyFromMMOCore(player));
            }
            addButton(form, ui, "buttons.back",
                    text(ui, "buttons.back.text", "&6Volver", player, tokens));
            actions.add(() -> plugin.openBedrockProfileRoot(player));

            send(player, form, actions);
            return true;
        } catch (Throwable ex) {
            return unavailable(player, ex);
        }
    }

    boolean openAttributes(Player player) {
        if (!ready(player))
            return false;
        try {
            Object data = playerData(player);
            Object mmocore = mmocore();
            if (data == null || mmocore == null)
                return unavailable(player, null);

            YamlConfiguration ui = ui("mmocore_atributos");
            Map<String, String> root = baseTokens(data, player);
            root.put("reallocation_points", string(invoke(data, "getAttributeReallocationPoints")));

            Object attributes = invoke(data, "getAttributes");
            root.put("spent_total", attributes == null ? "0" : string(invoke(attributes, "countPoints")));

            SimpleForm.Builder form = SimpleForm.builder()
                    .title(text(ui, "title", "&8&lAtributos", player, root))
                    .content(lines(ui, "content", List.of(
                            "&7Puntos disponibles: &6{attribute_points}",
                            "&7Puntos gastados: &e{spent_total}",
                            "&7Redistribuciones: &e{reallocation_points}"), player, root));

            List<Runnable> actions = new ArrayList<>();
            List<Object> all = getAttributes(mmocore);
            List<String> configuredOrder = ui.getStringList("order");
            all.sort(Comparator.comparingInt(attr -> attributeOrder(attr, configuredOrder))
                    .thenComparing(attr -> attributeName(attr).toLowerCase(Locale.ROOT)));

            for (Object attr : all) {
                String id = attributeId(attr);
                Object instance = invoke(attributes, "getInstance", attr);
                if (instance == null)
                    continue;
                Map<String, String> at = new HashMap<>(root);
                int base = integer(invoke(instance, "getBase"));
                int total = integer(invoke(instance, "getTotal"));
                boolean hasMax = bool(invoke(attr, "hasMax"));
                int max = integer(invoke(attr, "getMax"));
                at.put("id", id);
                at.put("name", attributeName(attr));
                at.put("spent", String.valueOf(base));
                at.put("current", String.valueOf(total));
                at.put("max", hasMax ? String.valueOf(max) : "∞");
                addAttributeBuffTokens(attr, total, at);

                String path = "attributes." + normalize(id);
                String generic = "&e&l{name}\n&r&7Nivel: &f{current} &8• &7Puntos: &f{spent}/{max}";
                String buttonText = text(ui, path + ".text",
                        textRaw(ui, "attribute.text", generic), player, at);
                addButton(form, ui, ui.contains(path) ? path : "attribute", buttonText);
                actions.add(() -> openAttributeDetails(player, attr));
            }

            if (ui.getBoolean("buttons.reallocate.enabled", true)) {
                addButton(form, ui, "buttons.reallocate",
                        text(ui, "buttons.reallocate.text",
                                "&e&lRedistribuir atributos\n&r&8Consume 1 punto de redistribución.", player, root));
                actions.add(() -> confirmAttributeReset(player));
            }
            addButton(form, ui, "buttons.back",
                    text(ui, "buttons.back.text", "&6Volver al Perfil", player, root));
            actions.add(() -> plugin.openBedrockProfileRoot(player));

            send(player, form, actions);
            return true;
        } catch (Throwable ex) {
            return unavailable(player, ex);
        }
    }

    boolean openClasses(Player player) {
        if (!ready(player))
            return false;
        try {
            Object data = playerData(player);
            Object mmocore = mmocore();
            if (data == null || mmocore == null)
                return unavailable(player, null);

            YamlConfiguration ui = ui("mmocore_clases");
            Map<String, String> root = baseTokens(data, player);
            SimpleForm.Builder form = SimpleForm.builder()
                    .title(text(ui, "title", "&8&lSelección de raza", player, root))
                    .content(lines(ui, "content", List.of(
                            "&7Selecciona una raza.",
                            "&7Raza actual: &f{class}",
                            "&7Puntos de clase: &e{class_points}"), player, root));
            List<Runnable> actions = new ArrayList<>();

            ConfigurationSection configured = ui.getConfigurationSection("classes");
            if (configured != null) {
                for (String key : configured.getKeys(false)) {
                    String path = "classes." + key;
                    if (!ui.getBoolean(path + ".enabled", true))
                        continue;
                    String configuredId = ui.getString(path + ".id", key);
                    Object clazz = findClass(mmocore, configuredId);
                    if (clazz == null)
                        continue;
                    addClassButton(player, data, form, actions, ui, path, clazz, root);
                }
            } else {
                for (Object clazz : getClasses(mmocore))
                    addClassButton(player, data, form, actions, ui, "class", clazz, root);
            }

            addButton(form, ui, "buttons.back",
                    text(ui, "buttons.back.text", "&6Volver al Perfil", player, root));
            actions.add(() -> plugin.openBedrockProfileRoot(player));
            send(player, form, actions);
            return true;
        } catch (Throwable ex) {
            return unavailable(player, ex);
        }
    }

    private void addClassButton(Player player, Object data, SimpleForm.Builder form, List<Runnable> actions,
            YamlConfiguration ui, String path, Object clazz, Map<String, String> root) throws Exception {
        Map<String, String> t = new HashMap<>(root);
        String id = string(invoke(clazz, "getId"));
        String name = string(invoke(clazz, "getName"));
        t.put("id", id);
        t.put("name", name);
        t.put("description", join(invoke(clazz, "getDescription"), " "));
        t.put("attribute_description", join(invoke(clazz, "getAttributeDescription"), " "));
        String generic = "&e&l{name}\n&r&7{description}\n&r&eToca para elegir esta raza.";
        addButton(form, ui, path, text(ui, path + ".text", textRaw(ui, "class.text", generic), player, t));
        actions.add(() -> chooseClass(player, clazz));
    }

    private void openAttributeDetails(Player player, Object attr) {
        if (!ready(player))
            return;
        try {
            Object data = playerData(player);
            if (data == null) {
                unavailable(player, null);
                return;
            }

            YamlConfiguration ui = ui("mmocore_atributos");
            Object attributes = invoke(data, "getAttributes");
            Object instance = invoke(attributes, "getInstance", attr);
            if (instance == null) {
                openAttributes(player);
                return;
            }

            Map<String, String> t = baseTokens(data, player);
            t.put("reallocation_points", string(invoke(data, "getAttributeReallocationPoints")));
            t.put("spent_total", attributes == null ? "0" : string(invoke(attributes, "countPoints")));

            String id = attributeId(attr);
            int base = integer(invoke(instance, "getBase"));
            int total = integer(invoke(instance, "getTotal"));
            boolean hasMax = bool(invoke(attr, "hasMax"));
            int max = integer(invoke(attr, "getMax"));
            t.put("id", id);
            t.put("name", attributeName(attr));
            t.put("spent", String.valueOf(base));
            t.put("current", String.valueOf(total));
            t.put("max", hasMax ? String.valueOf(max) : "∞");
            addAttributeBuffTokens(attr, total, t);

            String specific = "details." + normalize(id);
            String path = ui.contains(specific) ? specific : "details.attribute";
            List<String> genericContent = List.of(
                    "&7Nivel actual: &f{current}",
                    "&7Puntos invertidos: &6{spent}&7/&6{max}",
                    "",
                    "&7Puntos disponibles: &6{attribute_points}");

            SimpleForm.Builder form = SimpleForm.builder()
                    .title(text(ui, path + ".title", "&8&l{name}", player, t))
                    .content(lines(ui, path + ".content", genericContent, player, t));
            List<Runnable> actions = new ArrayList<>();

            boolean maxed = hasMax && base >= max;
            if (maxed) {
                addButton(form, ui, path + ".maxed",
                        text(ui, path + ".maxed.text", "&8&lMáximo alcanzado\n&r&7No puedes invertir más puntos.", player, t));
                actions.add(() -> openAttributeDetails(player, attr));
            } else if (integer(invoke(data, "getAttributePoints")) < 1) {
                addButton(form, ui, path + ".no-points",
                        text(ui, path + ".no-points.text", "&c&lSin puntos disponibles\n&r&7Consigue puntos de atributo para mejorar.", player, t));
                actions.add(() -> openAttributeDetails(player, attr));
            } else {
                addButton(form, ui, path + ".upgrade",
                        text(ui, path + ".upgrade.text", "&a&lSubir {name} +1\n&r&7Consume 1 punto de atributo.", player, t));
                actions.add(() -> upgradeAttribute(player, attr));
            }

            addButton(form, ui, path + ".back",
                    text(ui, path + ".back.text", "&6Volver", player, t));
            actions.add(() -> openAttributes(player));
            send(player, form, actions);
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo abrir el detalle de atributo MMOCore en Bedrock: " + compact(ex));
            openAttributes(player);
        }
    }

    private void upgradeAttribute(Player player, Object attr) {
        try {
            Object data = playerData(player);
            if (data == null)
                return;
            int points = integer(invoke(data, "getAttributePoints"));
            if (points < 1) {
                sendMMOCoreMessage(data, "ATTRIBUTE_MISSING_POINT");
                openAttributeDetails(player, attr);
                return;
            }
            Object container = invoke(data, "getAttributes");
            Object instance = invoke(container, "getInstance", attr);
            int base = integer(invoke(instance, "getBase"));
            boolean hasMax = bool(invoke(attr, "hasMax"));
            int max = integer(invoke(attr, "getMax"));
            if (hasMax && base >= max) {
                sendMMOCoreMessage(data, "ATTRIBUTE_MAX_POINTS_HIT");
                openAttributeDetails(player, attr);
                return;
            }

            invoke(instance, "addBase", 1);
            invoke(data, "giveAttributePoints", -1);
            int newBase = integer(invoke(instance, "getBase"));
            invoke(attr, "updateAdvancement", data, newBase);
            sendMMOCoreMessage(data, "ATTRIBUTE_LEVEL_UP", "attribute", attributeName(attr), "level", newBase);
            callAttributeUseEvent(data, attr);
            openAttributeDetails(player, attr);
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo subir atributo MMOCore desde Bedrock: " + compact(ex));
            player.sendMessage(plugin.bedrockText("&cNo se pudo actualizar el atributo.", player, null, "", false));
            openAttributeDetails(player, attr);
        }
    }

    private void confirmAttributeReset(Player player) {
        try {
            Object data = playerData(player);
            YamlConfiguration ui = ui("mmocore_atributos");
            Map<String, String> t = baseTokens(data, player);
            t.put("reallocation_points", string(invoke(data, "getAttributeReallocationPoints")));
            Object attrs = invoke(data, "getAttributes");
            t.put("spent_total", string(invoke(attrs, "countPoints")));

            SimpleForm.Builder form = SimpleForm.builder()
                    .title(text(ui, "reset.title", "&e&lRedistribuir atributos", player, t))
                    .content(lines(ui, "reset.content", List.of(
                            "&7¿Quieres recuperar todos los puntos gastados?",
                            "&7Costo: &e1 punto de redistribución"), player, t));
            List<Runnable> actions = new ArrayList<>();
            addButton(form, ui, "reset.confirm",
                    text(ui, "reset.confirm.text", "&aSí, redistribuir", player, t));
            actions.add(() -> resetAttributes(player));
            addButton(form, ui, "reset.back",
                    text(ui, "reset.back.text", "&6Volver", player, t));
            actions.add(() -> openAttributes(player));
            send(player, form, actions);
        } catch (Throwable ex) {
            unavailable(player, ex);
        }
    }

    private void resetAttributes(Player player) {
        try {
            Object data = playerData(player);
            Object attrs = invoke(data, "getAttributes");
            int spent = integer(invoke(attrs, "countPoints"));
            if (spent < 1) {
                sendMMOCoreMessage(data, "ATTRIBUTE_NO_POINTS_SPENT");
            } else if (integer(invoke(data, "getAttributeReallocationPoints")) < 1) {
                sendMMOCoreMessage(data, "ATTRIBUTE_MISSING_REALLOCATION_POINT");
            } else {
                Object instances = invoke(attrs, "getInstances");
                if (instances instanceof Iterable<?> iterable)
                    for (Object ins : iterable)
                        invoke(ins, "setBase", 0);
                invoke(data, "giveAttributePoints", spent);
                invoke(data, "giveAttributeReallocationPoints", -1);
                sendMMOCoreMessage(data, "ATTRIBUTE_POINTS_REALLOCATED", "points",
                        integer(invoke(data, "getAttributePoints")));
            }
            openAttributes(player);
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudieron redistribuir atributos MMOCore desde Bedrock: " + compact(ex));
            openAttributes(player);
        }
    }

    private void chooseClass(Player player, Object rootClass) {
        try {
            Object data = playerData(player);
            if (integer(invoke(data, "getClassPoints")) < 1) {
                sendMMOCoreMessage(data, "CANT_CHOOSE_NEW_CLASS");
                openClasses(player);
                return;
            }
            if (requiresPermission(rootClass)) {
                String id = string(invoke(rootClass, "getId"));
                if (!player.hasPermission("mmocore.class." + id.toLowerCase(Locale.ROOT))) {
                    sendMMOCoreMessage(data, "NO_PERMISSION_FOR_CLASS", "class", string(invoke(rootClass, "getName")));
                    openClasses(player);
                    return;
                }
            }
            Object current = invoke(data, "getProfess");
            if (rootClass.equals(current)) {
                sendMMOCoreMessage(data, "ALREADY_ON_CLASS", "class", string(invoke(rootClass, "getName")));
                openClasses(player);
                return;
            }

            Object target = deepestSavedSubclass(data, rootClass);
            openClassConfirmation(player, target);
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo preparar selección de clase MMOCore: " + compact(ex));
            player.sendMessage(plugin.bedrockText("&cNo se pudo abrir la selección de raza.", player, null, "", false));
        }
    }

    private void openClassConfirmation(Player player, Object clazz) throws Exception {
        Object data = playerData(player);
        YamlConfiguration ui = ui("mmocore_clases");
        Map<String, String> t = baseTokens(data, player);
        t.put("id", string(invoke(clazz, "getId")));
        t.put("name", string(invoke(clazz, "getName")));
        t.put("class", string(invoke(clazz, "getName")));
        t.put("description", join(invoke(clazz, "getDescription"), "\n"));
        t.put("attribute_description", join(invoke(clazz, "getAttributeDescription"), "\n"));

        SimpleForm.Builder form = SimpleForm.builder()
                .title(text(ui, "confirm.title", "&8Confirmar: &f{class}", player, t))
                .content(lines(ui, "confirm.content", List.of(
                        "&7¿Quieres elegir &f{class}&7?",
                        "",
                        "{description}",
                        "",
                        "{attribute_description}",
                        "",
                        "&7Costo: &e1 punto de clase"), player, t));
        List<Runnable> actions = new ArrayList<>();
        addButton(form, ui, "confirm.confirm",
                text(ui, "confirm.confirm.text", "&a&lConfirmar", player, t));
        actions.add(() -> applyClass(player, clazz));
        addButton(form, ui, "confirm.back",
                text(ui, "confirm.back.text", "&6Volver", player, t));
        actions.add(() -> openClasses(player));
        send(player, form, actions);
    }

    private void applyClass(Player player, Object clazz) {
        try {
            Object data = playerData(player);
            if (data == null)
                return;
            // Re-check at confirmation time in case points changed while the form was open.
            if (integer(invoke(data, "getClassPoints")) < 1) {
                sendMMOCoreMessage(data, "CANT_CHOOSE_NEW_CLASS");
                openClasses(player);
                return;
            }
            if (fireClassChangeEvent(data, clazz)) {
                openClasses(player);
                return;
            }

            invoke(data, "giveClassPoints", -1);
            boolean saved = bool(invoke(data, "hasSavedClass", clazz));
            Object info;
            if (saved) {
                info = invoke(data, "getClassInfo", clazz);
            } else {
                Object mmocore = mmocore();
                Object pdm = field(mmocore, "playerDataManager");
                Object defaults = invoke(pdm, "getDefaultData");
                Class<?> savedClass = Class.forName("net.Indyuce.mmocore.api.player.profess.SavedClassInformation");
                Constructor<?> constructor = findCompatibleConstructor(savedClass, defaults);
                if (constructor == null)
                    throw new NoSuchMethodException("SavedClassInformation(DefaultPlayerData)");
                info = constructor.newInstance(defaults);
            }
            invoke(info, "load", clazz, data);
            sendMMOCoreMessage(data, "CLASS_SELECT", "class", string(invoke(clazz, "getName")));

            YamlConfiguration ui = ui("mmocore_clases");
            String msg = text(ui, "messages.selected", "&aHas elegido &f{class}&a.", player,
                    Map.of("class", string(invoke(clazz, "getName"))));
            if (msg != null && !msg.isBlank())
                player.sendMessage(msg);
            openProfile(player);
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo aplicar clase MMOCore desde Bedrock: " + compact(ex));
            player.sendMessage(plugin.bedrockText("&cNo se pudo cambiar tu raza.", player, null, "", false));
            openClasses(player);
        }
    }

    /** @return true if the MMOCore event was cancelled. */
    private boolean fireClassChangeEvent(Object data, Object clazz) {
        try {
            Class<?> eventClass = Class.forName("net.Indyuce.mmocore.api.event.PlayerChangeClassEvent");
            Class<?> reasonClass = Class.forName("net.Indyuce.mmocore.api.event.PlayerChangeClassEvent$Reason");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object reason = Enum.valueOf((Class<? extends Enum>) reasonClass.asSubclass(Enum.class), "GUI");
            Constructor<?> constructor = null;
            for (Constructor<?> c : eventClass.getConstructors()) {
                if (c.getParameterCount() == 3
                        && compatible(c.getParameterTypes()[0], data)
                        && compatible(c.getParameterTypes()[1], clazz)
                        && compatible(c.getParameterTypes()[2], reason)) {
                    constructor = c;
                    break;
                }
            }
            if (constructor == null)
                return false;
            Object event = constructor.newInstance(data, clazz, reason);
            if (event instanceof Event bukkitEvent)
                Bukkit.getPluginManager().callEvent(bukkitEvent);
            Object cancelled = invoke(event, "isCancelled");
            return bool(cancelled);
        } catch (Throwable ex) {
            plugin.getLogger().fine("No se pudo emitir PlayerChangeClassEvent reflectivo: " + compact(ex));
            return false;
        }
    }

    private void callAttributeUseEvent(Object data, Object attr) {
        try {
            Class<?> eventClass = Class.forName("net.Indyuce.mmocore.api.event.PlayerAttributeUseEvent");
            for (Constructor<?> c : eventClass.getConstructors()) {
                if (c.getParameterCount() == 2
                        && compatible(c.getParameterTypes()[0], data)
                        && compatible(c.getParameterTypes()[1], attr)) {
                    Object event = c.newInstance(data, attr);
                    if (event instanceof Event bukkitEvent)
                        Bukkit.getPluginManager().callEvent(bukkitEvent);
                    return;
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().fine("No se pudo emitir PlayerAttributeUseEvent reflectivo: " + compact(ex));
        }
    }

    private Object deepestSavedSubclass(Object data, Object root) {
        try {
            Object saved = invoke(data, "getSavedClasses");
            Object mmocore = mmocore();
            Object manager = field(mmocore, "classManager");
            if (saved instanceof Iterable<?> iterable) {
                for (Object raw : iterable) {
                    if (raw == null)
                        continue;
                    Object checked = invoke(manager, "getOrThrow", raw.toString());
                    if (checked != null && bool(invoke(root, "hasSubclass", checked)))
                        return checked;
                }
            }
        } catch (Throwable ignored) {
        }
        return root;
    }

    private boolean requiresPermission(Object clazz) {
        try {
            Class<?> optionClass = Class.forName("net.Indyuce.mmocore.api.player.profess.ClassOption");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object option = Enum.valueOf((Class<? extends Enum>) optionClass.asSubclass(Enum.class), "NEEDS_PERMISSION");
            return bool(invoke(clazz, "hasOption", option));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void addProfileAttributeTokens(Object data, Map<String, String> tokens) {
        try {
            Object attrs = invoke(data, "getAttributes");
            for (Object attr : getAttributes(mmocore())) {
                String id = normalize(attributeId(attr));
                Object ins = invoke(attrs, "getInstance", attr);
                tokens.put("attribute_" + id, string(invoke(ins, "getTotal")));
            }
        } catch (Throwable ignored) {
        }
    }

    private void addAttributeBuffTokens(Object attr, int total, Map<String, String> tokens) {
        try {
            Object buffs = invoke(attr, "getBuffs");
            if (!(buffs instanceof Iterable<?> iterable))
                return;
            for (Object buff : iterable) {
                String stat = normalize(string(invoke(buff, "getTargetStat")));
                double coefficient = decimal(invoke(buff, "getCoefficient"));
                tokens.put("buff_" + stat, NUMBER.format(coefficient));
                tokens.put("total_" + stat, NUMBER.format(coefficient * total));
            }
        } catch (Throwable ignored) {
        }
    }

    private Map<String, String> baseTokens(Object data, Player player) throws Exception {
        Map<String, String> t = new LinkedHashMap<>();
        Object profess = invoke(data, "getProfess");
        t.put("player", player.getName());
        t.put("level", string(invoke(data, "getLevel")));
        t.put("class", profess == null ? "" : string(invoke(profess, "getName")));
        t.put("class_id", profess == null ? "" : string(invoke(profess, "getId")));
        t.put("exp", NUMBER.format(decimal(invoke(data, "getExperience"))));
        t.put("next_level", NUMBER.format(decimal(invoke(data, "getLevelUpExperience"))));
        t.put("next_exp", t.get("next_level"));
        t.put("class_points", string(invoke(data, "getClassPoints")));
        t.put("skill_points", string(invoke(data, "getSkillPoints")));
        t.put("attribute_points", string(invoke(data, "getAttributePoints")));
        return t;
    }

    private List<Object> getAttributes(Object mmocore) throws Exception {
        Object manager = field(mmocore, "attributeManager");
        return asList(invoke(manager, "getAll"));
    }

    private List<Object> getClasses(Object mmocore) throws Exception {
        Object manager = field(mmocore, "classManager");
        return asList(invoke(manager, "getAll"));
    }

    private Object findClass(Object mmocore, String rawId) throws Exception {
        Object manager = field(mmocore, "classManager");
        List<String> candidates = List.of(
                rawId == null ? "" : rawId,
                rawId == null ? "" : rawId.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'),
                rawId == null ? "" : rawId.toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-'));
        for (String id : candidates) {
            if (id.isBlank())
                continue;
            Object found = invoke(manager, "get", id);
            if (found != null)
                return found;
        }
        for (Object clazz : getClasses(mmocore)) {
            String id = string(invoke(clazz, "getId"));
            if (id.equalsIgnoreCase(rawId))
                return clazz;
        }
        return null;
    }

    private int attributeOrder(Object attr, List<String> configured) {
        String id = normalize(attributeId(attr));
        for (int i = 0; i < configured.size(); i++)
            if (normalize(configured.get(i)).equals(id))
                return i;
        return configured.size() + 100;
    }

    private String attributeId(Object attr) {
        try {
            return string(invoke(attr, "getId"));
        } catch (Throwable ignored) {
            return "attribute";
        }
    }

    private String attributeName(Object attr) {
        try {
            return string(invoke(attr, "getName"));
        } catch (Throwable ignored) {
            return attributeId(attr);
        }
    }

    private boolean ready(Player player) {
        if (player == null || !plugin.isBedrockPlayer(player))
            return false;
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            unavailable(player, null);
            return false;
        }
        return true;
    }

    private boolean unavailable(Player player, Throwable ex) {
        if (ex != null)
            plugin.getLogger().warning("Compatibilidad Bedrock MMOCore: " + compact(ex));
        if (player != null && player.isOnline()) {
            YamlConfiguration ui = ui("mmocore_perfil");
            player.sendMessage(text(ui, "messages.unavailable",
                    "&cMMOCore no está disponible en este momento.", player, Map.of()));
        }
        return false;
    }

    private Object mmocore() throws Exception {
        Class<?> type = Class.forName("net.Indyuce.mmocore.MMOCore");
        return type.getField("plugin").get(null);
    }

    private Object playerData(Player player) throws Exception {
        Class<?> type = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
        Method get = type.getMethod("get", org.bukkit.OfflinePlayer.class);
        return get.invoke(null, player);
    }

    private Object field(Object target, String name) throws Exception {
        if (target == null)
            return null;
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        try {
            Field field = type.getField(name);
            return field.get(target instanceof Class<?> ? null : target);
        } catch (NoSuchFieldException ex) {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target instanceof Class<?> ? null : target);
        }
    }

    private Object invoke(Object target, String name, Object... args) throws Exception {
        if (target == null)
            return null;
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length)
                continue;
            Class<?>[] params = method.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < params.length; i++) {
                if (!compatible(params[i], args[i])) {
                    ok = false;
                    break;
                }
            }
            if (!ok)
                continue;
            return method.invoke(target instanceof Class<?> ? null : target, args);
        }
        throw new NoSuchMethodException(type.getName() + "#" + name + "/" + args.length);
    }

    private static boolean compatible(Class<?> parameter, Object value) {
        if (value == null)
            return !parameter.isPrimitive();
        if (parameter.isInstance(value))
            return true;
        if (!parameter.isPrimitive())
            return false;
        return (parameter == int.class && value instanceof Integer)
                || (parameter == long.class && value instanceof Long)
                || (parameter == double.class && value instanceof Double)
                || (parameter == float.class && value instanceof Float)
                || (parameter == boolean.class && value instanceof Boolean)
                || (parameter == short.class && value instanceof Short)
                || (parameter == byte.class && value instanceof Byte)
                || (parameter == char.class && value instanceof Character);
    }

    private Constructor<?> findCompatibleConstructor(Class<?> type, Object arg) {
        for (Constructor<?> constructor : type.getConstructors())
            if (constructor.getParameterCount() == 1 && compatible(constructor.getParameterTypes()[0], arg))
                return constructor;
        return null;
    }

    private void sendMMOCoreMessage(Object data, String constant, Object... placeholders) {
        try {
            Class<?> type = Class.forName("net.Indyuce.mmocore.player.Message");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object value = Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), constant);
            for (Method method : type.getMethods()) {
                if (!method.getName().equals("send") || method.getParameterCount() != 2)
                    continue;
                if (!compatible(method.getParameterTypes()[0], data) || !method.getParameterTypes()[1].isArray())
                    continue;
                method.invoke(value, data, placeholders);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private YamlConfiguration ui(String id) {
        YamlConfiguration loaded = plugin.getBedrockMenuConfig(id);
        return loaded == null ? new YamlConfiguration() : loaded;
    }

    private String text(YamlConfiguration ui, String path, String fallback, Player player, Map<String, String> tokens) {
        String raw = textRaw(ui, path, fallback);
        return plugin.bedrockText(replace(raw, tokens), player, null, "", false);
    }

    private String textRaw(YamlConfiguration ui, String path, String fallback) {
        if (ui == null)
            return fallback;
        Object value = ui.get(path);
        if (value instanceof ConfigurationSection section)
            return section.getString("text", fallback);
        return value instanceof String string ? string : fallback;
    }

    private String lines(YamlConfiguration ui, String path, List<String> fallback, Player player,
            Map<String, String> tokens) {
        List<String> list = ui == null ? List.of() : ui.getStringList(path);
        if (list.isEmpty()) {
            String scalar = ui == null ? null : ui.getString(path);
            if (scalar != null && !scalar.isBlank())
                list = List.of(scalar.split("\\n", -1));
        }
        if (list.isEmpty())
            list = fallback;
        StringBuilder out = new StringBuilder();
        for (String line : list) {
            if (out.length() > 0)
                out.append('\n');
            out.append(plugin.bedrockText(replace(line, tokens), player, null, "", false));
        }
        return out.toString();
    }

    private String replace(String raw, Map<String, String> tokens) {
        String out = raw == null ? "" : raw;
        if (tokens != null)
            for (Map.Entry<String, String> entry : tokens.entrySet())
                out = out.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        return out;
    }

    private void addButton(SimpleForm.Builder builder, YamlConfiguration ui, String path, String label) {
        String data = ui == null ? "" : ui.getString(path + ".image.data", "");
        if (data == null || data.isBlank()) {
            builder.button(label);
            return;
        }
        String type = ui.getString(path + ".image.type", "URL");
        builder.button(label, "PATH".equalsIgnoreCase(type) ? FormImage.Type.PATH : FormImage.Type.URL, data);
    }

    private void send(Player player, SimpleForm.Builder builder, List<Runnable> actions) {
        long session = plugin.beginBedrockUiSession(player);
        builder.validResultHandler(response -> plugin.runBedrockUiAction(player, session, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        try {
            FloodgatePlayer floodgate = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (floodgate != null)
                floodgate.sendForm(builder.build());
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo enviar Form MMOCore a " + player.getName() + ": " + compact(ex));
        }
    }

    private static List<Object> asList(Object raw) {
        List<Object> out = new ArrayList<>();
        if (raw instanceof Collection<?> collection)
            out.addAll(collection);
        else if (raw instanceof Iterable<?> iterable)
            for (Object value : iterable)
                out.add(value);
        return out;
    }

    private static String join(Object raw, String separator) {
        if (raw instanceof Iterable<?> iterable) {
            List<String> lines = new ArrayList<>();
            for (Object value : iterable)
                if (value != null && !value.toString().isBlank())
                    lines.add(value.toString());
            return String.join(separator, lines);
        }
        return raw == null ? "" : raw.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static int integer(Object value) {
        return value instanceof Number n ? n.intValue() : parseInt(value == null ? null : value.toString());
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static double decimal(Object value) {
        if (value instanceof Number n)
            return n.doubleValue();
        try {
            return Double.parseDouble(value == null ? "0" : value.toString());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean b ? b : Boolean.parseBoolean(value == null ? "false" : value.toString());
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String compact(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root)
            root = root.getCause();
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }
}
