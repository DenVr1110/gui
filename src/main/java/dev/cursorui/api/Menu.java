package dev.cursorui.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A cursor menu definition. Register it through {@link CursorUIAPI#registerMenu(Menu)}. */
public final class Menu {
    public final String id;
    public String permission = "";
    /** Distance in blocks between the player's eyes and the menu plane. */
    public double distance = 3.0;
    public double pixelsPerBlock = 100.0;
    /** Size of the usable area in menu pixels; the cursor is clamped to it. */
    public double width = 420.0;
    public double height = 260.0;
    /** Vertical shift of the plane (blocks) relative to eye height. */
    public double yOffset = 0.0;
    public boolean closeOnSneak = true;
    public boolean closeOnSwap = true;
    public boolean invulnerable = true;
    /** Ticks between text/bar refreshes; 0 disables refreshing. */
    public int refreshTicks = 10;
    public final List<Element> elements = new ArrayList<>();

    public Menu(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Menu add(Element element) {
        elements.add(element);
        return this;
    }
}
