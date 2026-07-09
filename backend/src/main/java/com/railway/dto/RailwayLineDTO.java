package com.railway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RailwayLineDTO {

    private Long id;

    @NotBlank(message = "线路编码不能为空")
    @Size(max = 50, message = "线路编码长度不能超过50")
    private String lineCode;

    @NotBlank(message = "线路名称不能为空")
    @Size(max = 100, message = "线路名称长度不能超过100")
    private String lineName;

    private String lineType;

    private String description;

    private Integer status = 1;
}
