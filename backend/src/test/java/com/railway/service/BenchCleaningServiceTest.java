package com.railway.service;

import com.railway.dto.BenchSitDTO;
import com.railway.dto.CleaningStartDTO;
import com.railway.entity.BenchSitting;
import com.railway.entity.CleaningOccupancy;
import com.railway.entity.RailwayLine;
import com.railway.entity.RestBench;
import com.railway.entity.Station;
import com.railway.repository.BenchSittingRepository;
import com.railway.repository.CleaningOccupancyRepository;
import com.railway.repository.RestBenchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BenchCleaningServiceTest {

    @Mock
    private RestBenchRepository restBenchRepository;
    @Mock
    private CleaningOccupancyRepository cleaningOccupancyRepository;
    @Mock
    private BenchSittingRepository benchSittingRepository;
    @InjectMocks
    private BenchCleaningService benchCleaningService;

    private RestBench bench(long id, int status) {
        RailwayLine line = new RailwayLine();
        line.setId(7L);
        line.setLineName("1号线");
        Station station = new Station();
        station.setId(9L);
        station.setStationName("人民广场站");
        RestBench bench = new RestBench();
        bench.setId(id);
        bench.setBenchCode("B" + id);
        bench.setStatus(status);
        bench.setRailwayLine(line);
        bench.setStation(station);
        return bench;
    }

    private CleaningStartDTO startDto(Long... ids) {
        CleaningStartDTO dto = new CleaningStartDTO();
        dto.setBenchIds(List.of(ids));
        return dto;
    }

    @Test
    void startCreatesOccupancyAndNeverTouchesExistingSitter() {
        RestBench bench = bench(1L, 1);
        when(restBenchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bench));
        when(cleaningOccupancyRepository.findByBenchIdAndStatus(1L, 1))
                .thenReturn(Optional.empty());
        when(cleaningOccupancyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<CleaningOccupancy> result = benchCleaningService.startCleaning(startDto(1L));

        assertEquals(1, result.size());
        CleaningOccupancy o = result.get(0);
        assertEquals(1, o.getStatus());
        assertEquals("B1", o.getBenchCode());
        assertEquals(9L, o.getStationId());
        assertEquals("人民广场站", o.getStationName());
        assertEquals(7L, o.getLineId());
        // 关键：开清扫不动任何就座记录，不会清走已在座的人
        verify(benchSittingRepository, never()).delete(any());
        verify(benchSittingRepository, never()).save(any());
        // 台本身的编号、所属站没有被改
        assertEquals("B1", bench.getBenchCode());
        assertEquals(9L, bench.getStation().getId());
    }

    @Test
    void startRejectedWhenActiveOccupancyExists() {
        RestBench bench = bench(1L, 1);
        when(restBenchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bench));
        when(cleaningOccupancyRepository.findByBenchIdAndStatus(1L, 1))
                .thenReturn(Optional.of(new CleaningOccupancy()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> benchCleaningService.startCleaning(startDto(1L)));
        assertTrue(ex.getMessage().contains("已有生效清扫占台单"));
        verify(cleaningOccupancyRepository, never()).save(any());
        verify(benchSittingRepository, never()).delete(any());
    }

    @Test
    void startRejectsDeletedBenchAndCreatesNothing() {
        RestBench bench = bench(2L, 0);
        when(restBenchRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(bench));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> benchCleaningService.startCleaning(startDto(2L)));
        assertTrue(ex.getMessage().contains("已删除"));
        verify(cleaningOccupancyRepository, never()).save(any());
    }

    @Test
    void startDeduplicatesAndLocksInIdOrder() {
        RestBench a = bench(1L, 1);
        RestBench c = bench(3L, 1);
        when(restBenchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));
        when(restBenchRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(c));
        when(cleaningOccupancyRepository.findByBenchIdAndStatus(anyLong(), eq(1)))
                .thenReturn(Optional.empty());
        when(cleaningOccupancyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 重复点名只锁一次；乱序传入也要按 id 升序加锁
        List<CleaningOccupancy> result =
                benchCleaningService.startCleaning(startDto(3L, 1L, 3L));

        assertEquals(2, result.size());
        assertEquals("B1", result.get(0).getBenchCode());
        assertEquals("B3", result.get(1).getBenchCode());
        verify(restBenchRepository, times(1)).findByIdForUpdate(1L);
        verify(restBenchRepository, times(1)).findByIdForUpdate(3L);
    }

    @Test
    void sitBlockedWhileCleaningActive() {
        RestBench bench = bench(1L, 1);
        when(restBenchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bench));
        when(cleaningOccupancyRepository.findByBenchIdAndStatus(1L, 1))
                .thenReturn(Optional.of(new CleaningOccupancy()));

        BenchSitDTO dto = new BenchSitDTO("张三", "p1");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> benchCleaningService.sit(1L, dto));
        assertTrue(ex.getMessage().contains("清扫占台中"));
        verify(benchSittingRepository, never()).save(any());
    }

    @Test
    void sitAllowedWhenNoActiveOccupancyButSecondSitterRejected() {
        RestBench bench = bench(1L, 1);
        when(restBenchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bench));
        when(cleaningOccupancyRepository.findByBenchIdAndStatus(1L, 1))
                .thenReturn(Optional.empty());
        when(benchSittingRepository.findByBenchId(1L))
                .thenReturn(Optional.of(new BenchSitting()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> benchCleaningService.sit(1L, new BenchSitDTO("李四", "p2")));
        assertTrue(ex.getMessage().contains("已有客人在座"));
        verify(benchSittingRepository, never()).save(any());
    }

    @Test
    void leaveOnlyDeletesTheSittersOwnRecord() {
        BenchSitting sitting = new BenchSitting();
        sitting.setId(5L);
        sitting.setBenchCode("B1");
        when(benchSittingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(sitting));

        benchCleaningService.leave(5L);

        verify(benchSittingRepository).delete(sitting);
    }

    @Test
    void finishBlockedWhileSitterRemains() {
        CleaningOccupancy occupancy = new CleaningOccupancy();
        occupancy.setId(3L);
        occupancy.setStatus(1);
        occupancy.setBench(bench(1L, 1));
        occupancy.setBenchCode("B1");
        when(cleaningOccupancyRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(occupancy));
        when(benchSittingRepository.findByBenchId(1L))
                .thenReturn(Optional.of(new BenchSitting()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> benchCleaningService.finishCleaning(3L));
        assertTrue(ex.getMessage().contains("仍有客人在座"));
        assertEquals(1, occupancy.getStatus());
        verify(cleaningOccupancyRepository, never()).save(any());
    }
}
