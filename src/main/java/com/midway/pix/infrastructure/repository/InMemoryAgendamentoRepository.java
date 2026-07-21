package com.midway.pix.infrastructure.repository;

import com.midway.pix.domain.entity.Agendamento;
import com.midway.pix.domain.repository.AgendamentoRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryAgendamentoRepository implements AgendamentoRepository {

    private final Map<UUID, Agendamento> agendamentos = new ConcurrentHashMap<>();
    private final Map<String, UUID> idsPorChaveIdempotencia = new ConcurrentHashMap<>();

    @Override
    public Agendamento salvar(Agendamento agendamento) {
        Objects.requireNonNull(agendamento, "agendamento não pode ser nulo");

        UUID idExistente = idsPorChaveIdempotencia.putIfAbsent(
                agendamento.getChaveIdempotencia(),
                agendamento.getId()
        );

        if (idExistente != null && !idExistente.equals(agendamento.getId())) {
            throw new IllegalStateException("chave de idempotência já utilizada");
        }

        agendamentos.put(agendamento.getId(), agendamento);
        return agendamento;
    }

    @Override
    public Optional<Agendamento> buscarPorId(UUID id) {
        return Optional.ofNullable(agendamentos.get(Objects.requireNonNull(id)));
    }

    @Override
    public Optional<Agendamento> buscarPorChaveIdempotencia(String chaveIdempotencia) {
        UUID id = idsPorChaveIdempotencia.get(Objects.requireNonNull(chaveIdempotencia));
        return id == null ? Optional.empty() : buscarPorId(id);
    }

    @Override
    public List<Agendamento> buscarTodos() {
        return agendamentos.values().stream()
                .sorted(Comparator.comparing(Agendamento::getCriadoEm))
                .toList();
    }
}
