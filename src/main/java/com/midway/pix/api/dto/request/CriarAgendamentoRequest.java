package com.midway.pix.api.dto.request;

import com.midway.pix.domain.entity.Periodicidade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CriarAgendamentoRequest(
        @Schema(description = "Identificador do pagador", example = "cliente-123")
        @NotBlank(message = "identificadorPagador é obrigatório")
        @Size(max = 100, message = "identificadorPagador deve possuir no máximo 100 caracteres")
        String identificadorPagador,

        @Schema(description = "Chave Pix do recebedor", example = "recebedor@exemplo.com")
        @NotBlank(message = "chavePixRecebedor é obrigatória")
        @Size(max = 77, message = "chavePixRecebedor deve possuir no máximo 77 caracteres")
        String chavePixRecebedor,

        @Schema(description = "Valor de cada pagamento", example = "250.00")
        @NotNull(message = "valor é obrigatório")
        @DecimalMin(value = "0.01", message = "valor deve ser maior ou igual a 0.01")
        BigDecimal valor,

        @Schema(description = "Descrição do agendamento", example = "Mensalidade do serviço")
        @Size(max = 140, message = "descricao deve possuir no máximo 140 caracteres")
        String descricao,

        @Schema(description = "Periodicidade da cobrança", example = "MENSAL")
        @NotNull(message = "periodicidade é obrigatória")
        Periodicidade periodicidade,

        @Schema(description = "Data do primeiro pagamento", example = "2026-08-01")
        @NotNull(message = "dataPrimeiroPagamento é obrigatória")
        @Future(message = "dataPrimeiroPagamento deve ser a partir do dia seguinte")
        LocalDate dataPrimeiroPagamento,

        @Schema(description = "Data final da recorrência", example = "2027-08-01")
        @Future(message = "dataFim deve estar no futuro")
        LocalDate dataFim
) {
}
