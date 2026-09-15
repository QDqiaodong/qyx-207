package com.railway.repository;

import com.railway.entity.Station;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StationRepository extends JpaRepository<Station, Long> {

    Optional<Station> findByStationCode(String stationCode);

    List<Station> findByRailwayLineId(Long lineId);

    List<Station> findByRailwayLineIdAndStatus(Long lineId, Integer status);

    List<Station> findByStatus(Integer status);

    List<Station> findByStatusNot(Integer status);

    List<Station> findByRailwayLineIdAndStatusNot(Long lineId, Integer status);

    long countByRailwayLineIdAndStatusNot(Long lineId, Integer status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Station s WHERE s.id = :id")
    Optional<Station> findByIdForUpdate(@Param("id") Long id);

    boolean existsByStationCode(String stationCode);
}
