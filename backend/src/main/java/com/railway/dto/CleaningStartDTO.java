package com.railway.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 开清扫占台：必须点名具体休息台（benchIds），按现场口径只锁本站脏台，
 * 不提供按整条线/整站一把梭的入口。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleaningStartDTO {

    @NotEmpty(message = "必须点名至少一张具体休息台")
    private List<@NotNull(message = "休息台编号不能为空") Long> benchIds;

    @Size(max = 500, message = "清扫原因长度不能超过500")
    private String reason;
}
