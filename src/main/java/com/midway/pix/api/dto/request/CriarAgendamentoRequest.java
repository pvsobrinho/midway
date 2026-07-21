package com.midway.pix.api.dto.request;

import com.midway.pix.domain.entity.Periodicidade;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CriarAgendamentoRequest(
        @NotBlank(message = "identificadorPagador é obrigatório")
        @Size(max = 100, message = "identificadorPagador deve possuir no máximo 100 caracteres")
        String identificadorPagador,

        @NotBlank(message = "chavePixRecebedor é obrigatória")
        @Size(max = 77, message = "chavePixRecebedor deve possuir no máximo 77 caracteres")
        String chavePixRecebedor,

        @NotNull(message = "valor é obrigatório")
        @DecimalMin(value = "0.01", message = "valor deve ser maior ou igual a 0.01")
        BigDecimal valor,

        @Size(max = 140, message = "descricao deve possuir no máximo 140 caracteres")
        String descricao,

        @NotNull(message = "periodicidade é obrigatória")
        Periodicidade periodicidade,

        @NotNull(message = "dataPrimeiroPagamento é obrigatória")
        @FutureOrPresent(message = "dataPrimeiroPagamento não pode estar no passado")
        LocalDate dataPrimeiroPagamento,

        @FutureOrPresent(message = "dataFim não pode estar no passado")
        LocalDate dataFim
) {
}
