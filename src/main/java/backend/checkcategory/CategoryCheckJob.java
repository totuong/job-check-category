package backend.checkcategory;

import backend.checkcategory.service.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class CategoryCheckJob implements CommandLineRunner {

    private static final int BATCH_SIZE = 10;

    private static final Path INPUT_FILE =
            Path.of("D:\\Viettel\\TL\\file\\input.jsonl");

    private static final Path OUTPUT_FILE_FALSE =
            Path.of("output-false.jsonl");

    private static final Path OUTPUT_FILE_TRUE =
            Path.of("output-true.jsonl");

    private static final Path OUTPUT_FILE_EXCEPTION =
            Path.of("output-exception.jsonl");

    private final LlmClient llmClient;
    private final CheckpointService checkpointService;

    private final ObjectMapper mapper = new ObjectMapper();

    private final ExecutorService writerExecutor =
            Executors.newFixedThreadPool(2);

    private final AtomicInteger rowException = new AtomicInteger(0);

    @Override
    public void run(String... args) throws Exception {

        int lastProcessedLine = checkpointService.load();
        log.info("Resume from line: {}", lastProcessedLine);

        try (
                BufferedReader reader = Files.newBufferedReader(
                        INPUT_FILE, StandardCharsets.UTF_8
                );

                BufferedWriter writerFalse = Files.newBufferedWriter(
                        OUTPUT_FILE_FALSE,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );

                BufferedWriter writerTrue = Files.newBufferedWriter(
                        OUTPUT_FILE_TRUE,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );

                BufferedWriter writerException = Files.newBufferedWriter(
                        OUTPUT_FILE_EXCEPTION,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                )
        ) {
            String lineRaw;
            int line = 0;

            List<ProductCsv> batch = new ArrayList<>(BATCH_SIZE);

            while ((lineRaw = reader.readLine()) != null) {
                if (lineRaw.isBlank()) continue;

                line++;

                // resume theo checkpoint
                if (line <= lastProcessedLine) {
                    if (line % 10_000 == 0) {
                        log.info("Skipping line {} (checkpoint)", line);
                    }
                    continue;
                }

                Map<String, Object> obj;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed =
                            mapper.readValue(lineRaw, Map.class);
                    obj = parsed;
                    log.info("Processing line: {}", line);
                } catch (Exception e) {
                    rowException.incrementAndGet();
                    CategoryCheckResult result = new CategoryCheckResult(
                            "",
                            "Khác",
                            "",
                            "json-parse-error: " + e.getMessage(),
                            false
                    );

                    int errorLine = line;
                    writerExecutor.submit(() -> {
                        try {
                            synchronized (writerException) {
                                writerException.write(
                                        mapper.writeValueAsString(result)
                                );
                                writerException.newLine();
                                writerException.flush();
                            }
                        } catch (Exception ex) {
                            log.error("Write exception file failed", ex);
                        }
                    });

                    checkpointService.save(errorLine);
                    continue;
                }

                String name = getString(obj, "product_name", "name");
                String description = getString(obj, "description", "desc");
                String category = getString(obj, "category");

                if (name == null) name = "";
                if (description == null) description = "";
                if (category == null) category = "Khác";

                batch.add(new ProductCsv(name, description, category));

                if (batch.size() == BATCH_SIZE) {
                    processBatch(batch, line,
                            writerTrue, writerFalse, writerException);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                processBatch(batch, line,
                        writerTrue, writerFalse, writerException);
            }

        } finally {
            writerExecutor.shutdown();
            writerExecutor.awaitTermination(10, TimeUnit.MINUTES);
            log.info("Job finished. total exception rows={}",
                    rowException.get());
        }
    }

    /**
     * Xử lý 1 batch
     */
    private void processBatch(
            List<ProductCsv> batch,
            int currentLine,
            BufferedWriter writerTrue,
            BufferedWriter writerFalse,
            BufferedWriter writerException
    ) {

        List<String> names = batch.stream()
                .map(ProductCsv::getName)
                .toList();

        List<CategoryResult> categories = null;
        int maxRetry = 4;

        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                log.info("Calling LLM (attempt {}/{}) for batch ending at line {}",
                        attempt, maxRetry, currentLine);

                categories = llmClient.callLlm(names);
                break; // ✅ thành công → thoát retry

            } catch (Exception e) {
                log.error("LLM call failed (attempt {}/{})", attempt, maxRetry, e);

                if (attempt == maxRetry) {
                    // ❗ QUÁ RETRY → BỎ QUA BATCH
                    log.error("Skip batch ending at line {} after {} retries",
                            currentLine, maxRetry);

                    // (OPTIONAL) ghi exception cho từng record
                    for (ProductCsv p : batch) {
                        CategoryCheckResult errorResult =
                                new CategoryCheckResult(
                                        p.getName(),
                                        p.getCategory(),
                                        p.getDescription(),
                                        "LLM failed after " + maxRetry + " retries: " + e.getMessage(),
                                        false
                                );

                        writerExecutor.submit(() -> {
                            try {
                                synchronized (writerException) {
                                    writerException.write(
                                            mapper.writeValueAsString(errorResult)
                                    );
                                    writerException.newLine();
                                    writerException.flush();
                                }
                            } catch (Exception ex) {
                                log.error("Write exception file failed", ex);
                            }
                        });
                    }

                    // checkpoint tới cuối batch để không lặp lại
                    checkpointService.save(currentLine);
                    return; // ✅ BỎ QUA batch, KHÔNG throw
                }

                // backoff nhẹ để tránh spam server
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // nếu vẫn null → bỏ (phòng thủ)
        if (categories == null || categories.size() != batch.size()) {
            log.error("Invalid LLM result size at line {}, skip batch", currentLine);
            checkpointService.save(currentLine);
            return;
        }

        // ===== xử lý kết quả bình thường =====
        for (int i = 0; i < batch.size(); i++) {
            ProductCsv p = batch.get(i);
            String predicted = categories.get(i).getCategory();

            boolean isCorrect =
                    normalize(predicted).equals(normalize(p.getCategory()));

            CategoryCheckResult result =
                    new CategoryCheckResult(
                            p.getName(),
                            p.getCategory(),
                            p.getDescription(),
                            predicted,
                            isCorrect
                    );

            int processedLine =
                    currentLine - batch.size() + i + 1;

            writerExecutor.submit(() -> {
                try {
                    BufferedWriter w =
                            isCorrect ? writerTrue : writerFalse;
                    synchronized (w) {
                        w.write(mapper.writeValueAsString(result));
                        w.newLine();
                        w.flush();
                    }
                } catch (Exception e) {
                    log.error("Write file failed", e);
                }
            });

            checkpointService.save(processedLine);
        }
    }


    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private String getString(Map<String, Object> obj, String... keys) {
        for (String k : keys) {
            Object v = obj.get(k);
            if (v != null) return v.toString();
        }
        return null;
    }
}
