package dev.cursorui.hud;

import dev.cursorui.CursorUIPlugin;
import dev.cursorui.core.Placeholders;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Config-driven HUD built from boss bars (top of screen) and the action bar
 * (above the hotbar). It needs no resource pack.
 */
public final class HudManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final class Entry {
        String id;
        boolean boss;
        String text = "";
        String progress = "";
        BossBar.Color color = BossBar.Color.WHITE;
        BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;
        String permission = "";
        Set<String> worlds = new HashSet<>();
    }

    private final CursorUIPlugin plugin;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<UUID, Map<String, BossBar>> bars = new HashMap<>();
    private final Set<UUID> disabled = new HashSet<>();
    private BukkitTask task;
    private boolean enabled = true;
    private Component separator = Component.text("  ");

    public HudManager(CursorUIPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        shutdown();
        entries.clear();

        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "hud.yml"));
        enabled = y.getBoolean("enabled", true);
        separator = MM.deserialize(y.getString("separator", "  "));
        int ticks = Math.max(1, y.getInt("update-ticks", 10));

        ConfigurationSection els = y.getConfigurationSection("elements");
        if (els != null) {
            for (String key : els.getKeys(false)) {
                ConfigurationSection s = els.getConfigurationSection(key);
                if (s == null) continue;
                Entry e = new Entry();
                e.id = key;
                e.boss = s.getString("type", "actionbar").equalsIgnoreCase("bossbar");
                e.text = s.getString("text", "");
                e.progress = s.getString("progress", "");
                e.permission = s.getString("permission", "");
                for (String w : s.getStringList("worlds")) e.worlds.add(w.toLowerCase(Locale.ROOT));
                try {
                    e.color = BossBar.Color.valueOf(s.getString("color", "WHITE").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("HUD '" + key + "': unknown bossbar color.");
                }
                try {
                    e.overlay = BossBar.Overlay.valueOf(s.getString("overlay", "PROGRESS").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("HUD '" + key + "': unknown bossbar overlay.");
                }
                entries.add(e);
            }
        }
        if (enabled && !entries.isEmpty()) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, ticks);
        }
    }

    /** Stops updating and removes every boss bar. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map.Entry<UUID, Map<String, BossBar>> e : bars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            for (BossBar bar : e.getValue().values()) p.hideBossBar(bar);
        }
        bars.clear();
    }

    public void setEnabled(Player player, boolean on) {
        if (on) {
            disabled.remove(player.getUniqueId());
        } else {
            disabled.add(player.getUniqueId());
            clear(player);
            player.sendActionBar(Component.empty());
        }
    }

    public boolean isEnabled(Player player) {
        return !disabled.contains(player.getUniqueId());
    }

    public void forget(Player player) {
        bars.remove(player.getUniqueId());
        disabled.remove(player.getUniqueId());
    }

    private void clear(Player player) {
        Map<String, BossBar> mine = bars.remove(player.getUniqueId());
        if (mine == null) return;
        for (BossBar bar : mine.values()) player.hideBossBar(bar);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    private boolean allowed(Player p, Entry e) {
        if (!e.permission.isEmpty() && !p.hasPermission(e.permission)) return false;
        return e.worlds.isEmpty() || e.worlds.contains(p.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    private void update(Player p) {
        boolean on = isEnabled(p);
        Map<String, BossBar> mine = bars.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>());
        List<Component> action = new ArrayList<>();

        for (Entry en : entries) {
            boolean show = on && allowed(p, en);
            if (en.boss) {
                BossBar bar = mine.get(en.id);
                if (!show) {
                    if (bar != null) {
                        p.hideBossBar(bar);
                        mine.remove(en.id);
                    }
                    continue;
                }
                Component name = MM.deserialize(Placeholders.mini(p, en.text, null));
                float progress = en.progress.isEmpty()
                        ? 1f
                        : (float) Math.max(0.0, Math.min(1.0, Placeholders.number(p, en.progress, null)));
                if (bar == null) {
                    bar = BossBar.bossBar(name, progress, en.color, en.overlay);
                    mine.put(en.id, bar);
                    p.showBossBar(bar);
                } else {
                    bar.name(name);
                    bar.progress(progress);
                }
            } else if (show) {
                action.add(MM.deserialize(Placeholders.mini(p, en.text, null)));
            }
        }
        if (!action.isEmpty()) {
            p.sendActionBar(Component.join(JoinConfiguration.separator(separator), action));
        }
    }
}
