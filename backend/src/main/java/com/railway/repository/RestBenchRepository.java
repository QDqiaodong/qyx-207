package com.railway.repository;

import com.railway.entity.RestBench;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestBenchRepository extends JpaRepository<RestBench, Long> {

    Optional<RestBench> findByBenchCode(String benchCode);

    List<RestBench> findByRailwayLineId(Long lineId);

    List<RestBench> findByStationId(Long stationId);

    List<RestBench> findByRailwayLineIdAndStationId(Long lineId, Long stationId);

    List<RestBench> findByStatus(Integer status);

    boolean existsByBenchCode(String benchCode);
}
