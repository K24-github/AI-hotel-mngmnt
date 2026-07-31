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
import java.util.Optional;

final class CheckInDialog {
    /** What the desk clerk typed. Validation is the model's job, not the form's. */
    record Result(String guestName, String phone, String notes, int nights, int guestCount) {
    }

    private static final String[] PAYMENT_TYPES = {"Walk-in", "Transfer", "Card"};
    private static final int MAX_NIGHTS = 30;

    private CheckInDialog() {
    }

    static Optional<Result> show(Component parent, Room room, int defaultNights, int defaultGuests) {
        JTextField guestNameField = new JTextField();
        JTextField phoneField = new JTextField();

        JTextArea notesArea = new JTextArea(3, 20);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

        int nights = clamp(defaultNights, 1, MAX_NIGHTS);
        int guests = clamp(defaultGuests, 1, room.getCapacity());

        JSpinner nightsSpinner = new JSpinner(new SpinnerNumberModel(nights, 1, MAX_NIGHTS, 1));
        JSpinner guestsSpinner = new JSpinner(new SpinnerNumberModel(guests, 1, room.getCapacity(), 1));

        Dimension spinnerSize = new Dimension(120, 30);
        sizeTo(nightsSpinner, spinnerSize);
        sizeTo(guestsSpinner, spinnerSize);

        Dimension fieldSize = new Dimension(Integer.MAX_VALUE, 30);
        guestNameField.setMaximumSize(fieldSize);
        phoneField.setMaximumSize(fieldSize);

        JComboBox<String> paymentTypeBox = new JComboBox<>(PAYMENT_TYPES);
        paymentTypeBox.setMaximumSize(fieldSize);

        JCheckBox breakfastCheck = new JCheckBox("Breakfast requested");

        JPanel form = new JPanel();
        form.setBorder(new EmptyBorder(10, 10, 10, 10));
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        form.add(new JLabel("Guest name:"));
        form.add(guestNameField);
        form.add(Box.createVerticalStrut(8));

        form.add(new JLabel("Phone number:"));
        form.add(phoneField);
        form.add(Box.createVerticalStrut(8));

        form.add(new JLabel("Special notes:"));
        form.add(new JScrollPane(notesArea));
        form.add(Box.createVerticalStrut(8));

        form.add(UiTheme.compactFieldRow("Number of nights:", nightsSpinner));
        form.add(Box.createVerticalStrut(8));

        form.add(UiTheme.compactFieldRow("Number of guests:", guestsSpinner));
        form.add(Box.createVerticalStrut(8));

        form.add(new JLabel("Payment note:"));
        form.add(paymentTypeBox);
        form.add(Box.createVerticalStrut(8));

        form.add(breakfastCheck);

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setPreferredSize(new Dimension(420, 420));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        int choice = JOptionPane.showConfirmDialog(
                parent,
                scrollPane,
                "Book " + room.getRoomNumber() + " - " + room.getTierName(),
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

    private static void sizeTo(JComponent component, Dimension size) {
        component.setPreferredSize(size);
        component.setMaximumSize(size);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
