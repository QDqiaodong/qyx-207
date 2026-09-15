package com.railway.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BenchTransferDTO {

    @NotNull(message = "目标线路不能为空")
    private Long lineId;

    @NotNull(message = "目标站点不能为空")
    private Long stationId;

    @Size(max = 500, message = "转运原因长度不能超过500")
    private String reason;
}
