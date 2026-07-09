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
public class StationDTO {

    private Long id;

    @NotBlank(message = "站点编码不能为空")
    @Size(max = 50, message = "站点编码长度不能超过50")
    private String stationCode;

    @NotBlank(message = "站点名称不能为空")
    @Size(max = 100, message = "站点名称长度不能超过100")
    private String stationName;

    private Integer lineOrder;

    @NotNull(message = "所属线路不能为空")
    private Long lineId;

    private Integer status = 1;
}
