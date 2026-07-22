package com.midway.pix.infrastructure.kafka;

import com.midway.pix.domain.entity.Agendamento;
import com.midway.pix.domain.entity.PagamentoRecorrente;
import com.midway.pix.domain.entity.StatusAgendamento;
import com.midway.pix.domain.entity.StatusRisco;
import com.midway.pix.domain.repository.AgendamentoRepository;
import com.midway.pix.domain.repository.PagamentoRecorrenteRepository;
import com.midway.pix.domain.service.AnaliseFraudeService;
import com.midway.pix.fixture.AgendamentoFixture;
import com.midway.pix.fixture.PagamentoRecorrenteFixture;
import com.midway.pix.infrastructure.kafka.event.AgendamentoSolicitadoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgendamentoEventConsumerTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private PagamentoRecorrenteRepository pagamentoRepository;

    @Mock
    private AnaliseFraudeService analiseFraudeService;

    private AgendamentoEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AgendamentoEventConsumer(
                agendamentoRepository,
                pagamentoRepository,
                analiseFraudeService
        );
    }

    @Test
    void deveAtivarAgendamentoAprovadoECriarPrimeiroPagamento() {
        Agendamento agendamento = AgendamentoFixture.valido();
        AgendamentoSolicitadoEvent evento = eventoPara(agendamento);
        when(agendamentoRepository.buscarPorId(agendamento.getId())).thenReturn(Optional.of(agendamento));
        when(analiseFraudeService.analisar(agendamento)).thenReturn(resultado(StatusRisco.APROVADO));
        when(pagamentoRepository.buscarPorAgendamentoId(agendamento.getId())).thenReturn(List.of());

        consumer.processar(evento);

        assertEquals(StatusAgendamento.ATIVO, agendamento.getStatus());
        assertEquals(StatusRisco.APROVADO, agendamento.getStatusRisco());
        assertNotNull(agendamento.getAnalisadoEm());
        verify(agendamentoRepository).salvar(agendamento);

        ArgumentCaptor<PagamentoRecorrente> captor = ArgumentCaptor.forClass(PagamentoRecorrente.class);
        verify(pagamentoRepository).salvar(captor.capture());
        assertEquals(agendamento.getId(), captor.getValue().getAgendamentoId());
        assertEquals(agendamento.getValor(), captor.getValue().getValor());
        assertEquals(agendamento.getDataPrimeiroPagamento(), captor.getValue().getDataAgendada());
    }

    @Test
    void naoDeveDuplicarPagamentoQuandoEventoAprovadoForReprocessado() {
        Agendamento agendamento = AgendamentoFixture.valido();
        PagamentoRecorrente existente = PagamentoRecorrenteFixture.valido();
        when(agendamentoRepository.buscarPorId(agendamento.getId())).thenReturn(Optional.of(agendamento));
        when(analiseFraudeService.analisar(agendamento)).thenReturn(resultado(StatusRisco.APROVADO));
        when(pagamentoRepository.buscarPorAgendamentoId(agendamento.getId()))
                .thenReturn(List.of(existente));

        consumer.processar(eventoPara(agendamento));

        verify(pagamentoRepository, never()).salvar(any());
    }

    @Test
    void naoDeveCriarPagamentoQuandoAgendamentoExigirRevisao() {
        Agendamento agendamento = AgendamentoFixture.valido();
        when(agendamentoRepository.buscarPorId(agendamento.getId())).thenReturn(Optional.of(agendamento));
        when(analiseFraudeService.analisar(agendamento))
                .thenReturn(resultado(StatusRisco.REVISAO_MANUAL));

        consumer.processar(eventoPara(agendamento));

        assertEquals(StatusAgendamento.PENDENTE_ANALISE, agendamento.getStatus());
        assertEquals(StatusRisco.REVISAO_MANUAL, agendamento.getStatusRisco());
        verify(pagamentoRepository, never()).salvar(any());
    }

    @Test
    void naoDeveCriarPagamentoQuandoAgendamentoForRejeitado() {
        Agendamento agendamento = AgendamentoFixture.valido();
        when(agendamentoRepository.buscarPorId(agendamento.getId())).thenReturn(Optional.of(agendamento));
        when(analiseFraudeService.analisar(agendamento)).thenReturn(resultado(StatusRisco.REJEITADO));

        consumer.processar(eventoPara(agendamento));

        assertEquals(StatusAgendamento.REJEITADO, agendamento.getStatus());
        assertEquals(StatusRisco.REJEITADO, agendamento.getStatusRisco());
        verify(pagamentoRepository, never()).salvar(any());
    }

    @Test
    void deveIgnorarEventoJaProcessado() {
        Agendamento agendamento = AgendamentoFixture.novo()
                .comSituacao(StatusAgendamento.ATIVO, StatusRisco.APROVADO, "Aprovado")
                .build();
        when(agendamentoRepository.buscarPorId(agendamento.getId())).thenReturn(Optional.of(agendamento));

        consumer.processar(eventoPara(agendamento));

        verify(analiseFraudeService, never()).analisar(any());
        verify(agendamentoRepository, never()).salvar(any());
        verify(pagamentoRepository, never()).salvar(any());
    }

    @Test
    void deveFalharQuandoEventoReferenciarAgendamentoInexistente() {
        UUID id = UUID.randomUUID();
        AgendamentoSolicitadoEvent evento = new AgendamentoSolicitadoEvent(
                UUID.randomUUID(),
                id,
                Instant.now()
        );
        when(agendamentoRepository.buscarPorId(id)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> consumer.processar(evento)
        );

        assertTrue(exception.getMessage().contains(id.toString()));
    }

    @Test
    void deveMarcarRevisaoManualQuandoEventoChegarNaDlt() {
        Agendamento agendamento = AgendamentoFixture.valido();
        when(agendamentoRepository.buscarPorId(agendamento.getId())).thenReturn(Optional.of(agendamento));

        consumer.tratarDlt(eventoPara(agendamento));

        assertEquals(StatusRisco.REVISAO_MANUAL, agendamento.getStatusRisco());
        assertEquals("Falha ao processar a análise antifraude", agendamento.getMotivoAnalise());
        assertNotNull(agendamento.getAnalisadoEm());
        verify(agendamentoRepository).salvar(agendamento);
    }

    @Test
    void naoDeveAlterarAgendamentoJaProcessadoQuandoEventoChegarNaDlt() {
        Agendamento agendamento = AgendamentoFixture.novo()
                .comSituacao(StatusAgendamento.ATIVO, StatusRisco.APROVADO, "Aprovado")
                .build();
        when(agendamentoRepository.buscarPorId(agendamento.getId())).thenReturn(Optional.of(agendamento));

        consumer.tratarDlt(eventoPara(agendamento));

        assertEquals(StatusRisco.APROVADO, agendamento.getStatusRisco());
        verify(agendamentoRepository, never()).salvar(any());
    }

    private AgendamentoSolicitadoEvent eventoPara(Agendamento agendamento) {
        return new AgendamentoSolicitadoEvent(
                UUID.randomUUID(),
                agendamento.getId(),
                Instant.now()
        );
    }

    private AnaliseFraudeService.ResultadoAnalise resultado(StatusRisco status) {
        return new AnaliseFraudeService.ResultadoAnalise(status, "Resultado da análise", null);
    }
}
