package hotel.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("serial") // Swing components are never actually serialised here.
final class DateField extends JPanel {
    /** What a given date looks like for whatever the calendar is describing. */
    enum DayStatus {
        FREE,
        /** Held by a reservation; nobody has arrived. */
        RESERVED,
        /** A guest is in the room. */
        OCCUPIED
    }

    @FunctionalInterface
    interface DayStatusProvider {
        DayStatus statusOf(LocalDate date);
    }

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");
    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final JButton display = new JButton();
    private final List<Runnable> listeners = new ArrayList<>();

    private LocalDate value;
    private LocalDate minDate;
    private LocalDate maxDate;
    private String legendText = "Amber = reserved, Red = occupied";
    private DayStatusProvider statusProvider = date -> DayStatus.FREE;
    private YearMonth shownMonth;

    DateField(LocalDate initial, LocalDate minDate, LocalDate maxDate) {
        super(new BorderLayout());
        this.value = initial;
        this.minDate = minDate;
        this.maxDate = maxDate;
        this.shownMonth = YearMonth.from(initial);

        setOpaque(false);
        display.setHorizontalAlignment(SwingConstants.LEFT);
        display.setFont(UiTheme.BODY);
        display.setFocusPainted(false);
        display.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                new EmptyBorder(4, 8, 4, 8)
        ));
        display.addActionListener(e -> openCalendar());
        add(display, BorderLayout.CENTER);
        refreshDisplay();
    }

    LocalDate getValue() {
        return value;
    }

    void setValue(LocalDate date) {
        if (date == null || date.equals(value)) {
            return;
        }
        this.value = date;
        this.shownMonth = YearMonth.from(date);
        refreshDisplay();
        listeners.forEach(Runnable::run);
    }

    /** Called whenever the user picks a different date. */
    void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    /** Supplies the tint for each day cell. Set this before the calendar is opened. */
    void setDayStatusProvider(DayStatusProvider provider) {
        this.statusProvider = (provider == null) ? date -> DayStatus.FREE : provider;
    }

    /** Caption under the month grid. Should describe whatever tints are in use. */
    void setLegendText(String text) {
        this.legendText = (text == null) ? "" : text;
    }

    void setBounds(LocalDate min, LocalDate max) {
        this.minDate = min;
        this.maxDate = max;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        display.setEnabled(enabled);
    }

    private void refreshDisplay() {
        display.setText(value.format(DISPLAY));
    }

    // ---------------------------------------------------------------- calendar

    private void openCalendar() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        popup.add(buildCalendarPanel(popup));
        popup.show(display, 0, display.getHeight());
    }

    private JPanel buildCalendarPanel(JPopupMenu popup) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.setPreferredSize(new Dimension(300, 268));

        JLabel title = new JLabel(shownMonth.format(MONTH_TITLE), SwingConstants.CENTER);
        title.setFont(UiTheme.SECTION);

        JButton previous = navButton("<");
        JButton next = navButton(">");

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(previous, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);

        JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
        grid.setOpaque(false);
        fillGrid(grid, popup);

        previous.addActionListener(e -> {
            shownMonth = shownMonth.minusMonths(1);
            title.setText(shownMonth.format(MONTH_TITLE));
            grid.removeAll();
            fillGrid(grid, popup);
            grid.revalidate();
            grid.repaint();
        });
        next.addActionListener(e -> {
            shownMonth = shownMonth.plusMonths(1);
            title.setText(shownMonth.format(MONTH_TITLE));
            grid.removeAll();
            fillGrid(grid, popup);
            grid.revalidate();
            grid.repaint();
        });

        JLabel legend = new JLabel(legendText, SwingConstants.CENTER);
        legend.setFont(UiTheme.CAPTION);
        legend.setForeground(UiTheme.MUTED_TEXT);

        panel.add(header, BorderLayout.NORTH);
        panel.add(grid, BorderLayout.CENTER);
        panel.add(legend, BorderLayout.SOUTH);
        return panel;
    }

    private JButton navButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(44, 26));
        return button;
    }

    private void fillGrid(JPanel grid, JPopupMenu popup) {
        for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
            JLabel label = new JLabel(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).substring(0, 2),
                    SwingConstants.CENTER);
            label.setFont(UiTheme.CAPTION);
            label.setForeground(UiTheme.MUTED_TEXT);
            grid.add(label);
        }

        // Monday-first grid, so pad by however far the 1st sits from Monday.
        LocalDate first = shownMonth.atDay(1);
        int padding = first.getDayOfWeek().getValue() - 1;
        for (int i = 0; i < padding; i++) {
            grid.add(Box.createGlue());
        }

        for (int day = 1; day <= shownMonth.lengthOfMonth(); day++) {
            grid.add(buildDayCell(shownMonth.atDay(day), popup));
        }
    }

    private JButton buildDayCell(LocalDate date, JPopupMenu popup) {
        JButton cell = new JButton(String.valueOf(date.getDayOfMonth()));
        cell.setFont(UiTheme.CAPTION);
        cell.setMargin(new java.awt.Insets(2, 2, 2, 2));

        boolean selectable = !date.isBefore(minDate) && !date.isAfter(maxDate);
        DayStatus status = statusProvider.statusOf(date);

        Color background = switch (status) {
            case OCCUPIED -> UiTheme.OCCUPIED;
            case RESERVED -> UiTheme.RESERVED;
            case FREE -> Color.WHITE;
        };
        UiTheme.styleFlat(cell, background,
                status == DayStatus.OCCUPIED ? Color.WHITE : UiTheme.DARK_TEXT);

        if (date.equals(value)) {
            cell.setBorder(BorderFactory.createLineBorder(UiTheme.ACTION_CHECK_IN, 2));
        }
        if (!selectable) {
            cell.setEnabled(false);
        }

        cell.addActionListener(e -> {
            setValue(date);
            popup.setVisible(false);
        });
        return cell;
    }
}