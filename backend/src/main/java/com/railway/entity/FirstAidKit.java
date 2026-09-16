package com.railway.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 候车厅急救箱药品档案：每箱有唯一编号，登记所属线路与所属站，
 * 并按箱记下药品批次和到期日。
 * 状态字：1=可用（计入本站与线路可用箱数），0=已到期撤下（不再计入）。
 * 批次和到期日只在箱子上落一次：登记时刻一并留存，写过的箱子不能再写第二遍。
 */
@Entity
@Table(name = "first_aid_kit")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class FirstAidKit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kit_code", unique = true, nullable = false, length = 50)
    private String kitCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_id", nullable = false)
    private RailwayLine railwayLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    // 药品批次：与到期日一起登记，要填一起填
    @Column(name = "batch_no", length = 50)
    private String batchNo;

    // 药品到期日：到了这一天，箱子就要从本站与线路两处可用箱数里减掉
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    // 批次和到期日的登记时刻（谁几点几分写下的，留档可查）
    @Column(name = "expiry_recorded_at")
    private LocalDateTime expiryRecordedAt;

    // 箱子状态：1=可用，0=已到期撤下
    @Column(name = "status")
    private Integer status = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
