package backend.drawrace.domain.round.service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import backend.drawrace.domain.round.dto.AiInferenceResponse;
import backend.drawrace.global.exception.ServiceException;

import lombok.extern.slf4j.Slf4j;

/**
 * 외부 AI 게이트웨이 동시 호출 수를 제한한다. 동시 제출이 몰려도 호출 폭주를 막는다.
 */
@Slf4j
public class ConcurrentLimitedAiInferenceService implements AiInferenceService {

    private final AiInferenceService delegate;
    private final Semaphore semaphore;
    private final long acquireTimeoutSeconds;

    public ConcurrentLimitedAiInferenceService(
            AiInferenceService delegate, int maxConcurrent, long acquireTimeoutSeconds) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent must be >= 1");
        }
        this.delegate = delegate;
        this.semaphore = new Semaphore(maxConcurrent, true);
        this.acquireTimeoutSeconds = acquireTimeoutSeconds;
    }

    @Override
    public AiInferenceResponse infer(String imageData, String keyword) {
        boolean acquired = false;
        try {
            if (acquireTimeoutSeconds <= 0) {
                semaphore.acquireUninterruptibly();
            } else if (!semaphore.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn(
                        "AI 판별 세마포어 대기 시간 초과. keyword={}, timeoutSec={}",
                        keyword,
                        acquireTimeoutSeconds);
                throw new ServiceException(
                        "503-1", "AI 판별 대기 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.");
            }
            acquired = true;
            return delegate.infer(imageData, keyword);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("500-1", "AI 판별에 실패했습니다. 다시 시도해주세요.");
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }
}
