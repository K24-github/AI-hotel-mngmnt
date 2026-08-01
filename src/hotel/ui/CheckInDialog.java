package hotel.ui;

import hotel.model.Room;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Dimension;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The check-in form, also used to hold a room for future dates.
 * The only difference between the two is whether the arrival date can be moved,
 * so one form covers both rather than duplicating six fields.
 */
final class CheckInDialog {
    /** Which kind of booking the form is collecting. */
    enum Mode {
        /** Guest is at the desk now; arrival is today and cannot be changed. */
        WALK_IN("Check in", false),
        /** Booking for a future date; arrival is editable. */
        RESERVATION("Reserve", true);

        private final String verb;
        private final boolean arrivalEditable;

        Mode(String verb, boolean arrivalEditable) {
            this.verb = verb;
            this.arrivalEditable = arrivalEditable;
        }
    }

    /** What the desk clerk typed. Validation is the model's job, not the form's. */
    record Result(String guestName, String phone, String notes,
                  LocalDate arrivalDate, int nights, int guestCount) {
    }

    private static final String[] PAYMENT_TYPES = {"Walk-in", "Transfer", "Card"};
    private static final int MAX_NIGHTS = 30;
    private static final int MAX_MONTHS_AHEAD = 12;

    private CheckInDialog() {
    }

    static Optional<Result> show(Component parent, Room room, Mode mode,
                                 LocalDate defaultArrival, int defaultNights, int defaultGuests,
                                 DateField.DayStatusProvider arrivalStatus) {
        JTextField guestNameField = new JTextField();
        JTextField phoneField = new JTextField();

        JTextArea notesArea = new JTextArea(3, 20);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

        int nights = clamp(defaultNights, 1, MAX_NIGHTS);
        int guests = clamp(defaultGuests, 1, room.getCapacity());

        JSpinner nightsSpinner = new JSpinner(new SpinnerNumberModel(nights, 1, MAX_NIGHTS, 1));
        JSpinner guestsSpinner = new JSpinner(new SpinnerNumberModel(guests, 1, room.getCapacity(), 1));
        LocalDate arrival = (defaultArrival == null) ? LocalDate.now() : defaultArrival;
        DateField arrivalField = new DateField(arrival, LocalDate.now(),
                LocalDate.now().plusMonths(MAX_MONTHS_AHEAD));
        arrivalField.setDayStatusProvider(arrivalStatus);
        arrivalField.setLegendText("Amber = reserved, Red = guest in the room");
        arrivalField.setEnabled(mode.arrivalEditable);

        Dimension spinnerSize = new Dimension(120, 30);
        sizeTo(nightsSpinner, spinnerSize);
        sizeTo(guestsSpinner, spinnerSize);


        Dimension fieldSize = new Dimension(Integer.MAX_VALUE, 30);
        guestNameField.setMaximumSize(fieldSize);
        phoneField.setMaximumSize(fieldSize);

        JComboBox<String> paymentTypeBox = new JComboBox<>(PAYMENT_TYPES);
        paymentTypeBox.setMaximumSize(fieldSize);
        if (mode == Mode.RESERVATION) {
            paymentTypeBox.setSelectedItem("Transfer");
        }

        JCheckBox breakfastCheck = new JCheckBox("Breakfast requested");

        JPanel form = new JPanel();
        form.setBorder(new EmptyBorder(12, 12, 12, 12));
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        addRow(form, new JLabel("Guest name:"));
        addRow(form, guestNameField);
        addGap(form);

        addRow(form, new JLabel("Phone number:"));
        addRow(form, phoneField);
        addGap(form);

        addRow(form, new JLabel("Special notes:"));
        addRow(form, new JScrollPane(notesArea));
        addGap(form);

        addRow(form, UiTheme.compactFieldRow("Arrival date:", arrivalField));
        addGap(form);

        addRow(form, UiTheme.compactFieldRow("Number of nights:", nightsSpinner));
        addGap(form);

        addRow(form, UiTheme.compactFieldRow("Number of guests:", guestsSpinner));
        addGap(form);

        addRow(form, new JLabel("Payment note:"));
        addRow(form, paymentTypeBox);
        addGap(form);

        addRow(form, breakfastCheck);

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setPreferredSize(new Dimension(420, 460));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        int choice = JOptionPane.showConfirmDialog(
                parent,
                scrollPane,
                mode.verb + " - " + room.getRoomNumber() + " " + room.getTierName(),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (choice != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }

        return Optional.of(new Result(
                guestNameField.getText(),
                phoneField.getText(),
                buildNotes(notesArea.getText(), breakfastCheck.isSelected(), String.valueOf(paymentTypeBox.getSelectedItem())),
                arrivalField.getValue(),
                (int) nightsSpinner.getValue(),
                (int) guestsSpinner.getValue()
        ));
    }

    private static String buildNotes(String freeText, boolean breakfast, String paymentType) {
        StringBuilder notes = new StringBuilder();
        if (freeText != null && !freeText.isBlank()) {
            notes.append(freeText.trim());
        }
        if (breakfast) {
            appendPart(notes, "Breakfast requested");
        }
        appendPart(notes, "Payment: " + paymentType);
        return notes.toString();
    }

    private static void appendPart(StringBuilder notes, String part) {
        if (notes.length() > 0) {
            notes.append(" | ");
        }
        notes.append(part);
    }

    /**
     * Adds a row flush to the left edge. BoxLayout centres anything narrower than
     * the panel unless told otherwise, which is what left labels floating mid-form.
     */
    private static void addRow(JPanel form, JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(component);
    }

    private static void addGap(JPanel form) {
        form.add(Box.createVerticalStrut(8));
    }

    private static void sizeTo(JComponent component, Dimension size) {
        component.setPreferredSize(size);
        component.setMaximumSize(size);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
