package com.railway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 急救箱钥匙建档：登记钥匙编号与所属站，所属线由站点带出。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FirstAidKeyDTO {

    private Long id;

    @NotBlank(message = "钥匙编号不能为空")
    @Size(max = 50, message = "钥匙编号长度不能超过50")
    private String keyCode;

    @NotNull(message = "所属站点不能为空")
    private Long stationId;
}
