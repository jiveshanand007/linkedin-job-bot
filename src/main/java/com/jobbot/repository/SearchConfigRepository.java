package com.jobbot.repository;

import com.jobbot.entity.SearchConfig;
import com.jobbot.entity.UserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SearchConfigRepository extends JpaRepository<SearchConfig, Long> {
    Optional<SearchConfig> findByUserConfig(UserConfig userConfig);
}
