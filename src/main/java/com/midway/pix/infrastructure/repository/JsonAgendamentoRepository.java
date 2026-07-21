package com.midway.pix.infrastructure.repository;

import com.midway.pix.domain.entity.Agendamento;
import com.midway.pix.domain.entity.Periodicidade;
import com.midway.pix.domain.entity.StatusAgendamento;
import com.midway.pix.domain.entity.StatusRisco;
import com.midway.pix.domain.repository.AgendamentoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JsonAgendamentoRepository implements AgendamentoRepository {

    private final Path arquivo;
    private final ObjectMapper objectMapper;
    private final Map<UUID, Agendamento> agendamentos = new LinkedHashMap<>();

    public JsonAgendamentoRepository(
            @Value("${app.storage.agendamentos-file:data/agendamentos.json}") String caminhoArquivo,
            ObjectMapper objectMapper
    ) {
        this.arquivo = Path.of(caminhoArquivo);
        this.objectMapper = objectMapper;
        carregar();
    }

    @Override
    public synchronized Agendamento salvar(Agendamento agendamento) {
        buscarPorChaveIdempotencia(agendamento.getChaveIdempotencia())
                .filter(existente -> !existente.getId().equals(agendamento.getId()))
                .ifPresent(existente -> {
                    throw new IllegalStateException("chave de idempotência já utilizada");
                });

        agendamentos.put(agendamento.getId(), agendamento);
        persistir();
        return agendamento;
    }

    @Override
    public synchronized Optional<Agendamento> buscarPorId(UUID id) {
        return Optional.ofNullable(agendamentos.get(id));
    }

    @Override
    public synchronized Optional<Agendamento> buscarPorChaveIdempotencia(String chaveIdempotencia) {
        return agendamentos.values().stream()
                .filter(agendamento -> agendamento.getChaveIdempotencia().equals(chaveIdempotencia))
                .findFirst();
    }

    @Override
    public synchronized List<Agendamento> buscarTodos() {
        return agendamentos.values().stream()
                .sorted(Comparator.comparing(Agendamento::getCriadoEm))
                .toList();
    }

    private void carregar() {
        try {
            if (Files.notExists(arquivo)) {
                criarArquivoVazio();
                return;
            }

            List<AgendamentoJson> registros = objectMapper.readValue(
                    arquivo.toFile(),
                    new TypeReference<>() {
                    }
            );
            registros.stream()
                    .map(AgendamentoJson::paraEntidade)
                    .forEach(agendamento -> agendamentos.put(agendamento.getId(), agendamento));
        } catch (IOException exception) {
            throw new IllegalStateException("não foi possível ler o arquivo de agendamentos", exception);
        }
    }

    private void persistir() {
        try {
            Path diretorio = arquivo.toAbsolutePath().getParent();
            if (diretorio != null) {
                Files.createDirectories(diretorio);
            }

            Path temporario = arquivo.resolveSibling(arquivo.getFileName() + ".tmp");
            List<AgendamentoJson> registros = agendamentos.values().stream()
                    .map(AgendamentoJson::de)
                    .toList();

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporario.toFile(), registros);
            Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("não foi possível salvar o arquivo de agendamentos", exception);
        }
    }

    private void criarArquivoVazio() throws IOException {
        Path diretorio = arquivo.toAbsolutePath().getParent();
        if (diretorio != null) {
            Files.createDirectories(diretorio);
        }
        objectMapper.writeValue(arquivo.toFile(), List.of());
    }

    private record AgendamentoJson(
            UUID id,
            String chaveIdempotencia,
            String identificadorPagador,
            String chavePixRecebedor,
            BigDecimal valor,
            String descricao,
            Periodicidade periodicidade,
            LocalDate dataPrimeiroPagamento,
            LocalDate dataFim,
            StatusAgendamento status,
            StatusRisco statusRisco,
            String motivoAnalise,
            Instant criadoEm,
            Instant atualizadoEm,
            Instant analisadoEm
    ) {

        private static AgendamentoJson de(Agendamento agendamento) {
            return new AgendamentoJson(
                    agendamento.getId(),
                    agendamento.getChaveIdempotencia(),
                    agendamento.getIdentificadorPagador(),
                    agendamento.getChavePixRecebedor(),
                    agendamento.getValor(),
                    agendamento.getDescricao(),
                    agendamento.getPeriodicidade(),
                    agendamento.getDataPrimeiroPagamento(),
                    agendamento.getDataFim(),
                    agendamento.getStatus(),
                    agendamento.getStatusRisco(),
                    agendamento.getMotivoAnalise(),
                    agendamento.getCriadoEm(),
                    agendamento.getAtualizadoEm(),
                    agendamento.getAnalisadoEm()
            );
        }

        private Agendamento paraEntidade() {
            return new Agendamento(
                    id,
                    chaveIdempotencia,
                    identificadorPagador,
                    chavePixRecebedor,
                    valor,
                    descricao,
                    periodicidade,
                    dataPrimeiroPagamento,
                    dataFim,
                    status,
                    statusRisco,
                    motivoAnalise,
                    criadoEm,
                    atualizadoEm,
                    analisadoEm
            );
        }
    }
}
