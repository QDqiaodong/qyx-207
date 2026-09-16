package com.railway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRecordReverseDTO {

    // 冲正原因，可空；空时后端落默认文案
    private String reason;
}
