package dev.cursorui.api;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface ClickHandler {
    void onClick(Player player, Element element, ClickType type);
}
