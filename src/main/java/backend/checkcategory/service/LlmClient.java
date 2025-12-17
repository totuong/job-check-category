package backend.checkcategory.service;

import backend.checkcategory.CategoryResult;
import backend.checkcategory.constant.LLMConstant;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class LlmClient {

    private static final String ENDPOINT =
            "https://aihub.vietteltelecom.vn:8443/crs-api-public/base-llm/call-llm";


    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public List<CategoryResult> callLlm(List<String> products) throws Exception {

        String userContent = IntStream.range(0, products.size())
                .mapToObj(i -> (i + 1) + ". " + products.get(i))
                .collect(Collectors.joining("\n"));
        String jsonPayload = mapper.writeValueAsString(buildBody(userContent));


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("x-api-key", "CjuiGkZSLSyxWspiQaRfiA")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        // ✅ tương đương response.raise_for_status()
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(
                    "HTTP " + response.statusCode() + ": " + response.body()
            );
        }

        // ✅ response.text
        return mapper.readValue(response.body(), new TypeReference<>() {
        });

    }

    private Map<String, Object> buildBody(String productName) {
        return Map.of(
                "url", "http://10.254.135.31:8086/v1/chat/completions",
                "prompt", Map.of(
                        "model", "Qwen2.5-14B-Instruct",
                        "max_tokens", 1024,
                        "temperature", 0.0,
                        "stream", true,
                        "messages", List.of(
                                Map.of(
                                        "role", "system",
                                        "content", LLMConstant.SYSTEM_PROMPT
                                ),
                                Map.of(
                                        "role", "user",
                                        "content", productName
                                )
                        )
                )
        );
    }
}

