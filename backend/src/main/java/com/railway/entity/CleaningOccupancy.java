package com.railway.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 清扫占台单：车站清扫班点名某张休息台开始清扫时落的单据。
 * 单据生效期间，该台不能再被新客人坐上；已经坐着的人不清走，
 * 可以一直坐到自行离开。同一张台同一时刻只允许一套生效占台单。
 */
@Entity
@Table(name = "cleaning_occupancy")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CleaningOccupancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bench_id", nullable = false)
    private RestBench bench;

    // 占台时把台的编号、所属站、所属线原样记进单据，单据不随后续台位变动而改口
    @Column(name = "bench_code", nullable = false, length = 50)
    private String benchCode;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "station_name", length = 100)
    private String stationName;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    @Column(name = "line_name", length = 100)
    private String lineName;

    // 占台单状态：1=清扫中（生效占台），2=已结束（清扫完成，台子放开）
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
    }
}
