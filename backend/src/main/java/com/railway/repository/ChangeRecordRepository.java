package com.railway.repository;

import com.railway.entity.ChangeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChangeRecordRepository extends JpaRepository<ChangeRecord, Long> {

    List<ChangeRecord> findByBenchId(Long benchId);

    List<ChangeRecord> findByChangeType(String changeType);

    List<ChangeRecord> findByBenchIdOrderByCreatedAtDesc(Long benchId);

    List<ChangeRecord> findAllByOrderByCreatedAtDesc();
}
