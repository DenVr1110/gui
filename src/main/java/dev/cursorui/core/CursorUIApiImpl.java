package dev.cursorui.core;

import dev.cursorui.api.CursorUIAPI;
import dev.cursorui.api.Menu;
import dev.cursorui.hud.HudManager;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.entity.Player;

public final class CursorUIApiImpl implements CursorUIAPI {
    private final MenuManager menus;
    private final HudManager hud;

    public CursorUIApiImpl(MenuManager menus, HudManager hud) {
        this.menus = menus;
        this.hud = hud;
    }

    @Override public void registerMenu(Menu menu) { menus.register(menu); }
    @Override public boolean unregisterMenu(String id) { return menus.unregister(id); }
    @Override public Optional<Menu> getMenu(String id) { return menus.getMenu(id); }
    @Override public Collection<String> getMenuIds() { return menus.ids(); }
    @Override public boolean openMenu(Player player, String id) { return menus.open(player, id); }
    @Override public boolean openMenu(Player player, Menu menu) { return menus.open(player, menu); }
    @Override public void closeMenu(Player player) { menus.close(player); }
    @Override public boolean isInMenu(Player player) { return menus.isInMenu(player); }
    @Override public void setHudEnabled(Player player, boolean enabled) { hud.setEnabled(player, enabled); }
    @Override public boolean isHudEnabled(Player player) { return hud.isEnabled(player); }
}
