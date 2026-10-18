package dev.cursorui.command;

import dev.cursorui.CursorUIPlugin;
import dev.cursorui.api.Menu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class CursorUICommand implements TabExecutor {
    private static final List<String> SUBS = List.of("open", "close", "list", "reload", "hud");
    private final CursorUIPlugin plugin;

    public CursorUICommand(CursorUIPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            s.sendMessage(plugin.msg("help"));
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "open" -> open(s, a);
            case "close" -> close(s, a);
            case "list" -> {
                if (!s.hasPermission("cursorui.admin") && !s.hasPermission("cursorui.open")) {
                    s.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                s.sendMessage(plugin.msg("list", "{menus}", String.join(", ", plugin.getMenus().ids())));
            }
            case "reload" -> {
                if (!s.hasPermission("cursorui.admin")) {
                    s.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                plugin.reloadAll();
                s.sendMessage(plugin.msg("reloaded"));
            }
            case "hud" -> hud(s, a);
            default -> s.sendMessage(plugin.msg("help"));
        }
        return true;
    }

    private void open(CommandSender s, String[] a) {
        if (a.length < 2) {
            s.sendMessage(plugin.msg("help"));
            return;
        }
        if (!s.hasPermission("cursorui.open") && !s.hasPermission("cursorui.admin")) {
            s.sendMessage(plugin.msg("no-permission"));
            return;
        }
        Optional<Menu> menu = plugin.getMenus().getMenu(a[1]);
        if (menu.isEmpty()) {
            s.sendMessage(plugin.msg("unknown-menu", "{menu}", a[1]));
            return;
        }
        String perm = menu.get().permission;
        if (!perm.isEmpty() && !s.hasPermission(perm) && !s.hasPermission("cursorui.admin")) {
            s.sendMessage(plugin.msg("no-permission"));
            return;
        }
        Player target = target(s, a, 2);
        if (target == null) return;
        if (plugin.getMenus().open(target, menu.get())) {
            s.sendMessage(plugin.msg("opened", "{menu}", menu.get().id, "{player}", target.getName()));
        }
    }

    private void close(CommandSender s, String[] a) {
        Player target = target(s, a, 1);
        if (target == null) return;
        plugin.getMenus().close(target);
        s.sendMessage(plugin.msg("closed", "{player}", target.getName()));
    }

    private void hud(CommandSender s, String[] a) {
        if (!s.hasPermission("cursorui.hud") && !s.hasPermission("cursorui.admin")) {
            s.sendMessage(plugin.msg("no-permission"));
            return;
        }
        String mode = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "toggle";
        Player target = target(s, a, 2);
        if (target == null) return;
        boolean now = switch (mode) {
            case "on" -> true;
            case "off" -> false;
            default -> !plugin.getHud().isEnabled(target);
        };
        plugin.getHud().setEnabled(target, now);
        s.sendMessage(plugin.msg(now ? "hud-on" : "hud-off", "{player}", target.getName()));
    }

    /** Resolves the player argument at idx, or the sender itself. Returns null after messaging on failure. */
    private Player target(CommandSender s, String[] a, int idx) {
        if (a.length > idx) {
            if (!s.hasPermission("cursorui.admin")) {
                s.sendMessage(plugin.msg("no-permission"));
                return null;
            }
            Player p = Bukkit.getPlayerExact(a[idx]);
            if (p == null) s.sendMessage(plugin.msg("player-not-found"));
            return p;
        }
        if (s instanceof Player p) return p;
        s.sendMessage(plugin.msg("console-needs-player"));
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return filter(SUBS, a[0]);
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2 && sub.equals("open")) return filter(new ArrayList<>(plugin.getMenus().ids()), a[1]);
        if (a.length == 2 && sub.equals("hud")) return filter(List.of("on", "off", "toggle"), a[1]);
        if ((a.length == 3 && (sub.equals("open") || sub.equals("hud"))) || (a.length == 2 && sub.equals("close"))) {
            if (!s.hasPermission("cursorui.admin")) return List.of();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, a[a.length - 1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
