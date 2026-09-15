package com.railway.repository;

import com.railway.entity.RailwayLine;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RailwayLineRepository extends JpaRepository<RailwayLine, Long> {

    Optional<RailwayLine> findByLineCode(String lineCode);

    List<RailwayLine> findByStatus(Integer status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM RailwayLine l WHERE l.id = :id")
    Optional<RailwayLine> findByIdForUpdate(@Param("id") Long id);

    boolean existsByLineCode(String lineCode);
}
