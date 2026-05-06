package com.jobbot.repository;

import com.jobbot.entity.Job;
import com.jobbot.entity.UserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface JobRepository extends JpaRepository<Job, Long> {
    Optional<Job> findByUserConfigAndLinkedInJobId(UserConfig config, String linkedInJobId);
    List<Job> findByUserConfig(UserConfig config);

    @Query("SELECT j.linkedInJobId FROM Job j WHERE j.userConfig = :userConfig")
    Set<String> findLinkedInJobIdsByUserConfig(@Param("userConfig") UserConfig userConfig);
}
