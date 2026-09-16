package com.railway.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 本站可用箱数台账：每个站一行，availableCount 就是"本站可用箱数"。
 * 站名、线名原样抄进台账留档。箱子建档上架、到期撤下都会在同一事务里
 * 改动这一行——只动箱子不动这本账，不算做完。
 */
@Entity
@Table(name = "station_kit_stock")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class StationKitStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", unique = true, nullable = false)
    private Long stationId;

    @Column(name = "station_name", nullable = false, length = 100)
    private String stationName;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    @Column(name = "line_name", nullable = false, length = 100)
    private String lineName;

    // 本站可用箱数：只数状态字=可用的箱，已到期撤下的不计
    @Column(name = "available_count", nullable = false)
    private Integer availableCount = 0;

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
