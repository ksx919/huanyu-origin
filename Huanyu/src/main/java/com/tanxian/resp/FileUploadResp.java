package com.tanxian.resp;

/**
 * 文件上传响应DTO
 */
public class FileUploadResp {

    /**
     * 文件访问URL
     */
    private String filePath;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 上传时间戳
     */
    private Long uploadTime;

    public FileUploadResp() {
    }

    public FileUploadResp(String fileUrl, String fileName, Long fileSize) {
        this.filePath = fileUrl;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.uploadTime = System.currentTimeMillis();
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(Long uploadTime) {
        this.uploadTime = uploadTime;
    }

    @Override
    public String toString() {
        return "FileUploadResp{" +
                "fileUrl='" + filePath + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", uploadTime=" + uploadTime +
                '}';
    }
}