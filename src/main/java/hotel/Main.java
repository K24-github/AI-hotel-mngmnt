package hotel;

import com.formdev.flatlaf.FlatLightLaf;

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
                FlatLightLaf.setup();
            } catch (RuntimeException ignored) {
                // The native look and feel is uglier but it still runs.
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception alsoIgnored) {
                    // Cross-platform default it is.
                }
            }
            MainFrame frame = new MainFrame();
            frame.openStore(dataFile);
            frame.setVisible(true);
        });
    }
}
