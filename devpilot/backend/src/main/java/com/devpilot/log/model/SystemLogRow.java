package com.devpilot.log.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Database row of {@code system_log}. */
@TableName("system_log")
public class SystemLogRow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String serviceName;
    private String level;
    private String traceId;
    private String logger;
    private String message;
    private String exceptionType;
    private String stackTrace;
    private LocalDateTime logTime;
    private LocalDateTime createdAt;

    /** @return row identity */
    public Long getId() {
        return id;
    }

    /** @param id row identity */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return owning project */
    public Long getProjectId() {
        return projectId;
    }

    /** @param projectId owning project */
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    /** @return emitting service */
    public String getServiceName() {
        return serviceName;
    }

    /** @param serviceName emitting service */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /** @return log level */
    public String getLevel() {
        return level;
    }

    /** @param level log level */
    public void setLevel(String level) {
        this.level = level;
    }

    /** @return distributed trace identifier */
    public String getTraceId() {
        return traceId;
    }

    /** @param traceId distributed trace identifier */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /** @return logger name */
    public String getLogger() {
        return logger;
    }

    /** @param logger logger name */
    public void setLogger(String logger) {
        this.logger = logger;
    }

    /** @return log message */
    public String getMessage() {
        return message;
    }

    /** @param message log message */
    public void setMessage(String message) {
        this.message = message;
    }

    /** @return thrown exception type */
    public String getExceptionType() {
        return exceptionType;
    }

    /** @param exceptionType thrown exception type */
    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    /** @return captured stack trace */
    public String getStackTrace() {
        return stackTrace;
    }

    /** @param stackTrace captured stack trace */
    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    /** @return when the line was emitted */
    public LocalDateTime getLogTime() {
        return logTime;
    }

    /** @param logTime when the line was emitted */
    public void setLogTime(LocalDateTime logTime) {
        this.logTime = logTime;
    }

    /** @return when the line was stored */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt when the line was stored */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
