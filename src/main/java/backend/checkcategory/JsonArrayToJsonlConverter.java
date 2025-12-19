//package backend.checkcategory;
//
//import com.fasterxml.jackson.core.JsonParser;
//import com.fasterxml.jackson.core.JsonToken;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//import java.io.BufferedInputStream;
//import java.io.BufferedWriter;
//import java.io.InputStream;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.StandardOpenOption;
//import java.util.Map;
//
///**
// * Convert a large JSON array file (possibly with objects spanning multiple lines)
// * into a JSONL file where each object is one line. Uses Jackson streaming parser.
// */
//@Component
//@Slf4j
//public class JsonArrayToJsonlConverter {
//
//    private final ObjectMapper mapper = new ObjectMapper();
//
//
//    /**
//     * Convert input JSON array file to a .jsonl file next to the input.
//     * Returns path to the created .jsonl file.
//     */
//    public Path convert(Path inputJsonArrayPath) throws Exception {
//        Path out = inputJsonArrayPath.resolveSibling(inputJsonArrayPath.getFileName() + ".jsonl");
//        return convertTo(inputJsonArrayPath, out);
//    }
//
//    /**
//     * Convert input JSON array file to provided output jsonl path.
//     * Returns path to the created .jsonl file.
//     */
//    public Path convertTo(Path inputJsonArrayPath, Path out) throws Exception {
//        log.info("Converting JSON array '{}' -> '{}'", inputJsonArrayPath, out);
//
//        try (InputStream is = new BufferedInputStream(Files.newInputStream(inputJsonArrayPath));
//             JsonParser parser = mapper.getFactory().createParser(is);
//             BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
//                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
//
//            JsonToken t = parser.nextToken();
//            if (t != JsonToken.START_ARRAY) {
//                throw new IllegalStateException("Expected JSON array start but found: " + t);
//            }
//
//            int count = 0;
//            while (parser.nextToken() != JsonToken.END_ARRAY) {
//                if (parser.currentToken() == JsonToken.START_OBJECT) {
//                    @SuppressWarnings("unchecked")
//                    Map<String, Object> obj = mapper.readValue(parser, Map.class);
//                    writer.write(mapper.writeValueAsString(obj));
//                    writer.newLine();
//                    count++;
//                    if (count % 10000 == 0) log.info("Converted {} objects...", count);
//                } else {
//                    // skip tokens until next object
//                    parser.skipChildren();
//                }
//            }
//            writer.flush();
//            log.info("Conversion complete. Total objects: {}", count);
//            return out;
//        }
//    }
//
//    /**
//     * Standalone main to convert a JSON array file into a jsonl file.
//     * Usage:
//     *   java -cp your.jar backend.checkcategory.JsonArrayToJsonlConverter <input.json> [output.jsonl]
//     * If output not provided, defaults to "./input.jsonl"
//     */
//    public static void main(String[] args) throws Exception {
//        Path input = Path.of(
//                "D:\\Viettel\\TL\\file\\product_master_category_202512191514.json"
//        );
//        Path output = Path.of("input.jsonl");
//
//        JsonArrayToJsonlConverter converter = new JsonArrayToJsonlConverter();
//        Path created = converter.convertTo(input, output);
//        System.out.println("Created jsonl: " + created.toAbsolutePath());
//    }
//}
