package com.railway.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 休息台就座记录：哪位客人当前正坐在哪张台上。
 * 清扫占台生效时不会清走这些记录（不清人），但清扫中的台
 * 不允许再新增就座记录（拦新坐）；客人自行离开时删除本记录。
 */
@Entity
@Table(name = "bench_sitting", uniqueConstraints =
        @UniqueConstraint(name = "uk_bench_sitting_bench", columnNames = "bench_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BenchSitting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bench_id", nullable = false)
    private RestBench bench;

    // 占台时原样记录的台编号，单据失败回滚时客人归属仍是原样
    @Column(name = "bench_code", nullable = false, length = 50)
    private String benchCode;

    @Column(name = "passenger_name", length = 100)
    private String passengerName;

    // 客人标识（落座时必填），用于区分是哪位客人；占台单失败回滚后归属仍是原样
    @Column(name = "passenger_key", nullable = false, length = 100)
    private String passengerKey;

    @Column(name = "sat_at")
    private LocalDateTime satAt;

    @PrePersist
    protected void onCreate() {
        satAt = LocalDateTime.now();
    }
}
