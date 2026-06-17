package rs.novacode.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import rs.novacode.model.DirectorySummary;
import rs.novacode.service.pack.PackService;
import rs.novacode.service.scan.DirectoryScanner;
import rs.novacode.service.unpack.UnpackService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class PackManagement {

    private static final DateTimeFormatter PACK_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final DirectoryScanner directoryScanner;
    private final PackService packService;
    private final UnpackService unpackService;

    /**
     * Scans {@code sourceDir}, then packs every {@code .eml} file directly under it into a
     * single timestamped {@code .zst} archive created inside that same directory.
     *
     * @param sourceDir directory to scan and pack
     * @return path to the created archive
     */
    public Path pack(final Path sourceDir) throws IOException {
        String timestamp = LocalDateTime.now().format(PACK_TIMESTAMP);
        Path zst = sourceDir.resolve("pack_" + timestamp + ".zst");

        DirectorySummary summary = directoryScanner.scan(sourceDir);
        log.info("Total files: {}", summary.getTotalFiles());
        log.info("Files > 128 KiB: {}", summary.getLargeFiles());
        log.info("Total size: {} MB", String.format("%.2f", summary.totalSizeMb()));
        log.info("Large files size: {} MB", String.format("%.2f", summary.largeFilesSizeMb()));

        try (Stream<Path> paths = Files.list(sourceDir)) {
            List<Path> entries = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".eml"))
                    .sorted()
                    .toList();
            packService.pack(entries, zst);
        }

        log.info("Packed {} into {}", sourceDir.toAbsolutePath(), zst.toAbsolutePath());
        return zst;
    }

    /**
     * Extracts a single entry from a {@code .zst} archive into the current working directory.
     *
     * @param archive packed {@code .zst} archive
     * @param name    name of the entry to extract
     * @return path to the extracted file
     */
    public Path unpack(final Path archive, final String name) throws IOException {
        Path target = Path.of(name);
        unpackService.extract(archive, name, target);
        return target;
    }
}