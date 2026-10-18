package dev.cursorui;

import dev.cursorui.api.CursorUIAPI;
import dev.cursorui.command.CursorUICommand;
import dev.cursorui.core.CursorUIApiImpl;
import dev.cursorui.core.MenuListener;
import dev.cursorui.core.MenuManager;
import dev.cursorui.hud.HudManager;
import java.io.File;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class CursorUIPlugin extends JavaPlugin {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MenuManager menus;
    private HudManager hud;
    private CursorUIApiImpl api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "hud.yml").exists()) saveResource("hud.yml", false);
        if (!new File(getDataFolder(), "menus").exists()) saveResource("menus/example.yml", false);

        menus = new MenuManager(this);
        hud = new HudManager(this);
        menus.reload();
        hud.reload();

        api = new CursorUIApiImpl(menus, hud);
        Bukkit.getServicesManager().register(CursorUIAPI.class, api, this, ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this, menus, hud), this);

        PluginCommand cmd = getCommand("cursorui");
        if (cmd != null) {
            CursorUICommand handler = new CursorUICommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
    }

    @Override
    public void onDisable() {
        if (menus != null) menus.closeAll();
        if (hud != null) hud.shutdown();
        Bukkit.getServicesManager().unregisterAll(this);
    }

    public void reloadAll() {
        reloadConfig();
        menus.reload();
        hud.reload();
    }

    public MenuManager getMenus() { return menus; }
    public HudManager getHud() { return hud; }
    public CursorUIAPI getApi() { return api; }

    /** Builds a prefixed message from config. Pairs are {placeholder}, value (values are escaped). */
    public Component msg(String key, String... pairs) {
        String s = getConfig().getString("messages." + key, "<red>Missing message: " + key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            s = s.replace(pairs[i], MM.escapeTags(pairs[i + 1]));
        }
        return MM.deserialize(getConfig().getString("messages.prefix", "") + s);
    }
}
