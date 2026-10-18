package dev.cursorui.core;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Replaces {builtin} placeholders, menu variables and (optionally, via reflection)
 * PlaceholderAPI %placeholders%.
 *
 * "mini" output is safe to feed into MiniMessage (values are escaped);
 * "plain" output is meant for commands (user-typed values are sanitized).
 */
public final class Placeholders {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern PAPI_TOKEN = Pattern.compile("%[^%\\s]+%");
    private static Method papiMethod;
    private static boolean papiResolved;

    private Placeholders() {}

    public static String mini(Player player, String text, Map<String, String> vars) {
        return apply(player, text, vars, true);
    }

    public static String plain(Player player, String text, Map<String, String> vars) {
        return apply(player, text, vars, false);
    }

    /** Evaluates "a/b" or a plain number after placeholder replacement. Returns 0 on failure. */
    public static double number(Player player, String expression, Map<String, String> vars) {
        if (expression == null || expression.isBlank()) return 0;
        String s = plain(player, expression, vars).replace(" ", "");
        try {
            int slash = s.indexOf('/');
            if (slash > 0) {
                double a = Double.parseDouble(s.substring(0, slash));
                double b = Double.parseDouble(s.substring(slash + 1));
                return b == 0 ? 0 : a / b;
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String apply(Player player, String text, Map<String, String> vars, boolean mini) {
        if (text == null || text.isEmpty()) return "";
        String out = text;
        if (player != null && out.indexOf('{') >= 0) {
            for (Map.Entry<String, String> e : builtins(player).entrySet()) {
                out = out.replace("{" + e.getKey() + "}", mini ? MM.escapeTags(e.getValue()) : e.getValue());
            }
        }
        if (vars != null && !vars.isEmpty() && out.indexOf('{') >= 0) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                String v = e.getValue();
                out = out.replace("{" + e.getKey() + "}", mini ? MM.escapeTags(v) : sanitize(v));
            }
        }
        if (player != null && out.indexOf('%') >= 0) {
            out = papi(player, out, mini);
        }
        return out;
    }

    private static Map<String, String> builtins(Player p) {
        Map<String, String> m = new HashMap<>();
        m.put("player", p.getName());
        m.put("displayname", PlainTextComponentSerializer.plainText().serialize(p.displayName()));
        m.put("health", String.valueOf((int) Math.ceil(p.getHealth())));
        m.put("max_health", String.valueOf((int) Math.ceil(p.getMaxHealth())));
        m.put("food", String.valueOf(p.getFoodLevel()));
        m.put("level", String.valueOf(p.getLevel()));
        m.put("world", p.getWorld().getName());
        m.put("x", String.valueOf(p.getLocation().getBlockX()));
        m.put("y", String.valueOf(p.getLocation().getBlockY()));
        m.put("z", String.valueOf(p.getLocation().getBlockZ()));
        m.put("ping", String.valueOf(p.getPing()));
        m.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        m.put("max_players", String.valueOf(Bukkit.getMaxPlayers()));
        m.put("tps", String.format("%.1f", Math.min(20.0, Bukkit.getTPS()[0])));
        return m;
    }

    private static String sanitize(String v) {
        return v.replaceAll("[^\\p{L}\\p{N}_ .,:\\-]", "");
    }

    private static boolean resolvePapi() {
        if (!papiResolved) {
            papiResolved = true;
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    papiMethod = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                            .getMethod("setPlaceholders", Player.class, String.class);
                } catch (ReflectiveOperationException ex) {
                    papiMethod = null;
                }
            }
        }
        return papiMethod != null;
    }

    private static String papi(Player player, String in, boolean mini) {
        if (!resolvePapi()) return in;
        Matcher m = PAPI_TOKEN.matcher(in);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String token = m.group();
            String res = token;
            try {
                Object o = papiMethod.invoke(null, player, token);
                if (o instanceof String s) res = s;
            } catch (ReflectiveOperationException ignored) {
                // keep token as is
            }
            if (mini && !res.equals(token)) {
                // legacy colors -> MiniMessage, everything else escaped (no click events etc.)
                res = MM.serialize(LegacyComponentSerializer.legacySection().deserialize(res));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(res));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
