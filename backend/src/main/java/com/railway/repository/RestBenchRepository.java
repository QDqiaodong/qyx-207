package com.railway.repository;

import com.railway.entity.RestBench;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestBenchRepository extends JpaRepository<RestBench, Long> {

    Optional<RestBench> findByBenchCode(String benchCode);

    List<RestBench> findByRailwayLineId(Long lineId);

    List<RestBench> findByRailwayLineIdAndStatus(Long lineId, Integer status);

    List<RestBench> findByStationId(Long stationId);

    List<RestBench> findByStationIdAndStatus(Long stationId, Integer status);

    List<RestBench> findByRailwayLineIdAndStationId(Long lineId, Long stationId);

    List<RestBench> findByStatus(Integer status);

    List<RestBench> findByStatusNot(Integer status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM RestBench b WHERE b.id = :id")
    Optional<RestBench> findByIdForUpdate(@Param("id") Long id);

    boolean existsByBenchCode(String benchCode);
}
