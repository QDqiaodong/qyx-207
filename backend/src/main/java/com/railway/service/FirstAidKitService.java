package com.railway.service;

import com.railway.dto.FirstAidKitDTO;
import com.railway.dto.KitExpiryDTO;
import com.railway.entity.FirstAidKit;
import com.railway.entity.LineKitStock;
import com.railway.entity.RailwayLine;
import com.railway.entity.Station;
import com.railway.entity.StationKitStock;
import com.railway.repository.FirstAidKitRepository;
import com.railway.repository.LineKitStockRepository;
import com.railway.repository.StationKitStockRepository;
import com.railway.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 急救箱药品到期口径（按现场要求实现）：
 * 1. 按箱记下批次和到期日：每箱一档，批次、到期日、登记时刻都落在箱子上；
 * 2. 到期日到了，本站可用箱数和按线路加起来的可用箱数同一事务各减一，
 *    只减这一箱，别站、别线的计数不动；
 * 3. 还没到期的箱不能从可用里拿掉：写上将来日期，箱子照算可用，两处计数不动；
 * 4. 两名站务抢着给同一箱写到期日：对箱子行加悲观写锁串行，只成一次——
 *    先到的落批次和到期日，后到的看到箱上已有到期日被拒，计数不会减两遍；
 * 5. 写到一半中断整体回滚：箱子、本站台账、线路台账任何一步失败，
 *    两处可用箱数仍是动手前的数，箱子上也不留半截日期；
 * 6. 只改箱子不算做完：到期撤下时箱子状态字、本站台账、线路台账三处
 *    同一事务一起落，只在箱子上改个日期、线路侧箱数不动的不算完成。
 */
@Service
@RequiredArgsConstructor
public class FirstAidKitService {

    // 箱子状态：1=可用，0=已到期撤下
    private static final int KIT_AVAILABLE = 1;
    private static final int KIT_WITHDRAWN = 0;

    private final FirstAidKitRepository firstAidKitRepository;
    private final StationKitStockRepository stationKitStockRepository;
    private final LineKitStockRepository lineKitStockRepository;
    private final StationRepository stationRepository;

    /**
     * 急救箱建档：登记箱编号与所属站，所属线由站点带出。批次和到期日选填，
     * 要填一起填；填了且到期日已到的，建档即撤下、不计入可用箱数——
     * 到期的药品不许再按能用计。可用的新箱上架，本站与线路两处可用箱数
     * 同一事务各加一。
     */
    @Transactional
    public FirstAidKit registerKit(FirstAidKitDTO dto) {
        if (firstAidKitRepository.existsByKitCode(dto.getKitCode())) {
            throw new RuntimeException("箱编号「" + dto.getKitCode() + "」已存在");
        }
        Station station = stationRepository.findById(dto.getStationId())
                .orElseThrow(() -> new RuntimeException("所属站点不存在: id=" + dto.getStationId()));
        boolean hasBatch = StringUtils.hasText(dto.getBatchNo());
        boolean hasExpiry = dto.getExpiryDate() != null;
        if (hasBatch != hasExpiry) {
            throw new RuntimeException("批次和到期日要填一起填，不能只填一样");
        }

        FirstAidKit kit = new FirstAidKit();
        kit.setKitCode(dto.getKitCode().trim());
        kit.setStation(station);
        kit.setRailwayLine(station.getRailwayLine());
        if (hasExpiry) {
            kit.setBatchNo(dto.getBatchNo().trim());
            kit.setExpiryDate(dto.getExpiryDate());
            kit.setExpiryRecordedAt(LocalDateTime.now());
            // 到期日已到的建档即撤下，不进可用箱数
            kit.setStatus(isDue(dto.getExpiryDate()) ? KIT_WITHDRAWN : KIT_AVAILABLE);
        } else {
            kit.setStatus(KIT_AVAILABLE);
        }
        FirstAidKit saved = firstAidKitRepository.save(kit);
        if (saved.getStatus() == KIT_AVAILABLE) {
            bumpStationStock(station, 1);
            bumpLineStock(station.getRailwayLine(), 1);
        }
        return saved;
    }

