package com.railway.service;

import com.railway.dto.StationDTO;
import com.railway.entity.RailwayLine;
import com.railway.entity.Station;
import com.railway.repository.RailwayLineRepository;
import com.railway.repository.RestBenchRepository;
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
class StationServiceTest {

    @Mock
    private StationRepository stationRepository;
    @Mock
    private RailwayLineRepository railwayLineRepository;
    @Mock
    private RestBenchRepository restBenchRepository;
    @InjectMocks
    private StationService stationService;

    private StationDTO newStationDto(Long lineId) {
        StationDTO dto = new StationDTO();
        dto.setStationCode("S001");
        dto.setStationName("人民广场站");
        dto.setLineId(lineId);
        dto.setStatus(1);
        return dto;
    }

    @Test
    void createStationRejectsInvalidatedLine() {
        RailwayLine line = new RailwayLine();
        line.setId(1L);
        line.setStatus(0);
        when(stationRepository.existsByStationCode("S001")).thenReturn(false);
        when(railwayLineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(line));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> stationService.createStation(newStationDto(1L)));

        assertTrue(ex.getMessage().contains("已作废"));
        verify(stationRepository, never()).save(any());
    }

    @Test
    void createStationOnActiveLineWorks() {
        RailwayLine line = new RailwayLine();
        line.setId(1L);
        line.setStatus(1);
        when(stationRepository.existsByStationCode("S001")).thenReturn(false);
        when(railwayLineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(line));
        when(stationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Station saved = stationService.createStation(newStationDto(1L));

        assertEquals("S001", saved.getStationCode());
        assertEquals(1L, saved.getRailwayLine().getId());
    }

    @Test
    void updateStationRejectsReattachToInvalidatedLine() {
        Station station = new Station();
        station.setId(5L);
        station.setStationCode("S001");
        station.setStatus(1);
        RailwayLine oldLine = new RailwayLine();
        oldLine.setId(1L);
        oldLine.setStatus(1);
        station.setRailwayLine(oldLine);

        RailwayLine deadLine = new RailwayLine();
        deadLine.setId(2L);
        deadLine.setStatus(0);

        when(stationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(station));
        when(railwayLineRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(deadLine));

        StationDTO dto = newStationDto(2L);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> stationService.updateStation(5L, dto));

        assertTrue(ex.getMessage().contains("已作废"));
        // 站点仍挂在原线，未被半截改挂
        assertEquals(1L, station.getRailwayLine().getId());
        verify(stationRepository, never()).save(any());
    }
}
