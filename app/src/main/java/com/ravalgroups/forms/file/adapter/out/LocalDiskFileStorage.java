package com.ravalgroups.forms.file.adapter.out;

import com.ravalgroups.forms.file.application.port.FileStoragePort;
import com.ravalgroups.forms.shared.config.FormsFileStorageProperties;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "forms.file-storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalDiskFileStorage implements FileStoragePort {

    private final Path root;
    private final String keyPrefix;

    public LocalDiskFileStorage(FormsFileStorageProperties properties) {
        String configuredRoot =
                properties.root() == null || properties.root().isBlank() ? "./data/forms-files" : properties.root();
        this.root = Path.of(configuredRoot).toAbsolutePath().normalize();
        this.keyPrefix = properties.keyPrefix() == null || properties.keyPrefix().isBlank()
                ? "forms/"
                : properties.keyPrefix();
        try {
            Files.createDirectories(this.root);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create file storage root " + this.root, ex);
        }
    }

    @Override
    public StoredFile store(String contentType, InputStream content, long contentLength) throws IOException {
        String storageKey = keyPrefix + UuidV7.create();
        Path destination = resolve(storageKey);
        Files.createDirectories(destination.getParent());
        Files.copy(content, destination);
        long size = Files.size(destination);
        if (contentLength > 0 && size != contentLength) {
            Files.deleteIfExists(destination);
            throw new IOException("Stored size " + size + " did not match declared length " + contentLength);
        }
        return new StoredFile(storageKey, contentType, size);
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        return Files.newInputStream(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    private Path resolve(String storageKey) throws IOException {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")) {
            throw new IOException("Invalid storage key");
        }
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new IOException("Invalid storage key");
        }
        return path;
    }
}
