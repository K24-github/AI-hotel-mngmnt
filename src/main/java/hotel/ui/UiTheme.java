package hotel.ui;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class UiTheme {
    static final Color BACKGROUND = new Color(241, 245, 249);
    static final Color CARD = new Color(248, 250, 252);
    static final Color BORDER = new Color(203, 213, 225);
    static final Color MUTED_TEXT = new Color(71, 85, 105);
    static final Color OCCUPIED = new Color(239, 68, 68);
    static final Color AVAILABLE = new Color(34, 197, 94);
    /** Held by a reservation on the viewed date, but nobody has arrived. */
    static final Color RESERVED = new Color(245, 158, 11);
    static final Color DARK_TEXT = new Color(15, 23, 42);

    static final Color ACTION_CHECK_IN = new Color(37, 99, 235);
    static final Color ACTION_EXTEND = new Color(14, 116, 144);
    static final Color ACTION_UPGRADE = new Color(147, 51, 234);
    static final Color ACTION_CHECK_OUT = new Color(220, 38, 38);
    static final Color ACTION_RESERVE = new Color(180, 83, 9);
    static final Color ACTION_CANCEL = new Color(100, 116, 139);

    static final Font TITLE = new Font("SansSerif", Font.BOLD, 28);
    static final Font SUBTITLE = new Font("SansSerif", Font.PLAIN, 14);
    static final Font SECTION = new Font("SansSerif", Font.BOLD, 16);
    static final Font BODY = new Font("SansSerif", Font.PLAIN, 14);
    static final Font LABEL = new Font("SansSerif", Font.BOLD, 12);
    static final Font CAPTION = new Font("SansSerif", Font.PLAIN, 12);
    static final Font STAT = new Font("SansSerif", Font.BOLD, 18);
    /** Slightly smaller, for currency values that would otherwise be clipped. */
    static final Font STAT_MONEY = new Font("SansSerif", Font.BOLD, 14);
    static final Font ROOM_BUTTON = new Font("SansSerif", Font.BOLD, 12);

    private UiTheme() {
    }

    /** Rupiah amounts are whole numbers; the default currency format adds ",00". */
    static NumberFormat rupiahFormat() {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"));
        format.setMaximumFractionDigits(0);
        return format;
    }

    /** Grouped digits with no currency symbol, for dashboard cards. */
    static NumberFormat plainAmountFormat() {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID"));
        format.setMaximumFractionDigits(0);
        return format;
    }

    static Border panelBorder(int padding) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(padding, padding, padding, padding)
        );
    }

    static JPanel labeledControl(String labelText, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(LABEL);

        panel.add(label, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    static JPanel compactFieldRow(String labelText, JComponent component) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setPreferredSize(new Dimension(140, 30));

        row.add(label, BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        return row;
    }

    static JPanel statCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(MUTED_TEXT);
        titleLabel.setFont(CAPTION);

        valueLabel.setFont(STAT);
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    static JButton actionButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        styleFlat(button, color, Color.WHITE);
        return button;
    }

    /**
     * A solid button in a colour we choose.
     * <p>
     * This used to install {@code BasicButtonUI} by hand, because the Windows and GTK
     * look-and-feels paint buttons with the native theme engine and ignore
     * {@code setBackground}. FlatLaf paints its own buttons on every platform and takes
     * the colours, so the hand-rolled hover and press states went with it.
     */
    static void styleFlat(AbstractButton button, Color background, Color foreground) {
        button.setFocusPainted(false);
        button.setForeground(foreground);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFlatBackground(button, background);
    }

    /** Repaints a flat button in a new colour, keeping hover, press and border in step. */
    static void setFlatBackground(AbstractButton button, Color background) {
        Map<String, Object> style = new LinkedHashMap<>();
        style.put("background", background);
        style.put("hoverBackground", blend(background, Color.WHITE, 0.18));
        style.put("pressedBackground", blend(background, Color.BLACK, 0.18));
        style.put("borderColor", blend(background, Color.BLACK, 0.18));
        style.put("focusedBorderColor", blend(background, Color.BLACK, 0.18));
        button.putClientProperty(FlatClientProperties.STYLE, style);
    }

    private static Color blend(Color from, Color to, double ratio) {
        return new Color(
                (int) Math.round(from.getRed() * (1 - ratio) + to.getRed() * ratio),
                (int) Math.round(from.getGreen() * (1 - ratio) + to.getGreen() * ratio),
                (int) Math.round(from.getBlue() * (1 - ratio) + to.getBlue() * ratio)
        );
    }
}
