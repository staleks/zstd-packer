package rs.novacode.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileEntry {
    private String name;
    private long offset;
    private long compressedLength;
    private long originalSize;
}
