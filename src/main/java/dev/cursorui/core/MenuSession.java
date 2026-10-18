package dev.cursorui.core;

import dev.cursorui.CursorUIPlugin;
import dev.cursorui.api.ClickType;
import dev.cursorui.api.Element;
import dev.cursorui.api.ElementType;
import dev.cursorui.api.Menu;
import dev.cursorui.api.MenuClickEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A menu opened for one player.
 *
 * How it works: when the menu opens, a vertical plane is placed {@code distance}
 * blocks in front of the player's eyes (facing the player). The player is frozen in
 * place (see MenuListener) but can still turn their head. Every rotation packet is
 * turned into a ray, the ray is intersected with the plane, and that point is where
 * the cursor entity is teleported. Nothing is polled per tick, so the cursor follows
 * the mouse as fast as the client sends look packets.
 *
 * All entities are Display entities visible only to this player.
 */
public final class MenuSession {
    private static final double THICK = 0.002;
    private static final double TEXT_PX = 0.025; // blocks per text pixel at scale 1
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final class View {
        final Element el;
        BlockDisplay bg;
        BlockDisplay fill;
        TextDisplay text;
        ItemDisplay item;
        String lastText = "";

        View(Element el) {
            this.el = el;
        }
    }

    private final CursorUIPlugin plugin;
    private final Player player;
    private final Menu menu;
    private final World world;
    private final Vector center;
    private final Vector hdir;
    private final Vector right;
    private final float facingYaw;
    private final float ppb;
    private final double step;
    private final double cursorZ;
    private final float startYaw;
    private final float startPitch;

    private final Map<String, View> views = new LinkedHashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<Element> hitOrder = new ArrayList<>();
    private final Map<String, String> vars = new HashMap<>();

    private Display cursor;
    private Element hovered;
    private volatile Element awaiting;
    private BukkitTask refreshTask;
    private long lastClick;
    private boolean closed;

