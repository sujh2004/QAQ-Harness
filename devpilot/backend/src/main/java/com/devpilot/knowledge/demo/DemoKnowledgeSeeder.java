package com.devpilot.knowledge.demo;

import com.devpilot.knowledge.model.ImportDocumentRequest;
import com.devpilot.knowledge.rag.KnowledgeUnavailableException;
import com.devpilot.knowledge.rag.ProjectVectorStores;
import com.devpilot.knowledge.service.KnowledgeService;
import com.devpilot.project.model.ProjectResponse;
import com.devpilot.project.service.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * Imports the demo knowledge corpus at startup.
 *
 * <p>Enabled by configuration rather than by profile, because both demo paths need it: the
 * in-memory demo profile, whose database is empty on every boot, and the Docker stack on MySQL,
 * where the schema is seeded but the knowledge index is not.
 *
 * <p>Seeding is skipped when the project already has documents, so it is safe to leave on: a
 * persistent deployment imports once, and an in-memory one imports on every fresh start. The vector
 * index is reset first so a persisted index file cannot drift into holding two copies of the corpus.
 *
 * <p>Seeding must never keep the application from starting: without an embedding model the
 * knowledge base is simply reported as unavailable, and any other failure is logged and skipped.
 */
@Component
@ConditionalOnProperty(prefix = "app.knowledge", name = "seed-demo-documents", havingValue = "true")
public class DemoKnowledgeSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoKnowledgeSeeder.class);
    private static final String DEMO_PROJECT_CODE = "order-demo";
    private static final String LOCATION = "classpath:demo-knowledge/*.md";

    private final KnowledgeService knowledgeService;
    private final ProjectVectorStores vectorStores;
    private final ProjectService projectService;

    /**
     * Creates the seeder.
     *
     * @param knowledgeService knowledge application service
     * @param vectorStores per-project vector indexes, reset before seeding
     * @param projectService project lookup
     */
    public DemoKnowledgeSeeder(
            KnowledgeService knowledgeService,
            ProjectVectorStores vectorStores,
            ProjectService projectService) {
        this.knowledgeService = knowledgeService;
        this.vectorStores = vectorStores;
        this.projectService = projectService;
    }

    /** @param event application ready event, unused */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            seed();
        } catch (KnowledgeUnavailableException exception) {
            LOGGER.info("Demo knowledge base not seeded: {}", exception.getMessage());
        } catch (Exception exception) {
            LOGGER.warn("Demo knowledge base could not be seeded", exception);
        }
    }

    private void seed() throws Exception {
        Long projectId = projectService.list(0, 100).items().stream()
                .filter(project -> DEMO_PROJECT_CODE.equals(project.code()))
                .map(ProjectResponse::id)
                .findFirst()
                .orElse(null);
        if (projectId == null || !knowledgeService.list(projectId).isEmpty()) {
            return;
        }

        vectorStores.reset(projectId);
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
        Arrays.sort(resources, Comparator.comparing(resource -> resource.getFilename()));

        int imported = 0;
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            knowledgeService.importDocument(projectId, new ImportDocumentRequest(
                    titleOf(filename, content), typeOf(filename),
                    "classpath:demo-knowledge/" + filename, content));
            imported++;
        }
        LOGGER.info("Imported {} demo knowledge documents into project {}", imported, projectId);
    }

    /**
     * Derives the display title from the document's first Markdown heading, falling back to the
     * filename — the heading is what a person wrote for other people.
     *
     * @param filename resource filename
     * @param content document text
     * @return display title
     */
    private static String titleOf(String filename, String content) {
        for (String line : content.split("\n", -1)) {
            String stripped = line.strip();
            if (stripped.startsWith("#")) {
                String title = stripped.replaceFirst("^#+\\s*", "").strip();
                if (!title.isEmpty()) {
                    return title;
                }
            }
        }
        String base = filename.substring(0, filename.length() - ".md".length());
        return base.replace('-', ' ');
    }

    private static String typeOf(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.startsWith("review-")) {
            return "incident-review";
        }
        if (lower.startsWith("architecture")) {
            return "architecture";
        }
        if (lower.startsWith("api-")) {
            return "api-design";
        }
        if (lower.startsWith("standards-")) {
            return "standards";
        }
        if (lower.startsWith("error")) {
            return "error-codes";
        }
        if (lower.startsWith("testing")) {
            return "testing";
        }
        return "doc";
    }
}
