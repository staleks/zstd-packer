# zstd-packer

## What this is

`zstd-packer` is a Spring Boot **command-line** application (not a web service) that scans a directory and packs its `.eml` files into a single compressed `.zst` archive. Spring Boot 4.1.0, Java 21, Gradle.

## Commands

```bash
./gradlew build            # compile + test + assemble
./gradlew compileJava      # compile only (fast sanity check)
./gradlew test             # run the JUnit 5 test suite
./gradlew test --tests 'rs.novacode.SomeTest'   # run a single test class

# Run the app — note the trailing words go inside --args:
./gradlew bootRun --args='scan /absolute/path/to/dir'
./gradlew bootRun --args='pack /absolute/path/to/dir'
./gradlew bootRun --args='unpack /absolute/path/to/archive.zst entry-name.eml'

# Or from the assembled jar:
java -jar build/libs/zstd-packer-0.0.1-SNAPSHOT.jar pack /absolute/path/to/dir
```

## CLI argument contract (important, easy to get wrong)

`Application.applicationRunner` reads positional (non-option) arguments. The first is the command; remaining args depend on it:
- `scan <dir>` / `pack <dir>` — second arg is a directory.
- `unpack <archive.zst> <name>` — second arg is the archive file, third is the entry name to extract; the file is written to the current working directory as `./<name>`.

- The directory MUST be a plain positional arg, e.g. `pack /Users/me/data`. Do **not** use `--dir=...` — anything starting with `--` becomes a Spring *option* arg and is invisible to `getNonOptionArgs()`, producing a "Usage:" warning.
- The directory MUST be absolute (start with `/`). A relative path is resolved against the process working directory, which silently yields a wrong path and a "Not a directory" error.
- In IntelliJ, set these in **Program arguments** (not VM options).

## Archive format (the non-obvious part)

`ZstdPackService.pack` writes a self-describing single-file archive; `ZstdUnpackService.extract` reads it back. Both must stay in sync on this layout — read trailer from the end, locate the index, then seek to the wanted entry's frame:

1. **Body** — each input file is compressed into its *own independent* zstd frame, concatenated. `CloseShieldOutputStream` keeps the shared `CountingOutputStream` open while each `ZstdOutputStream` frame is finalized individually. `CountingOutputStream.getByteCount()` gives each frame's start offset and length.
2. **Index footer** — a JSON `PackedFilesIndex` (`version` + list of `FileEntry{name, offset, compressedLength, originalSize}`) serialized via the configured Jackson `ObjectMapper`.
3. **16-byte trailer** — 8-byte magic `ZSTPACK1` + an 8-byte `long` giving the index's start offset, so a reader can seek from the end of the file to locate the index.

## Architecture

- `app/Application` — entry point; the `ApplicationRunner` bean parses args and dispatches `scan` (via `DirectoryScanner` directly) or `pack` (via `PackManagement`).
- `service/PackManagement` — orchestrator: scans, then lists `*.eml` files directly under the dir (sorted), then delegates to `PackService`. Computes the timestamped output name `pack_<yyyyMMdd_HHmmss>.zst` inside the source dir.
- `service/scan/DirectoryScanner` + `DirectoryScannerImpl` — single-pass `Files.walkFileTree`; reads size from `BasicFileAttributes` (no extra `stat`), tolerates unreadable files, and flags files larger than 128 KiB. Returns `DirectorySummary`.
- `service/pack/PackService` + `ZstdPackService` — the archive writer described above (compression level 9).
- `service/unpack/UnpackService` + `ZstdUnpackService` — the matching reader: reads the trailer/index via a `FileChannel`, slices the requested entry's frame into memory, and decompresses it with `ZstdInputStream`. Validates up front and throws `IOException` with a descriptive message on a malformed archive (`too small`, `bad magic`) or a missing entry (`No entry named '...'`). This is the only `UnpackService` implementation — an earlier duplicate (`AIZstdUnpackService`) has been removed.

### Bean wiring convention

Services are **plain classes** (no `@Component`/`@Service`), wired explicitly as `@Bean` methods in `config/ApplicationConfig`. When adding a new service, register it there and add it to `ApplicationConfig` rather than relying on component scanning. The shared Jackson `ObjectMapper` (JavaTimeModule, no timestamp dates) is also defined there.

## Testing

Tests use JUnit 5 + Mockito, pulled in via `testImplementation 'org.springframework.boot:spring-boot-starter-test'`. Mirror the `main` package under `src/test/java`. Conventions:

- **Structure** — `@DisplayName` on the class, `@Nested` class per method, `should...When...` method names, Given/When/Then bodies.
- **Plain services** are unit-tested directly: mock collaborators with `@ExtendWith(MockitoExtension.class)` (see `PackManagementTest`, `ApplicationTest`).
- **Filesystem and archive code** use a real `@TempDir` rather than mocking `java.nio.file.Files` — `DirectoryScannerImplTest` scans real files; the pack/unpack tests round-trip through real `.zst` archives (`ZstdUnpackServiceTest` builds its fixtures with a real `ZstdPackService`).
- **`Application`** is tested without booting Spring: call `new Application().applicationRunner(...)` with mocks and drive it via `DefaultApplicationArguments`. `main()` is intentionally not unit-tested.

## Logging

`logback-spring.xml` defines the `STDOUT` console appender wired to the root logger at `INFO`. Use Lombok `@Slf4j` and `log.info(...)`; the codebase logs scan summaries via slf4j rather than `System.out`.