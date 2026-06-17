package rs.novacode.service.scan;

import lombok.extern.slf4j.Slf4j;
import rs.novacode.model.DirectorySummary;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

@Slf4j
public class DirectoryScannerImpl implements DirectoryScanner {
    private static final long SIZE_THRESHOLD_BYTES = 128L * 1024L;

    @Override
    public DirectorySummary scan(final Path root) throws IOException {
        var totalFiles          = new long[1];
        var largeFiles          = new long[1];
        var totalSize           = new long[1];
        var largeSize           = new long[1];

        Files.walkFileTree(root, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    long size = attrs.size();   // pulled from attrs — no extra stat() call
                    totalFiles[0]++;
                    totalSize[0] += size;
                    if (size > SIZE_THRESHOLD_BYTES) {
                        largeFiles[0]++;
                        largeSize[0] += size;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Don't abort the whole scan because one file is unreadable.
                return FileVisitResult.CONTINUE;
            }
        });
        return new DirectorySummary(totalFiles[0], largeFiles[0], totalSize[0], largeSize[0]);
    }

}
