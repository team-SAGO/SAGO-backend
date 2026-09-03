package com.sago.domain.photo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoTagRepository extends JpaRepository<PhotoTag, Long> {

    List<PhotoTag> findByPhoto_PhotoId(Long photoId);
}
