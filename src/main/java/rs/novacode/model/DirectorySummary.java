package rs.novacode.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DirectorySummary {
    private long totalFiles;
    private long largeFiles;
    private long totalSizeBytes;
    private long largeFilesSizeBytes;

    public double totalSizeMb() {
        return totalSizeBytes / (1024.0 * 1024.0);
    }

    public double largeFilesSizeMb() {
        return largeFilesSizeBytes / (1024.0 * 1024.0);
    }
}
