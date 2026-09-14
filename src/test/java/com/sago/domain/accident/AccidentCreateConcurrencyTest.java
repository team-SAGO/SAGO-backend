package com.sago.domain.accident;

import com.sago.domain.accident.dto.AccidentCreateRequest;
import com.sago.domain.accident.dto.AccidentCreation;
import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 버튼을 여러 번 눌러 요청이 거의 동시에 도착해도 사고가 하나만 만들어지는지 확인한다 (#34).
 *
 * 요청마다 각자의 트랜잭션이 필요해 테스트 트랜잭션으로 감싸지 않는다. 만든 데이터는 끝나고 지운다.
 */
@SpringBootTest
class AccidentCreateConcurrencyTest {

    private static final int CONCURRENT_REQUESTS = 8;

    @Autowired
    private AccidentService accidentService;

    @Autowired
    private AccidentRepository accidentRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
            .email("concurrent-" + UUID.randomUUID() + "@example.com")
            .nickname("라이더")
            .build());
    }

    @AfterEach
    void tearDown() {
        accidentRepository.deleteAll(accidentRepository.findByUser_UserIdOrderByOccurredAtDesc(user.getUserId()));
        userRepository.delete(user);
    }

    @Test
    @DisplayName("동시에 들어온 사고 시작 요청은 하나만 새로 만들고 나머지는 같은 사고를 돌려받는다")
    void concurrentRequestsCreateOnlyOneAccident() throws Exception {
        AccidentCreateRequest request = new AccidentCreateRequest(
            AccidentType.VEHICLE, null, null, null, null, null, null, null, null);
        CyclicBarrier startTogether = new CyclicBarrier(CONCURRENT_REQUESTS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);

        List<Future<AccidentCreation>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(executor.submit(() -> {
                    startTogether.await(5, TimeUnit.SECONDS);
                    return accidentService.create(user.getUserId(), request);
                }));
            }

            List<AccidentCreation> results = new ArrayList<>();
            for (Future<AccidentCreation> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(AccidentCreation::created).hasSize(1);
            assertThat(results).extracting(result -> result.accident().accidentId()).containsOnly(
                results.get(0).accident().accidentId());
            assertThat(accidentRepository.findByUser_UserIdOrderByOccurredAtDesc(user.getUserId())).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
