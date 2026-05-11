package backend.drawrace.domain.room.service;

import static org.assertj.core.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ZSetOperations;

import backend.drawrace.global.config.TestRedisConfig;

@SpringBootTest
@Import(TestRedisConfig.class)
class RankingServiceTest {

    @Autowired
    private RankingService rankingService;

    @AfterEach
    void tearDown() {
        // 테스트가 끝나면 1번 방 데이터를 지워줌
        rankingService.clearRanking(1L);
    }

    @Test
    @DisplayName("레디스에서 점수가 누적되고 내림차순으로 정렬되는지 확인한다")
    void redis_integration_test() {
        // given
        Long roomId = 1L;

        // 점수 업데이트
        rankingService.updateScore(roomId, 101L, 10.0);
        rankingService.updateScore(roomId, 101L, 20.0); // 101번 유저는 총 30점
        rankingService.updateScore(roomId, 102L, 50.0); // 102번 유저는 50점 (1등)

        // 랭킹 조회
        Set<ZSetOperations.TypedTuple<String>> rankingList = rankingService.getRankingList(roomId);

        // 검증
        assertThat(rankingList).hasSize(2);

        // 첫 번째 요소(1등)가 102번 유저인지 확인
        ZSetOperations.TypedTuple<String> first = rankingList.iterator().next();
        assertThat(first.getValue()).isEqualTo("102");
        assertThat(first.getScore()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("방 정산 완료 후 Redis 데이터가 실제로 삭제되는지 확인한다")
    void clearRanking_Integration_Test() {
        Long roomId = 1L;
        rankingService.updateScore(roomId, 10L, 100.0);

        rankingService.clearRanking(roomId);

        Set<ZSetOperations.TypedTuple<String>> rankingList = rankingService.getRankingList(roomId);
        assertThat(rankingList).isEmpty(); // 데이터가 삭제되어 비어있어야 함
    }
}
