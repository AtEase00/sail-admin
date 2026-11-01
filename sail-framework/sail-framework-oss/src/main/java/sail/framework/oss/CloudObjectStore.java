package sail.framework.oss;

import com.aliyun.oss.*;
import com.aliyun.oss.model.ObjectMetadata;

import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.net.URI;
import java.time.Duration;

/** MinIO/S3 与阿里云 OSS 的真实客户端适配，不混用渠道认证参数。 */
@Component
public class CloudObjectStore implements ObjectStore {
    private S3Client minio(StorageSpec s) {
        // MinIO 明确采用路径式寻址和配置的区域，不猜测 endpoint 中的 bucket。
        return S3Client.builder()
                .endpointOverride(URI.create(s.endpoint()))
                .region(Region.of(s.region()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(s.accessKey(), s.secretKey())))
                .httpClientBuilder(
                        UrlConnectionHttpClient.builder()
                                .connectionTimeout(Duration.ofSeconds(5))
                                .socketTimeout(Duration.ofSeconds(30)))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private OSS aliyun(StorageSpec s) {
        // 阿里云使用原生 SDK 与独立 endpoint，避免把 S3 参数误当作 OSS 签名参数。
        ClientBuilderConfiguration c = new ClientBuilderConfiguration();
        c.setConnectionTimeout(5000);
        c.setSocketTimeout(30000);
        return new OSSClientBuilder().build(s.endpoint(), s.accessKey(), s.secretKey(), c);
    }

    @Override
    public void upload(StorageSpec s, String key, InputStream input, long size, String type) {
        if (s.type() == StorageType.MINIO) {
            // 每次调用关闭客户端，防止配置切换后继续使用旧凭据。
            try (S3Client client = minio(s)) {
                client.putObject(
                        PutObjectRequest.builder()
                                .bucket(s.bucket())
                                .key(key)
                                .contentType(type)
                                .build(),
                        RequestBody.fromInputStream(input, size));
            }
            return;
        }
        OSS client = aliyun(s);
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(size);
            meta.setContentType(type);
            client.putObject(s.bucket(), key, input, meta);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public InputStream download(StorageSpec s, String key) {
        // 下载流持有客户端，关闭响应流时同时释放 SDK 资源。
        if (s.type() == StorageType.MINIO) {
            S3Client client = minio(s);
            try {
                return new FilterInputStream(
                        client.getObject(
                                GetObjectRequest.builder().bucket(s.bucket()).key(key).build())) {
                    @Override
                    public void close() throws IOException {
                        try {
                            super.close();
                        } finally {
                            client.close();
                        }
                    }
                };
            } catch (RuntimeException ex) {
                client.close();
                throw ex;
            }
        }
        OSS client = aliyun(s);
        try {
            return new FilterInputStream(client.getObject(s.bucket(), key).getObjectContent()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        client.shutdown();
                    }
                }
            };
        } catch (RuntimeException ex) {
            client.shutdown();
            throw ex;
        }
    }

    @Override
    public void delete(StorageSpec s, String key) {
        // 只删除指定对象，业务配置不得删除整个 bucket。
        if (s.type() == StorageType.MINIO) {
            try (S3Client client = minio(s)) {
                client.deleteObject(
                        DeleteObjectRequest.builder().bucket(s.bucket()).key(key).build());
            }
            return;
        }
        OSS client = aliyun(s);
        try {
            client.deleteObject(s.bucket(), key);
        } finally {
            client.shutdown();
        }
    }
}
