package com.midway.pix.domain.repository;

import com.midway.pix.domain.entity.Agendamento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendamentoRepository {

    Agendamento salvar(Agendamento agendamento);

    Optional<Agendamento> buscarPorId(UUID id);

    Optional<Agendamento> buscarPorChaveIdempotencia(String chaveIdempotencia);

    List<Agendamento> buscarTodos();
}
