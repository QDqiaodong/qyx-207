package com.railway.repository;

import com.railway.entity.CleaningOccupancy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CleaningOccupancyRepository extends JpaRepository<CleaningOccupancy, Long> {

    // 一张台同一时刻最多一套生效（清扫中）占台单
    Optional<CleaningOccupancy> findByBenchIdAndStatus(Long benchId, Integer status);

    List<CleaningOccupancy> findByStatusOrderByStartedAtDesc(Integer status);

    List<CleaningOccupancy> findByBenchIdOrderByStartedAtDesc(Long benchId);

    /**
     * 对占台单行加悲观写锁：两人同时给同一张台开清扫时在此串行，
     * 先到的落单，后到的等锁释放后看到已有生效单而被拒绝，只留一套占台。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CleaningOccupancy o WHERE o.id = :id")
    Optional<CleaningOccupancy> findByIdForUpdate(@Param("id") Long id);
}
