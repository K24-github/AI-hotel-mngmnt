package hotel.ui;
import hotel.model.Booking;
import hotel.model.BookingStatus;
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
import javax.swing.JTabbedPane;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("serial") // Swing components are never actually serialised here.
public final class MainFrame extends JFrame {
    private static final int MAX_EXTRA_NIGHTS = 14;
    private static final String DETACH_LABEL = "Open in separate window";
    private static final String CLOSE_LABEL = "Close and return to tabs";
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");

    private final HotelManager hotelManager = new HotelManager();
    private final Map<Room, JButton> roomButtons = new LinkedHashMap<>();
    private final NumberFormat rupiah = UiTheme.rupiahFormat();
    private final NumberFormat plainAmount = UiTheme.plainAmountFormat();

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
    private final JLabel reservedLabel = new JLabel();
    private final JLabel reservedValueLabel = new JLabel();

    private final JComboBox<String> floorFilterBox;
    private final JRadioButton allRoomsRadio = new JRadioButton("All", true);
    private final JRadioButton readyRoomsRadio = new JRadioButton("Ready Only");
    private final JRadioButton occupiedRoomsRadio = new JRadioButton("Occupied Only");
    private final DateField dateField = new DateField(
            LocalDate.now(), LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(12));
    private final JButton todayButton = UiTheme.actionButton("Today", UiTheme.ACTION_CANCEL);
    private final JSpinner quickNightsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 30, 1));
    private final JSpinner quickGuestsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, maxRoomCapacity(), 1));

    private final JTabbedPane infoTabs = new JTabbedPane();
    private final Map<String, JFrame> detachedWindows = new LinkedHashMap<>();
    private final Map<String, JButton> detachButtons = new LinkedHashMap<>();

    private final DefaultTableModel bookingTableModel;
    private final JTable bookingTable;

    /** Floor numbers parallel to the combo box entries; index 0 ("All Floors") is null. */
    private final List<Integer> floorOptions;

    private Room selectedRoom;

    public MainFrame() {
        floorOptions = buildFloorOptions();
        floorFilterBox = new JComboBox<>(buildFloorLabels());

        bookingTableModel = new DefaultTableModel(
                new String[]{"Booking ID", "Guest", "Room", "Tier", "Arrival", "Nights", "Bill", "Status"}, 0
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

        dateField.setDayStatusProvider(this::statusOn);
        dateField.setLegendText("Amber = the hotel has bookings that day");
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

        JPanel row1 = new JPanel(new GridLayout(1, 3, 10, 10));
        row1.setOpaque(false);
        row1.add(UiTheme.labeledControl("Floor Filter", floorFilterBox));

        JPanel datePanel = new JPanel(new BorderLayout(8, 0));
        datePanel.setOpaque(false);
        datePanel.add(dateField, BorderLayout.CENTER);
        todayButton.setPreferredSize(new Dimension(78, 30));
        datePanel.add(todayButton, BorderLayout.EAST);
        row1.add(UiTheme.labeledControl("Date", datePanel));

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

        JLabel legend = new JLabel("Green = free, Amber = reserved, Red = guest in the room.");
        legend.setForeground(UiTheme.MUTED_TEXT);

        filterPanel.add(row1);
        filterPanel.add(row2);
        filterPanel.add(legend);

        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setOpaque(false);
        gridHolder.add(gridPanel, BorderLayout.NORTH);

        JScrollPane gridScroll = new JScrollPane(gridHolder);
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

        JPanel top = new JPanel(new GridLayout(2, 4, 10, 10));
        top.setOpaque(false);
        // Rupiah amounts are long; the default stat size overflows a quarter-width card.
        projectedRevenueLabel.setFont(UiTheme.STAT_MONEY);
        realizedRevenueLabel.setFont(UiTheme.STAT_MONEY);
        reservedValueLabel.setFont(UiTheme.STAT_MONEY);
        top.add(UiTheme.statCard("Free (on date)", availableLabel));
        top.add(UiTheme.statCard("Booked (on date)", occupiedLabel));
        top.add(UiTheme.statCard("Occupancy (on date)", occupancyLabel));
        top.add(UiTheme.statCard("Arrivals (on date)", reservedLabel));
        top.add(UiTheme.statCard("In-House (Rp)", projectedRevenueLabel));
        top.add(UiTheme.statCard("Checked Out (Rp)", realizedRevenueLabel));
        top.add(UiTheme.statCard("Booked Ahead (Rp)", reservedValueLabel));
        top.add(UiTheme.statCard("Check-outs", checkedOutLabel));

        selectedRoomLabel.setFont(UiTheme.SECTION);

        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setFont(UiTheme.BODY);
        detailsArea.setText("Click a room to view guest information, rate, and current bill details.");

        JPanel detailsPanel = new JPanel(new BorderLayout(8, 8));
        detailsPanel.setOpaque(false);
        detailsPanel.add(selectedRoomLabel, BorderLayout.NORTH);
        detailsPanel.add(new JScrollPane(detailsArea), BorderLayout.CENTER);
        detailsPanel.add(buildDetachBar("Room Details", detailsPanel), BorderLayout.SOUTH);

        JPanel historyPanel = new JPanel(new BorderLayout(8, 8));
        historyPanel.setOpaque(false);
        historyPanel.add(new JScrollPane(bookingTable), BorderLayout.CENTER);
        historyPanel.add(buildDetachBar("Booking History", historyPanel), BorderLayout.SOUTH);

        infoTabs.addTab("Room Details", detailsPanel);
        infoTabs.addTab("Booking History", historyPanel);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(infoTabs, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(3, 2, 10, 10));
        actions.setOpaque(false);
        actions.add(buildActionButton("Check In / Book Room", UiTheme.ACTION_CHECK_IN, this::handleCheckIn));
        actions.add(buildActionButton("Reserve Future Dates", UiTheme.ACTION_RESERVE, this::handleReserve));
        actions.add(buildActionButton("Extend Stay", UiTheme.ACTION_EXTEND, this::handleExtendStay));
        actions.add(buildActionButton("Upgrade Room", UiTheme.ACTION_UPGRADE, this::handleUpgrade));
        actions.add(buildActionButton("Cancel Reservation", UiTheme.ACTION_CANCEL, this::handleCancelReservation));
        actions.add(buildActionButton("One-Click Check Out", UiTheme.ACTION_CHECK_OUT, this::handleCheckOut));

        panel.add(top, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * A small "open in its own window" control for a tab. Handy when the desk has a
     * second monitor, or when the booking table needs more room than the tab allows.
     */
    private JPanel buildDetachBar(String title, JPanel content) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bar.setOpaque(false);

        JButton toggle = new JButton(DETACH_LABEL);
        toggle.setFont(UiTheme.CAPTION);
        toggle.setFocusPainted(false);
        // One button, two jobs: it detaches while docked and closes while floating.
        toggle.addActionListener(e -> {
            JFrame window = detachedWindows.get(title);
            if (window == null) {
                detachTab(title, content);
            } else {
                window.dispose();   // fires windowClosed, which re-docks the panel
            }
        });
        detachButtons.put(title, toggle);

        bar.add(toggle);
        return bar;
    }

    private void setDetachButtonLabel(String title, String label) {
        JButton button = detachButtons.get(title);
        if (button != null) {
            button.setText(label);
        }
    }

    /** Moves a tab's panel into its own window; closing that window puts it back. */
    private void detachTab(String title, JPanel content) {
        JFrame existing = detachedWindows.get(title);
        if (existing != null) {
            existing.toFront();
            return;
        }

        int tabIndex = infoTabs.indexOfComponent(content);
        if (tabIndex >= 0) {
            infoTabs.removeTabAt(tabIndex);
        }

        JFrame window = new JFrame(title + " - Hotel Management Software");
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setSize(640, 420);
        window.setLocationRelativeTo(this);
        window.setContentPane(content);
        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                detachedWindows.remove(title);
                reattachTab(title, content);
            }
        });
        detachedWindows.put(title, window);
        setDetachButtonLabel(title, CLOSE_LABEL);
        window.setVisible(true);
        showEmptyTabHint();
    }

    /** Restores a detached panel, keeping Room Details ahead of Booking History. */
    private void reattachTab(String title, JPanel content) {
        setDetachButtonLabel(title, DETACH_LABEL);
        removeEmptyTabHint();
        int insertAt = "Room Details".equals(title) ? 0 : infoTabs.getTabCount();
        infoTabs.insertTab(title, null, content, null, Math.min(insertAt, infoTabs.getTabCount()));
        infoTabs.setSelectedComponent(content);
        revalidate();
        repaint();
    }

    private void showEmptyTabHint() {
        if (infoTabs.getTabCount() == 0) {
            JLabel hint = new JLabel("Both panels are open in separate windows.", SwingConstants.CENTER);
            hint.setForeground(UiTheme.MUTED_TEXT);
            infoTabs.addTab("Panels", hint);
        }
    }

    private void removeEmptyTabHint() {
        int hintIndex = infoTabs.indexOfTab("Panels");
        if (hintIndex >= 0) {
            infoTabs.removeTabAt(hintIndex);
        }
    }

    private JButton buildActionButton(String text, Color color, Runnable action) {
        JButton button = UiTheme.actionButton(text, color);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void registerEvents() {
        dateField.addChangeListener(() -> refreshAfterChange(selectedRoom));
        todayButton.addActionListener(e -> dateField.setValue(LocalDate.now()));
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
                        || (readyRoomsRadio.isSelected() && isFreeOnViewedDate(room))
                        || (occupiedRoomsRadio.isSelected() && !isFreeOnViewedDate(room)))
                .toList();
    }

    private String buildRoomLabel(Room room) {
        return "<html><center>" + room.getRoomNumber() + "<br>" + room.getTierName()
                + "<br>" + describeState(room) + "</center></html>";
    }

    /** What the room is doing on the date currently being viewed. */
    private String describeState(Room room) {
        if (room.isOccupied() && isToday(getViewedDate())) {
            return "Occupied";
        }
        return holderOn(room).isPresent() ? "Reserved" : "Ready";
    }

    /** The booking holding this room on the viewed date, if any. */
    private Optional<Booking> holderOn(Room room) {
        return hotelManager.getBookingOn(room, getViewedDate());
    }

    private boolean isFreeOnViewedDate(Room room) {
        return holderOn(room).isEmpty();
    }

    private void updateRoomColors() {
        for (Map.Entry<Room, JButton> entry : roomButtons.entrySet()) {
            Room room = entry.getKey();
            JButton button = entry.getValue();
            button.setText(buildRoomLabel(room));

            Optional<Booking> holder = holderOn(room);
            if (holder.isEmpty()) {
                UiTheme.setFlatBackground(button, UiTheme.AVAILABLE);
                button.setForeground(UiTheme.DARK_TEXT);
            } else if (holder.get().isActive() && isToday(getViewedDate())) {
                UiTheme.setFlatBackground(button, UiTheme.OCCUPIED);
                button.setForeground(Color.WHITE);
            } else {
                UiTheme.setFlatBackground(button, UiTheme.RESERVED);
                button.setForeground(UiTheme.DARK_TEXT);
            }
        }
    }

    private void selectRoom(Room room) {
        selectedRoom = room;
        selectedRoomLabel.setText("Selected Room: " + room.getRoomNumber() + " - " + room.getTierName());

        int currentGuests = Math.min((int) quickGuestsSpinner.getValue(), room.getCapacity());
        quickGuestsSpinner.setModel(new SpinnerNumberModel(currentGuests, 1, room.getCapacity(), 1));

        Optional<Booking> holder = holderOn(room);
        detailsArea.setText(holder.isPresent() ? describeBooked(room, holder.get()) : describeAvailable(room));
        detailsArea.setCaretPosition(0);
    }

    private String describeBooked(Room room, Booking booking) {
        StringBuilder text = new StringBuilder()
                .append("Room status: ").append(booking.getStatus().getLabel()).append('\n')
                .append("Guest: ").append(booking.getGuest().getFullName()).append('\n')
                .append("Phone: ").append(booking.getGuest().getPhoneNumber()).append('\n')
                .append("Notes: ").append(booking.getGuest().getNotes()).append('\n')
                .append("Guests staying: ").append(booking.getGuestCount()).append('\n')
                .append("Nights booked: ").append(booking.getNights())
                .append(booking.isActive() ? " (" + booking.getNightsStayed() + " stayed so far)\n" : "\n")
                .append(booking.isActive() ? "Checked in: " : "Arrives: ")
                .append(booking.getArrivalDate().format(DATE_LABEL))
                .append("  |  Departs: ").append(booking.getDepartureDate().format(DATE_LABEL)).append('\n')
                .append(booking.isActive() ? "Current bill: " : "Quoted total: ")
                .append(rupiah.format(booking.getCurrentBill())).append('\n')
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
        return "Room status: Free on " + getViewedDate().format(DATE_LABEL) + "\n"
                + "Tier: " + room.getTierName() + '\n'
                + "Nightly rate: " + rupiah.format(room.getNightlyRate()) + '\n'
                + "Capacity: " + room.getCapacity() + " guest(s)\n"
                + "Floor: " + room.getFloorNumber() + '\n'
                + "Use the quick controls above to set expected nights and guests, then check in or reserve.";
    }

    // -------------------------------------------------------------- actions

    /**
     * Two jobs behind one button: if the room is held by a reservation whose guest
     * has arrived, check that reservation in; otherwise take a walk-in.
     */
    private void handleCheckIn() {
        if (selectedRoom == null) {
            showMessage("Please click a room first.");
            return;
        }
        if (selectedRoom.isOccupied()) {
            showMessage("There is already a guest in this room.");
            return;
        }

        Optional<Booking> arriving = hotelManager.getBookingOn(selectedRoom, LocalDate.now())
                .filter(booking -> booking.getStatus() == BookingStatus.RESERVED);
        if (arriving.isPresent()) {
            checkInExistingReservation(arriving.get());
            return;
        }

        Optional<Booking> futureHolder = holderOn(selectedRoom);
        if (futureHolder.isPresent() && !isToday(getViewedDate())) {
            showMessage("That room is reserved on the date you are viewing. Switch to today to take a walk-in.");
            return;
        }

        takeWalkIn();
    }

    private void checkInExistingReservation(Booking booking) {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Check in " + booking.getGuest().getFullName() + " for booking " + booking.getBookingId() + "?\n"
                        + booking.getNights() + " night(s), total " + rupiah.format(booking.getCurrentBill()),
                "Check In Reservation",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        run(() -> {
            hotelManager.checkIn(booking);
            refreshAfterChange(booking.getRoom());
            showMessage("Checked in. Room " + booking.getRoom().getRoomNumber() + " is now occupied.");
        });
    }

    private void takeWalkIn() {
        Optional<CheckInDialog.Result> result = CheckInDialog.show(
                this,
                selectedRoom,
                CheckInDialog.Mode.WALK_IN,
                LocalDate.now(),
                (int) quickNightsSpinner.getValue(),
                (int) quickGuestsSpinner.getValue(),
                date -> statusOn(selectedRoom, date)
        );
        if (result.isEmpty()) {
            return;
        }

        CheckInDialog.Result form = result.get();
        run(() -> {
            Booking booking = hotelManager.createBooking(
                    selectedRoom, form.guestName(), form.phone(), form.notes(),
                    form.nights(), form.guestCount()
            );
            refreshAfterChange(booking.getRoom());
            showMessage("Booking " + booking.getBookingId() + " created. Bill so far: "
                    + rupiah.format(booking.getCurrentBill()));
        });
    }

    /** Books a room for dates ahead without anybody arriving now. */
    private void handleReserve() {
        if (selectedRoom == null) {
            showMessage("Please click a room first.");
            return;
        }

        LocalDate suggested = getViewedDate().isBefore(LocalDate.now()) ? LocalDate.now() : getViewedDate();
        Optional<CheckInDialog.Result> result = CheckInDialog.show(
                this,
                selectedRoom,
                CheckInDialog.Mode.RESERVATION,
                suggested,
                (int) quickNightsSpinner.getValue(),
                (int) quickGuestsSpinner.getValue(),
                date -> statusOn(selectedRoom, date)
        );
        if (result.isEmpty()) {
            return;
        }

        CheckInDialog.Result form = result.get();
        Room room = selectedRoom;
        run(() -> {
            Booking booking = hotelManager.createReservation(
                    room, form.guestName(), form.phone(), form.notes(),
                    form.arrivalDate(), form.nights(), form.guestCount()
            );
            dateField.setValue(booking.getArrivalDate());
            refreshAfterChange(room);
            showMessage("Reservation " + booking.getBookingId() + " held for "
                    + booking.getArrivalDate().format(DATE_LABEL) + " to "
                    + booking.getDepartureDate().format(DATE_LABEL) + ".\nQuoted total: "
                    + rupiah.format(booking.getCurrentBill()));
        });
    }

    /** Releases a reservation whose guest has not arrived. */
    private void handleCancelReservation() {
        if (selectedRoom == null) {
            showMessage("Please click a room first.");
            return;
        }

        Optional<Booking> holder = holderOn(selectedRoom)
                .filter(booking -> booking.getStatus() == BookingStatus.RESERVED);
        if (holder.isEmpty()) {
            showMessage("No reservation on " + getViewedDate().format(DATE_LABEL)
                    + " for this room. A guest who has already checked in must be checked out instead.");
            return;
        }

        Booking booking = holder.get();
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Cancel " + booking.getBookingId() + " for " + booking.getGuest().getFullName() + "?\n"
                        + booking.getArrivalDate().format(DATE_LABEL) + ", "
                        + booking.getNights() + " night(s).\nThe room will be released for those dates.",
                "Cancel Reservation",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        Room room = selectedRoom;
        run(() -> {
            hotelManager.cancelReservation(booking);
            refreshAfterChange(room);
            showMessage("Reservation " + booking.getBookingId() + " cancelled.");
        });
    }

    private void handleExtendStay() {
        Optional<Booking> holder = openBookingOnViewedDate();
        if (holder.isEmpty()) {
            showMessage("Select a room with a guest or a reservation on the chosen date.");
            return;
        }
        Booking booking = holder.get();

        JSpinner extraNightsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, MAX_EXTRA_NIGHTS, 1));
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.add(new JLabel(booking.getBookingId() + " - " + booking.getGuest().getFullName()
                + " (" + booking.getStatus().getLabel().toLowerCase() + ")"));
        panel.add(new JLabel("Currently leaving " + booking.getDepartureDate().format(DATE_LABEL)));
        panel.add(new JLabel("Add extra nights:"));
        panel.add(extraNightsSpinner);

        if (JOptionPane.showConfirmDialog(this, panel, "Extend Stay", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }

        Room room = selectedRoom;
        run(() -> {
            hotelManager.extendStay(booking, (int) extraNightsSpinner.getValue());
            refreshAfterChange(room);
            showMessage("Now " + booking.getNights() + " night(s), leaving "
                    + booking.getDepartureDate().format(DATE_LABEL) + ".\nUpdated total: "
                    + rupiah.format(booking.getCurrentBill()));
        });
    }

    private void handleUpgrade() {
        Optional<Booking> holder = openBookingOnViewedDate();
        if (holder.isEmpty()) {
            showMessage("Select a room with a guest or a reservation on the chosen date.");
            return;
        }
        Booking booking = holder.get();

        List<Room> options = hotelManager.getUpgradeOptionsFor(booking);
        if (options.isEmpty()) {
            showMessage(explainNoUpgrade(booking));
            return;
        }

        JComboBox<Room> optionBox = new JComboBox<>(options.toArray(new Room[0]));
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.add(new JLabel(booking.getBookingId() + " - " + booking.getGuest().getFullName()
                + " (" + booking.getStatus().getLabel().toLowerCase() + ")"));
        panel.add(new JLabel("Choose a new room:"));
        panel.add(optionBox);
        panel.add(new JLabel(booking.isActive()
                ? "Nights already stayed keep the current room's rate."
                : "Nobody has arrived, so the whole stay moves across."));

        if (JOptionPane.showConfirmDialog(this, panel, "Move Booking", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }

        Room newRoom = (Room) optionBox.getSelectedItem();
        run(() -> {
            hotelManager.upgradeBooking(booking, newRoom);
            refreshAfterChange(booking.getRoom());
            showMessage("Moved to " + booking.getRoom() + ".\nUpdated total: "
                    + rupiah.format(booking.getCurrentBill()));
        });
    }
    
    private String explainNoUpgrade(Booking booking) {
        Room current = booking.getRoom();
        boolean anythingDearer = hotelManager.getRooms().stream()
                .anyMatch(room -> room.getNightlyRate() > current.getNightlyRate());
        if (!anythingDearer) {
            return current.getTierName() + " is the highest tier, so room "
                    + current.getRoomNumber() + " cannot be upgraded further.";
        }

        boolean anyBigEnough = hotelManager.getRooms().stream()
                .filter(room -> room.getNightlyRate() > current.getNightlyRate())
                .anyMatch(room -> room.getCapacity() >= booking.getGuestCount());
        if (!anyBigEnough) {
            return "No higher-tier room takes " + booking.getGuestCount() + " guests.";
        }

        return "Every higher-tier room is taken for at least one night of this stay ("
                + booking.getArrivalDate().format(DATE_LABEL) + " to "
                + booking.getDepartureDate().format(DATE_LABEL) + ").";
    }

    /** The booking holding the selected room on the viewed date, if it is still changeable. */
    private Optional<Booking> openBookingOnViewedDate() {
        if (selectedRoom == null) {
            return Optional.empty();
        }
        return holderOn(selectedRoom).filter(Booking::holdsInventory);
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
        LocalDate viewed = getViewedDate();
        availableLabel.setText(String.valueOf(hotelManager.getFreeCount(viewed)));
        occupiedLabel.setText(String.valueOf(hotelManager.getBookedCount(viewed)));
        occupancyLabel.setText(String.format("%.0f%%", hotelManager.getOccupancyRate(viewed)));
        reservedLabel.setText(String.valueOf(hotelManager.getArrivalsOn(viewed)));
        checkedOutLabel.setText(String.valueOf(hotelManager.getCheckedOutCount()));

        setMoney(projectedRevenueLabel, hotelManager.getProjectedRevenue());
        setMoney(realizedRevenueLabel, hotelManager.getRealizedRevenue());
        setMoney(reservedValueLabel, hotelManager.getReservedValue());
        refreshBookingTable();
    }

    /** Card values drop the "Rp" prefix, which lives in the title, so long amounts fit. */
    private void setMoney(JLabel label, double amount) {
        label.setText(plainAmount.format(amount));
        label.setToolTipText(rupiah.format(amount));
    }

    private void refreshBookingTable() {
        bookingTableModel.setRowCount(0);
        for (Booking booking : hotelManager.getBookings()) {
            bookingTableModel.addRow(new Object[]{
                    booking.getBookingId(),
                    booking.getGuest().getFullName(),
                    booking.getRoom().getRoomNumber(),
                    booking.getRoom().getTierName(),
                    booking.getArrivalDate().format(DATE_LABEL),
                    booking.getNights(),
                    rupiah.format(booking.getCurrentBill()),
                    booking.getStatus().getLabel()
            });
        }
    }

    // --------------------------------------------------------------- helpers

    /** The date the grid is showing. Defaults to today. */
    private LocalDate getViewedDate() {
        return dateField.getValue();
    }

    /**
     * Tint for the main calendar: one colour for "the hotel has something booked on
     * this date", whichever room it is. Deliberately hotel-wide and independent of
     * the selected room, so the colours stay put while the clerk clicks around.
     * The per-room, two-tone view lives in the booking dialog instead.
     */
    private DateField.DayStatus statusOn(LocalDate date) {
        boolean anythingBooked = hotelManager.getRooms().stream()
                .anyMatch(room -> hotelManager.getBookingOn(room, date).isPresent());
        return anythingBooked ? DateField.DayStatus.RESERVED : DateField.DayStatus.FREE;
    }

    private DateField.DayStatus statusOn(Room room, LocalDate date) {
        Optional<Booking> holder = hotelManager.getBookingOn(room, date);
        if (holder.isEmpty()) {
            return DateField.DayStatus.FREE;
        }
        return (holder.get().isActive() && isToday(date))
                ? DateField.DayStatus.OCCUPIED
                : DateField.DayStatus.RESERVED;
    }

    private static boolean isToday(LocalDate date) {
        return LocalDate.now().equals(date);
    }

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