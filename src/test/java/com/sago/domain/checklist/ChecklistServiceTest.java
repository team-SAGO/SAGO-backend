package com.sago.domain.checklist;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentNotFoundException;
import com.sago.domain.accident.AccidentService;
import com.sago.domain.accident.AccidentType;
import com.sago.domain.checklist.dto.ChecklistItemResponse;
import com.sago.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChecklistServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ACCIDENT_ID = 10L;

    private AccidentService accidentService;
    private ChecklistGenerationService generationService;
    private ChecklistStore checklistStore;
    private ChecklistItemRepository checklistItemRepository;
    private ChecklistService checklistService;

    private Accident accident;

    @BeforeEach
    void setUp() {
        accidentService = mock(AccidentService.class);
        generationService = mock(ChecklistGenerationService.class);
        checklistStore = mock(ChecklistStore.class);
        checklistItemRepository = mock(ChecklistItemRepository.class);
        checklistService = new ChecklistService(
            accidentService, generationService, checklistStore, checklistItemRepository);

        accident = Accident.builder()
            .user(User.builder().email("rider@example.com").build())
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(LocalDateTime.now())
            .build();
        setId(accident, "accidentId", ACCIDENT_ID);

        when(accidentService.getOwnedAccident(USER_ID, ACCIDENT_ID)).thenReturn(accident);
    }

    @Test
    @DisplayName("항목이 없으면 첫 조회에서 생성한다")
    void generatesOnFirstFetch() {
        List<ChecklistItem> generated = List.of(item(100L, "119 신고하기", 1));
        when(checklistStore.findItems(ACCIDENT_ID)).thenReturn(List.of());
        when(generationService.generateChecklist(accident)).thenReturn(generated);
        when(checklistStore.saveIfAbsent(ACCIDENT_ID, generated)).thenReturn(generated);

        List<ChecklistItemResponse> response = checklistService.getOrGenerate(USER_ID, ACCIDENT_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).content()).isEqualTo("119 신고하기");
    }

    @Test
    @DisplayName("이미 항목이 있으면 다시 생성하지 않는다")
    void doesNotRegenerateWhenItemsExist() {
        when(checklistStore.findItems(ACCIDENT_ID))
            .thenReturn(List.of(item(100L, "119 신고하기", 1)));

        checklistService.getOrGenerate(USER_ID, ACCIDENT_ID);

        verify(generationService, never()).generateChecklist(any());
    }

    @Test
    @DisplayName("생성 도중 다른 요청이 먼저 저장했으면 그쪽 결과를 쓴다")
    void losingRaceReturnsAlreadySavedItems() {
        List<ChecklistItem> mine = List.of(item(100L, "내가 만든 항목", 1));
        List<ChecklistItem> winner = List.of(item(200L, "먼저 저장된 항목", 1));
        when(checklistStore.findItems(ACCIDENT_ID)).thenReturn(List.of());
        when(generationService.generateChecklist(accident)).thenReturn(mine);
        // saveIfAbsent가 락을 잡고 다시 확인해, 이미 있으면 기존 것을 돌려준다.
        when(checklistStore.saveIfAbsent(ACCIDENT_ID, mine)).thenReturn(winner);

        List<ChecklistItemResponse> response = checklistService.getOrGenerate(USER_ID, ACCIDENT_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).content()).isEqualTo("먼저 저장된 항목");
    }

    @Test
    @DisplayName("남의 사고 체크리스트는 조회할 수 없다")
    void cannotReadOthersChecklist() {
        when(accidentService.getOwnedAccident(999L, ACCIDENT_ID))
            .thenThrow(new AccidentNotFoundException("사고 기록을 찾을 수 없습니다."));

        assertThatThrownBy(() -> checklistService.getOrGenerate(999L, ACCIDENT_ID))
            .isInstanceOf(AccidentNotFoundException.class);

        verify(generationService, never()).generateChecklist(any());
    }

    @Test
    @DisplayName("항목을 완료 처리하면 완료 시각이 기록된다")
    void completingItemRecordsTimestamp() {
        ChecklistItem item = item(100L, "119 신고하기", 1);
        when(checklistItemRepository.findById(100L)).thenReturn(Optional.of(item));

        ChecklistItemResponse response =
            checklistService.updateCompletion(USER_ID, ACCIDENT_ID, 100L, true);

        assertThat(response.completed()).isTrue();
        assertThat(response.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("완료를 해제하면 완료 시각도 지워진다")
    void uncompletingItemClearsTimestamp() {
        ChecklistItem item = item(100L, "119 신고하기", 1);
        item.complete();
        when(checklistItemRepository.findById(100L)).thenReturn(Optional.of(item));

        ChecklistItemResponse response =
            checklistService.updateCompletion(USER_ID, ACCIDENT_ID, 100L, false);

        assertThat(response.completed()).isFalse();
        assertThat(response.completedAt()).isNull();
    }

    @Test
    @DisplayName("다른 사고의 항목 번호를 넣으면 거부된다")
    void cannotUpdateItemOfAnotherAccident() {
        Accident other = Accident.builder()
            .user(User.builder().email("other@example.com").build())
            .accidentType(AccidentType.SINGLE)
            .occurredAt(LocalDateTime.now())
            .build();
        setId(other, "accidentId", 99L);

        ChecklistItem foreignItem = ChecklistItem.builder()
            .accident(other)
            .content("안전 지대로 이동")
            .orderNo(1)
            .source(ChecklistSource.STATIC)
            .build();
        setId(foreignItem, "checklistItemId", 200L);
        when(checklistItemRepository.findById(200L)).thenReturn(Optional.of(foreignItem));

        assertThatThrownBy(() ->
            checklistService.updateCompletion(USER_ID, ACCIDENT_ID, 200L, true))
            .isInstanceOf(ChecklistItemNotFoundException.class);
    }

    @Test
    @DisplayName("없는 항목을 수정하려 하면 거부된다")
    void unknownItemIsRejected() {
        when(checklistItemRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            checklistService.updateCompletion(USER_ID, ACCIDENT_ID, 404L, true))
            .isInstanceOf(ChecklistItemNotFoundException.class);
    }

    private ChecklistItem item(Long id, String content, int orderNo) {
        ChecklistItem item = ChecklistItem.builder()
            .accident(accident)
            .content(content)
            .orderNo(orderNo)
            .source(ChecklistSource.AI)
            .build();
        setId(item, "checklistItemId", id);
        return item;
    }

    /** 식별자는 DB가 채우는 값이라 테스트에서는 리플렉션으로 직접 넣는다. */
    private void setId(Object target, String fieldName, Long value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
