package dev.cursorui.api;

public enum ElementType {
    /** A flat colored rectangle (any full-block material). */
    PANEL,
    /** Text only. */
    LABEL,
    /** Rectangle + text, reacts to hover and click. */
    BUTTON,
    /** An item (flat item / custom model data) drawn on the plane. */
    ITEM,
    /** A button that captures the player's next chat message as a value. */
    INPUT,
    /** A progress bar driven by an expression such as "{health}/{max_health}". */
    BAR
}
