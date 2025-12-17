package backend.checkcategory;

import backend.checkcategory.service.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CategoryCheckJob implements CommandLineRunner {

    private static final int BATCH_SIZE = 5;
    private static final Path OUTPUT_FILE = Path.of("output.jsonl");

    private final LlmClient llmClient;
    private final CheckpointService checkpointService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void run(String... args) throws Exception {

        int lastProcessedLine = checkpointService.load();
        log.info("Resume from line: {}", lastProcessedLine);

        try (
                CSVReader reader = new CSVReader(
                        new InputStreamReader(
                                new FileInputStream("D:\\Viettel\\TL\\file\\product_master_category_202512170935.csv"),
                                StandardCharsets.UTF_8
                        )
                );
                BufferedWriter writer = Files.newBufferedWriter(
                        OUTPUT_FILE,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                )
        ) {

            String[] row;
            int line = 0;

            List<ProductCsv> batch = new ArrayList<>(BATCH_SIZE);

            while ((row = reader.readNext()) != null) {
                line++;
                log.info("Processing line: {}", line);
                // resume theo checkpoint
                if (line <= lastProcessedLine) {
                    continue;
                }

                batch.add(new ProductCsv(row[0], row[1], row[2]));

                // đủ batch thì xử lý
                if (batch.size() == BATCH_SIZE) {
                    processBatch(batch, line, writer);
                    batch.clear();
                }
            }

            // xử lý nốt phần còn lại (<5)
            if (!batch.isEmpty()) {
                processBatch(batch, line, writer);
            }
        }
    }

    /**
     * Xử lý 1 batch (size <= 5)
     * Retry tối đa 4 lần, fail lần 4 -> STOP JOB
     */
    private void processBatch(
            List<ProductCsv> batch,
            int currentLine,
            BufferedWriter writer
    ) throws Exception {

        int maxRetry = 4;

        List<String> names = batch.stream()
                .map(ProductCsv::getName)
                .toList();
        log.info("names={}", names);

        List<CategoryResult> categories = null;

        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                categories = llmClient.callLlm(names);
                log.info("categories={}", categories);
                break; // ✅ thành công
            } catch (Exception e) {
                log.error("Batch LLM failed (attempt {}/{})", attempt, maxRetry, e);

                if (attempt == maxRetry) {
                    // ❌ FAIL CỨNG -> DỪNG JOB
                    throw new RuntimeException(
                            "LLM batch failed after " + maxRetry + " attempts, STOP JOB", e
                    );
                }

                Thread.sleep(5000); // backoff
            }
        }

        // validate an toàn
        if (categories == null || categories.size() != batch.size()) {
            throw new IllegalStateException(
                    "LLM returned invalid result size. expected="
                            + batch.size() + ", actual="
                            + (categories == null ? "null" : categories.size())
            );
        }

        // ghi output + checkpoint từng dòng
        for (int i = 0; i < batch.size(); i++) {
            ProductCsv p = batch.get(i);
            String predicted = categories.get(i).getCategory();

            boolean isCorrect =
                    normalize(predicted).equals(normalize(p.getCategory()));

            CategoryCheckResult result = new CategoryCheckResult(
                    p.getName(),
                    p.getCategory(),
                    predicted,
                    isCorrect
            );

            writer.write(mapper.writeValueAsString(result));
            writer.newLine();
            writer.flush();

            int processedLine = currentLine - batch.size() + i + 1;
            checkpointService.save(processedLine);
        }
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}
