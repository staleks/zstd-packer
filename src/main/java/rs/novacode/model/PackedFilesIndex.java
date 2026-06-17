package rs.novacode.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PackedFilesIndex {
    private int version;
    private List<FileEntry> entries;
}
