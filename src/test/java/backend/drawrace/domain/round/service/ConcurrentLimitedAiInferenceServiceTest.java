package backend.drawrace.domain.round.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import backend.drawrace.domain.round.dto.AiInferenceResponse;
import backend.drawrace.global.exception.ServiceException;

class ConcurrentLimitedAiInferenceServiceTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(8);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void infer_respectsMaxConcurrent() throws Exception {
        AtomicInteger concurrent = new AtomicInteger(0);
        AtomicInteger maxSeen = new AtomicInteger(0);
        CountDownLatch insideInfer = new CountDownLatch(2);
        CountDownLatch releaseGate = new CountDownLatch(1);

        AiInferenceService delegate = Mockito.mock(AiInferenceService.class);
        Mockito.when(delegate.infer(Mockito.anyString(), Mockito.anyString())).thenAnswer(invocation -> {
            int c = concurrent.incrementAndGet();
            maxSeen.updateAndGet(m -> Math.max(m, c));
            insideInfer.countDown();
            try {
                releaseGate.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                concurrent.decrementAndGet();
            }
            return new AiInferenceResponse("ok", 0.5);
        });

        ConcurrentLimitedAiInferenceService limited = new ConcurrentLimitedAiInferenceService(delegate, 2, 5);

        Future<AiInferenceResponse> f1 = executor.submit(() -> limited.infer("img", "k"));
        Future<AiInferenceResponse> f2 = executor.submit(() -> limited.infer("img", "k"));
        Future<AiInferenceResponse> f3 = executor.submit(() -> limited.infer("img", "k"));

        assertThat(insideInfer.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(maxSeen.get()).isLessThanOrEqualTo(2);

        releaseGate.countDown();

        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        f3.get(5, TimeUnit.SECONDS);

        verify(delegate, times(3)).infer(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void infer_timesOutWhenSlotsNeverFree() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);

        AiInferenceService delegate = Mockito.mock(AiInferenceService.class);
        Mockito.when(delegate.infer(Mockito.anyString(), Mockito.anyString())).thenAnswer(invocation -> {
            hold.await(30, TimeUnit.SECONDS);
            return new AiInferenceResponse("ok", 0.5);
        });

        ConcurrentLimitedAiInferenceService limited = new ConcurrentLimitedAiInferenceService(delegate, 1, 1);

        Future<?> blocker = executor.submit(() -> limited.infer("img", "k"));
        Thread.sleep(100);

        assertThatThrownBy(() -> limited.infer("img", "k"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "503-1");

        hold.countDown();
        blocker.get(5, TimeUnit.SECONDS);
    }

    @Test
    void infer_invalidMaxConcurrent_throws() {
        AiInferenceService delegate = Mockito.mock(AiInferenceService.class);
        assertThatThrownBy(() -> new ConcurrentLimitedAiInferenceService(delegate, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
