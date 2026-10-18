package dev.cursorui.api;

import java.util.Collection;
import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * Public API. Obtain it with:
 * <pre>
 * CursorUIAPI api = Bukkit.getServicesManager().load(CursorUIAPI.class);
 * </pre>
 */
public interface CursorUIAPI {
    /** Registers (or replaces) a menu defined in code. */
    void registerMenu(Menu menu);

    boolean unregisterMenu(String id);

    Optional<Menu> getMenu(String id);

    Collection<String> getMenuIds();

    /** Opens a registered menu. Returns false if it does not exist or an event cancelled it. */
    boolean openMenu(Player player, String id);

    boolean openMenu(Player player, Menu menu);

    void closeMenu(Player player);

    boolean isInMenu(Player player);

    void setHudEnabled(Player player, boolean enabled);

    boolean isHudEnabled(Player player);
}
