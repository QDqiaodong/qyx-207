package com.railway.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 候车厅急救箱钥匙档案：每把钥匙有唯一编号，登记所属线路与所属站。
 * 状态字：1=在库（挂在站上），2=在外（已被领用未交还），0=已注销。
 * 状态字只是台账外观，真正的凭据是钥匙领用登记单（key_checkout）：
 * 领用/交还必须单据与状态字同一事务一起落，只改状态字不落单不算数。
 */
@Entity
@Table(name = "first_aid_key")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class FirstAidKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_code", unique = true, nullable = false, length = 50)
    private String keyCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_id", nullable = false)
    private RailwayLine railwayLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    // 钥匙状态：1=在库，2=在外（领用未还），0=已注销
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
