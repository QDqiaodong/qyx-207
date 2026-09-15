package com.railway.service;

import com.railway.entity.RailwayLine;
import com.railway.entity.RestBench;
import com.railway.repository.ChangeRecordRepository;
import com.railway.repository.RailwayLineRepository;
import com.railway.repository.RestBenchRepository;
import com.railway.repository.StationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestBenchServiceTest {

    @Mock
    private RestBenchRepository restBenchRepository;
    @Mock
    private RailwayLineRepository railwayLineRepository;
    @Mock
    private StationRepository stationRepository;
    @Mock
    private ChangeRecordRepository changeRecordRepository;
    @Mock
    private RedisCacheService redisCacheService;
    @InjectMocks
    private RestBenchService restBenchService;

    private RailwayLine lineWithStatus(int status) {
        RailwayLine line = new RailwayLine();
        line.setId(1L);
        line.setStatus(status);
        return line;
    }

    @Test
    void benchesByLineEmptyAfterLineInvalidated() {
        when(railwayLineRepository.findById(1L)).thenReturn(Optional.of(lineWithStatus(0)));

        List<RestBench> benches = restBenchService.getBenchesByLineId(1L);

        assertTrue(benches.isEmpty());
        verify(restBenchRepository, never()).findByRailwayLineIdAndStatus(anyLong(), anyInt());
    }

    @Test
    void benchesByLineEmptyWhenLineMissing() {
        when(railwayLineRepository.findById(99L)).thenReturn(Optional.empty());

        assertTrue(restBenchService.getBenchesByLineId(99L).isEmpty());
    }

    @Test
    void benchesByLineNormalWhenLineActive() {
        when(railwayLineRepository.findById(1L)).thenReturn(Optional.of(lineWithStatus(1)));
        when(restBenchRepository.findByRailwayLineIdAndStatus(1L, 1))
                .thenReturn(List.of(new RestBench()));

        assertEquals(1, restBenchService.getBenchesByLineId(1L).size());
    }
}
