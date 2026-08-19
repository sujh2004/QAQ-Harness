package com.devpilot.project;

import com.devpilot.project.model.CreateProjectRequest;
import com.devpilot.project.model.ProjectResponse;
import com.devpilot.project.model.RepositoryValidationResponse;
import com.devpilot.project.model.UpdateProjectRequest;
import com.devpilot.project.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract: projects can be created, updated and bound to a readable local repository. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectService projectService;

    @Test
    void createsReadsAndUpdatesAProject() throws Exception {
        String code = uniqueCode();
        ProjectResponse created = projectService.create(
                new CreateProjectRequest("订单服务", code, "demo", "../demo-project/order-demo", null));

        assertThat(created.id()).isNotNull();
        assertThat(created.defaultBranch()).isEqualTo("main");
        assertThat(created.status()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/projects/{id}", created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value(code));

        UpdateProjectRequest update =
                new UpdateProjectRequest("订单服务 v2", "updated", "/srv/repos/order", "develop", 0);
        mockMvc.perform(put("/api/v1/projects/{id}", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("订单服务 v2"))
                .andExpect(jsonPath("$.data.defaultBranch").value("develop"))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.code").value(code));
    }

    @Test
    void rejectsADuplicateProjectCode() throws Exception {
        String code = uniqueCode();
        projectService.create(new CreateProjectRequest("first", code, null, "/srv/repos/a", null));

        CreateProjectRequest duplicate = new CreateProjectRequest("second", code, null, "/srv/repos/b", null);
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void rejectsAnInvalidCreateRequest() throws Exception {
        CreateProjectRequest invalid = new CreateProjectRequest("", "has space", null, "", null);

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void reportsAMissingProjectAsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{id}", 9_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void listsProjectsNewestFirst() throws Exception {
        ProjectResponse created = projectService.create(
                new CreateProjectRequest("newest", uniqueCode(), null, "/srv/repos/newest", null));

        mockMvc.perform(get("/api/v1/projects").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(created.id()))
                .andExpect(jsonPath("$.data.size").value(5));
    }

    @Test
    void reportsAReadableRepositoryDirectory(@TempDir Path repository) throws Exception {
        Files.createDirectory(repository.resolve(".git"));
        ProjectResponse created = projectService.create(new CreateProjectRequest(
                "with-repo", uniqueCode(), null, repository.toAbsolutePath().toString(), null));

        RepositoryValidationResponse validation = projectService.validateRepository(created.id());

        assertThat(validation.exists()).isTrue();
        assertThat(validation.directory()).isTrue();
        assertThat(validation.readable()).isTrue();
        assertThat(validation.gitRepository()).isTrue();
        assertThat(validation.accessible()).isTrue();
        assertThat(validation.resolvedPath()).isEqualTo(repository.toAbsolutePath().normalize().toString());
    }

    @Test
    void reportsAMissingRepositoryPathWithoutFailingTheRequest() throws Exception {
        ProjectResponse created = projectService.create(new CreateProjectRequest(
                "no-repo", uniqueCode(), null, "/definitely/not/here/order-demo", null));

        mockMvc.perform(post("/api/v1/projects/{id}/validate-repository", created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessible").value(false))
                .andExpect(jsonPath("$.data.detail").value("Path does not exist"));
    }

    @Test
    void resolvesRelativeRepositoryPathsToAnAbsoluteDirectory() {
        Path resolved = projectService.resolveRepositoryPath("../demo-project/order-demo");

        assertThat(resolved.isAbsolute()).isTrue();
        assertThat(resolved.toString()).doesNotContain("..");
    }

    private static String uniqueCode() {
        return "p-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
