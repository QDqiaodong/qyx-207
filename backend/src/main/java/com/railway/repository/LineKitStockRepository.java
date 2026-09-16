package com.railway.repository;

import com.railway.entity.LineKitStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LineKitStockRepository extends JpaRepository<LineKitStock, Long> {

    Optional<LineKitStock> findByLineId(Long lineId);

    /**
     * 对线路可用箱数台账行加悲观写锁：核增核减都在锁内进行，
     * 与箱子上的登记同一事务落库，同线两笔不会把计数写花。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM LineKitStock s WHERE s.lineId = :lineId")
    Optional<LineKitStock> findByLineIdForUpdate(@Param("lineId") Long lineId);
}
