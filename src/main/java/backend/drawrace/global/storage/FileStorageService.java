package backend.drawrace.global.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    String store(MultipartFile file);

    void delete(String fileUrl);

    default String getPresignedUrl(String fileUrl) {
        return fileUrl;
    }
}
