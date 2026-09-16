package com.railway.repository;

import com.railway.entity.ChangeRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChangeRecordRepository extends JpaRepository<ChangeRecord, Long> {

    List<ChangeRecord> findByBenchId(Long benchId);

    List<ChangeRecord> findByChangeType(String changeType);

    List<ChangeRecord> findByBenchIdOrderByCreatedAtDesc(Long benchId);

    List<ChangeRecord> findAllByOrderByCreatedAtDesc();

    // 冲正定位用：只取 benchId 标量，不把记录实体放进持久化上下文，
    // 避免持锁后的锁定读被一级缓存里的旧实体顶包
    @Query("SELECT r.benchId FROM ChangeRecord r WHERE r.id = :id")
    Optional<Long> findBenchIdById(@Param("id") Long id);

    // 冲正校验用：持休息台行锁后锁定读该台全部台账，与并发冲正串行，
    // 能读到对方已提交的冲正标记，按 id 倒序（id 单调，比 created_at 可靠）
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ChangeRecord r WHERE r.benchId = :benchId ORDER BY r.id DESC")
    List<ChangeRecord> findByBenchIdForUpdateOrderByIdDesc(@Param("benchId") Long benchId);
}
