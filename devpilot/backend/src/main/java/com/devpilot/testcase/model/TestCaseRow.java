package com.devpilot.testcase.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Database row of {@code test_case}. */
@TableName("test_case")
public class TestCaseRow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String sessionId;
    private String title;
    private String priority;
    private String precondition;
    private String stepsJson;
    private String expectedResult;
    private String source;
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

    /** @return session the case was generated in */
    public String getSessionId() {
        return sessionId;
    }

    /** @param sessionId session the case was generated in */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** @return case title */
    public String getTitle() {
        return title;
    }

    /** @param title case title */
    public void setTitle(String title) {
        this.title = title;
    }

    /** @return priority such as P0 */
    public String getPriority() {
        return priority;
    }

    /** @param priority priority such as P0 */
    public void setPriority(String priority) {
        this.priority = priority;
    }

    /** @return preconditions */
    public String getPrecondition() {
        return precondition;
    }

    /** @param precondition preconditions */
    public void setPrecondition(String precondition) {
        this.precondition = precondition;
    }

    /** @return serialized step list */
    public String getStepsJson() {
        return stepsJson;
    }

    /** @param stepsJson serialized step list */
    public void setStepsJson(String stepsJson) {
        this.stepsJson = stepsJson;
    }

    /** @return expected result */
    public String getExpectedResult() {
        return expectedResult;
    }

    /** @param expectedResult expected result */
    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
    }

    /** @return who produced the case, for example AGENT */
    public String getSource() {
        return source;
    }

    /** @param source who produced the case */
    public void setSource(String source) {
        this.source = source;
    }

    /** @return creation time */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt creation time */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
