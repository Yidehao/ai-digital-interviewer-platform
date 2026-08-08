package org.interviewer.base;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * MinIO utility class
 */
@Slf4j
public class MinIOUtils {

    private static MinioClient minioClient;

    private static String endpoint;
    private static String fileHost;
    private static String bucketName;
    private static String accessKey;
    private static String secretKey;

    private static final String SEPARATOR = "/";

    public MinIOUtils() {
    }

    public MinIOUtils(String endpoint, String fileHost, String bucketName, String accessKey, String secretKey) {
        MinIOUtils.endpoint = endpoint;
        MinIOUtils.fileHost = fileHost;
        MinIOUtils.bucketName = bucketName;
        MinIOUtils.accessKey = accessKey;
        MinIOUtils.secretKey = secretKey;
        createMinioClient();
    }

    /**
     * Create MinioClient based on Java
     */
    public void createMinioClient() {
        try {
            if (null == minioClient) {
                log.info("Starting to create MinioClient...");
                minioClient = MinioClient
                                .builder()
                                .endpoint(endpoint)
                                .credentials(accessKey, secretKey)
                                .build();
                createBucket(bucketName);
                log.info("MinioClient created successfully...");
            }
        } catch (Exception e) {
            log.error("MinIO server exception: {}", e);
        }
    }

    /**
     * Get upload file prefix path
     * @return
     */
    public static String getBasisUrl() {
        return endpoint + SEPARATOR + bucketName + SEPARATOR;
    }

    /******************************  Operate Bucket Start  ******************************/

    /**
     * Initialize Bucket when SpringBoot container starts
     * Create Bucket if it doesn't exist
     * @throws Exception
     */
    private static void createBucket(String bucketName) throws Exception {
        if (!bucketExists(bucketName)) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * Check if Bucket exists, true: exists, false: doesn't exist
     * @return
     * @throws Exception
     */
    public static boolean bucketExists(String bucketName) throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }


    /**
     * Get Bucket policy
     * @param bucketName
     * @return
     * @throws Exception
     */
    public static String getBucketPolicy(String bucketName) throws Exception {
        String bucketPolicy = minioClient
                                .getBucketPolicy(
                                        GetBucketPolicyArgs
                                                .builder()
                                                .bucket(bucketName)
                                                .build()
                                );
        return bucketPolicy;
    }


    /**
     * Get all Bucket list
     * @return
     * @throws Exception
     */
    public static List<Bucket> getAllBuckets() throws Exception {
        return minioClient.listBuckets();
    }

    /**
     * Get related information based on bucketName
     * @param bucketName
     * @return
     * @throws Exception
     */
    public static Optional<Bucket> getBucket(String bucketName) throws Exception {
        return getAllBuckets().stream().filter(b -> b.name().equals(bucketName)).findFirst();
    }

    /**
     * Delete Bucket based on bucketName, true: deletion successful; false: deletion failed, file or already doesn't exist
     * @param bucketName
     * @throws Exception
     */
    public static void removeBucket(String bucketName) throws Exception {
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
    }

    /******************************  Operate Bucket End  ******************************/


    /******************************  Operate Files Start  ******************************/

