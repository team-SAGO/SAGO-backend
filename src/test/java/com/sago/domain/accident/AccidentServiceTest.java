package com.sago.domain.accident;

import com.sago.domain.accident.dto.AccidentCreateRequest;
import com.sago.domain.user.User;
import com.sago.domain.user.UserNotFoundException;
import com.sago.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccidentServiceTest {

    private AccidentRepository accidentRepository;
    private UserRepository userRepository;
    private AccidentService accidentService;

    private final User owner = User.builder().email("rider@example.com").nickname("라이더").build();

    @BeforeEach
    void setUp() {
        accidentRepository = mock(AccidentRepository.class);
        userRepository = mock(UserRepository.class);
        accidentService = new AccidentService(accidentRepository, userRepository, Duration.ofHours(1));

        setUserId(owner, 1L);
        when(accidentRepository.save(any(Accident.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("발생 시각을 보내지 않으면 현재 시각으로 채운다")
    void occurredAtDefaultsToNow() {
        when(userRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(owner));
        LocalDateTime before = LocalDateTime.now();

        var response = accidentService.create(1L, request(null)).accident();

        assertThat(response.occurredAt()).isBetween(before, LocalDateTime.now());
        assertThat(response.status()).isEqualTo(AccidentStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("발생 시각을 보내면 그 값을 그대로 쓴다")
    void occurredAtIsKeptWhenGiven() {
        when(userRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(owner));
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 1, 12, 30);

        var response = accidentService.create(1L, request(occurredAt)).accident();

        assertThat(response.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("미래 시각으로 사고를 만들 수 없다")
    void futureOccurredAtIsRejected() {
        when(userRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> accidentService.create(
            1L, request(LocalDateTime.now().plusHours(1))))
            .isInstanceOf(IllegalArgumentException.class);

        verify(accidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("시계 오차 범위(5분) 안의 미래 시각은 허용한다")
    void slightlyFutureOccurredAtIsAcceptedForClockSkew() {
        when(userRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(owner));
        LocalDateTime slightlyAhead = LocalDateTime.now().plusMinutes(1);

        var response = accidentService.create(1L, request(slightlyAhead)).accident();

        assertThat(response.occurredAt()).isEqualTo(slightlyAhead);
    }

    @Test
    @DisplayName("탈퇴한 회원은 사고를 생성할 수 없다")
    void withdrawnUserCannotCreateAccident() {
        when(userRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accidentService.create(1L, request(null)))
            .isInstanceOf(UserNotFoundException.class);

        verify(accidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("진행 중인 사고가 없으면 새로 만든다")
    void createsNewAccidentWhenNothingInProgress() {
        when(userRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(owner));

        var result = accidentService.create(1L, request(null));

        assertThat(result.created()).isTrue();
        verify(accidentRepository).save(any(Accident.class));
    }

    @Test
    @DisplayName("최근에 시작한 진행 중 사고가 있으면 새로 만들지 않고 그 사고를 돌려준다")
    void resumesRecentInProgressAccident() {
        when(userRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(owner));
        Accident inProgress = accidentOf(LocalDateTime.of(2026, 9, 14, 9, 0));
        when(accidentRepository.findFirstByUser_UserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            eq(1L), eq(AccidentStatus.IN_PROGRESS), any())).thenReturn(Optional.of(inProgress));

        var result = accidentService.create(1L, request(LocalDateTime.of(2026, 9, 14, 9, 30)));

        assertThat(result.created()).isFalse();
        // 이어 쓸 때는 요청 값을 반영하지 않는다
        assertThat(result.accident().occurredAt()).isEqualTo(inProgress.getOccurredAt());
        verify(accidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("이어 쓸 사고는 만든 지 한 시간 이내인 것만 찾는다")
    void looksUpOnlyWithinResumeWindow() {
        when(userRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(owner));
        ArgumentCaptor<LocalDateTime> createdAfter = ArgumentCaptor.forClass(LocalDateTime.class);
        LocalDateTime before = LocalDateTime.now();

        accidentService.create(1L, request(null));

        verify(accidentRepository).findFirstByUser_UserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            eq(1L), eq(AccidentStatus.IN_PROGRESS), createdAfter.capture());
        assertThat(createdAfter.getValue())
            .isBetween(before.minusHours(1), LocalDateTime.now().minusHours(1));
    }

    @Test
    @DisplayName("미래 시각은 진행 중 사고가 있어도 거부한다 — 요청 자체가 잘못됐다")
    void invalidRequestIsRejectedBeforeResuming() {
        assertThatThrownBy(() -> accidentService.create(1L, request(LocalDateTime.now().plusHours(1))))
            .isInstanceOf(IllegalArgumentException.class);

        // 요청 검증이 먼저라 회원 행을 잠그지도 않는다
        verify(userRepository, never()).findActiveByIdForUpdate(any());
    }

    @Test
    @DisplayName("사고 목록은 최신순으로 내 것만 조회한다")
    void listsOnlyMyAccidentsInRecentOrder() {
        Accident older = accidentOf(LocalDateTime.of(2026, 9, 1, 10, 0));
        Accident newer = accidentOf(LocalDateTime.of(2026, 9, 5, 10, 0));
        // 정렬은 리포지토리 쿼리가 보장한다. 서비스는 그 순서를 흐트러뜨리지 않아야 한다.
        when(accidentRepository.findByUser_UserIdOrderByOccurredAtDesc(1L))
            .thenReturn(List.of(newer, older));

        var responses = accidentService.getMyAccidents(1L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).occurredAt()).isEqualTo(newer.getOccurredAt());
        assertThat(responses.get(1).occurredAt()).isEqualTo(older.getOccurredAt());
    }

    @Test
    @DisplayName("사고가 없으면 빈 목록이 나온다")
    void emptyHistoryReturnsEmptyList() {
        when(accidentRepository.findByUser_UserIdOrderByOccurredAtDesc(1L)).thenReturn(List.of());

        assertThat(accidentService.getMyAccidents(1L)).isEmpty();
    }

    @Test
    @DisplayName("남의 사고 상세는 404다")
    void othersAccidentDetailIsNotFound() {
        Accident accident = accidentOf(LocalDateTime.now());
        when(accidentRepository.findById(10L)).thenReturn(Optional.of(accident));

        assertThatThrownBy(() -> accidentService.getAccident(999L, 10L))
            .isInstanceOf(AccidentNotFoundException.class);
    }

    @Test
    @DisplayName("남의 사고를 조회하면 존재 여부를 감추기 위해 404로 응답한다")
    void othersAccidentIsNotFound() {
        Accident accident = Accident.builder()
            .user(owner)
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(LocalDateTime.now())
            .build();
        when(accidentRepository.findById(10L)).thenReturn(Optional.of(accident));

        assertThatThrownBy(() -> accidentService.getOwnedAccident(999L, 10L))
            .isInstanceOf(AccidentNotFoundException.class);
    }

    @Test
    @DisplayName("본인 사고는 정상적으로 조회된다")
    void ownAccidentIsReturned() {
        Accident accident = Accident.builder()
            .user(owner)
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(LocalDateTime.now())
            .build();
        when(accidentRepository.findById(10L)).thenReturn(Optional.of(accident));

        assertThat(accidentService.getOwnedAccident(1L, 10L)).isSameAs(accident);
    }

    @Test
    @DisplayName("사고를 끝내면 상태가 COMPLETED가 된다")
    void completeMarksAccidentCompleted() {
        Accident accident = accidentOf(LocalDateTime.now());
        when(accidentRepository.findById(10L)).thenReturn(Optional.of(accident));

        var response = accidentService.complete(1L, 10L);

        assertThat(response.status()).isEqualTo(AccidentStatus.COMPLETED);
    }

    @Test
    @DisplayName("이미 끝난 사고를 다시 끝내도 오류가 아니다")
    void completeIsIdempotent() {
        Accident accident = accidentOf(LocalDateTime.now());
        accident.complete();
        when(accidentRepository.findById(10L)).thenReturn(Optional.of(accident));

        assertThat(accidentService.complete(1L, 10L).status()).isEqualTo(AccidentStatus.COMPLETED);
    }

    @Test
    @DisplayName("남의 사고는 끝낼 수 없다")
    void strangerCannotCompleteAccident() {
        when(accidentRepository.findById(10L)).thenReturn(Optional.of(accidentOf(LocalDateTime.now())));

        assertThatThrownBy(() -> accidentService.complete(999L, 10L))
            .isInstanceOf(AccidentNotFoundException.class);
    }

    private Accident accidentOf(LocalDateTime occurredAt) {
        return Accident.builder()
            .user(owner)
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(occurredAt)
            .build();
    }

    private AccidentCreateRequest request(LocalDateTime occurredAt) {
        return new AccidentCreateRequest(
            AccidentType.VEHICLE, InjuryLevel.NONE, InjuryLevel.NONE,
            occurredAt, null, null, null, null, null);
    }

    private void setUserId(User user, Long userId) {
        try {
            var field = User.class.getDeclaredField("userId");
            field.setAccessible(true);
            field.set(user, userId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
