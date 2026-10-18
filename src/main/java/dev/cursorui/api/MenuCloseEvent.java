package dev.cursorui.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class MenuCloseEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Menu menu;
    private boolean cancelled;

    public MenuCloseEvent(Player player, Menu menu) {
        super(player);
        this.menu = menu;
    }

    public Menu getMenu() { return menu; }

    /** Only meaningful for the open event; cancelling a close event has no effect. */
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
