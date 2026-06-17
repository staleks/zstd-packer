package rs.novacode.service.unpack;

import java.io.IOException;
import java.nio.file.Path;

public interface UnpackService {
    void extract(final Path archive, final String name, final Path target) throws IOException;
}
