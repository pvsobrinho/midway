package com.midway.pix.application;

import com.midway.pix.api.dto.request.CriarAgendamentoRequest;
import com.midway.pix.api.dto.response.AgendamentoResponse;
import com.midway.pix.api.dto.response.PagamentoRecorrenteResponse;
import com.midway.pix.domain.entity.Agendamento;
import com.midway.pix.domain.entity.PagamentoRecorrente;
import com.midway.pix.domain.entity.StatusAgendamento;
import com.midway.pix.domain.entity.StatusRisco;
import com.midway.pix.domain.repository.AgendamentoRepository;
import com.midway.pix.domain.repository.PagamentoRecorrenteRepository;
import com.midway.pix.infrastructure.kafka.AgendamentoEventProducer;
import com.midway.pix.shared.LogSeguro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AgendamentoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgendamentoService.class);

    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRecorrenteRepository pagamentoRepository;
    private final AgendamentoEventProducer eventProducer;

    public AgendamentoService(
            AgendamentoRepository agendamentoRepository,
            PagamentoRecorrenteRepository pagamentoRepository,
            AgendamentoEventProducer eventProducer
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.eventProducer = eventProducer;
    }

    public synchronized AgendamentoResponse criar(String chaveIdempotencia, CriarAgendamentoRequest request) {
        LOGGER.info(
                "Solicitação de agendamento: idempotencyKey={}, pagador={}, recebedor={}, valor={}, "
                        + "periodicidade={}, primeiroPagamento={}, dataFim={}",
                LogSeguro.mascarar(chaveIdempotencia),
                LogSeguro.mascarar(request.identificadorPagador()),
                LogSeguro.mascarar(request.chavePixRecebedor()),
                request.valor(),
                request.periodicidade(),
                request.dataPrimeiroPagamento(),
                request.dataFim()
        );

        return agendamentoRepository.buscarPorChaveIdempotencia(chaveIdempotencia)
                .map(agendamento -> {
                    LOGGER.info(
                            "Requisição idempotente: idempotencyKey={}, agendamentoId={}, status={}",
                            LogSeguro.mascarar(chaveIdempotencia),
                            agendamento.getId(),
                            agendamento.getStatus()
                    );
                    return paraResponse(agendamento);
                })
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
        validarDataDoAgendamento(chaveIdempotencia, request);

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

        agendamentoRepository.salvar(agendamento);
        LOGGER.info(
                "Agendamento persistido: agendamentoId={}, status={}, statusRisco={}, motivo={}, bloqueadoAte={}",
                agendamento.getId(),
                agendamento.getStatus(),
                agendamento.getStatusRisco(),
                agendamento.getMotivoAnalise(),
                agendamento.getBloqueadoAte()
        );

        eventProducer.publicar(agendamento)
                .exceptionally(erro -> {
                    marcarRevisaoManualPorFalhaDePublicacao(agendamento, erro);
                    return null;
                });

        return paraResponse(agendamento);
    }

    private void marcarRevisaoManualPorFalhaDePublicacao(Agendamento agendamento, Throwable erro) {
        synchronized (agendamento) {
            if (agendamento.getStatusRisco() != StatusRisco.PENDENTE) {
                return;
            }

            agendamento.registrarAnalise(
                    StatusRisco.REVISAO_MANUAL,
                    "Falha ao enviar a solicitação para análise antifraude",
                    null,
                    Instant.now()
            );
            agendamentoRepository.salvar(agendamento);
        }

        LOGGER.error(
                "Agendamento enviado para revisão manual após falha de publicação: agendamentoId={}",
                agendamento.getId(),
                erro
        );
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
                agendamento.getBloqueadoAte(),
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

    private void validarDataDoAgendamento(
            String chaveIdempotencia,
            CriarAgendamentoRequest request
    ) {
        LocalDate hoje = LocalDate.now();
        if (!request.dataPrimeiroPagamento().isAfter(hoje)) {
            LOGGER.warn(
                    "Regra de data rejeitada: regra=PRIMEIRO_PAGAMENTO_DIA_SEGUINTE, idempotencyKey={}, "
                            + "pagador={}, recebedor={}, dataSolicitada={}, dataMinima={}",
                    LogSeguro.mascarar(chaveIdempotencia),
                    LogSeguro.mascarar(request.identificadorPagador()),
                    LogSeguro.mascarar(request.chavePixRecebedor()),
                    request.dataPrimeiroPagamento(),
                    hoje.plusDays(1)
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "o primeiro pagamento deve ser agendado a partir do dia seguinte"
            );
        }

        boolean possuiAgendamentoAnterior = agendamentoRepository.buscarTodos().stream()
                .filter(agendamento -> agendamento.getStatus() != StatusAgendamento.CANCELADO)
                .filter(agendamento -> agendamento.getStatus() != StatusAgendamento.REJEITADO)
                .filter(agendamento -> agendamento.getStatus() != StatusAgendamento.CONCLUIDO)
                .anyMatch(agendamento -> agendamento.getIdentificadorPagador()
                        .equalsIgnoreCase(request.identificadorPagador())
                        && agendamento.getChavePixRecebedor()
                        .equalsIgnoreCase(request.chavePixRecebedor()));

        if (possuiAgendamentoAnterior && request.dataPrimeiroPagamento().isBefore(hoje.plusDays(2))) {
            LOGGER.warn(
                    "Regra de data rejeitada: regra=SEGUNDO_AGENDAMENTO_48_HORAS, pagador={}, recebedor={}, "
                            + "dataSolicitada={}, dataMinima={}",
                    LogSeguro.mascarar(request.identificadorPagador()),
                    LogSeguro.mascarar(request.chavePixRecebedor()),
                    request.dataPrimeiroPagamento(),
                    hoje.plusDays(2)
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "a partir do segundo agendamento, o primeiro pagamento deve ocorrer em no mínimo dois dias"
            );
        }
    }
}
