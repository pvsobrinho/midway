package com.midway.pix.domain.repository;

import com.midway.pix.domain.entity.PagamentoRecorrente;
import com.midway.pix.domain.entity.StatusPagamento;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagamentoRecorrenteRepository {

    PagamentoRecorrente salvar(PagamentoRecorrente pagamento);

    Optional<PagamentoRecorrente> buscarPorId(UUID id);

    List<PagamentoRecorrente> buscarPorAgendamentoId(UUID agendamentoId);

    List<PagamentoRecorrente> buscarPorStatusEDataAgendadaAte(
            StatusPagamento status,
            LocalDate dataLimite
    );
}
