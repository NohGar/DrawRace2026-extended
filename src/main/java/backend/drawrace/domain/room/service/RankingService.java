package backend.drawrace.domain.room.service;

import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String RANKING_KEY_PREFIX = "room:ranking:";

    // 유저의 점수를 업데이트 (기존 점수에 합산)
    public void updateScore(Long roomId, Long userId, double score) {
        String key = RANKING_KEY_PREFIX + roomId;

        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(userId), score);
    }

    // 해당 방의 전체 랭킹 조회 (점수 내림차순)
    public Set<ZSetOperations.TypedTuple<String>> getRankingList(Long roomId) {
        String key = RANKING_KEY_PREFIX + roomId;

        return redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);
    }

    // 게임 종료 시 해당 방의 랭킹 데이터 삭제
    public void clearRanking(Long roomId) {
        String key = RANKING_KEY_PREFIX + roomId;
        redisTemplate.delete(key);
    }
}
