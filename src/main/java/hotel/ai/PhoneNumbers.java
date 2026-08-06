package hotel.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhoneNumbers {
    private static final Pattern CANDIDATE = Pattern.compile("\\+?\\d[\\d\\s().-]{5,}\\d");
    private static final int MINIMUM_DIGITS = 7;

    private PhoneNumbers() {
    }

    public static Optional<String> findIn(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        List<String> candidates = new ArrayList<>();
        Matcher matcher = CANDIDATE.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group().trim();
            if (digitsIn(candidate) >= MINIMUM_DIGITS) {
                candidates.add(candidate);
            }
        }
        return (candidates.size() == 1) ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    private static int digitsIn(String text) {
        return (int) text.chars().filter(Character::isDigit).count();
    }
}
