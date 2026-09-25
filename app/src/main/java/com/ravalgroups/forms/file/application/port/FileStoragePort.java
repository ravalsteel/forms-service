package com.ravalgroups.forms.file.application.port;

import java.io.IOException;
import java.io.InputStream;

public interface FileStoragePort {

    StoredFile store(String contentType, InputStream content, long contentLength) throws IOException;

    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;

    record StoredFile(String storageKey, String contentType, long sizeBytes) {}
}
