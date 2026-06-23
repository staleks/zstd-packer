package rs.novacode.service.unpack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import rs.novacode.model.FileEntry;
import rs.novacode.model.PackedFilesIndex;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Reads a {@link ZstdUnpackService}-compatible archive directly from S3 using ranged GETs.
 * <p>
 * The on-disk layout (see {@code ZstdPackService}) is self-describing, so a reader only needs the
 * tail of the object to find any entry: read the 16-byte trailer from the end to locate the index
 * footer, read the index, then ranged-GET just the requested entry's independent zstd frame. Only
 * those bytes cross the wire — the rest of the archive is never downloaded.
 */
@Slf4j
@RequiredArgsConstructor
public class S3ZstdUnpackService implements S3UnpackService {

    private static final byte[] MAGIC = "ZSTPACK1".getBytes(StandardCharsets.US_ASCII); // mirrors ZstdPackService
    private static final int    TRAILER_SIZE = MAGIC.length + Long.BYTES;

    private final S3Client s3Client;
    private final ObjectMapper mapper;

    @Override
    public void extract(final String bucket, final String key, final String name, final Path target) throws IOException {
        String uri = "s3://" + bucket + "/" + key;

        long objectSize = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
                .contentLength();
        if (objectSize < TRAILER_SIZE) {
            throw new IOException("Not a valid archive (too small): " + uri);
        }

        // ----- trailer: magic + index offset, read from the very end -----
        ByteBuffer trailer = ByteBuffer.wrap(range(bucket, key, objectSize - TRAILER_SIZE, objectSize - 1));
        byte[] magic = new byte[MAGIC.length];
        trailer.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("Not a valid archive (bad magic): " + uri);
        }
        long indexOffset = trailer.getLong();

        // ----- index footer: JSON between indexOffset and the trailer -----
        byte[] indexBytes = range(bucket, key, indexOffset, objectSize - TRAILER_SIZE - 1);
        PackedFilesIndex index = mapper.readValue(indexBytes, PackedFilesIndex.class);

        FileEntry entry = index.getEntries().stream()
                .filter(e -> e.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IOException("No entry named '" + name + "' in archive " + uri));

        // ----- ranged GET of just this entry's independent zstd frame, then decompress -----
        byte[] frame = range(bucket, key, entry.getOffset(), entry.getOffset() + entry.getCompressedLength() - 1);
        try (ZstdInputStream in = new ZstdInputStream(new ByteArrayInputStream(frame));
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            in.transferTo(out);
        }

        log.info("Extracted '{}' ({} bytes) from {} to {}",
                name, entry.getOriginalSize(), uri, target.toAbsolutePath());
    }

    /** Downloads the inclusive byte range {@code [start, endInclusive]} of the object as a byte array. */
    private byte[] range(final String bucket, final String key, final long start, final long endInclusive) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .range("bytes=" + start + "-" + endInclusive)
                .build();
        return s3Client.getObjectAsBytes(request).asByteArray();
    }
}
