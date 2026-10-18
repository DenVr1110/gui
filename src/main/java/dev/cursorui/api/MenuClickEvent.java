package dev.cursorui.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a player clicks a BUTTON element. Cancel it to stop the element's actions. */
public class MenuClickEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Menu menu;
    private final Element element;
    private final ClickType clickType;
    private boolean cancelled;

    public MenuClickEvent(Player player, Menu menu, Element element, ClickType clickType) {
        super(player);
        this.menu = menu;
        this.element = element;
        this.clickType = clickType;
    }

    public Menu getMenu() { return menu; }
    public Element getElement() { return element; }
    public ClickType getClickType() { return clickType; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
