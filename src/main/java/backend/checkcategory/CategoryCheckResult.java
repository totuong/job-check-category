package backend.checkcategory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryCheckResult {
    private String name;
    private String originCategory;
    private String description;
    private String predictedCategory;
    private boolean isCorrect;
}
