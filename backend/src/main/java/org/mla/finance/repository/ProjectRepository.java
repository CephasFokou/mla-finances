package org.mla.finance.repository;

import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByStatus(ProjectStatus status);
}
