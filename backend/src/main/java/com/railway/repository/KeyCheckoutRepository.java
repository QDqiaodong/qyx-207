package com.railway.repository;

import com.railway.entity.KeyCheckout;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KeyCheckoutRepository extends JpaRepository<KeyCheckout, Long> {

    // 某把钥匙当前未交还的那份登记（同一把同一时刻最多一份）
    Optional<KeyCheckout> findByKeyIdAndStatus(Long keyId, Integer status);

    List<KeyCheckout> findByStatusOrderByCheckedOutAtDesc(Integer status);

    List<KeyCheckout> findByKeyIdOrderByCheckedOutAtDesc(Long keyId);

    /**
     * 对登记单行加悲观写锁：交还与并发查询串行，
     * 不会出现登记单已交还而钥匙状态字还挂在外的对穿结果。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM KeyCheckout c WHERE c.id = :id")
    Optional<KeyCheckout> findByIdForUpdate(@Param("id") Long id);
}
