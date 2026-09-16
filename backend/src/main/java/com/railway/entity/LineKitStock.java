package com.railway.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 线路可用箱数台账：每条线一行，availableCount 就是"按线路加起来的可用箱数"，
 * 应当时刻等于该线各站可用箱数之和。箱子建档上架、到期撤下都会在同一事务里
 * 改动这一行——只在箱子上改个日期、这本账不动，不算做完。
 */
@Entity
@Table(name = "line_kit_stock")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class LineKitStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_id", unique = true, nullable = false)
    private Long lineId;

    @Column(name = "line_name", nullable = false, length = 100)
    private String lineName;

    // 按线路加起来的可用箱数：该线各站可用箱数之和
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
