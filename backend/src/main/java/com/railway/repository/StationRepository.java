package com.railway.repository;

import com.railway.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StationRepository extends JpaRepository<Station, Long> {

    Optional<Station> findByStationCode(String stationCode);

    List<Station> findByRailwayLineId(Long lineId);

    List<Station> findByRailwayLineIdAndStatus(Long lineId, Integer status);

    List<Station> findByStatus(Integer status);

    boolean existsByStationCode(String stationCode);
}
