package backend.checkcategory;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Service
public class CheckpointService {

    private static final Path CHECKPOINT_FILE = Paths.get("checkpoint.txt");

    public int load() {
        try {
            if (!Files.exists(CHECKPOINT_FILE)) return 0;
            return Integer.parseInt(Files.readString(CHECKPOINT_FILE).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public void save(int lineNumber) {
        try {
            Files.writeString(
                    CHECKPOINT_FILE,
                    String.valueOf(lineNumber),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