    /**
     * 给急救箱写批次和到期日：每箱只成一次。先对箱子行加悲观写锁，
     * 持锁期间确认箱上还没有到期日才落笔——两人抢写同一箱，先到的落笔，
     * 后到的看到已有到期日被拒。写上的到期日已经到了的，箱子当场撤下，
     * 本站与线路两处可用箱数同一事务各减一；还没到期的，箱子照算可用，
     * 两处计数一根指头都不动。任何一步失败整体回滚，两处可用箱数仍是
     * 动手前的数。
     */
    @Transactional
    public FirstAidKit writeExpiry(Long kitId, KitExpiryDTO dto) {
        if (!StringUtils.hasText(dto.getBatchNo())) {
            throw new RuntimeException("批次不能为空");
        }
        if (dto.getExpiryDate() == null) {
            throw new RuntimeException("到期日不能为空");
        }
        FirstAidKit kit = firstAidKitRepository.findByIdForUpdate(kitId)
                .orElseThrow(() -> new RuntimeException("急救箱不存在: id=" + kitId));
        if (kit.getExpiryDate() != null) {
            throw new RuntimeException("急救箱「" + kit.getKitCode() + "」已登记批次和到期日（批次「"
                    + kit.getBatchNo() + "」，到期日 " + kit.getExpiryDate() + "），不能重复登记");
        }

        kit.setBatchNo(dto.getBatchNo().trim());
        kit.setExpiryDate(dto.getExpiryDate());
        kit.setExpiryRecordedAt(LocalDateTime.now());
        if (isDue(dto.getExpiryDate())) {
            // 到期日到了：箱子撤下，两处可用箱数同一事务一起减这一箱
            kit.setStatus(KIT_WITHDRAWN);
            bumpStationStock(kit.getStation(), -1);
            bumpLineStock(kit.getRailwayLine(), -1);
        }
        // 还没到期的：只落批次和到期日，箱子仍在可用里，两处计数不动
        return firstAidKitRepository.save(kit);
    }

    public List<FirstAidKit> getAllKits() {
        return firstAidKitRepository.findAllByOrderByKitCodeAsc();
    }

    /**
     * 两处可用箱数一起端出来：本站台账逐站一行，线路台账逐线一行，
     * 现场可对账——每条线的可用箱数应等于它各站之和。
     */
    public Map<String, Object> getStocks() {
        Map<String, Object> stocks = new LinkedHashMap<>();
        stocks.put("stations", stationKitStockRepository.findAll(Sort.by("stationId")));
        stocks.put("lines", lineKitStockRepository.findAll(Sort.by("lineId")));
        return stocks;
    }

    /** 到期日到了没有：今天或更早都算到了 */
    private boolean isDue(LocalDate expiryDate) {
        return !expiryDate.isAfter(LocalDate.now());
    }

    /**
     * 本站可用箱数核增/核减：持悲观写锁改台账行。首只上架的站当场建行；
     * 核减时台账行缺失或已是 0，说明账对不上，直接抛错让整笔回滚——
     * 两处可用箱数仍是动手前的数。
     */
    private void bumpStationStock(Station station, int delta) {
        Optional<StationKitStock> found = stationKitStockRepository.findByStationIdForUpdate(station.getId());
        if (found.isEmpty()) {
            if (delta < 0) {
                throw new RuntimeException("站点「" + station.getStationName()
                        + "」的可用箱数台账缺失，无法核减，本次登记整体回滚");
            }
            StationKitStock stock = new StationKitStock();
            stock.setStationId(station.getId());
            stock.setStationName(station.getStationName());
            stock.setLineId(station.getRailwayLine().getId());
            stock.setLineName(station.getRailwayLine().getLineName());
            stock.setAvailableCount(0);
            found = Optional.of(stationKitStockRepository.save(stock));
        }
        StationKitStock stock = found.get();
        int next = stock.getAvailableCount() + delta;
        if (next < 0) {
            throw new RuntimeException("站点「" + station.getStationName() + "」可用箱数已为 0，不能再减");
        }
        stock.setAvailableCount(next);
        stationKitStockRepository.save(stock);
    }

    /**
     * 线路可用箱数核增/核减：与本站台账同一把锁口径，缺失或为 0 还核减
     * 一样抛错回滚——只动箱子不动线路侧，不算做完。
     */
    private void bumpLineStock(RailwayLine line, int delta) {
        Optional<LineKitStock> found = lineKitStockRepository.findByLineIdForUpdate(line.getId());
        if (found.isEmpty()) {
            if (delta < 0) {
                throw new RuntimeException("线路「" + line.getLineName()
                        + "」的可用箱数台账缺失，无法核减，本次登记整体回滚");
            }
            LineKitStock stock = new LineKitStock();
            stock.setLineId(line.getId());
            stock.setLineName(line.getLineName());
            stock.setAvailableCount(0);
            found = Optional.of(lineKitStockRepository.save(stock));
        }
        LineKitStock stock = found.get();
        int next = stock.getAvailableCount() + delta;
        if (next < 0) {
            throw new RuntimeException("线路「" + line.getLineName() + "」可用箱数已为 0，不能再减");
        }
        stock.setAvailableCount(next);
        lineKitStockRepository.save(stock);
    }
}
