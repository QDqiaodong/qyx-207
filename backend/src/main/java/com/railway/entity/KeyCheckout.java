package com.railway.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 钥匙领用登记单：每领走一把急救箱钥匙落一份登记，记清
 * 钥匙编号、所属站、领用人、交出时刻四要素。
 * 同一把钥匙同一时刻只允许一份未交还登记；上一份没交还，
 * 下一份开不出来，拒绝时写明上一份是谁领走的。
 * 登记单把钥匙的编号、所属站、所属线原样抄进单据留档，
 * 不随后续钥匙档案变动而改口。
 */
@Entity
@Table(name = "key_checkout")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class KeyCheckout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key_id", nullable = false)
    private FirstAidKey key;

    // 领用时把钥匙编号、所属站、所属线原样抄进单据，单据独立留档
    @Column(name = "key_code", nullable = false, length = 50)
    private String keyCode;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "station_name", length = 100)
    private String stationName;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    @Column(name = "line_name", length = 100)
    private String lineName;

    // 领用人姓名（谁把钥匙领走的）
    @Column(name = "borrower", nullable = false, length = 50)
    private String borrower;

    // 领用人角色：SUPERVISOR=值班长（可一次领空全线），STATION_STAFF=站务（只能领本站名下那把）
    @Column(name = "borrower_role", nullable = false, length = 20)
    private String borrowerRole;

    // 登记单状态：1=未交还（钥匙在外），2=已交还
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    // 交出时刻：钥匙交到领用人手里的时刻
    @Column(name = "checked_out_at")
    private LocalDateTime checkedOutAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @PrePersist
    protected void onCreate() {
        checkedOutAt = LocalDateTime.now();
    }
}
