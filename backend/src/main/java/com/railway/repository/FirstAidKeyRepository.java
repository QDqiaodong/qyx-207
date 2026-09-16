package com.railway.repository;

import com.railway.entity.FirstAidKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FirstAidKeyRepository extends JpaRepository<FirstAidKey, Long> {

    Optional<FirstAidKey> findByKeyCode(String keyCode);

    List<FirstAidKey> findAllByOrderByKeyCodeAsc();

    List<FirstAidKey> findByStationId(Long stationId);

    List<FirstAidKey> findByRailwayLineId(Long lineId);

    boolean existsByKeyCode(String keyCode);

    /**
     * 对钥匙行加悲观写锁：两人同时领同一把钥匙时在此串行，
     * 先到的落登记单并把状态字翻成在外，后到的看到已有未交还登记被拒，
     * 未交还登记只会有一条，状态字也不会写成两份在外。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM FirstAidKey k WHERE k.id = :id")
    Optional<FirstAidKey> findByIdForUpdate(@Param("id") Long id);
}
