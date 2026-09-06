package hotel.test;

import hotel.ai.EvalSet;
import hotel.ai.ModelEval;
import hotel.ai.OllamaConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfSystemProperty(named = "hotel.ai.eval", matches = "true")
class ModelEvalTests {

    @Test
    void scoreEveryModelAgainstTheEvalSet() throws IOException {
        OllamaConfig base = OllamaConfig.fromSystemProperties();
        assumeTrue(ModelEval.ollamaIsUp(base.endpoint()), "Ollama is not answering; skipping the eval");

        List<EvalSet.Row> rows = EvalSet.load();
        List<String> models = List.of(System.getProperty("hotel.ai.models",
                OllamaConfig.DEFAULT_MODEL).split(","));

        List<String> report = new ArrayList<>();
        report.add("Eval set: " + rows.size() + " sentences");
        report.add("");

        for (String model : models) {
            ModelEval.Report result = ModelEval.run(base.withModel(model.trim()), rows);
            report.add("=".repeat(72));
            report.add(model.trim());
            report.add("=".repeat(72));
            report.add(String.format("  latency p50 %d ms | p95 %d ms | no reply %d",
                    result.p50Millis(), result.p95Millis(), result.emptyReplies()));
            report.add("  placement: " + result.placement()
                    + (System.getProperty("hotel.ai.gpuLayers") == null
                       ? "" : " (num_gpu=" + System.getProperty("hotel.ai.gpuLayers") + ")"));
            report.add(String.format("  resolver agreed on %d of %d rows",
                    result.resolveAgreements(), result.rows()));
            report.add("");
            for (ModelEval.FieldScore field : result.fields()) {
                report.add(String.format("  %-12s %5.1f%%  (%d/%d)",
                        field.field(), field.percent(), field.right(), field.scored()));
            }
            report.add("");
            report.add("  misses:");
            result.misses().forEach(miss -> report.add("    " + miss));
            report.add("");
        }

        String text = String.join(System.lineSeparator(), report);
        Path out = Path.of("build", "eval-report.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, text, StandardCharsets.UTF_8);
        System.out.println(text);
        System.out.println("written to " + out.toAbsolutePath());
    }
}
