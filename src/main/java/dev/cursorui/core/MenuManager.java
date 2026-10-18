package dev.cursorui.core;

import dev.cursorui.CursorUIPlugin;
import dev.cursorui.api.Menu;
import dev.cursorui.api.MenuCloseEvent;
import dev.cursorui.api.MenuOpenEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class MenuManager {
    private final CursorUIPlugin plugin;
    private final Map<String, Menu> menus = new LinkedHashMap<>();
    private final Set<String> fileMenus = new HashSet<>();
    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();

    public MenuManager(CursorUIPlugin plugin) {
        this.plugin = plugin;
    }

    /** Closes everything and re-reads plugins/CursorUI/menus/*.yml. Code-registered menus are kept. */
    public void reload() {
        closeAll();
        for (String id : fileMenus) menus.remove(id);
        fileMenus.clear();

        File dir = new File(plugin.getDataFolder(), "menus");
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            try {
                Menu m = MenuLoader.load(f, plugin.getLogger());
                String key = m.id.toLowerCase(Locale.ROOT);
                menus.put(key, m);
                fileMenus.add(key);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load menu " + f.getName(), ex);
            }
        }
        plugin.getLogger().info("Loaded " + fileMenus.size() + " menu(s).");
    }

    public void register(Menu menu) {
        menus.put(menu.id.toLowerCase(Locale.ROOT), menu);
    }

    public boolean unregister(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        Menu removed = menus.remove(key);
        if (removed == null) return false;
        fileMenus.remove(key);
        for (MenuSession s : new ArrayList<>(sessions.values())) {
            if (s.getMenu() == removed) close(s.getPlayer());
        }
        return true;
    }

    public Optional<Menu> getMenu(String id) {
        return Optional.ofNullable(menus.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<String> ids() {
        return new ArrayList<>(menus.keySet());
    }

    public boolean open(Player player, String id) {
        Optional<Menu> menu = getMenu(id);
        return menu.isPresent() && open(player, menu.get());
    }

    public boolean open(Player player, Menu menu) {
        if (!player.isOnline() || player.isDead()) return false;
        close(player);

        MenuOpenEvent event = new MenuOpenEvent(player, menu);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        MenuSession session = new MenuSession(plugin, player, menu);
        sessions.put(player.getUniqueId(), session);
        try {
            session.build();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open menu '" + menu.id + "'", ex);
            sessions.remove(player.getUniqueId());
            session.close();
            return false;
        }
        return true;
    }

    public void close(Player player) {
        MenuSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.close();
        Bukkit.getPluginManager().callEvent(new MenuCloseEvent(player, session.getMenu()));
    }

    public void closeAll() {
        for (MenuSession s : new ArrayList<>(sessions.values())) {
            close(s.getPlayer());
        }
    }

    public MenuSession get(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean isInMenu(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public List<MenuSession> sessions() {
        return new ArrayList<>(sessions.values());
    }
}
