package rs.novacode.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import rs.novacode.config.ApplicationConfig;
import rs.novacode.model.DirectorySummary;
import rs.novacode.service.PackManagement;
import rs.novacode.service.scan.DirectoryScanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Import({
        ApplicationConfig.class
})
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public ApplicationRunner applicationRunner(DirectoryScanner directoryScanner, PackManagement packManagement) {
        return args -> {
            List<String> positional = args.getNonOptionArgs();
            if (positional.isEmpty()) {
                logUsage();
                return;
            }

            String command = positional.getFirst().toLowerCase();
            switch (command) {
                case "scan", "pack" -> {
                    if (positional.size() < 2) {
                        logUsage();
                        return;
                    }
                    Path root = Path.of(positional.get(1));
                    if (!Files.isDirectory(root)) {
                        log.error("Not a directory: {}", root.toAbsolutePath());
                        return;
                    }
                    if (command.equals("scan")) {
                        DirectorySummary summary = directoryScanner.scan(root);
                        log.info("Total files: {}", summary.getTotalFiles());
                        log.info("Files > 128 KiB: {}", summary.getLargeFiles());
                        log.info("Total size: {} MB", String.format("%.2f", summary.totalSizeMb()));
                        log.info("Large files size: {} MB", String.format("%.2f", summary.largeFilesSizeMb()));
                    } else {
                        packManagement.pack(root);
                    }
                }
                case "unpack" -> {
                    if (positional.size() < 3) {
                        logUsage();
                        return;
                    }
                    Path archive = Path.of(positional.get(1));
                    String name = positional.get(2);
                    if (!Files.isRegularFile(archive)) {
                        log.error("Not a file: {}", archive.toAbsolutePath());
                        return;
                    }
                    packManagement.unpack(archive, name);
                }
                case "s3unpack" -> {
                    if (positional.size() < 4) {
                        logUsage();
                        return;
                    }
                    String bucket = positional.get(1);
                    String key = positional.get(2);
                    String name = positional.get(3);
                    packManagement.s3Unpack(bucket, key, name);
                }
                default -> log.warn("Unknown command '{}'. Expected 'scan', 'pack', 'unpack' or 's3unpack'.", command);
            }
        };
    }

    private static void logUsage() {
        log.warn("""
                Usage:
                  scan      <dir>                  scan a directory and print a summary
                  pack      <dir>                  pack *.eml files in a directory into a timestamped .zst
                  unpack    <archive.zst> <name>   extract one entry into the current directory
                  s3unpack  <bucket> <key> <name>   extract one entry from an archive in S3 (ranged GET) into the current directory""");
    }

}
