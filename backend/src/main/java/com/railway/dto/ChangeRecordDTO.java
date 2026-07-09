package com.railway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRecordDTO {

    private Long benchId;

    private String changeType;

    private Long oldLineId;

    private Long newLineId;

    private Long oldStationId;

    private Long newStationId;

    private String changeReason;

    private String operator;
}
