package com.sago.domain.photo;

import com.sago.domain.accident.Accident;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사고 현장 사진 (Step 6~7). S3 업로드 자체는 이윤지님 담당 S3Uploader가 처리하고,
 * 이 엔티티는 업로드된 파일의 메타데이터(URL·촬영 위치·시간)만 보관한다.
 */
@Entity
@Table(name = "photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "photo_id")
    private Long photoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accident_id", nullable = false)
    private Accident accident;

    @Column(name = "file_url", nullable = false, length = 512)
    private String fileUrl;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Photo(Accident accident, String fileUrl, String category, BigDecimal latitude,
                 BigDecimal longitude, LocalDateTime takenAt) {
        this.accident = accident;
        this.fileUrl = fileUrl;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.takenAt = takenAt;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
