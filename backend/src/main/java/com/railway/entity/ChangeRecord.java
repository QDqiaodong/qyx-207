package com.railway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "change_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bench_id", nullable = false)
    private Long benchId;

    @Column(name = "bench_code", length = 50)
    private String benchCode;

    @Column(name = "change_type", length = 20)
    private String changeType;

    @Column(name = "old_line_id")
    private Long oldLineId;

    @Column(name = "old_line_name", length = 100)
    private String oldLineName;

    @Column(name = "new_line_id")
    private Long newLineId;

    @Column(name = "new_line_name", length = 100)
    private String newLineName;

    @Column(name = "old_station_id")
    private Long oldStationId;

    @Column(name = "old_station_name", length = 100)
    private String oldStationName;

    @Column(name = "new_station_id")
    private Long newStationId;

    @Column(name = "new_station_name", length = 100)
    private String newStationName;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "operator", length = 50)
    private String operator;

    // 本条是冲正单时，指向被它冲正的那条原记录；普通变更为空
    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    // 本条已被冲正时，指向冲掉它的那条冲正单；未被冲正为空
    @Column(name = "reversed_by_id")
    private Long reversedById;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
