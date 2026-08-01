package hotel.ui;

import hotel.model.Booking;
import hotel.model.Room;
import hotel.model.StaySegment;
import hotel.service.HotelManager;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("serial") // Swing components are never actually serialised here.
public final class MainFrame extends JFrame {
    private static final int MAX_EXTRA_NIGHTS = 14;

    private final HotelManager hotelManager = new HotelManager();
    private final Map<Room, JButton> roomButtons = new LinkedHashMap<>();
    private final NumberFormat rupiah = UiTheme.rupiahFormat();

    private final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final JPanel gridPanel = new JPanel(new GridLayout(0, 4, 12, 12));
    private final JTextArea detailsArea = new JTextArea();
    private final JLabel selectedRoomLabel = new JLabel("Selected Room: -");

    private final JLabel availableLabel = new JLabel();
    private final JLabel occupiedLabel = new JLabel();
    private final JLabel occupancyLabel = new JLabel();
    private final JLabel projectedRevenueLabel = new JLabel();
    private final JLabel realizedRevenueLabel = new JLabel();
    private final JLabel checkedOutLabel = new JLabel();

    private final JComboBox<String> floorFilterBox;
    private final JRadioButton allRoomsRadio = new JRadioButton("All", true);
    private final JRadioButton readyRoomsRadio = new JRadioButton("Ready Only");
    private final JRadioButton occupiedRoomsRadio = new JRadioButton("Occupied Only");
    private final JSpinner quickNightsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 30, 1));
    private final JSpinner quickGuestsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, maxRoomCapacity(), 1));

    private final DefaultTableModel bookingTableModel;
    private final JTable bookingTable;

    /** Floor numbers parallel to the combo box entries; index 0 ("All Floors") is null. */
    private final List<Integer> floorOptions;

    private Room selectedRoom;

    public MainFrame() {
        floorOptions = buildFloorOptions();
        floorFilterBox = new JComboBox<>(buildFloorLabels());

        bookingTableModel = new DefaultTableModel(
                new String[]{"Booking ID", "Guest", "Room", "Tier", "Guests", "Nights", "Bill", "Status"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        bookingTable = new JTable(bookingTableModel);
        bookingTable.setRowHeight(26);

        setTitle("Hotel Management Software");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1360, 820);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.setBackground(UiTheme.BACKGROUND);
        setContentPane(root);

        gridPanel.setOpaque(false);

        splitPane.setBorder(null);
        splitPane.setResizeWeight(0.62);
        splitPane.setLeftComponent(buildLeftPane());
        splitPane.setRightComponent(buildRightPanel());
        splitPane.setDividerLocation(760);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(splitPane, BorderLayout.CENTER);

        registerEvents();
        rebuildGrid();
        refreshDashboard();
    }

    // ----------------------------------------------------------------- layout

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleWrap = new JPanel(new GridLayout(2, 1));
        titleWrap.setOpaque(false);

        JLabel title = new JLabel("Hotel Management Software", SwingConstants.CENTER);
        title.setFont(UiTheme.TITLE);

        JLabel subtitle = new JLabel("Made by Kevin Heryanto (K24-github)", SwingConstants.CENTER);
        subtitle.setForeground(UiTheme.MUTED_TEXT);
        subtitle.setFont(UiTheme.SUBTITLE);

        titleWrap.add(title);
        titleWrap.add(subtitle);
        header.add(titleWrap, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildLeftPane() {
        JPanel leftPane = new JPanel(new BorderLayout(12, 12));
        leftPane.setOpaque(false);

        JPanel filterPanel = new JPanel(new GridLayout(3, 1, 8, 8));
        filterPanel.setBackground(Color.WHITE);
        filterPanel.setBorder(UiTheme.panelBorder(12));

        JPanel row1 = new JPanel(new GridLayout(1, 2, 10, 10));
        row1.setOpaque(false);
        row1.add(UiTheme.labeledControl("Floor Filter", floorFilterBox));

        JPanel quickPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        quickPanel.setOpaque(false);
        quickPanel.add(UiTheme.labeledControl("Quick Nights", quickNightsSpinner));
        quickPanel.add(UiTheme.labeledControl("Quick Guests", quickGuestsSpinner));
        row1.add(quickPanel);

        ButtonGroup group = new ButtonGroup();
        group.add(allRoomsRadio);
        group.add(readyRoomsRadio);
        group.add(occupiedRoomsRadio);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row2.setOpaque(false);
        row2.add(new JLabel("Status View:"));
        row2.add(allRoomsRadio);
        row2.add(readyRoomsRadio);
        row2.add(occupiedRoomsRadio);

        JLabel legend = new JLabel("Green = available / ready, Red = occupied. Click any room box to inspect the details.");
        legend.setForeground(UiTheme.MUTED_TEXT);

        filterPanel.add(row1);
        filterPanel.add(row2);
        filterPanel.add(legend);

        JScrollPane gridScroll = new JScrollPane(gridPanel);
        gridScroll.setBorder(BorderFactory.createTitledBorder("Room Status Grid"));
        gridScroll.getVerticalScrollBar().setUnitIncrement(16);

        leftPane.add(filterPanel, BorderLayout.NORTH);
        leftPane.add(gridScroll, BorderLayout.CENTER);
        return leftPane;
    }

    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(Color.WHITE);
        panel.setBorder(UiTheme.panelBorder(16));

        JPanel top = new JPanel(new GridLayout(2, 3, 10, 10));
        top.setOpaque(false);
        top.add(UiTheme.statCard("Available Rooms", availableLabel));
        top.add(UiTheme.statCard("Occupied Rooms", occupiedLabel));
        top.add(UiTheme.statCard("Occupancy", occupancyLabel));
        top.add(UiTheme.statCard("In-House (projected)", projectedRevenueLabel));
        top.add(UiTheme.statCard("Collected (checked out)", realizedRevenueLabel));
        top.add(UiTheme.statCard("Completed Check-outs", checkedOutLabel));

        JPanel center = new JPanel(new GridLayout(2, 1, 12, 12));
        center.setOpaque(false);

        JPanel detailsPanel = new JPanel(new BorderLayout(8, 8));
        detailsPanel.setOpaque(false);

        selectedRoomLabel.setFont(UiTheme.SECTION);

        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setFont(UiTheme.BODY);
        detailsArea.setText("Click a room to view guest information, rate, and current bill details.");

        detailsPanel.add(selectedRoomLabel, BorderLayout.NORTH);
        detailsPanel.add(new JScrollPane(detailsArea), BorderLayout.CENTER);

        JPanel historyPanel = new JPanel(new BorderLayout(8, 8));
        historyPanel.setOpaque(false);

        JLabel historyLabel = new JLabel("Booking History Table");
        historyLabel.setFont(UiTheme.SECTION);

        historyPanel.add(historyLabel, BorderLayout.NORTH);
        historyPanel.add(new JScrollPane(bookingTable), BorderLayout.CENTER);

        center.add(detailsPanel);
        center.add(historyPanel);

        JPanel actions = new JPanel(new GridLayout(4, 1, 10, 10));
        actions.setOpaque(false);
        actions.add(buildActionButton("Check In / Book Room", UiTheme.ACTION_CHECK_IN, this::handleCheckIn));
        actions.add(buildActionButton("Extend Stay", UiTheme.ACTION_EXTEND, this::handleExtendStay));
        actions.add(buildActionButton("Upgrade Room", UiTheme.ACTION_UPGRADE, this::handleUpgrade));
        actions.add(buildActionButton("One-Click Check Out", UiTheme.ACTION_CHECK_OUT, this::handleCheckOut));

        panel.add(top, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JButton buildActionButton(String text, Color color, Runnable action) {
        JButton button = UiTheme.actionButton(text, color);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void registerEvents() {
        floorFilterBox.addActionListener(e -> rebuildGrid());
        allRoomsRadio.addActionListener(e -> rebuildGrid());
        readyRoomsRadio.addActionListener(e -> rebuildGrid());
        occupiedRoomsRadio.addActionListener(e -> rebuildGrid());
    }

    // ------------------------------------------------------------ room grid

    private void rebuildGrid() {
        gridPanel.removeAll();
        roomButtons.clear();

        for (Room room : getFilteredRooms()) {
            JButton button = new JButton(buildRoomLabel(room));
            button.setPreferredSize(new Dimension(145, 90));
            button.setFont(UiTheme.ROOM_BUTTON);
            UiTheme.styleFlat(button, UiTheme.AVAILABLE, UiTheme.DARK_TEXT);
            button.setToolTipText(room + " - up to " + room.getCapacity()
                    + " guest(s) - " + rupiah.format(room.getNightlyRate()) + " / night");
            button.addActionListener(e -> selectRoom(room));
            roomButtons.put(room, button);
            gridPanel.add(button);
        }

        updateRoomColors();
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private List<Room> getFilteredRooms() {
        Integer floor = floorOptions.get(floorFilterBox.getSelectedIndex());
        List<Room> rooms = (floor == null) ? hotelManager.getRooms() : hotelManager.getRoomsByFloor(floor);

        return rooms.stream()
                .filter(room -> allRoomsRadio.isSelected()
                        || (readyRoomsRadio.isSelected() && !room.isOccupied())
                        || (occupiedRoomsRadio.isSelected() && room.isOccupied()))
                .toList();
    }

    private String buildRoomLabel(Room room) {
        String state = room.isOccupied() ? "Occupied" : "Ready";
        return "<html><center>" + room.getRoomNumber() + "<br>" + room.getTierName() + "<br>" + state + "</center></html>";
    }

    private void updateRoomColors() {
        for (Map.Entry<Room, JButton> entry : roomButtons.entrySet()) {
            Room room = entry.getKey();
            JButton button = entry.getValue();
            button.setText(buildRoomLabel(room));

            if (room.isOccupied()) {
                UiTheme.setFlatBackground(button, UiTheme.OCCUPIED);
                button.setForeground(Color.WHITE);
            } else {
                UiTheme.setFlatBackground(button, UiTheme.AVAILABLE);
                button.setForeground(UiTheme.DARK_TEXT);
            }
        }
    }

    private void selectRoom(Room room) {
        selectedRoom = room;
        selectedRoomLabel.setText("Selected Room: " + room.getRoomNumber() + " - " + room.getTierName());

        int currentGuests = Math.min((int) quickGuestsSpinner.getValue(), room.getCapacity());
        quickGuestsSpinner.setModel(new SpinnerNumberModel(currentGuests, 1, room.getCapacity(), 1));

        detailsArea.setText(room.isOccupied() ? describeOccupied(room) : describeAvailable(room));
        detailsArea.setCaretPosition(0);
    }

    private String describeOccupied(Room room) {
        Booking booking = room.getActiveBooking();
        StringBuilder text = new StringBuilder()
                .append("Room status: Occupied\n")
                .append("Guest: ").append(booking.getGuest().getFullName()).append('\n')
                .append("Phone: ").append(booking.getGuest().getPhoneNumber()).append('\n')
                .append("Notes: ").append(booking.getGuest().getNotes()).append('\n')
                .append("Guests staying: ").append(booking.getGuestCount()).append('\n')
                .append("Nights booked: ").append(booking.getNights())
                .append(" (").append(booking.getNightsStayed()).append(" stayed so far)\n")
                .append("Checked in: ").append(booking.getArrivalDate())
                .append("  |  Departs: ").append(booking.getDepartureDate()).append('\n')
                .append("Current bill: ").append(rupiah.format(booking.getCurrentBill())).append('\n')
                .append("Booking ID: ").append(booking.getBookingId()).append('\n')
                .append("Floor: ").append(room.getFloorNumber());

        if (booking.hasMultipleSegments()) {
            text.append("\n\nBill breakdown:");
            for (StaySegment segment : booking.getSegments()) {
                text.append("\n  ").append(segment.getNights()).append(" x ")
                        .append(segment.getRoom().getTierName()).append(' ')
                        .append(segment.getRoom().getRoomNumber()).append(" = ")
                        .append(rupiah.format(segment.getSubtotal()));
            }
        }
        return text.toString();
    }

    private String describeAvailable(Room room) {
        return "Room status: Ready / available\n"
                + "Tier: " + room.getTierName() + '\n'
                + "Nightly rate: " + rupiah.format(room.getNightlyRate()) + '\n'
                + "Capacity: " + room.getCapacity() + " guest(s)\n"
                + "Floor: " + room.getFloorNumber() + '\n'
                + "Use the quick controls above to set expected nights and guests before checking in.";
    }

    // -------------------------------------------------------------- actions

    private void handleCheckIn() {
        if (selectedRoom == null) {
            showMessage("Please click an available room first.");
            return;
        }
        if (selectedRoom.isOccupied()) {
            showMessage("The selected room is already occupied.");
            return;
        }

        Optional<CheckInDialog.Result> result = CheckInDialog.show(
                this,
                selectedRoom,
                (int) quickNightsSpinner.getValue(),
                (int) quickGuestsSpinner.getValue()
        );
        if (result.isEmpty()) {
            return;
        }

        CheckInDialog.Result form = result.get();
        run(() -> {
            Booking booking = hotelManager.createBooking(
                    selectedRoom,
                    form.guestName(),
                    form.phone(),
                    form.notes(),
                    form.nights(),
                    form.guestCount()
            );
            refreshAfterChange(booking.getRoom());
            showMessage("Booking " + booking.getBookingId() + " created. Bill so far: "
                    + rupiah.format(booking.getCurrentBill()));
        });
    }

    private void handleExtendStay() {
        if (selectedRoom == null || !selectedRoom.isOccupied()) {
            showMessage("Please select an occupied room to extend.");
            return;
        }

        JSpinner extraNightsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, MAX_EXTRA_NIGHTS, 1));
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.add(new JLabel("Add extra nights:"));
        panel.add(extraNightsSpinner);

        if (JOptionPane.showConfirmDialog(this, panel, "Extend Stay", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }

        Room room = selectedRoom;
        run(() -> {
            Booking booking = hotelManager.extendStay(room, (int) extraNightsSpinner.getValue());
            refreshAfterChange(room);
            showMessage("Stay extended to " + booking.getNights() + " night(s). Updated bill: "
                    + rupiah.format(booking.getCurrentBill()));
        });
    }

    private void handleUpgrade() {
        if (selectedRoom == null || !selectedRoom.isOccupied()) {
            showMessage("Please select an occupied room to upgrade.");
            return;
        }

        List<Room> options = hotelManager.getAvailableUpgradeOptions(selectedRoom);
        if (options.isEmpty()) {
            showMessage("No higher-tier rooms are available for upgrade right now.");
            return;
        }

        JComboBox<Room> optionBox = new JComboBox<>(options.toArray(new Room[0]));
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.add(new JLabel("Choose a new room:"));
        panel.add(optionBox);
        panel.add(new JLabel("Nights already stayed keep the current room's rate."));

        if (JOptionPane.showConfirmDialog(this, panel, "Upgrade Booking", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }

        Room currentRoom = selectedRoom;
        Room newRoom = (Room) optionBox.getSelectedItem();
        run(() -> {
            Booking booking = hotelManager.upgradeBooking(currentRoom, newRoom);
            refreshAfterChange(booking.getRoom());
            showMessage("Moved to " + booking.getRoom() + ". Updated bill: "
                    + rupiah.format(booking.getCurrentBill()));
        });
    }

    private void handleCheckOut() {
        if (selectedRoom == null || !selectedRoom.isOccupied()) {
            showMessage("Please select an occupied room to check out.");
            return;
        }

        Room room = selectedRoom;
        Booking booking = room.getActiveBooking();
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Check out " + booking.getGuest().getFullName() + " from room " + room.getRoomNumber()
                        + "?\nAmount due: " + rupiah.format(booking.getCurrentBill()),
                "Confirm Check Out",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        run(() -> {
            double finalBill = hotelManager.checkOut(room);
            refreshAfterChange(room);
            showMessage("Check-out complete. Final bill: " + rupiah.format(finalBill));
        });
    }

    /** Runs a service call and reports any rule violation as a dialog instead of a stack trace. */
    private void run(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            showError(ex.getMessage());
        }
    }

    // ------------------------------------------------------------- refresh

    private void refreshAfterChange(Room roomToSelect) {
        rebuildGrid();
        refreshDashboard();
        if (roomToSelect != null) {
            selectRoom(roomToSelect);
        }
    }

    private void refreshDashboard() {
        updateRoomColors();
        availableLabel.setText(String.valueOf(hotelManager.getAvailableCount()));
        occupiedLabel.setText(String.valueOf(hotelManager.getOccupiedCount()));
        occupancyLabel.setText(String.format("%.0f%%", hotelManager.getOccupancyRate()));
        projectedRevenueLabel.setText(rupiah.format(hotelManager.getProjectedRevenue()));
        realizedRevenueLabel.setText(rupiah.format(hotelManager.getRealizedRevenue()));
        checkedOutLabel.setText(String.valueOf(hotelManager.getCheckedOutCount()));
        refreshBookingTable();
    }

    private void refreshBookingTable() {
        bookingTableModel.setRowCount(0);
        for (Booking booking : hotelManager.getBookings()) {
            bookingTableModel.addRow(new Object[]{
                    booking.getBookingId(),
                    booking.getGuest().getFullName(),
                    booking.getRoom().getRoomNumber(),
                    booking.getRoom().getTierName(),
                    booking.getGuestCount(),
                    booking.getNights(),
                    rupiah.format(booking.getCurrentBill()),
                    booking.getStatus().getLabel()
            });
        }
    }

    // --------------------------------------------------------------- helpers

    private List<Integer> buildFloorOptions() {
        // A leading null marks the "All Floors" entry, so List.copyOf is not usable here.
        List<Integer> options = new ArrayList<>();
        options.add(null);
        options.addAll(hotelManager.getFloors());
        return Collections.unmodifiableList(options);
    }

    private String[] buildFloorLabels() {
        return floorOptions.stream()
                .map(floor -> floor == null ? "All Floors" : "Floor " + floor)
                .toArray(String[]::new);
    }

    private int maxRoomCapacity() {
        return hotelManager.getRooms().stream().mapToInt(Room::getCapacity).max().orElse(1);
    }

    private void showMessage(String text) {
        JOptionPane.showMessageDialog(this, text);
    }

    private void showError(String text) {
        JOptionPane.showMessageDialog(this, text, "Error", JOptionPane.ERROR_MESSAGE);
    }
}