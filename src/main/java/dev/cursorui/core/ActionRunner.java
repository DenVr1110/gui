package dev.cursorui.core;

import dev.cursorui.CursorUIPlugin;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Executes action strings such as:
 *   [player] spawn          [console] give {player} apple 1
 *   [close]                 [menu] other-menu
 *   [message] text          [actionbar] text
 *   [sound] ui.button.click
 */
final class ActionRunner {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ActionRunner() {}

    static void run(CursorUIPlugin plugin, Player player, List<String> actions, Map<String, String> vars) {
        for (String raw : actions) {
            if (raw == null || raw.isBlank()) continue;
            String s = raw.trim();
            String tag = "";
            String arg = s;
            if (s.startsWith("[")) {
                int end = s.indexOf(']');
                if (end > 0) {
                    tag = s.substring(1, end).toLowerCase(Locale.ROOT);
                    arg = s.substring(end + 1).trim();
                }
            }
            switch (tag) {
                case "player" -> Bukkit.dispatchCommand(player, stripSlash(Placeholders.plain(player, arg, vars)));
                case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(Placeholders.plain(player, arg, vars)));
                case "close" -> plugin.getMenus().close(player);
                case "menu" -> {
                    final String id = arg;
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getMenus().open(player, id));
                }
                case "message" -> player.sendMessage(MM.deserialize(Placeholders.mini(player, arg, vars)));
                case "actionbar" -> player.sendActionBar(MM.deserialize(Placeholders.mini(player, arg, vars)));
                case "sound" -> player.playSound(player.getLocation(), arg, 1f, 1f);
                default -> plugin.getLogger().warning("Unknown action: " + raw);
            }
        }
    }

    private static String stripSlash(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }
}
