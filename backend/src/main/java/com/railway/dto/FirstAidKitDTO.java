package com.railway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 急救箱建档：登记箱编号与所属站，所属线由站点带出。
 * 批次和到期日选填，但要填一起填；填了且到期日已到的，建档即撤下，
 * 不计入可用箱数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FirstAidKitDTO {

    @NotBlank(message = "箱编号不能为空")
    @Size(max = 50, message = "箱编号长度不能超过50")
    private String kitCode;

    @NotNull(message = "所属站不能为空")
    private Long stationId;

    // 选填：药品批次，与到期日一起填
    @Size(max = 50, message = "批次长度不能超过50")
    private String batchNo;

    // 选填：药品到期日，与批次一起填
    private LocalDate expiryDate;
}
