package com.sago.domain.accident;

import com.sago.domain.accident.dto.AccidentCreateRequest;
import com.sago.domain.user.User;
import com.sago.domain.user.UserNotFoundException;
import com.sago.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        accidentService = new AccidentService(accidentRepository, userRepository);

        setUserId(owner, 1L);
        when(accidentRepository.save(any(Accident.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("발생 시각을 보내지 않으면 현재 시각으로 채운다")
    void occurredAtDefaultsToNow() {
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(owner));
        LocalDateTime before = LocalDateTime.now();

        var response = accidentService.create(1L, request(null));

        assertThat(response.occurredAt()).isBetween(before, LocalDateTime.now());
        assertThat(response.status()).isEqualTo(AccidentStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("발생 시각을 보내면 그 값을 그대로 쓴다")
    void occurredAtIsKeptWhenGiven() {
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(owner));
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 1, 12, 30);

        var response = accidentService.create(1L, request(occurredAt));

        assertThat(response.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("탈퇴한 회원은 사고를 생성할 수 없다")
    void withdrawnUserCannotCreateAccident() {
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accidentService.create(1L, request(null)))
            .isInstanceOf(UserNotFoundException.class);

        verify(accidentRepository, never()).save(any());
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
