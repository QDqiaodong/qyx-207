package com.railway.repository;

import com.railway.entity.FirstAidKit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FirstAidKitRepository extends JpaRepository<FirstAidKit, Long> {

    List<FirstAidKit> findAllByOrderByKitCodeAsc();

    List<FirstAidKit> findByStationId(Long stationId);

    List<FirstAidKit> findByRailwayLineId(Long lineId);

    boolean existsByKitCode(String kitCode);

    /**
     * 对箱子行加悲观写锁：两名站务同时给同一箱写到期日时在此串行，
     * 先到的落批次和到期日（到期的连同两处可用箱数一起核减），
     * 后到的看到箱上已有到期日被拒——同一箱只成一次，不会减两遍。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM FirstAidKit k WHERE k.id = :id")
    Optional<FirstAidKit> findByIdForUpdate(@Param("id") Long id);
}
