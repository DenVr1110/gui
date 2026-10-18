package dev.cursorui.core;

import dev.cursorui.api.Element;
import dev.cursorui.api.ElementType;
import dev.cursorui.api.Menu;
import java.io.File;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Loads a menu from a YAML file in plugins/CursorUI/menus. The menu id is the file name. */
final class MenuLoader {
    private MenuLoader() {}

    static Menu load(File file, Logger log) {
        String name = file.getName();
        String id = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        Menu menu = new Menu(id);
        menu.permission = y.getString("permission", "");
        menu.distance = y.getDouble("distance", 3.0);
        menu.pixelsPerBlock = Math.max(1.0, y.getDouble("pixels-per-block", 100.0));
        menu.width = y.getDouble("width", 420.0);
        menu.height = y.getDouble("height", 260.0);
        menu.yOffset = y.getDouble("y-offset", 0.0);
        menu.closeOnSneak = y.getBoolean("close-on-sneak", true);
        menu.closeOnSwap = y.getBoolean("close-on-swap", true);
        menu.invulnerable = y.getBoolean("invulnerable", true);
        menu.refreshTicks = y.getInt("refresh-ticks", 10);

        ConfigurationSection els = y.getConfigurationSection("elements");
        if (els == null) {
            log.warning("Menu '" + id + "' has no elements.");
            return menu;
        }
        for (String key : els.getKeys(false)) {
            ConfigurationSection s = els.getConfigurationSection(key);
            if (s == null) continue;
            ElementType type;
            try {
                type = ElementType.valueOf(s.getString("type", "PANEL").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                log.warning("Menu '" + id + "', element '" + key + "': unknown type, skipped.");
                continue;
            }
            Element e = new Element(key, type);
            e.x = s.getDouble("x", 0);
            e.y = s.getDouble("y", 0);
            e.width = s.getDouble("width", 100);
            e.height = s.getDouble("height", 30);
            e.layer = s.getInt("layer", 1);
            e.material = s.getString("material", "GRAY_CONCRETE");
            e.hoverMaterial = s.getString("hover-material", "");
            e.fillMaterial = s.getString("fill-material", "LIME_CONCRETE");
            e.text = s.getString("text", type == ElementType.INPUT ? "<gray>Click to type..." : "");
            e.hoverText = s.getString("hover-text", "");
            e.valueText = s.getString("value-text", "<white>{value}");
            e.textScale = s.getDouble("text-scale", 0.6);
            e.item = s.getString("item", "STONE");
            e.customModelData = s.getInt("custom-model-data", 0);
            e.value = s.getString("value", "");
            e.maxLength = Math.max(1, s.getInt("max-length", 32));
            e.actions.addAll(s.getStringList("actions"));
            e.leftActions.addAll(s.getStringList("left-actions"));
            e.rightActions.addAll(s.getStringList("right-actions"));
            e.submitActions.addAll(s.getStringList("submit-actions"));
            menu.add(e);
        }
        return menu;
    }
}
