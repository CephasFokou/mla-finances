package org.mla.finance.web.dto;

import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;

/** Payload de GET /projects. */
public record ProjectSummary(Long id, String name, ProjectStatus status) {
    public static ProjectSummary from(Project p) {
        return new ProjectSummary(p.getId(), p.getName(), p.getStatus());
    }
}