    public MenuSession(CursorUIPlugin plugin, Player player, Menu menu) {
        this.plugin = plugin;
        this.player = player;
        this.menu = menu;
        this.ppb = (float) Math.max(1.0, menu.pixelsPerBlock);
        this.step = plugin.getConfig().getDouble("render.layer-step", 0.006);

        Location eye = player.getEyeLocation();
        this.world = eye.getWorld();
        this.startYaw = eye.getYaw();
        this.startPitch = eye.getPitch();

        double yawRad = Math.toRadians(eye.getYaw());
        this.hdir = new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad));
        // right = hdir x up
        this.right = new Vector(-hdir.getZ(), 0, hdir.getX());
        this.center = eye.toVector().add(hdir.clone().multiply(menu.distance)).add(new Vector(0, menu.yOffset, 0));
        this.facingYaw = eye.getYaw() + 180f + (float) plugin.getConfig().getDouble("render.yaw-offset", 0.0);

        int maxLayer = 0;
        for (Element e : menu.elements) maxLayer = Math.max(maxLayer, e.layer);
        this.cursorZ = step * (maxLayer + 3);
    }

    // ------------------------------------------------------------------ build

    public void build() {
        List<Element> sorted = new ArrayList<>(menu.elements);
        sorted.sort(Comparator.comparingInt((Element e) -> e.layer));

        for (Element el : sorted) {
            View v = new View(el);
            double z = step * (el.layer + 1);
            switch (el.type) {
                case PANEL -> v.bg = rect(el.x, el.y, el.width, el.height, z, mat(el.material), false);
                case LABEL -> v.text = text(el.x, el.y, z, el.textScale);
                case BUTTON, INPUT -> {
                    v.bg = rect(el.x, el.y, el.width, el.height, z, mat(el.material), false);
                    v.text = text(el.x, el.y, z + step / 2, el.textScale);
                    hitOrder.add(el);
                }
                case ITEM -> v.item = item(el, z);
                case BAR -> {
                    v.bg = rect(el.x, el.y, el.width, el.height, z, mat(el.material), false);
                    v.fill = rect(el.x, el.y, el.width, el.height, z + step / 2, mat(el.fillMaterial), true);
                }
            }
            views.put(el.id, v);
        }
        hitOrder.sort((a, b) -> Integer.compare(b.layer, a.layer));

        spawnInteractionCatcher();
        spawnCursor();

        refresh();
        updateCursor(startYaw, startPitch);

        int ticks = Math.max(0, menu.refreshTicks);
        if (ticks > 0) {
            refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, ticks, ticks);
        }
    }

    private <T extends Display> T spawn(Class<T> type, Consumer<T> init) {
        Location loc = new Location(world, center.getX(), center.getY(), center.getZ(), facingYaw, 0f);
        Consumer<T> pre = d -> {
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            init.accept(d);
        };
        T entity = world.spawn(loc, type, pre);
        player.showEntity(plugin, entity);
        entities.add(entity);
        return entity;
    }

    /**
     * An invisible, player-sized Interaction entity around the player. It makes the client
     * send click packets for both mouse buttons even with an empty hand (vanilla does not
     * fire RIGHT_CLICK_AIR with an empty hand).
     */
    private void spawnInteractionCatcher() {
        Consumer<Interaction> pre = i -> {
            i.setVisibleByDefault(false);
            i.setPersistent(false);
            i.setInteractionWidth(3f);
            i.setInteractionHeight(3f);
            i.setResponsive(true);
        };
        Interaction it = world.spawn(player.getLocation(), Interaction.class, pre);
        player.showEntity(plugin, it);
        entities.add(it);
    }

    private void spawnCursor() {
        String type = plugin.getConfig().getString("cursor.type", "block").toLowerCase(Locale.ROOT);
        double size = plugin.getConfig().getDouble("cursor.size", 5.0);
        if (type.equals("item")) {
            Material m = Material.matchMaterial(plugin.getConfig().getString("cursor.item-material", "ARROW"));
            if (m == null || m.isAir()) m = Material.ARROW;
            ItemStack stack = new ItemStack(m);
            int cmd = plugin.getConfig().getInt("cursor.custom-model-data", 0);
            if (cmd > 0) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.setCustomModelData(cmd);
                    stack.setItemMeta(meta);
                }
            }
            float sc = (float) (size / ppb);
            float ox = (float) (plugin.getConfig().getDouble("cursor.item-offset-x", 0) / ppb);
            float oy = (float) (plugin.getConfig().getDouble("cursor.item-offset-y", 0) / ppb);
            Transformation t = new Transformation(new Vector3f(ox, oy, 0f), flip(),
                    new Vector3f(sc, sc, sc), new Quaternionf());
            cursor = spawn(ItemDisplay.class, d -> {
                d.setItemStack(stack);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(t);
            });
        } else {
            Material m = mat(plugin.getConfig().getString("cursor.material", "WHITE_CONCRETE"));
            Transformation t = rectTransform(0, 0, size, size, 0);
            cursor = spawn(BlockDisplay.class, d -> {
                d.setBlock(m.createBlockData());
                d.setTransformation(t);
            });
        }
    }

    private BlockDisplay rect(double cx, double cy, double w, double h, double z, Material m, boolean interpolate) {
        Transformation t = rectTransform(cx, cy, w, h, z);
        return spawn(BlockDisplay.class, d -> {
            d.setBlock(m.createBlockData());
            d.setTransformation(t);
            if (interpolate) d.setInterpolationDuration(3);
        });
    }

    private Transformation rectTransform(double cx, double cy, double w, double h, double z) {
        return new Transformation(
                new Vector3f((float) ((cx - w / 2) / ppb), (float) ((cy - h / 2) / ppb), (float) z),
                new Quaternionf(),
                new Vector3f((float) (w / ppb), (float) (h / ppb), (float) THICK),
                new Quaternionf());
    }

    private TextDisplay text(double cx, double cy, double z, double scale) {
        float s = (float) scale;
        float yOff = (float) (plugin.getConfig().getDouble("render.text-y-offset", -4.5) * TEXT_PX * scale);
        Transformation t = new Transformation(
                new Vector3f((float) (cx / ppb), (float) (cy / ppb) + yOff, (float) z),
                new Quaternionf(),
                new Vector3f(s, s, s),
                new Quaternionf());
        return spawn(TextDisplay.class, d -> {
            d.text(Component.empty());
            d.setLineWidth(10000);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setTransformation(t);
        });
    }

    private ItemDisplay item(Element el, double z) {
        Material m = Material.matchMaterial(el.item == null ? "" : el.item);
        if (m == null || m.isAir()) m = Material.STONE;
        ItemStack stack = new ItemStack(m);
        if (el.customModelData > 0) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(el.customModelData);
                stack.setItemMeta(meta);
            }
        }
        float sx = (float) (el.width / ppb);
        float sy = (float) (el.height / ppb);
        Transformation t = new Transformation(
                new Vector3f((float) (el.x / ppb), (float) (el.y / ppb), (float) z),
                flip(),
                new Vector3f(sx, sy, Math.min(sx, sy) * 0.2f),
                new Quaternionf());
        return spawn(ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(t);
        });
    }

    private Quaternionf flip() {
        Quaternionf q = new Quaternionf();
        if (plugin.getConfig().getBoolean("render.item-flip", false)) {
            q.rotateY((float) Math.PI);
        }
        return q;
    }

    private static Material mat(String name) {
        Material m = Material.matchMaterial(name == null ? "" : name);
        if (m == null || !m.isBlock()) return Material.GRAY_CONCRETE;
        return m;
    }

    // --------------------------------------------------------------- refresh

    private void refresh() {
        if (closed) return;
        for (View v : views.values()) {
            refreshText(v, v.el == hovered);
            if (v.fill != null) updateBar(v);
        }
    }

    private String textFor(View v, boolean hover) {
        Element el = v.el;
        if (el.type == ElementType.INPUT) {
            String val = vars.get("input:" + el.id);
            if (val != null) return el.valueText.replace("{value}", MM.escapeTags(val));
        }
        return hover && !el.hoverText.isEmpty() ? el.hoverText : el.text;
    }

    private void refreshText(View v, boolean hover) {
        if (v.text == null) return;
        String computed = Placeholders.mini(player, textFor(v, hover), vars);
        if (computed.equals(v.lastText)) return;
        v.lastText = computed;
        v.text.text(MM.deserialize(computed));
    }

    private void updateBar(View v) {
        Element el = v.el;
        double ratio = Placeholders.number(player, el.value, vars);
        ratio = Math.max(0.0001, Math.min(1.0, ratio));
        double fw = el.width * ratio;
        double cx = el.x - el.width / 2 + fw / 2;
        v.fill.setInterpolationDelay(0);
        v.fill.setTransformation(rectTransform(cx, el.y, fw, el.height, step * (el.layer + 1) + step / 2));
    }

    // ---------------------------------------------------------------- cursor

    /** Called for every look packet. Must run on the main thread. */
    public void updateCursor(float yaw, float pitch) {
        if (closed || cursor == null) return;
        Vector d = new Location(null, 0, 0, 0, yaw, pitch).getDirection();
        double denom = d.dot(hdir);
        if (denom < 0.1) {
            setHover(null); // looking away from the menu plane
            return;
        }
        double t = menu.distance / denom;
        double lx = d.dot(right) * t;
        double ly = d.getY() * t - menu.yOffset;

        double halfW = menu.width / 2.0 / ppb;
        double halfH = menu.height / 2.0 / ppb;
        lx = Math.max(-halfW, Math.min(halfW, lx));
        ly = Math.max(-halfH, Math.min(halfH, ly));

        double px = lx * ppb;
        double py = ly * ppb;

        Element hit = null;
        for (Element el : hitOrder) {
            if (Math.abs(px - el.x) <= el.width / 2 && Math.abs(py - el.y) <= el.height / 2) {
                hit = el;
                break;
            }
        }
        setHover(hit);

        double wx = center.getX() + right.getX() * lx - hdir.getX() * cursorZ;
        double wy = center.getY() + ly;
        double wz = center.getZ() + right.getZ() * lx - hdir.getZ() * cursorZ;
        cursor.teleport(new Location(world, wx, wy, wz, facingYaw, 0f));
    }

    private void setHover(Element next) {
        if (next == hovered) return;
        if (hovered != null) applyHover(hovered, false);
        hovered = next;
        if (next != null) {
            applyHover(next, true);
            sound(plugin.getConfig().getString("sounds.hover", ""), 0.4f, 1.6f);
        }
    }

    private void applyHover(Element el, boolean on) {
        View v = views.get(el.id);
        if (v == null) return;
        if (v.bg != null) {
            String name = on && !el.hoverMaterial.isEmpty() ? el.hoverMaterial : el.material;
            v.bg.setBlock(mat(name).createBlockData());
        }
        refreshText(v, on);
    }

    private void sound(String key, float volume, float pitch) {
        if (key == null || key.isEmpty()) return;
        player.playSound(player.getLocation(), key, volume, pitch);
    }

    // ----------------------------------------------------------------- input

    public void click(ClickType type) {
        if (closed) return;
        long now = System.currentTimeMillis();
        if (now - lastClick < 100) return; // several events can fire for one physical click
        lastClick = now;

        if (awaiting != null && awaiting != hovered) cancelInput();
        Element el = hovered;
        if (el == null) return;

        sound(plugin.getConfig().getString("sounds.click", ""), 1f, 1f);

        if (el.type == ElementType.INPUT) {
            beginInput(el);
            return;
        }
        MenuClickEvent event = new MenuClickEvent(player, menu, el, type);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        if (el.handler != null) el.handler.onClick(player, el, type);

        List<String> acts = new ArrayList<>(el.actions);
        acts.addAll(type == ClickType.LEFT ? el.leftActions : el.rightActions);
        ActionRunner.run(plugin, player, acts, vars);
    }

    private void beginInput(Element el) {
        awaiting = el;
        player.sendMessage(plugin.msg("input-prompt"));
    }

    private void cancelInput() {
        awaiting = null;
        player.sendMessage(plugin.msg("input-cancelled"));
    }

    public boolean isAwaitingInput() {
        return awaiting != null;
    }

    /** Called on the main thread with the player's next chat message. */
    public void submitInput(String value) {
        Element el = awaiting;
        awaiting = null;
        if (el == null || closed) return;
        if (value.equalsIgnoreCase("cancel")) {
            player.sendMessage(plugin.msg("input-cancelled"));
            return;
        }
        if (value.length() > el.maxLength) value = value.substring(0, el.maxLength);
        vars.put("input:" + el.id, value);
        vars.put("input", value);

        View v = views.get(el.id);
        if (v != null) refreshText(v, hovered == el);
        if (el.inputHandler != null) el.inputHandler.accept(player, value);
        ActionRunner.run(plugin, player, el.submitActions, vars);
    }

    // ----------------------------------------------------------------- close

    public void close() {
        if (closed) return;
        closed = true;
        if (refreshTask != null) refreshTask.cancel();
        for (Entity e : entities) e.remove();
        entities.clear();
        player.sendActionBar(Component.empty());
    }

    public Player getPlayer() { return player; }
    public Menu getMenu() { return menu; }
}
