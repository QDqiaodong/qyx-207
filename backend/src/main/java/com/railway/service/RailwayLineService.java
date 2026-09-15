package com.railway.service;

import com.railway.dto.RailwayLineDTO;
import com.railway.entity.RailwayLine;
import com.railway.repository.RailwayLineRepository;
import com.railway.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RailwayLineService {

    // 线路状态：1=正常，0=已作废
    private static final int LINE_STATUS_DELETED = 0;
    private static final int LINE_STATUS_ACTIVE = 1;

    // 站点状态：0=已删除（正常运营、停运改造都算未删除）
    private static final int STATION_STATUS_DELETED = 0;

    private final RailwayLineRepository railwayLineRepository;
    private final StationRepository stationRepository;

    public List<RailwayLine> getAllLines() {
        return railwayLineRepository.findByStatus(LINE_STATUS_ACTIVE);
    }

    public Optional<RailwayLine> getLineById(Long id) {
        return railwayLineRepository.findById(id);
    }

    public Optional<RailwayLine> getLineByCode(String lineCode) {
        return railwayLineRepository.findByLineCode(lineCode);
    }

    @Transactional
    public RailwayLine createLine(RailwayLineDTO dto) {
        if (railwayLineRepository.existsByLineCode(dto.getLineCode())) {
            throw new RuntimeException("线路编码已存在");
        }
        RailwayLine line = new RailwayLine();
        line.setLineCode(dto.getLineCode());
        line.setLineName(dto.getLineName());
        line.setLineType(dto.getLineType());
        line.setDescription(dto.getDescription());
        line.setStatus(dto.getStatus());
        return railwayLineRepository.save(line);
    }

    @Transactional
    public RailwayLine updateLine(Long id, RailwayLineDTO dto) {
        RailwayLine line = railwayLineRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("线路不存在"));
        if (!line.getLineCode().equals(dto.getLineCode()) &&
            railwayLineRepository.existsByLineCode(dto.getLineCode())) {
            throw new RuntimeException("线路编码已存在");
        }
        // 通过档案编辑把线路置为作废，与删除走同一套口径：还有未删除站点时拒绝
        if (dto.getStatus() != null && dto.getStatus() == LINE_STATUS_DELETED
                && (line.getStatus() == null || line.getStatus() != LINE_STATUS_DELETED)) {
            assertNoRemainingStations(id);
        }
        line.setLineCode(dto.getLineCode());
        line.setLineName(dto.getLineName());
        line.setLineType(dto.getLineType());
        line.setDescription(dto.getDescription());
        line.setStatus(dto.getStatus());
        return railwayLineRepository.save(line);
    }

    /**
     * 线路作废：线路上还有未删除站点（含停运改造）时拒绝作废，站点清空后才允许。
     * 对线路行加悲观锁，两人同时作废串行成一套结果，先成功者落库，
     * 后到者看到已作废直接拒绝，不会一个成功一个把站摘空。
     * 整个作废只翻转线路状态，不摘站点、不改名称编码；
     * 任一步失败事务整体回滚，线路档案和站点挂接保持原样。
     */
    @Transactional
    public void deleteLine(Long id) {
        RailwayLine line = railwayLineRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("线路不存在"));
        if (line.getStatus() != null && line.getStatus() == LINE_STATUS_DELETED) {
            throw new RuntimeException("线路已作废，请勿重复操作");
        }
        assertNoRemainingStations(id);
        line.setStatus(LINE_STATUS_DELETED);
        railwayLineRepository.save(line);
    }

    private void assertNoRemainingStations(Long lineId) {
        long remaining = stationRepository.countByRailwayLineIdAndStatusNot(lineId, STATION_STATUS_DELETED);
        if (remaining > 0) {
            throw new RuntimeException("线路下还有 " + remaining + " 个未删除的站点，不能作废，请先清空站点");
        }
    }
}
