package com.midway.pix.api.dto.response;

import java.time.Instant;
import java.util.List;

public record ErroResponse(
        Instant timestamp,
        int status,
        String erro,
        List<String> mensagens,
        String caminho
) {
}
