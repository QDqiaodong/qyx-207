package com.railway.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新客人就座：清扫占台生效中的台会被拒绝落座。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BenchSitDTO {

    @Size(max = 100, message = "客人称呼长度不能超过100")
    private String passengerName;

    @NotEmpty(message = "客人标识不能为空")
    @Size(max = 100, message = "客人标识长度不能超过100")
    private String passengerKey;
}
