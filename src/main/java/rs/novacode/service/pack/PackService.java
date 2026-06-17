package rs.novacode.service.pack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface PackService {
    void pack(final List<Path> files, final Path packedArchive) throws IOException;
}
