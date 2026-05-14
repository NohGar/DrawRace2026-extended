package backend.drawrace.domain.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import backend.drawrace.domain.chat.dto.ChatMessageDto;
import backend.drawrace.domain.round.dto.gateway.GatewayChatRequest;
import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse;
import backend.drawrace.global.config.AiProperties;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatModerationService {

    private final AiProperties aiProperties;
    private final RestClient restClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmbeddingService embeddingService;
    private final StringRedisTemplate redisTemplate;

    private final Map<Long, LastChatInfo> chatHistory = new ConcurrentHashMap<>();
    private final List<float[]> aggressiveVectors = new ArrayList<>();
    private final List<float[]> normalVectors = new ArrayList<>();

    private static final int SPAM_LIMIT_MS = 1000;
    private static final String BANNED_PATTERN = ".*(시발|병신|개새끼|패드립|느금).*";
    private static final double SIMILARITY_THRESHOLD = 0.90;

    @PostConstruct
    public void initAggressiveVectors() {
        List<String> badsamples = List.of(
                "너 진짜 지능 낮냐",
                "그림 진짜 못 그린다",
                "손가락 장애 있음?",
                "나가 죽어라",
                "뇌가 없나보네",
                "진짜 극혐이다",
                "손가락 문제 있냐",
                "가정교육 독학했냐",
                "부모님 안 계시냐",
                "부모님 홀수냐");

        List<String> normalSamples = List.of(
                "안녕하세요 반가워요", "제 이름은 입니다", "뿌우 입니다", "그림 잘 그리시네요", "반갑습니다", "어떻게 하는 건가요", "감사합니다", "고마워요", "다음에 또 봐요");

        for (String sample : badsamples) {
            aggressiveVectors.add(embeddingService.embed(sample));
        }
        for (String s : normalSamples) normalVectors.add(embeddingService.embed(s));

        log.info("공격적 문장 임베딩 데이터 {}건 로드 완료!", aggressiveVectors.size());
    }

    public String fastFilter(Long userId, String originalMessage) {

        // 차단 여부 확인
        if (isUserBanned(userId)) {
            throw new ServiceException("403-3", "부적절한 언어 사용으로 인해 3일간 채팅이 금지되었습니다.");
        }

        // 도배 체크
        checkSpam(userId, originalMessage);

        String normalized = originalMessage.replaceAll("[^가-힣ㄱ-ㅎㅏ-ㅣ]", "");

        String BANNED_PATTERN = ".*(시발|ㅅㅂ|씨발|병신|ㅂㅅ|새끼|야발|지랄|니미|느금|ㅗ|시바|ㅅ1ㅂ).*";

        // 키워드 기반 즉시 차단
        if (originalMessage.matches(BANNED_PATTERN) || normalized.matches(BANNED_PATTERN)) {
            return "****"; // 욕설 발견 시 즉시 마스킹
        }

        // 짧은 문장은 임베딩 검사 패스
        if (originalMessage.trim().length() < 3) {
            return originalMessage;
        }

        // 임베딩 유사도 체크
        float[] currentVector = embeddingService.embed(originalMessage);

        // 나쁜 문장과 얼마나 닮았는지 계산
        double maxBadSimilarity = 0;
        for (float[] bad : aggressiveVectors) {
            maxBadSimilarity = Math.max(maxBadSimilarity, embeddingService.calculateSimilarity(currentVector, bad));
        }

        // 일상 대화와 얼마나 닮았는지 계산
        double maxNormalSimilarity = 0;
        for (float[] normal : normalVectors) {
            maxNormalSimilarity =
                    Math.max(maxNormalSimilarity, embeddingService.calculateSimilarity(currentVector, normal));
        }

        if (maxBadSimilarity > SIMILARITY_THRESHOLD) {
            // 나쁜 문장과 닮았지만, 정상 문장과 더 닮았다면 일상적인 대화로 간주
            if (maxNormalSimilarity > maxBadSimilarity) {
                log.info("임베딩 검열 제외 (일상 대화와 더 유사): {}", originalMessage);
                return originalMessage;
            }

            log.info("임베딩 검열 감지 (유사도: {}): {}", maxBadSimilarity, originalMessage);
            return "[부적절한 메시지입니다]";
        }

        return originalMessage; // 깨끗하면 원문 그대로 즉시 반환
    }

    @Async("taskExecutor")
    public void processAiModeration(Long userId, Long roomId, ChatMessageDto chatMessage) {
        try {

            String aiResult = getAiDecisionStrict(chatMessage.getMessage());

            if ("1".equals(aiResult)) {
                // 부적절하다고 판단되면 메시지를 교체하고 다시 브로드캐스트
                chatMessage.setMessage("[AI 검열에 의해 삭제된 메시지입니다]");
                messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/chat", chatMessage);

                applyStrikeAndCheckBan(userId);

                log.info("AI가 부적절한 메시지 감지 및 수정 완료: {}", chatMessage.getMessage());
            }
        } catch (Exception e) {
            log.error("비동기 AI 검열 중 오류 발생: {}", e.getMessage());
        }
    }

    private void applyStrikeAndCheckBan(Long userId) {
        String strikeKey = "strike:user:" + userId;
        String banKey = "ban:user:" + userId;

        // 스트라이크 1 증가
        Long currentStrikes = redisTemplate.opsForValue().increment(strikeKey);

        // 첫 적발 시 24시간 후 스트라이크 초기화 설정
        if (currentStrikes != null && currentStrikes == 1) {
            redisTemplate.expire(strikeKey, 24, TimeUnit.HOURS);
        }

        // 3회 적발 시 3일간 차단
        if (currentStrikes != null && currentStrikes >= 3) {
            redisTemplate.opsForValue().set(banKey, "BANNED", 3, TimeUnit.DAYS);
            redisTemplate.delete(strikeKey); // 차단됐으니 스트라이크는 삭제
            log.info("유저 {}님 3회 적발로 3일간 채팅 금지", userId);
        }
    }

    private String getAiDecisionStrict(String message) {
        String systemPrompt = """
                너는 온라인 게임의 채팅 검열관이야.
                사용자의 메시지가 욕설, 비하, 따돌림, 혹은 심각한 공격성을 띄는지 판단해.
                [규칙]
                - 부적절하면 오직 '1'만 출력.
                - 적절하면 오직 '0'만 출력.
                - 다른 설명은 절대 금지.
                """;

        try {
            GatewayChatRequest request = GatewayChatRequest.builder()
                    .model(aiProperties.model())
                    .messages(List.of(
                            GatewayChatRequest.systemMessage(systemPrompt), GatewayChatRequest.userMessage(message)))
                    .temperature(0.0)
                    .build();

            GatewayChatResponse response = restClient
                    .post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .toEntity(GatewayChatResponse.class)
                    .getBody();

            return response.getChoices().get(0).getMessage().getContent().trim();
        } catch (Exception e) {
            return "0";
        }
    }

    private void checkSpam(Long userId, String message) {
        long now = System.currentTimeMillis();
        LastChatInfo last = chatHistory.get(userId);

        if (last != null) {
            if (now - last.timestamp < SPAM_LIMIT_MS) throw new ServiceException("429-1", "채팅이 너무 빠릅니다.");
            if (last.message.equals(message)) {
                last.repeatCount++;
                if (last.repeatCount > 10) {
                    throw new ServiceException("429-2", "동일한 내용의 채팅입니다.");
                }
            } else {
                last.repeatCount = 1;
            }
            last.timestamp = now;
            last.message = message;
        } else {
            chatHistory.put(userId, new LastChatInfo(message, now, 1));
        }
    }

    private boolean isUserBanned(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("ban:user:" + userId));
    }

    private static class LastChatInfo {
        String message;
        long timestamp;
        int repeatCount;

        LastChatInfo(String message, long timestamp, int repeatCount) {
            this.message = message;
            this.timestamp = timestamp;
            this.repeatCount = repeatCount;
        }
    }
    /*
    private final Map<Long, LastChatInfo> chatHistory = new ConcurrentHashMap<>();
    private static final int SPAM_LIMIT_MS = 1000;

    public String filterMessage(Long userId, String originalMessage) {
        try {
            // 도배 체크
            checkSpam(userId, originalMessage);

            // 분리된 메서드 호출
            String aiDecision = getAiDecision(originalMessage);

            // AI 판정 결과
            return aiDecision;

        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 검열 중 오류 발생: {}", e.getMessage());
            return originalMessage;
        }
    }

    protected String getAiDecision(String message) {
        RestClient restClient = RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + aiProperties.apiKey())
                .build();

        // 프롬프트
        String systemPrompt = """
                [기본 원칙]
                당신은 유저가 그린 그림을 AI가 판별하는 게임 'DrawRace'의 커뮤니티 관리자입니다. 유저들은 AI의 판별 결과에 따라 순위 경쟁을 하고 있습니다. 아래 기준에 따라 부적절한 메시지의 해당 부분만 ****로 치환하세요.

                [상세 검열 기준]
                1. 비속어 및 공격적 언어 (Profanity & Aggression)

                - 직접적인 욕설, 패드립, 타인에 대한 비하 발언.

                - 변형된 욕설 및 초성 욕설 (예: 'ㅅㅂ', 'ㄲㅈ' 등).

                2. 유저 간 비난 및 도발 (Competitive Toxicity)

                - 레이스 중 다른 참가자의 그림 실력을 심하게 비하하거나 조롱하여 불쾌감을 주는 행위.

                - (예: "와 님 손은 장식임? 진짜 드럽게 못 그리네" -> "와 님 손은 장식임? 진짜 **** 그리네")

                3. 성적 콘텐츠 및 혐오 표현 (Sexual & Hate Speech)

                - 음란한 표현, 성희롱, 특정 집단(성별, 지역 등)에 대한 혐오 발언.

                4. 도배 및 스팸 (Spamming)

                - 의미 없는 문자의 반복, 홍보성 문구 등 게임 진행을 방해하는 채팅.

                [출력 규칙]
                - 필터링된 결과값만 출력 (설명이나 인사말 절대 금지).

                - 금지 단어에 해당하는 부분만 정확히 ****로 치환.

                - 해당하는 위반 사항이 전혀 없으면 유저의 원문을 토씨 하나 안 틀리고 그대로 출력.
            """;

        GatewayChatRequest request = GatewayChatRequest.builder()
                .model(aiProperties.model())
                .messages(List.of(
                        GatewayChatRequest.systemMessage(systemPrompt), GatewayChatRequest.userMessage(message)))
                .temperature(0.0)
                .build();

        GatewayChatResponse response = restClient
                .post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .toEntity(GatewayChatResponse.class)
                .getBody();

        return response.getChoices().get(0).getMessage().getContent().trim();
    }

    private void checkSpam(Long userId, String message) {
        long now = System.currentTimeMillis();
        LastChatInfo last = chatHistory.get(userId);

        if (last != null) {
            if (now - last.timestamp < SPAM_LIMIT_MS) throw new ServiceException("429-1", "채팅이 너무 빠릅니다.");
            if (last.message.equals(message)) throw new ServiceException("429-2", "동일한 메시지 반복 금지.");
        }
        chatHistory.put(userId, new LastChatInfo(message, now));
    }

    private record LastChatInfo(String message, long timestamp) {}

     */
}
