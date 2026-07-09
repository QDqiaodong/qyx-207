package com.railway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestBenchDTO {

    private Long id;

    @NotBlank(message = "休息台编号不能为空")
    @Size(max = 50, message = "休息台编号长度不能超过50")
    private String benchCode;

    private String material;

    private String specification;

    private String positionDesc;

    @NotNull(message = "所属线路不能为空")
    private Long lineId;

    @NotNull(message = "所属站点不能为空")
    private Long stationId;

    private Integer status = 1;
}
