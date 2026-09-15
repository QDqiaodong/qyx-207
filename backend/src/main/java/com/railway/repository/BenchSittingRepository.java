package com.railway.repository;

import com.railway.entity.BenchSitting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BenchSittingRepository extends JpaRepository<BenchSitting, Long> {

    // 一张台同一时刻只坐一位客人（当前坐客），清扫中也不新增
    Optional<BenchSitting> findByBenchId(Long benchId);

    List<BenchSitting> findAllByOrderBySatAtDesc();

    /**
     * 对就座记录行加悲观写锁：落座/离开与开清扫占台相互串行，
     * 不会出现占台已生效而新坐记录同时插入的对穿结果。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BenchSitting s WHERE s.id = :id")
    Optional<BenchSitting> findByIdForUpdate(@Param("id") Long id);
}
