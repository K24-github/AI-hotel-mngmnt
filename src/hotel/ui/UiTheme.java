package hotel.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.Locale;

final class UiTheme {
    static final Color BACKGROUND = new Color(241, 245, 249);
    static final Color CARD = new Color(248, 250, 252);
    static final Color BORDER = new Color(203, 213, 225);
    static final Color MUTED_TEXT = new Color(71, 85, 105);
    static final Color OCCUPIED = new Color(239, 68, 68);
    static final Color AVAILABLE = new Color(34, 197, 94);
    static final Color DARK_TEXT = new Color(15, 23, 42);

    static final Color ACTION_CHECK_IN = new Color(37, 99, 235);
    static final Color ACTION_EXTEND = new Color(14, 116, 144);
    static final Color ACTION_UPGRADE = new Color(147, 51, 234);
    static final Color ACTION_CHECK_OUT = new Color(220, 38, 38);

    static final Font TITLE = new Font("SansSerif", Font.BOLD, 28);
    static final Font SUBTITLE = new Font("SansSerif", Font.PLAIN, 14);
    static final Font SECTION = new Font("SansSerif", Font.BOLD, 16);
    static final Font BODY = new Font("SansSerif", Font.PLAIN, 14);
    static final Font LABEL = new Font("SansSerif", Font.BOLD, 12);
    static final Font CAPTION = new Font("SansSerif", Font.PLAIN, 12);
    static final Font STAT = new Font("SansSerif", Font.BOLD, 18);
    static final Font ROOM_BUTTON = new Font("SansSerif", Font.BOLD, 12);

    private UiTheme() {
    }

    /** Rupiah amounts are whole numbers; the default currency format adds ",00". */
    static NumberFormat rupiahFormat() {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"));
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
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel label = new JLabel(labelText);
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

    static void styleFlat(AbstractButton button, Color background, Color foreground) {
        button.setUI(new BasicButtonUI());   // must run before the colours are applied
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setFocusPainted(false);
        button.setForeground(foreground);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFlatBackground(button, background);
        button.addMouseListener(new FlatHoverListener(button));
    }

    static void setFlatBackground(AbstractButton button, Color background) {
        button.putClientProperty(BASE_COLOR, background);
        button.setBackground(background);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(blend(background, Color.BLACK, 0.18)),
                new EmptyBorder(6, 10, 6, 10)
        ));
    }

    private static final String BASE_COLOR = "hotel.baseColor";

    private static Color baseColorOf(AbstractButton button) {
        Object stored = button.getClientProperty(BASE_COLOR);
        return (stored instanceof Color color) ? color : button.getBackground();
    }

    private static Color blend(Color from, Color to, double ratio) {
        return new Color(
                (int) Math.round(from.getRed() * (1 - ratio) + to.getRed() * ratio),
                (int) Math.round(from.getGreen() * (1 - ratio) + to.getGreen() * ratio),
                (int) Math.round(from.getBlue() * (1 - ratio) + to.getBlue() * ratio)
        );
    }


    private static final class FlatHoverListener extends MouseAdapter {
        private final AbstractButton button;

        private FlatHoverListener(AbstractButton button) {
            this.button = button;
        }

        @Override
        public void mouseEntered(MouseEvent event) {
            if (button.isEnabled()) {
                button.setBackground(blend(baseColorOf(button), Color.WHITE, 0.18));
            }
        }

        @Override
        public void mouseExited(MouseEvent event) {
            button.setBackground(baseColorOf(button));
        }

        @Override
        public void mousePressed(MouseEvent event) {
            if (button.isEnabled()) {
                button.setBackground(blend(baseColorOf(button), Color.BLACK, 0.18));
            }
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            boolean stillInside = button.contains(event.getPoint());
            button.setBackground(stillInside
                    ? blend(baseColorOf(button), Color.WHITE, 0.18)
                    : baseColorOf(button));
        }
    }
}
