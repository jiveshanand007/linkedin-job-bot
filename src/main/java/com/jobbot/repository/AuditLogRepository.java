package com.jobbot.repository;

import com.jobbot.entity.AuditLog;
import com.jobbot.entity.UserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUserConfigOrderByTimestampDesc(UserConfig config);
}
