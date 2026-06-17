package rs.novacode.service.scan;

import rs.novacode.model.DirectorySummary;

import java.io.IOException;
import java.nio.file.Path;

public interface DirectoryScanner {
    DirectorySummary scan(final Path root) throws IOException;
}
