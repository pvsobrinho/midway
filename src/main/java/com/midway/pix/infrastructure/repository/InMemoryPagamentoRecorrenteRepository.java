package com.midway.pix.infrastructure.repository;

import com.midway.pix.domain.entity.PagamentoRecorrente;
import com.midway.pix.domain.entity.StatusPagamento;
import com.midway.pix.domain.repository.PagamentoRecorrenteRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryPagamentoRecorrenteRepository implements PagamentoRecorrenteRepository {

    private final Map<UUID, PagamentoRecorrente> pagamentos = new ConcurrentHashMap<>();

    @Override
    public PagamentoRecorrente salvar(PagamentoRecorrente pagamento) {
        Objects.requireNonNull(pagamento, "pagamento não pode ser nulo");
        pagamentos.put(pagamento.getId(), pagamento);
        return pagamento;
    }

    @Override
    public Optional<PagamentoRecorrente> buscarPorId(UUID id) {
        return Optional.ofNullable(pagamentos.get(Objects.requireNonNull(id)));
    }

    @Override
    public List<PagamentoRecorrente> buscarPorAgendamentoId(UUID agendamentoId) {
        Objects.requireNonNull(agendamentoId, "agendamentoId não pode ser nulo");

        return pagamentos.values().stream()
                .filter(pagamento -> pagamento.getAgendamentoId().equals(agendamentoId))
                .sorted(Comparator.comparing(PagamentoRecorrente::getDataAgendada))
                .toList();
    }

    @Override
    public List<PagamentoRecorrente> buscarPorStatusEDataAgendadaAte(
            StatusPagamento status,
            LocalDate dataLimite
    ) {
        Objects.requireNonNull(status, "status não pode ser nulo");
        Objects.requireNonNull(dataLimite, "dataLimite não pode ser nula");

        return pagamentos.values().stream()
                .filter(pagamento -> pagamento.getStatus() == status)
                .filter(pagamento -> !pagamento.getDataAgendada().isAfter(dataLimite))
                .sorted(Comparator.comparing(PagamentoRecorrente::getDataAgendada))
                .toList();
    }
}
