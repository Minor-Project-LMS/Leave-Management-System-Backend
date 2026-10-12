package com.lms.Leave_Management_System_Backend.service;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

public interface BlobStorageService {
    
    /**
     * Generate a presigned PUT URL for direct client upload
     * @param key The storage key for the object
     * @param contentType The content type of the file
     * @param expiry The expiry duration for the URL
     * @return The presigned PUT URL
     */
    URL generatePresignedPutUrl(String key, String contentType, Duration expiry);
    
    /**
     * Generate a presigned GET URL for downloading
     * @param key The storage key for the object
     * @param expiry The expiry duration for the URL
     * @return The presigned GET URL
     */
    URL generatePresignedGetUrl(String key, Duration expiry);
    
    /**
     * Check if an object exists and get its metadata
     * @param key The storage key for the object
     * @return Object metadata (size, etc.)
     * @throws Exception if object doesn't exist or error occurs
     */
    ObjectMetadata headObject(String key) throws Exception;
    
    /**
     * Stream upload an object to blob storage
     * @param key The storage key for the object
     * @param inputStream The input stream to upload
     * @param contentType The content type of the file
     * @param contentLength The length of the content in bytes
     * @throws Exception if upload fails
     */
    void putObject(String key, InputStream inputStream, String contentType, long contentLength) throws Exception;
    
    /**
     * Delete an object from blob storage
     * @param key The storage key for the object
     * @throws Exception if deletion fails
     */
    void deleteObject(String key) throws Exception;
    
    /**
     * Get the appropriate bucket name for a given entity type
     * @param entityType The entity type (LEAVE_REQUEST, USER_AVATAR, etc.)
     * @return The bucket name
     */
    String getBucketForEntityType(String entityType);
    
    /**
     * Generate a unique storage key for a file
     * @param entityType The entity type
     * @param entityId The entity ID (can be null for presigned uploads)
     * @param fileName The original file name
     * @return A unique storage key
     */
    String generateStorageKey(String entityType, Long entityId, String fileName);
    
    /**
     * Object metadata class
     */
    class ObjectMetadata {
        private long size;
        private String contentType;
        private String eTag;
        
        public ObjectMetadata(long size, String contentType, String eTag) {
            this.size = size;
            this.contentType = contentType;
            this.eTag = eTag;
        }
        
        public long getSize() {
            return size;
        }
        
        public String getContentType() {
            return contentType;
        }
        
        public String getETag() {
            return eTag;
        }
    }
}
