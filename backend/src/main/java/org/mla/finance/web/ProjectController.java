package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.service.ProjectService;
import org.mla.finance.web.dto.ProjectCreateRequest;
import org.mla.finance.web.dto.ProjectOccurrenceRequest;
import org.mla.finance.web.dto.ProjectResponse;
import org.mla.finance.web.dto.ProjectUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * Gestion des projets/enveloppes (module "Projets & catégories"). Remplace l'ancienne liste
 * en lecture seule de ReferenceDataController : GET /api/v1/projects renvoie désormais le détail
 * complet (ProjectResponse au lieu de ProjectSummary), ce qui reste compatible avec les listes
 * déroulantes du formulaire de contribution qui ne lisent que id/name/status.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public List<ProjectResponse> list(@RequestParam(required = false) ProjectStatus status) {
        return projectService.list(status);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return projectService.get(id);
    }

    @GetMapping("/series/{seriesId}")
    public List<ProjectResponse> occurrences(@PathVariable String seriesId) {
        return projectService.occurrencesOfSeries(seriesId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_PROJECTS')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request) {
        ProjectResponse created = projectService.create(request);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
            .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_PROJECTS')")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectUpdateRequest request) {
        return projectService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('MANAGE_PROJECTS')")
    public ProjectResponse activate(@PathVariable Long id) {
        return projectService.activate(id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('MANAGE_PROJECTS')")
    public ProjectResponse complete(@PathVariable Long id) {
        return projectService.complete(id);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('MANAGE_PROJECTS')")
    public ProjectResponse archive(@PathVariable Long id) {
        return projectService.archive(id);
    }

    @PostMapping("/{id}/duplicate-occurrence")
    @PreAuthorize("hasAuthority('MANAGE_PROJECTS')")
    public ResponseEntity<ProjectResponse> duplicateOccurrence(
        @PathVariable Long id, @Valid @RequestBody ProjectOccurrenceRequest request
    ) {
        ProjectResponse created = projectService.createOccurrence(id, request);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/projects/{id}").buildAndExpand(created.id()).toUri())
            .body(created);
    }
}
