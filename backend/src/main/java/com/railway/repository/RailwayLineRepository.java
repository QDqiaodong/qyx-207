package com.railway.repository;

import com.railway.entity.RailwayLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RailwayLineRepository extends JpaRepository<RailwayLine, Long> {

    Optional<RailwayLine> findByLineCode(String lineCode);

    List<RailwayLine> findByStatus(Integer status);

    boolean existsByLineCode(String lineCode);
}
