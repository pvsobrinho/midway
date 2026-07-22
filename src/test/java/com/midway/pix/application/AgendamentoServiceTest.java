package com.midway.pix.application;

import com.midway.pix.api.dto.request.CriarAgendamentoRequest;
import com.midway.pix.domain.entity.Agendamento;
import com.midway.pix.domain.entity.PagamentoRecorrente;
import com.midway.pix.domain.entity.StatusAgendamento;
import com.midway.pix.domain.entity.StatusRisco;
import com.midway.pix.domain.repository.AgendamentoRepository;
import com.midway.pix.domain.repository.PagamentoRecorrenteRepository;
import com.midway.pix.fixture.AgendamentoFixture;
import com.midway.pix.fixture.CriarAgendamentoRequestFixture;
import com.midway.pix.infrastructure.kafka.AgendamentoEventProducer;
import com.midway.pix.infrastructure.kafka.event.AgendamentoSolicitadoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgendamentoServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private PagamentoRecorrenteRepository pagamentoRepository;

    @Mock
    private AgendamentoEventProducer eventProducer;

    private AgendamentoService service;

    @BeforeEach
    void setUp() {
        service = new AgendamentoService(
                agendamentoRepository,
                pagamentoRepository,
                eventProducer
        );
    }

    @Test
    void deveCriarPersistirEPublicarAgendamentoPendente() {
        CriarAgendamentoRequest request = CriarAgendamentoRequestFixture.valido();
        prepararCriacaoComSucesso();

        var response = service.criar("chave-001", request);

        ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).salvar(captor.capture());
        verify(eventProducer).publicar(captor.getValue());
        assertEquals(StatusAgendamento.PENDENTE_ANALISE, response.status());
        assertNull(response.statusRisco());
        assertEquals("Aguardando processamento da análise antifraude", response.motivoAnalise());
        assertEquals(request.valor(), response.valor());
        assertNotNull(response.id());
    }

    @Test
    void deveRetornarMesmoAgendamentoParaChaveIdempotente() {
        Agendamento existente = AgendamentoFixture.valido();
        when(agendamentoRepository.buscarPorChaveIdempotencia("mesma-chave"))
                .thenReturn(Optional.of(existente));
        when(pagamentoRepository.buscarPorAgendamentoId(existente.getId())).thenReturn(List.of());

        var response = service.criar("mesma-chave", CriarAgendamentoRequestFixture.valido());

        assertEquals(existente.getId(), response.id());
        verify(agendamentoRepository, never()).salvar(any());
        verify(eventProducer, never()).publicar(any());
    }

    @Test
    void deveRejeitarPrimeiroPagamentoNoMesmoDia() {
        CriarAgendamentoRequest request = CriarAgendamentoRequestFixture.novo()
                .comPrimeiroPagamento(LocalDate.now())
                .build();
        when(agendamentoRepository.buscarPorChaveIdempotencia("chave"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.criar("chave", request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("dia seguinte"));
        verify(agendamentoRepository, never()).salvar(any());
        verify(eventProducer, never()).publicar(any());
    }

    @Test
    void deveRejeitarPrimeiroPagamentoNoPassado() {
        CriarAgendamentoRequest request = CriarAgendamentoRequestFixture.novo()
                .comPrimeiroPagamento(LocalDate.now().minusDays(1))
                .build();
        when(agendamentoRepository.buscarPorChaveIdempotencia("chave"))
                .thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.criar("chave", request));

        verify(agendamentoRepository, never()).salvar(any());
    }

    @Test
    void devePermitirPrimeiroAgendamentoParaAmanha() {
        CriarAgendamentoRequest request = CriarAgendamentoRequestFixture.novo()
                .comPrimeiroPagamento(LocalDate.now().plusDays(1))
                .build();
        prepararCriacaoComSucesso();

        var response = service.criar("chave", request);

        assertEquals(LocalDate.now().plusDays(1), response.dataPrimeiroPagamento());
    }

    @Test
    void deveRejeitarSegundoAgendamentoParaAmanha() {
        Agendamento existente = agendamentoAbertoParaHoje();
        CriarAgendamentoRequest request = CriarAgendamentoRequestFixture.novo()
                .comPrimeiroPagamento(LocalDate.now().plusDays(1))
                .build();
        when(agendamentoRepository.buscarPorChaveIdempotencia("segunda-chave"))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(existente));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.criar("segunda-chave", request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("no mínimo dois dias"));
        verify(agendamentoRepository, never()).salvar(any());
    }

    @Test
    void devePermitirSegundoAgendamentoParaDaquiADoisDias() {
        Agendamento existente = agendamentoAbertoParaHoje();
        CriarAgendamentoRequest request = CriarAgendamentoRequestFixture.novo()
                .comPrimeiroPagamento(LocalDate.now().plusDays(2))
                .build();
        when(agendamentoRepository.buscarPorChaveIdempotencia("segunda-chave"))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(existente));
        when(agendamentoRepository.salvar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventProducer.publicar(any())).thenReturn(publicacaoConcluida());
        when(pagamentoRepository.buscarPorAgendamentoId(any())).thenReturn(List.of());

        var response = service.criar("segunda-chave", request);

        assertEquals(LocalDate.now().plusDays(2), response.dataPrimeiroPagamento());
        verify(eventProducer).publicar(any());
    }

    @Test
    void deveIgnorarAgendamentoRejeitadoNaRegraDeQuarentaEOitoHoras() {
        Agendamento rejeitado = AgendamentoFixture.novo()
                .comPrimeiroPagamento(LocalDate.now().plusDays(10))
                .comDataFim(LocalDate.now().plusYears(1))
                .comSituacao(StatusAgendamento.REJEITADO, StatusRisco.REJEITADO, "Rejeitado")
                .build();
        CriarAgendamentoRequest request = CriarAgendamentoRequestFixture.novo()
                .comPrimeiroPagamento(LocalDate.now().plusDays(1))
                .build();
        when(agendamentoRepository.buscarPorChaveIdempotencia("nova-chave"))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(rejeitado));
        when(agendamentoRepository.salvar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventProducer.publicar(any())).thenReturn(publicacaoConcluida());
        when(pagamentoRepository.buscarPorAgendamentoId(any())).thenReturn(List.of());

        assertEquals(
                LocalDate.now().plusDays(1),
                service.criar("nova-chave", request).dataPrimeiroPagamento()
        );
    }

    @Test
    void deveIgnorarOutroParNaRegraDeQuarentaEOitoHoras() {
        Agendamento outroRecebedor = AgendamentoFixture.novo()
                .comPrimeiroPagamento(LocalDate.now().plusDays(10))
                .comDataFim(LocalDate.now().plusYears(1))
                .comRecebedor("outro@exemplo.com")
                .build();
        CriarAgendamentoRequest request = CriarAgendamentoRequestFixture.novo()
                .comPrimeiroPagamento(LocalDate.now().plusDays(1))
                .build();
        when(agendamentoRepository.buscarPorChaveIdempotencia("nova-chave"))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(outroRecebedor));
        when(agendamentoRepository.salvar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventProducer.publicar(any())).thenReturn(publicacaoConcluida());
        when(pagamentoRepository.buscarPorAgendamentoId(any())).thenReturn(List.of());

        assertEquals(
                LocalDate.now().plusDays(1),
                service.criar("nova-chave", request).dataPrimeiroPagamento()
        );
    }

    @Test
    void deveMarcarRevisaoManualQuandoPublicacaoFalhar() {
        when(agendamentoRepository.buscarPorChaveIdempotencia("chave-falha"))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of());
        when(agendamentoRepository.salvar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventProducer.publicar(any())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("Kafka indisponível"))
        );
        when(pagamentoRepository.buscarPorAgendamentoId(any())).thenReturn(List.of());

        var response = service.criar("chave-falha", CriarAgendamentoRequestFixture.valido());

        assertEquals(StatusRisco.REVISAO_MANUAL, response.statusRisco());
        assertTrue(response.motivoAnalise().contains("Falha ao enviar"));
        assertNotNull(response.analisadoEm());
        verify(agendamentoRepository, times(2)).salvar(any());
    }

    @Test
    void deveBuscarAgendamentoComPagamentos() {
        UUID agendamentoId = UUID.randomUUID();
        Agendamento agendamento = AgendamentoFixture.novo().comId(agendamentoId).build();
        PagamentoRecorrente pagamento = PagamentoRecorrente.agendar(
                UUID.randomUUID(),
                agendamentoId,
                new BigDecimal("250.00"),
                LocalDate.now().plusDays(3),
                Instant.now()
        );
        when(agendamentoRepository.buscarPorId(agendamentoId)).thenReturn(Optional.of(agendamento));
        when(pagamentoRepository.buscarPorAgendamentoId(agendamentoId)).thenReturn(List.of(pagamento));

        var response = service.buscarPorId(agendamentoId);

        assertEquals(agendamentoId, response.id());
        assertEquals(1, response.pagamentos().size());
        assertEquals(pagamento.getId(), response.pagamentos().getFirst().id());
    }

    @Test
    void deveRetornarNotFoundQuandoAgendamentoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(agendamentoRepository.buscarPorId(id)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buscarPorId(id)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private void prepararCriacaoComSucesso() {
        when(agendamentoRepository.buscarPorChaveIdempotencia(any())).thenReturn(Optional.empty());
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of());
        when(agendamentoRepository.salvar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventProducer.publicar(any())).thenReturn(publicacaoConcluida());
        when(pagamentoRepository.buscarPorAgendamentoId(any())).thenReturn(List.of());
    }

    private CompletableFuture<SendResult<String, AgendamentoSolicitadoEvent>> publicacaoConcluida() {
        return CompletableFuture.<SendResult<String, AgendamentoSolicitadoEvent>>completedFuture(null);
    }

    private Agendamento agendamentoAbertoParaHoje() {
        return AgendamentoFixture.novo()
                .comPrimeiroPagamento(LocalDate.now().plusDays(10))
                .comDataFim(LocalDate.now().plusYears(1))
                .build();
    }
}
