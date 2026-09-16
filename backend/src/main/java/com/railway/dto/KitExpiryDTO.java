package com.railway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 给急救箱写批次和到期日：批次、到期日一样都不能少。
 * 每箱只成一次——箱上已有到期日的，再写直接拒绝。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KitExpiryDTO {

    @NotBlank(message = "批次不能为空")
    @Size(max = 50, message = "批次长度不能超过50")
    private String batchNo;

    @NotNull(message = "到期日不能为空")
    private LocalDate expiryDate;
}
