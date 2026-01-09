package kitchenmaker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Zentrale Theme-Klasse für alle UI-Farben, Schriftarten und Dimensionen.
 *
 * @author Niklas Puls
 */
public final class Theme {

    // Hintergrundfarben
    public static final Color BACKGROUND = new Color(30, 30, 30);
    public static final Color BACKGROUND_DARK = new Color(25, 25, 30);
    public static final Color PANEL = new Color(35, 35, 40);
    public static final Color LIST_BACKGROUND = new Color(40, 40, 45);

    // Textfarben
    public static final Color TEXT_PRIMARY = Color.WHITE;
    public static final Color TEXT_SECONDARY = new Color(220, 220, 220);
    public static final Color TEXT_MUTED = new Color(180, 180, 180);
    public static final Color TEXT_LABEL = new Color(150, 150, 150);
    public static final Color TEXT_DISABLED = new Color(100, 100, 100);

    // Akzentfarben
    public static final Color SELECTION = new Color(70, 130, 180);
    public static final Color HOVER = new Color(60, 60, 70);
    public static final Color BORDER = new Color(60, 60, 65);
    public static final Color ICON_BORDER = new Color(80, 80, 85);
    public static final Color ICON_BORDER_LIGHT = new Color(100, 100, 100);

    // Statusfarben
    public static final Color SUCCESS = new Color(100, 180, 100);
    public static final Color ERROR = new Color(200, 80, 80);

    // Schriftarten
    public static final Font TITLE = new Font("SansSerif", Font.BOLD, 14);
    public static final Font LABEL = new Font("SansSerif", Font.BOLD, 12);
    public static final Font LABEL_SMALL = new Font("SansSerif", Font.PLAIN, 11);

    // Standard-Dimensionen
    public static final Dimension TOOLBAR_BUTTON = new Dimension(36, 36);
    public static final Dimension SIDE_PANEL = new Dimension(280, 0);
    public static final Dimension WEBCAM_PREVIEW = new Dimension(260, 195);
    public static final Dimension COLOR_BUTTON = new Dimension(80, 28);
    public static final Dimension LABEL_FIELD = new Dimension(80, 20);
    public static final Dimension VALUE_LABEL = new Dimension(40, 20);
}
