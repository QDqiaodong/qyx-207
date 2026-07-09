package com.railway.service;

import com.railway.entity.RestBench;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String BENCH_MATERIAL_KEY = "bench:material";

    public void cacheBenchMaterial(RestBench bench) {
        String member = bench.getId() + ":" + bench.getBenchCode() + ":" + 
                       (bench.getMaterial() != null ? bench.getMaterial() : "");
        double score = bench.getId();
        redisTemplate.opsForZSet().add(BENCH_MATERIAL_KEY, member, score);
    }

    public void removeBenchMaterial(Long benchId) {
        Set<Object> members = redisTemplate.opsForZSet().range(BENCH_MATERIAL_KEY, 0, -1);
        if (members != null) {
            for (Object member : members) {
                String memberStr = (String) member;
                if (memberStr.startsWith(benchId + ":")) {
                    redisTemplate.opsForZSet().remove(BENCH_MATERIAL_KEY, member);
                    break;
                }
            }
        }
    }

    public Set<Object> getAllBenchMaterials() {
        return redisTemplate.opsForZSet().range(BENCH_MATERIAL_KEY, 0, -1);
    }

    public Set<Object> getBenchMaterialsByScoreRange(double minScore, double maxScore) {
        return redisTemplate.opsForZSet().rangeByScore(BENCH_MATERIAL_KEY, minScore, maxScore);
    }

    public Long getBenchCount() {
        return redisTemplate.opsForZSet().size(BENCH_MATERIAL_KEY);
    }

    public Double getBenchScore(Long benchId) {
        Set<Object> members = redisTemplate.opsForZSet().range(BENCH_MATERIAL_KEY, 0, -1);
        if (members != null) {
            for (Object member : members) {
                String memberStr = (String) member;
                if (memberStr.startsWith(benchId + ":")) {
                    return redisTemplate.opsForZSet().score(BENCH_MATERIAL_KEY, member);
                }
            }
        }
        return null;
    }
}
