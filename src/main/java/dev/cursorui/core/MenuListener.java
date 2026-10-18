package dev.cursorui.core;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import dev.cursorui.CursorUIPlugin;
import dev.cursorui.api.ClickType;
import dev.cursorui.hud.HudManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class MenuListener implements Listener {
    private final CursorUIPlugin plugin;
    private final MenuManager menus;
    private final HudManager hud;

    public MenuListener(CursorUIPlugin plugin, MenuManager menus, HudManager hud) {
        this.plugin = plugin;
        this.menus = menus;
        this.hud = hud;
    }

    /**
     * Freezes the player's position (rotation stays free) and moves the cursor.
     * This runs for every look packet, which is what makes the cursor feel instant.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        MenuSession s = menus.get(e.getPlayer());
        if (s == null) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location locked = from.clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            e.setTo(locked);
            to = locked;
        }
        s.updateCursor(to.getYaw(), to.getPitch());
    }

    // ----------------------------------------------------------------- clicks

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        MenuSession s = menus.get(e.getPlayer());
        if (s == null) return;
        e.setCancelled(true);
        Action a = e.getAction();
        if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            s.click(ClickType.LEFT);
        } else if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && e.getHand() == EquipmentSlot.HAND) {
            s.click(ClickType.RIGHT);
        }
    }

    /** Right click on the invisible Interaction entity around the player. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        MenuSession s = menus.get(e.getPlayer());
        if (s == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) s.click(ClickType.RIGHT);
    }

    /** Left click on the invisible Interaction entity around the player. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        MenuSession s = menus.get(e.getPlayer());
        if (s == null) return;
        e.setCancelled(true);
        s.click(ClickType.LEFT);
    }

    // ------------------------------------------------------------ protection

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        MenuSession s = menus.get(p);
        if (s != null && s.getMenu().invulnerable) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (menus.isInMenu(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (menus.isInMenu(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (menus.isInMenu(e.getPlayer())) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- closing

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        MenuSession s = menus.get(e.getPlayer());
        if (s != null && e.isSneaking() && s.getMenu().closeOnSneak) menus.close(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        MenuSession s = menus.get(e.getPlayer());
        if (s == null) return;
        e.setCancelled(true);
        if (s.getMenu().closeOnSwap) menus.close(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        // Our own freeze goes through PlayerMoveEvent#setTo and is reported with cause UNKNOWN.
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;
        if (menus.isInMenu(e.getPlayer())) menus.close(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        menus.close(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        menus.close(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        menus.close(e.getPlayer());
        hud.forget(e.getPlayer());
    }

    // ------------------------------------------------------------- text input

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        MenuSession s = menus.get(p);
        if (s == null || !s.isAwaitingInput()) return;
        e.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            MenuSession current = menus.get(p);
            if (current != null) current.submitInput(message);
        });
    }
}
