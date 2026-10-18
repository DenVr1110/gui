package dev.cursorui.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;

/**
 * One element of a {@link Menu}. Coordinates are in menu pixels: (0,0) is the
 * center of the menu, x grows to the right, y grows upwards.
 */
public final class Element {
    public final String id;
    public final ElementType type;

    public double x;
    public double y;
    public double width = 100;
    public double height = 30;
    /** Higher layers are drawn in front and win hover/click tests. */
    public int layer = 1;

    /** Block material name for PANEL/BUTTON/INPUT/BAR background. */
    public String material = "GRAY_CONCRETE";
    public String hoverMaterial = "";
    /** BAR only: material of the filled part. */
    public String fillMaterial = "LIME_CONCRETE";

    /** MiniMessage text with {placeholders}. For INPUT this is the empty-state text. */
    public String text = "";
    public String hoverText = "";
    /** INPUT only: text shown once a value exists. {value} is the typed value. */
    public String valueText = "<white>{value}";
    /** 1.0 = vanilla text size (about 0.225 blocks per line). */
    public double textScale = 0.6;

    /** ITEM only. */
    public String item = "STONE";
    public int customModelData = 0;

    /** BAR only: expression, e.g. "{health}/{max_health}" or "0.5". */
    public String value = "";
    /** INPUT only. */
    public int maxLength = 32;

    public final List<String> actions = new ArrayList<>();
    public final List<String> leftActions = new ArrayList<>();
    public final List<String> rightActions = new ArrayList<>();
    public final List<String> submitActions = new ArrayList<>();

    /** Code-side click callback (in addition to the actions lists). */
    public ClickHandler handler;
    /** INPUT only: called with the typed value. */
    public BiConsumer<Player, String> inputHandler;

    public Element(String id, ElementType type) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static Element panel(String id, double x, double y, double w, double h, String material) {
        Element e = new Element(id, ElementType.PANEL);
        e.x = x; e.y = y; e.width = w; e.height = h; e.material = material;
        return e;
    }

    public static Element label(String id, double x, double y, String text) {
        Element e = new Element(id, ElementType.LABEL);
        e.x = x; e.y = y; e.text = text;
        return e;
    }

    public static Element button(String id, double x, double y, double w, double h, String text) {
        Element e = new Element(id, ElementType.BUTTON);
        e.x = x; e.y = y; e.width = w; e.height = h; e.text = text;
        return e;
    }

    public Element layer(int layer) { this.layer = layer; return this; }
    public Element materials(String normal, String hover) { this.material = normal; this.hoverMaterial = hover; return this; }
    public Element onClick(ClickHandler handler) { this.handler = handler; return this; }
}
