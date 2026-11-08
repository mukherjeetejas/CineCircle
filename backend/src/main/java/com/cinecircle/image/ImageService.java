package com.cinecircle.image;

import com.cinecircle.common.ApiException;
import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Stores images in the GridFS bucket {@code images} (bucket name configured in application.yml). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final GridFsTemplate gridFs;

    public record StoredImage(byte[] bytes, String contentType) {
    }

    /**
     * Validates type (jpeg/png/webp, by declared content type AND magic bytes) and size (max 5 MB),
     * then stores the file. Returns the GridFS id (hex ObjectId).
     */
    public String store(MultipartFile file, String field, String ownerId) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation(field, "Image file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.validation(field, "Image must be at most 5 MB");
        }
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED.containsKey(declared)) {
            throw ApiException.validation(field, "Only JPEG, PNG or WebP images are allowed");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation(field, "Could not read uploaded file");
        }
        String sniffed = sniff(bytes);
        if (sniffed == null) {
            throw ApiException.validation(field, "File content is not a valid JPEG, PNG or WebP image");
        }
        return store(bytes, sniffed, ownerId);
    }

    public String store(byte[] bytes, String contentType, String ownerId) {
        Document metadata = new Document("contentType", contentType);
        if (ownerId != null) {
            metadata.append("ownerId", ownerId);
        }
        String filename = "img-" + System.currentTimeMillis() + "." + ALLOWED.getOrDefault(contentType, "bin");
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            ObjectId id = gridFs.store(in, filename, contentType, metadata);
            return id.toHexString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store image", e);
        }
    }

    public StoredImage load(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            throw ApiException.notFound("Image not found");
        }
        GridFSFile file = gridFs.findOne(Query.query(Criteria.where("_id").is(new ObjectId(id))));
        if (file == null) {
            throw ApiException.notFound("Image not found");
        }
        GridFsResource resource = gridFs.getResource(file);
        try (InputStream in = resource.getInputStream()) {
            String contentType = file.getMetadata() != null && file.getMetadata().getString("contentType") != null
                    ? file.getMetadata().getString("contentType")
                    : resource.getContentType();
            return new StoredImage(in.readAllBytes(), contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read image", e);
        }
    }

    public void delete(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return;
        }
        try {
            gridFs.delete(Query.query(Criteria.where("_id").is(new ObjectId(id))));
        } catch (RuntimeException e) {
            log.warn("Failed to delete image {}: {}", id, e.getMessage());
        }
    }

    /** Detects the real image type from magic bytes; null when not jpeg/png/webp. */
    static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
