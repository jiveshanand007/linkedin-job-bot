package com.jobbot.repository;

import com.jobbot.entity.Job;
import com.jobbot.entity.UserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    Optional<Job> findByUserConfigAndLinkedInJobId(UserConfig config, String linkedInJobId);
    List<Job> findByUserConfig(UserConfig config);
}
