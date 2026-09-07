package hotel.test;

import hotel.ai.OllamaConfig;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OllamaConfigTests {

    private static OllamaConfig withEndpoint(String endpoint) {
        return new OllamaConfig(endpoint, "gemma4:e2b-it-qat", OllamaConfig.DEFAULT_PROMPT,
                1024, 96, "30m", Duration.ofSeconds(30), null);
    }

    @Test
    void anEndpointOnThisMachineIsAccepted() {
        assertEquals("http://localhost:11434/api/generate",
                withEndpoint("http://localhost:11434/api/generate").endpoint(), "localhost");
        assertEquals("http://127.0.0.1:11434/api/generate",
                withEndpoint("http://127.0.0.1:11434/api/generate").endpoint(), "loopback address");
    }

    /** A stronger machine in the back office still keeps guest details in the building. */
    @Test
    void aMachineOnTheSameNetworkIsAccepted() {
        assertEquals("http://192.168.1.50:11434/api/generate",
                withEndpoint("http://192.168.1.50:11434/api/generate").endpoint(), "home network");
        assertEquals("http://10.0.0.7:11434/api/generate",
                withEndpoint("http://10.0.0.7:11434/api/generate").endpoint(), "private range");
        assertEquals("http://172.16.4.2:11434/api/generate",
                withEndpoint("http://172.16.4.2:11434/api/generate").endpoint(), "private range");
    }

    /** Constraint: guest details never leave the building. */
    @Test
    void aPublicEndpointIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> withEndpoint("https://api.example.com/api/generate"), "someone else's server");
        assertThrows(IllegalArgumentException.class,
                () -> withEndpoint("http://8.8.8.8:11434/api/generate"), "a public address");
        assertThrows(IllegalArgumentException.class,
                () -> withEndpoint("http://172.32.0.1:11434/api/generate"), "just outside the private range");
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> withEndpoint("not a url"), "not a url");
        assertThrows(IllegalArgumentException.class, () -> withEndpoint(""), "blank");
    }
}