    /**
     * Check if file exists
     * @param bucketName bucket name
     * @param objectName file name
     * @return
     */
    public static boolean isObjectExist(String bucketName, String objectName) {
        boolean exist = true;
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
        } catch (Exception e) {
            exist = false;
        }
        return exist;
    }

    /**
     * Check if folder exists
     * @param bucketName bucket name
     * @param objectName folder name
     * @return
     */
    public static boolean isFolderExist(String bucketName, String objectName) {
        boolean exist = false;
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).prefix(objectName).recursive(false).build());
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir() && objectName.equals(item.objectName())) {
                    exist = true;
                }
            }
        } catch (Exception e) {
            exist = false;
        }
        return exist;
    }

    /**
     * Query files based on file prefix
     * @param bucketName bucket name
     * @param prefix prefix
     * @param recursive whether to use recursive query
     * @return MinioItem list
     * @throws Exception
     */
    public static List<Item> getAllObjectsByPrefix(String bucketName,
                                                   String prefix,
                                                   boolean recursive) throws Exception {
        List<Item> list = new ArrayList<>();
        Iterable<Result<Item>> objectsIterator = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).prefix(prefix).recursive(recursive).build());
        if (objectsIterator != null) {
            for (Result<Item> o : objectsIterator) {
                Item item = o.get();
                list.add(item);
            }
        }
        return list;
    }

    /**
     * Get file stream
     * @param bucketName bucket name
     * @param objectName file name
     * @return binary stream
     */
    public static InputStream getObject(String bucketName, String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
    }

    /**
     * Resume download
     * @param bucketName bucket name
     * @param objectName file name
     * @param offset starting byte position
     * @param length length to read
     * @return binary stream
     */
    public InputStream getObject(String bucketName, String objectName, long offset, long length)throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .offset(offset)
                        .length(length)
                        .build());
    }

    /**
     * Get file list under path
     * @param bucketName bucket name
     * @param prefix file name
     * @param recursive whether to recursively search, false: simulate folder structure search
     * @return binary stream
     */
    public static Iterable<Result<Item>> listObjects(String bucketName, String prefix,
                                                     boolean recursive) {
        return minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .recursive(recursive)
                        .build());
    }

    /**
     * Upload file using MultipartFile
     * @param bucketName bucket name
     * @param file file name
     * @param objectName object name
     * @param contentType content type
     * @return
     * @throws Exception
     */
    public static ObjectWriteResponse uploadFile(String bucketName, MultipartFile file,
                                                 String objectName, String contentType) throws Exception {
        InputStream inputStream = file.getInputStream();
        return minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .contentType(contentType)
                        .stream(inputStream, inputStream.available(), -1)
                        .build());
    }

    /**
     * Upload local file
     * @param bucketName bucket name
     * @param objectName object name
     * @param fileName local file path
     */
    public static ObjectWriteResponse uploadFile(String bucketName, String objectName,
                                                 String fileName) throws Exception {
        return minioClient.uploadObject(
                UploadObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .filename(fileName)
                        .build());
    }

    /**
     * Upload file via stream
     *
     * @param bucketName bucket name
     * @param objectName file object
     * @param inputStream file stream
     */
    public static ObjectWriteResponse uploadFile(String bucketName, String objectName, InputStream inputStream) throws Exception {
        return minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(inputStream, inputStream.available(), -1)
                        .build());
    }

    public static String uploadFile(String bucketName, String objectName, InputStream inputStream, boolean needUrl) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(inputStream, inputStream.available(), -1)
                        .build());
        if (needUrl) {
            String imageUrl = fileHost
                    + "/"
                    + bucketName
                    + "/"
                    + objectName;
            return imageUrl;
        }
        return "";
    }

    /**
     * Create folder or directory
     * @param bucketName bucket name
     * @param objectName directory path
     */
    public static ObjectWriteResponse createDir(String bucketName, String objectName) throws Exception {
        return minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(new byte[]{}), 0, -1)
                        .build());
    }

    /**
     * Get file information, if exception is thrown, it means file doesn't exist
     *
     * @param bucketName bucket name
     * @param objectName file name
     */
    public static String getFileStatusInfo(String bucketName, String objectName) throws Exception {
        return minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()).toString();
    }

    /**
     * Copy file
     *
     * @param bucketName bucket name
     * @param objectName file name
     * @param srcBucketName target bucket name
     * @param srcObjectName target file name
     */
    public static ObjectWriteResponse copyFile(String bucketName, String objectName,
                                               String srcBucketName, String srcObjectName) throws Exception {
        return minioClient.copyObject(
                CopyObjectArgs.builder()
                        .source(CopySource.builder().bucket(bucketName).object(objectName).build())
                        .bucket(srcBucketName)
                        .object(srcObjectName)
                        .build());
    }

    /**
     * Delete file
     * @param bucketName bucket name
     * @param objectName file name
     */
    public static void removeFile(String bucketName, String objectName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build());
    }

    public static void removeFile(String fileUrl) throws Exception {

        String hostPrefix = fileHost + "/" + bucketName + "/";
        String objectName = fileUrl.substring(hostPrefix.length(), fileUrl.length());

        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build());
    }

    /**
     * Batch delete files
     * @param bucketName bucket name
     * @param keys list of files to delete
     * @return
     */
    public static void removeFiles(String bucketName, List<String> keys) {
        List<DeleteObject> objects = new LinkedList<>();
        keys.forEach(s -> {
            objects.add(new DeleteObject(s));
            try {
                removeFile(bucketName, s);
            } catch (Exception e) {
                log.error("Batch deletion failed! error: {}", e);
            }
        });
    }

    /**
     * Get file external link
     * @param bucketName bucket name
     * @param objectName file name
     * @param expires expiration time <=7 seconds (external link valid time (unit: seconds))
     * @return url
     * @throws Exception
     */
    public static String getPresignedObjectUrl(String bucketName, String objectName, Integer expires) throws Exception {
        GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder().expiry(expires).bucket(bucketName).object(objectName).build();
        return minioClient.getPresignedObjectUrl(args);
    }

    /**
     * Get file external link
     * @param bucketName
     * @param objectName
     * @return url
     * @throws Exception
     */
    public static String getPresignedObjectUrl(String bucketName, String objectName) throws Exception {
        GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                                                                    .bucket(bucketName)
                                                                    .object(objectName)
                                                                    .method(Method.GET).build();
        return minioClient.getPresignedObjectUrl(args);
    }

    /**
     * Convert URLDecoder encoding to UTF8
     * @param str
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String getUtf8ByURLDecoder(String str) throws UnsupportedEncodingException {
        String url = str.replaceAll("%(?![0-9a-fA-F]{2})", "%25");
        return URLDecoder.decode(url, "UTF-8");
    }

    /******************************  Operate Files End  ******************************/


}
