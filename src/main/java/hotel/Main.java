package hotel;

import hotel.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

public class Main {
    private static final String DEFAULT_DATA_FILE = "hotel-bookings.json";

    public static void main(String[] args) {
        Path dataFile = Path.of(System.getProperty("hotel.data", DEFAULT_DATA_FILE));
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fall back to the cross-platform look and feel.
            }
            MainFrame frame = new MainFrame();
            frame.openStore(dataFile);
            frame.setVisible(true);
        });
    }
}
