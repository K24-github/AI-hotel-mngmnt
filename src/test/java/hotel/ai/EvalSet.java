package hotel.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

public final class EvalSet {
    private static final String RESOURCE = "/eval/booking-sentences.json";

    public record Row(String sentence, String note, boolean resolves, BookingDraft expected) {
    }

    private record EvalFile(int version, List<Row> rows) {
    }

    private EvalSet() {
    }

    public static List<Row> load() {
        ObjectMapper mapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try (InputStream stream = EvalSet.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Eval set " + RESOURCE + " is not on the test classpath.");
            }
            return mapper.readValue(stream, EvalFile.class).rows();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the eval set at " + RESOURCE, ex);
        }
    }
}
