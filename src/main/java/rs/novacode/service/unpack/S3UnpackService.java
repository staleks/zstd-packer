package rs.novacode.service.unpack;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Extracts a single entry from a {@code .zst} archive that lives in an S3 object,
 * downloading only the bytes needed (trailer, index footer, and the entry's frame)
 * rather than the whole archive.
 */
public interface S3UnpackService {

    /**
     * Extracts the entry named {@code name} from the archive stored at {@code s3://bucket/key}
     * and writes it to {@code target}.
     *
     * @param bucket S3 bucket holding the packed archive
     * @param key    S3 key of the packed {@code .zst} archive
     * @param name   name of the entry to extract
     * @param target local file to write the decompressed entry to
     */
    void extract(final String bucket, final String key, final String name, final Path target) throws IOException;
}