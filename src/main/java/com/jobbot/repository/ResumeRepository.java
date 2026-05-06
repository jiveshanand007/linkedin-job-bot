package com.jobbot.repository;

import com.jobbot.entity.Resume;
import com.jobbot.entity.UserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    List<Resume> findByUserConfig(UserConfig config);
    List<Resume> findByUserConfigAndIsActive(UserConfig config, Boolean active);
    Optional<Resume> findFirstByUserConfigAndIsActive(UserConfig config, Boolean active);
}
