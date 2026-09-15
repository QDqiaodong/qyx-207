package com.railway.service;

import com.railway.dto.RailwayLineDTO;
import com.railway.entity.RailwayLine;
import com.railway.repository.RailwayLineRepository;
import com.railway.repository.StationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RailwayLineServiceTest {

    @Mock
    private RailwayLineRepository railwayLineRepository;
    @Mock
    private StationRepository stationRepository;
    @InjectMocks
    private RailwayLineService railwayLineService;

    private RailwayLine activeLine(Long id) {
        RailwayLine line = new RailwayLine();
        line.setId(id);
        line.setLineCode("L1");
        line.setLineName("1号线");
        line.setStatus(1);
        return line;
    }

    @Test
    void deleteLineBlockedWhenStationsRemain() {
        RailwayLine line = activeLine(1L);
        when(railwayLineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(line));
        when(stationRepository.countByRailwayLineIdAndStatusNot(1L, 0)).thenReturn(2L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> railwayLineService.deleteLine(1L));

        assertTrue(ex.getMessage().contains("不能作废"));
        // 作废被拦截：状态、名称、编码全部保持原样，不落库
        assertEquals(1, line.getStatus());
        assertEquals("1号线", line.getLineName());
        assertEquals("L1", line.getLineCode());
        verify(railwayLineRepository, never()).save(any());
    }

    @Test
    void deleteLineSucceedsWhenStationsCleared() {
        RailwayLine line = activeLine(1L);
        when(railwayLineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(line));
        when(stationRepository.countByRailwayLineIdAndStatusNot(1L, 0)).thenReturn(0L);

        railwayLineService.deleteLine(1L);

        // 只翻转状态，名称编码原样
        assertEquals(0, line.getStatus());
        assertEquals("1号线", line.getLineName());
        assertEquals("L1", line.getLineCode());
        verify(railwayLineRepository).save(line);
    }

    @Test
    void deleteLineRejectsRepeatedInvalidation() {
        RailwayLine line = activeLine(1L);
        line.setStatus(0);
        when(railwayLineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(line));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> railwayLineService.deleteLine(1L));

        assertTrue(ex.getMessage().contains("已作废"));
        verify(stationRepository, never()).countByRailwayLineIdAndStatusNot(anyLong(), anyInt());
        verify(railwayLineRepository, never()).save(any());
    }

    @Test
    void updateLineToDeletedAlsoBlockedByStations() {
        RailwayLine line = activeLine(1L);
        when(railwayLineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(line));
        when(stationRepository.countByRailwayLineIdAndStatusNot(1L, 0)).thenReturn(1L);

        RailwayLineDTO dto = new RailwayLineDTO();
        dto.setLineCode("L1");
        dto.setLineName("1号线");
        dto.setStatus(0);

        assertThrows(RuntimeException.class, () -> railwayLineService.updateLine(1L, dto));
        assertEquals(1, line.getStatus());
        verify(railwayLineRepository, never()).save(any());
    }

    @Test
    void updateLineNormalEditNotAffected() {
        RailwayLine line = activeLine(1L);
        when(railwayLineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(line));
        when(railwayLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RailwayLineDTO dto = new RailwayLineDTO();
        dto.setLineCode("L1");
        dto.setLineName("1号线（改名）");
        dto.setStatus(1);

        RailwayLine saved = railwayLineService.updateLine(1L, dto);

        assertEquals("1号线（改名）", saved.getLineName());
        assertEquals(1, saved.getStatus());
        verify(stationRepository, never()).countByRailwayLineIdAndStatusNot(anyLong(), anyInt());
    }
}
