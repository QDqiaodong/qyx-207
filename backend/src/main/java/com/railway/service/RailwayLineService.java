package com.railway.service;

import com.railway.dto.RailwayLineDTO;
import com.railway.entity.RailwayLine;
import com.railway.repository.RailwayLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RailwayLineService {

    private final RailwayLineRepository railwayLineRepository;

    public List<RailwayLine> getAllLines() {
        return railwayLineRepository.findByStatus(1);
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
        RailwayLine line = railwayLineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("线路不存在"));
        if (!line.getLineCode().equals(dto.getLineCode()) && 
            railwayLineRepository.existsByLineCode(dto.getLineCode())) {
            throw new RuntimeException("线路编码已存在");
        }
        line.setLineCode(dto.getLineCode());
        line.setLineName(dto.getLineName());
        line.setLineType(dto.getLineType());
        line.setDescription(dto.getDescription());
        line.setStatus(dto.getStatus());
        return railwayLineRepository.save(line);
    }

    @Transactional
    public void deleteLine(Long id) {
        RailwayLine line = railwayLineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("线路不存在"));
        line.setStatus(0);
        railwayLineRepository.save(line);
    }
}
