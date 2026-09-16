package com.railway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 钥匙领用登记：必须点名具体钥匙（keyIds），记领用人与角色。
 * 值班长（SUPERVISOR）可一次领空全线；站务（STATION_STAFF）必须报自己所在站，
 * 只能领本站名下那一把。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeyCheckoutDTO {

    @NotEmpty(message = "必须点名至少一把具体钥匙")
    private List<@NotNull(message = "钥匙id不能为空") Long> keyIds;

    @NotBlank(message = "领用人不能为空")
    @Size(max = 50, message = "领用人长度不能超过50")
    private String borrower;

    @NotBlank(message = "领用人角色不能为空")
    private String role;

    // 站务领用时必传：本人所在站，只能领该站名下的钥匙
    private Long stationId;
}
