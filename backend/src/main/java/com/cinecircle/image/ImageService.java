package com.cinecircle.image;

import com.cinecircle.common.ApiException;
import com.cinecircle.common.ErrorCode;
import com.cinecircle.config.ImageProperties;
import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** Stores images in the GridFS bucket {@code images} (bucket name configured in application.yml). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    /** Files collection of the GridFS bucket {@code images} (spring.data.mongodb.gridfs.bucket). */
    static final String FILES_COLLECTION = "images.files";

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final GridFsTemplate gridFs;
    private final MongoTemplate mongo;
    private final ImageProperties props;

    public record StoredImage(byte[] bytes, String contentType) {
    }

    /**
     * Validates size ({@code app.images.max-bytes} -> 400 VALIDATION_ERROR), the storage guard (GridFS bucket
     * total &gt;= {@code app.images.storage-limit-mb} -> 507 STORAGE_FULL) and type (jpeg/png/webp, by declared
     * content type AND magic bytes), then stores the file. Returns the GridFS id (hex ObjectId).
     */
    public String store(MultipartFile file, String field, String ownerId) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation(field, "Image file is required");
        }
        if (file.getSize() > props.maxBytes()) {
            throw ApiException.validation(field, props.tooLargeMessage());
        }
        ensureStorageAvailable();
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

    /** 507 STORAGE_FULL when the GridFS {@code images} bucket already holds {@code storage-limit-mb} or more. */
    public void ensureStorageAvailable() {
        if (totalStoredBytes() >= props.storageLimitBytes()) {
            log.warn("Image storage limit of {} MB reached: uploads are refused", props.storageLimitMb());
            throw new ApiException(ErrorCode.STORAGE_FULL,
                    "Image uploads are paused because storage is full — you can still post without a custom image.");
        }
    }

    /** Sum of {@code length} over {@code images.files} (one small aggregation). */
    public long totalStoredBytes() {
        Document result = mongo.getCollection(FILES_COLLECTION).aggregate(List.of(
                        new Document("$group", new Document("_id", null)
                                .append("total", new Document("$sum", "$length")))))
                .first();
        if (result == null) {
            return 0;
        }
        Object total = result.get("total");
        return total instanceof Number n ? n.longValue() : 0;
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
