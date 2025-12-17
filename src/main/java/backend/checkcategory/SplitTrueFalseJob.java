//package backend.checkcategory;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//import java.io.BufferedReader;
//import java.io.BufferedWriter;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.StandardOpenOption;
//
//public class SplitTrueFalseJob {
//    public static void main(String[] args) throws Exception {
//
//        Path input = Path.of("output.jsonl");
//        Path outTrue = Path.of("output-true.jsonl");
//        Path outFalse = Path.of("output-false.jsonl");
//
//        ObjectMapper mapper = new ObjectMapper();
//
//        try (
//                BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8);
//                BufferedWriter writerTrue = Files.newBufferedWriter(
//                        outTrue, StandardCharsets.UTF_8,
//                        StandardOpenOption.CREATE, StandardOpenOption.APPEND
//                );
//                BufferedWriter writerFalse = Files.newBufferedWriter(
//                        outFalse, StandardCharsets.UTF_8,
//                        StandardOpenOption.CREATE, StandardOpenOption.APPEND
//                )
//        ) {
//            String line;
//            while ((line = reader.readLine()) != null) {
//                if (line.isBlank()) continue;
//
//                CategoryCheckResult r =
//                        mapper.readValue(line, CategoryCheckResult.class);
//
//                BufferedWriter w = r.isCorrect() ? writerTrue : writerFalse;
//                w.write(line);
//                w.newLine();
//            }
//        }
//
//        System.out.println("✅ Split done");
//    }
//}
