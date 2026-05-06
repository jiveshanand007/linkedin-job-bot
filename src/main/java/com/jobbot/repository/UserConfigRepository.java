package com.jobbot.repository;

import com.jobbot.entity.UserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserConfigRepository extends JpaRepository<UserConfig, Long> {
    Optional<UserConfig> findByEmail(String email);
    List<UserConfig> findBySchedulerActiveTrue();
}
