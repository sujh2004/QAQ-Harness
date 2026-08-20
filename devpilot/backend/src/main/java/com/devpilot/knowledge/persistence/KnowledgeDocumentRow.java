package com.devpilot.knowledge.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Database row of {@code knowledge_document}.
 *
 * <p>The original text is stored alongside the metadata so the vector index can be rebuilt from the
 * database. An index that can only be rebuilt by re-uploading every file is not recoverable.
 */
@TableName("knowledge_document")
public class KnowledgeDocumentRow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String documentName;
    private String documentType;
    private String sourcePath;
    private String content;
    private String vectorStatus;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** @return owning project */
    public Long getProjectId() {
        return projectId;
    }

    /** @param projectId owning project */
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
    /** @return file or title */
    public String getDocumentName() {
        return documentName;
    }

    /** @param documentName file or title */
    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }
    /** @return category of the document */
    public String getDocumentType() {
        return documentType;
    }

    /** @param documentType category of the document */
    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }
    /** @return where the document came from */
    public String getSourcePath() {
        return sourcePath;
    }

    /** @param sourcePath where the document came from */
    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }
    /** @return document text, kept so the index can be rebuilt */
    public String getContent() {
        return content;
    }

    /** @param content document text, kept so the index can be rebuilt */
    public void setContent(String content) {
        this.content = content;
    }
    /** @return PENDING, INDEXED or FAILED */
    public String getVectorStatus() {
        return vectorStatus;
    }

    /** @param vectorStatus PENDING, INDEXED or FAILED */
    public void setVectorStatus(String vectorStatus) {
        this.vectorStatus = vectorStatus;
    }
    /** @return how many chunks the document produced */
    public Integer getChunkCount() {
        return chunkCount;
    }

    /** @param chunkCount how many chunks the document produced */
    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }
    /** @return import time */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt import time */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    /** @return last update time */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** @param updatedAt last update time */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** @return document identity */
    public Long getId() {
        return id;
    }

    /** @param id document identity */
    public void setId(Long id) {
        this.id = id;
    }
}
