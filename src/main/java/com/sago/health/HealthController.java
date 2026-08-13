package com.sago.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 서버가 정상적으로 떠 있는지 확인하는 헬스체크 엔드포인트.
 * GET /api/health -> {"status":"UP","service":"sago-backend"}
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "service", "sago-backend"
        );
    }
}
