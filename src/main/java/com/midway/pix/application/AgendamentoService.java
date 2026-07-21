package com.midway.pix.application;

import com.midway.pix.api.dto.request.CriarAgendamentoRequest;
import com.midway.pix.api.dto.response.AgendamentoResponse;
import com.midway.pix.api.dto.response.PagamentoRecorrenteResponse;
import com.midway.pix.domain.entity.Agendamento;
import com.midway.pix.domain.entity.PagamentoRecorrente;
import com.midway.pix.domain.entity.StatusRisco;
import com.midway.pix.domain.repository.AgendamentoRepository;
import com.midway.pix.domain.repository.PagamentoRecorrenteRepository;
import com.midway.pix.domain.service.AnaliseFraudeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AgendamentoService {

    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRecorrenteRepository pagamentoRepository;
    private final AnaliseFraudeService analiseFraudeService;

    public AgendamentoService(
            AgendamentoRepository agendamentoRepository,
            PagamentoRecorrenteRepository pagamentoRepository,
            AnaliseFraudeService analiseFraudeService
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.analiseFraudeService = analiseFraudeService;
    }

    public synchronized AgendamentoResponse criar(String chaveIdempotencia, CriarAgendamentoRequest request) {
        return agendamentoRepository.buscarPorChaveIdempotencia(chaveIdempotencia)
                .map(this::paraResponse)
                .orElseGet(() -> criarNovo(chaveIdempotencia, request));
    }

    public AgendamentoResponse buscarPorId(UUID id) {
        Agendamento agendamento = agendamentoRepository.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "agendamento não encontrado"
                ));
        return paraResponse(agendamento);
    }

    private AgendamentoResponse criarNovo(String chaveIdempotencia, CriarAgendamentoRequest request) {
        Instant agora = Instant.now();
        Agendamento agendamento = Agendamento.criar(
                UUID.randomUUID(),
                chaveIdempotencia,
                request.identificadorPagador(),
                request.chavePixRecebedor(),
                request.valor(),
                request.descricao(),
                request.periodicidade(),
                request.dataPrimeiroPagamento(),
                request.dataFim(),
                agora
        );

        AnaliseFraudeService.ResultadoAnalise resultado = analiseFraudeService.analisar(agendamento);
        agendamento.registrarAnalise(resultado.status(), resultado.motivo(), Instant.now());
        agendamentoRepository.salvar(agendamento);

        if (resultado.status() == StatusRisco.APROVADO) {
            pagamentoRepository.salvar(PagamentoRecorrente.agendar(
                    UUID.randomUUID(),
                    agendamento.getId(),
                    agendamento.getValor(),
                    agendamento.getDataPrimeiroPagamento(),
                    Instant.now()
            ));
        }

        return paraResponse(agendamento);
    }

    private AgendamentoResponse paraResponse(Agendamento agendamento) {
        List<PagamentoRecorrenteResponse> pagamentos = pagamentoRepository
                .buscarPorAgendamentoId(agendamento.getId())
                .stream()
                .map(this::paraResponse)
                .toList();

        return new AgendamentoResponse(
                agendamento.getId(),
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
                agendamento.getAnalisadoEm(),
                pagamentos
        );
    }

    private PagamentoRecorrenteResponse paraResponse(PagamentoRecorrente pagamento) {
        return new PagamentoRecorrenteResponse(
                pagamento.getId(),
                pagamento.getAgendamentoId(),
                pagamento.getValor(),
                pagamento.getDataAgendada(),
                pagamento.getStatus(),
                pagamento.getNumeroTentativas(),
                pagamento.getCodigoTransacaoPix(),
                pagamento.getMotivoFalha(),
                pagamento.getCriadoEm(),
                pagamento.getAtualizadoEm(),
                pagamento.getEnviadoEm(),
                pagamento.getProcessadoEm()
        );
    }
}
