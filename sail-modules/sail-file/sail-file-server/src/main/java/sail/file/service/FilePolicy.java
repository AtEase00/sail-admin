package sail.file.service;

import java.util.Set;

/** 上传与文档预览共享的允许类型和资源限制。 */
public final class FilePolicy {
    private FilePolicy() {
        /* 不允许实例化常量集合。 */
    }

    public static final int PREVIEW_CHARACTERS = 200000;
    public static final int PREVIEW_ROWS = 1000;
    public static final Set<String> EXTENSIONS =
            Set.of(
                    "png",
                    "jpg",
                    "jpeg",
                    "gif",
                    "bmp",
                    "webp",
                    "pdf",
                    "txt",
                    "md",
                    "markdown",
                    "csv",
                    "json",
                    "log",
                    "zip",
                    "doc",
                    "docx",
                    "xls",
                    "xlsx",
                    "ppt",
                    "pptx",
                    "mp3",
                    "wav",
                    "ogg",
                    "mp4",
                    "webm");
    public static final Set<String> CONTENT_TYPES =
            Set.of(
                    "image/png",
                    "image/jpeg",
                    "image/gif",
                    "image/bmp",
                    "image/x-ms-bmp",
                    "image/webp",
                    "application/pdf",
                    "text/plain",
                    "text/markdown",
                    "text/x-web-markdown",
                    "text/csv",
                    "application/json",
                    "application/zip",
                    "application/x-tika-ooxml",
                    "application/x-tika-msoffice",
                    "application/msword",
                    "application/vnd.ms-excel",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "audio/mpeg",
                    "audio/x-wav",
                    "audio/vnd.wave",
                    "audio/wav",
                    "audio/ogg",
                    "application/ogg",
                    "video/mp4",
                    "video/webm",
                    "audio/webm");
    public static final Set<String> RASTER_TYPES =
            Set.of(
                    "image/png",
                    "image/jpeg",
                    "image/gif",
                    "image/webp",
                    "image/bmp",
                    "image/x-ms-bmp");
}
