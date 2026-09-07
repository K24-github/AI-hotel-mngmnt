package hotel.test;

import hotel.ai.ModelHealth;
import hotel.ai.OllamaConfig;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ModelHealthTests {

    /** A missing model and a sentence the model could not read are different faults. */
    @Test
    void aModelThatNeverAnsweredIsNotTheClerksFault() {
        assertEquals(ModelHealth.NO_MODEL, ModelHealth.messageWhenNothingCameBack(false),
                "nothing was listening, so say so");
        assertEquals(ModelHealth.COULD_NOT_READ, ModelHealth.messageWhenNothingCameBack(true),
                "the model answered, it just did not understand");
    }

    @Test
    void theTwoMessagesSayDifferentThings() {
        assertFalse(ModelHealth.NO_MODEL.equals(ModelHealth.COULD_NOT_READ), "not the same text");
    }

    @Test
    void anEndpointWithNothingBehindItIsNotAnswering() {
        OllamaConfig nobodyHome = new OllamaConfig("http://127.0.0.1:1/api/generate",
                "gemma4:e2b-it-qat", OllamaConfig.DEFAULT_PROMPT,
                1024, 96, "30m", Duration.ofSeconds(30), null);

        assertFalse(ModelHealth.isAnswering(nobodyHome), "port 1 has nothing on it");
        assertFalse(ModelHealth.isAnswering((OllamaConfig) null), "no config at all");
        assertFalse(ModelHealth.isAnswering((String) null), "no endpoint at all");
    }
}
