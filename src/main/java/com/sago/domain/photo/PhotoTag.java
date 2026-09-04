package com.sago.domain.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Gemini Vision이 사진에서 인식한 파손·객체 태그 (Step 7, Prompt 4).
 * isManual은 "사용자가 직접 수정했는지"뿐 아니라, 신뢰도가 낮아 AI가 스스로
 * 수동 확인이 필요하다고 표시한 경우에도 true로 둔다(기획안 Prompt 4 제약).
 */
@Entity
@Table(name = "photo_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhotoTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long tagId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", nullable = false)
    private Photo photo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_type", nullable = false, length = 10)
    private TagType tagType;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "confidence", precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "is_manual", nullable = false)
    private boolean manual;

    @Builder
    public PhotoTag(Photo photo, TagType tagType, String label, BigDecimal confidence, boolean manual) {
        this.photo = photo;
        this.tagType = tagType;
        this.label = label;
        this.confidence = confidence;
        this.manual = manual;
    }

    public void updateLabel(String label) {
        this.label = label;
        this.manual = true;
    }
}
