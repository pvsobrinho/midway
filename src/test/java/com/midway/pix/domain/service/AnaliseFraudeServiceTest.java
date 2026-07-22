package com.midway.pix.domain.service;

import com.midway.pix.domain.entity.Agendamento;
import com.midway.pix.domain.entity.StatusAgendamento;
import com.midway.pix.domain.entity.StatusRisco;
import com.midway.pix.domain.repository.AgendamentoRepository;
import com.midway.pix.fixture.AgendamentoFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnaliseFraudeServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    private AnaliseFraudeService service;

    @BeforeEach
    void setUp() {
        service = new AnaliseFraudeService(agendamentoRepository);
    }

    @Test
    void deveAprovarQuandoNaoHaIndicioDeRisco() {
        Agendamento novo = AgendamentoFixture.valido();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(novo));

        var resultado = service.analisar(novo);

        assertEquals(StatusRisco.APROVADO, resultado.status());
        assertEquals("Nenhum indício de risco identificado", resultado.motivo());
        assertNull(resultado.bloqueadoAte());
    }

    @Test
    void deveAprovarValorExatamenteNoLimiteDeRevisao() {
        Agendamento novo = AgendamentoFixture.novo().comValor("5000.00").build();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(novo));

        assertEquals(StatusRisco.APROVADO, service.analisar(novo).status());
    }

    @Test
    void deveEnviarParaRevisaoQuandoValorSuperaCincoMil() {
        Agendamento novo = AgendamentoFixture.novo().comValor("5000.01").build();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(novo));

        var resultado = service.analisar(novo);

        assertEquals(StatusRisco.REVISAO_MANUAL, resultado.status());
        assertEquals("Valor requer revisão manual", resultado.motivo());
    }

    @Test
    void deveEnviarParaRevisaoQuandoValorForExatamenteDezMil() {
        Agendamento novo = AgendamentoFixture.novo().comValor("10000.00").build();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(novo));

        assertEquals(StatusRisco.REVISAO_MANUAL, service.analisar(novo).status());
    }

    @Test
    void deveRejeitarQuandoValorSuperaDezMil() {
        Agendamento novo = AgendamentoFixture.novo().comValor("10000.01").build();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(novo));

        var resultado = service.analisar(novo);

        assertEquals(StatusRisco.REJEITADO, resultado.status());
        assertEquals("Valor acima do limite permitido", resultado.motivo());
    }

    @Test
    void deveEnviarParaRevisaoQuandoRecorrenciaSuperaDoisAnos() {
        Agendamento novo = AgendamentoFixture.novo()
                .comDataFim(AgendamentoFixture.PRIMEIRO_PAGAMENTO.plusYears(2).plusDays(1))
                .build();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(novo));

        var resultado = service.analisar(novo);

        assertEquals(StatusRisco.REVISAO_MANUAL, resultado.status());
        assertEquals("Período da recorrência superior a dois anos", resultado.motivo());
    }

    @Test
    void deveAprovarRecorrenciaComExatamenteDoisAnos() {
        Agendamento novo = AgendamentoFixture.novo()
                .comDataFim(AgendamentoFixture.PRIMEIRO_PAGAMENTO.plusYears(2))
                .build();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(novo));

        assertEquals(StatusRisco.APROVADO, service.analisar(novo).status());
    }

    @Test
    void deveAprovarRecorrenciaSemDataFinal() {
        Agendamento novo = AgendamentoFixture.novo().comDataFim(null).build();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(novo));

        assertEquals(StatusRisco.APROVADO, service.analisar(novo).status());
    }

    @Test
    void deveIdentificarDuplicidadeMesmoComIdempotenciaEFormatacaoDiferentes() {
        Agendamento existente = AgendamentoFixture.novo()
                .comChaveIdempotencia("outra-chave")
                .comPagador("CLIENTE-123")
                .comRecebedor("RECEBEDOR@EXEMPLO.COM")
                .comValor("250.0")
                .build();
        Agendamento novo = AgendamentoFixture.novo()
                .comChaveIdempotencia("nova-chave")
                .build();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(existente, novo));

        var resultado = service.analisar(novo);

        assertEquals(StatusRisco.REVISAO_MANUAL, resultado.status());
        assertTrue(resultado.motivo().contains("mesmo pagador"));
    }

    @Test
    void deveIgnorarDuplicidadeDeAgendamentoRejeitado() {
        Agendamento rejeitado = AgendamentoFixture.novo()
                .comChaveIdempotencia("rejeitado")
                .comSituacao(StatusAgendamento.REJEITADO, StatusRisco.REJEITADO, "Rejeitado")
                .build();
        Agendamento novo = AgendamentoFixture.valido();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(rejeitado, novo));

        assertEquals(StatusRisco.APROVADO, service.analisar(novo).status());
    }

    @Test
    void naoDeveConsiderarDuplicadoQuandoValorDataPagadorOuRecebedorForemDiferentes() {
        Agendamento novo = AgendamentoFixture.valido();
        List<Agendamento> diferentes = List.of(
                AgendamentoFixture.novo().comChaveIdempotencia("a").comValor("251.00").build(),
                AgendamentoFixture.novo().comChaveIdempotencia("b")
                        .comPrimeiroPagamento(AgendamentoFixture.PRIMEIRO_PAGAMENTO.plusDays(1)).build(),
                AgendamentoFixture.novo().comChaveIdempotencia("c").comPagador("outro-pagador").build(),
                AgendamentoFixture.novo().comChaveIdempotencia("d").comRecebedor("outro@exemplo.com").build(),
                novo
        );
        when(agendamentoRepository.buscarTodos()).thenReturn(diferentes);

        assertEquals(StatusRisco.APROVADO, service.analisar(novo).status());
    }

    @Test
    void deveEnviarTerceiroAgendamentoDaMesmaDataParaRevisao() {
        Agendamento novo = AgendamentoFixture.novo().comValor("300.00").build();
        Agendamento primeiro = AgendamentoFixture.novo()
                .comChaveIdempotencia("primeiro").comValor("100.00").build();
        Agendamento segundo = AgendamentoFixture.novo()
                .comChaveIdempotencia("segundo").comValor("200.00").build();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(primeiro, segundo, novo));

        var resultado = service.analisar(novo);

        assertEquals(StatusRisco.REVISAO_MANUAL, resultado.status());
        assertTrue(resultado.motivo().startsWith("Terceiro"));
    }

    @Test
    void devePermitirSegundoAgendamentoNaMesmaData() {
        Agendamento novo = AgendamentoFixture.novo().comValor("300.00").build();
        Agendamento primeiro = AgendamentoFixture.novo()
                .comChaveIdempotencia("primeiro").comValor("100.00").build();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(primeiro, novo));

        assertEquals(StatusRisco.APROVADO, service.analisar(novo).status());
    }

    @Test
    void deveEnviarQuartoAgendamentoEmCincoMinutosParaRevisao() {
        Agendamento novo = AgendamentoFixture.valido();
        List<Agendamento> existentes = agendamentosRecentes(novo, 3, Duration.ofMinutes(1));
        existentes.add(novo);
        when(agendamentoRepository.buscarTodos()).thenReturn(existentes);

        var resultado = service.analisar(novo);

        assertEquals(StatusRisco.REVISAO_MANUAL, resultado.status());
        assertTrue(resultado.motivo().contains("5 minutos"));
        assertNull(resultado.bloqueadoAte());
    }

    @Test
    void deveIncluirLimiteExatoDeCincoMinutosNaContagem() {
        Agendamento novo = AgendamentoFixture.valido();
        List<Agendamento> existentes = new ArrayList<>(agendamentosRecentes(
                novo,
                2,
                Duration.ofMinutes(1)
        ));
        existentes.add(agendamentoDiferente(novo, 99, novo.getCriadoEm().minus(Duration.ofMinutes(5))));
        existentes.add(novo);
        when(agendamentoRepository.buscarTodos()).thenReturn(existentes);

        assertTrue(service.analisar(novo).motivo().contains("5 minutos"));
    }

    @Test
    void deveIgnorarAgendamentoAnteriorAoIntervaloDeCincoMinutos() {
        Agendamento novo = AgendamentoFixture.valido();
        List<Agendamento> existentes = new ArrayList<>(agendamentosRecentes(
                novo,
                2,
                Duration.ofMinutes(1)
        ));
        existentes.add(agendamentoDiferente(
                novo,
                99,
                novo.getCriadoEm().minus(Duration.ofMinutes(5)).minusMillis(1)
        ));
        existentes.add(novo);
        when(agendamentoRepository.buscarTodos()).thenReturn(existentes);

        assertEquals(StatusRisco.APROVADO, service.analisar(novo).status());
    }

    @Test
    void deveBloquearParPorVinteEQuatroHorasNoDecimoAgendamentoEmSessentaMinutos() {
        Agendamento novo = AgendamentoFixture.valido();
        List<Agendamento> existentes = agendamentosRecentes(novo, 9, Duration.ofMinutes(6));
        existentes.add(novo);
        when(agendamentoRepository.buscarTodos()).thenReturn(existentes);

        var resultado = service.analisar(novo);

        assertEquals(StatusRisco.REVISAO_MANUAL, resultado.status());
        assertTrue(resultado.motivo().contains("60 minutos"));
        assertEquals(novo.getCriadoEm().plus(Duration.ofHours(24)), resultado.bloqueadoAte());
    }

    @Test
    void deveIncluirLimiteExatoDeSessentaMinutosNaContagem() {
        Agendamento novo = AgendamentoFixture.valido();
        List<Agendamento> existentes = agendamentosRecentes(novo, 8, Duration.ofMinutes(6));
        existentes.add(agendamentoDiferente(novo, 99, novo.getCriadoEm().minus(Duration.ofMinutes(60))));
        existentes.add(novo);
        when(agendamentoRepository.buscarTodos()).thenReturn(existentes);

        assertTrue(service.analisar(novo).motivo().contains("60 minutos"));
    }

    @Test
    void deveRejeitarEnquantoBloqueioDoParEstiverAtivo() {
        Instant bloqueadoAte = AgendamentoFixture.DATA_CRIACAO.plus(Duration.ofHours(2));
        Agendamento bloqueador = AgendamentoFixture.novo()
                .comChaveIdempotencia("bloqueador")
                .comSituacao(
                        StatusAgendamento.PENDENTE_ANALISE,
                        StatusRisco.REVISAO_MANUAL,
                        "Volume elevado"
                )
                .bloqueadoAte(bloqueadoAte)
                .build();
        Agendamento novo = AgendamentoFixture.novo()
                .criadoEm(AgendamentoFixture.DATA_CRIACAO.plus(Duration.ofHours(1)))
                .build();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(bloqueador, novo));

        var resultado = service.analisar(novo);

        assertEquals(StatusRisco.REJEITADO, resultado.status());
        assertEquals(bloqueadoAte, resultado.bloqueadoAte());
        assertTrue(resultado.motivo().contains("bloqueados temporariamente"));
    }

    @Test
    void devePermitirQuandoBloqueioJaExpirou() {
        Agendamento bloqueador = AgendamentoFixture.novo()
                .comChaveIdempotencia("bloqueador")
                .comValor("100.00")
                .comSituacao(
                        StatusAgendamento.PENDENTE_ANALISE,
                        StatusRisco.REVISAO_MANUAL,
                        "Volume elevado"
                )
                .bloqueadoAte(AgendamentoFixture.DATA_CRIACAO.plus(Duration.ofHours(1)))
                .build();
        Agendamento novo = AgendamentoFixture.novo()
                .criadoEm(AgendamentoFixture.DATA_CRIACAO.plus(Duration.ofHours(1)))
                .build();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(bloqueador, novo));

        assertEquals(StatusRisco.APROVADO, service.analisar(novo).status());
    }

    @Test
    void naoDeveAplicarBloqueioDeOutroParPagadorRecebedor() {
        Agendamento bloqueador = AgendamentoFixture.novo()
                .comChaveIdempotencia("bloqueador")
                .comRecebedor("outro@exemplo.com")
                .comSituacao(
                        StatusAgendamento.PENDENTE_ANALISE,
                        StatusRisco.REVISAO_MANUAL,
                        "Volume elevado"
                )
                .bloqueadoAte(AgendamentoFixture.DATA_CRIACAO.plus(Duration.ofHours(24)))
                .build();
        Agendamento novo = AgendamentoFixture.valido();
        when(agendamentoRepository.buscarTodos()).thenReturn(List.of(bloqueador, novo));

        assertEquals(StatusRisco.APROVADO, service.analisar(novo).status());
    }

    @Test
    void deveIgnorarAgendamentosCriadosDepoisDoEventoAnalisadoNaRegraDeVolume() {
        Agendamento novo = AgendamentoFixture.valido();
        List<Agendamento> futuros = new ArrayList<>();
        for (int indice = 0; indice < 10; indice++) {
            futuros.add(agendamentoDiferente(
                    novo,
                    indice,
                    novo.getCriadoEm().plusSeconds(indice + 1L)
            ));
        }
        futuros.add(novo);
        when(agendamentoRepository.buscarTodos()).thenReturn(futuros);

        assertEquals(StatusRisco.APROVADO, service.analisar(novo).status());
    }

    private List<Agendamento> agendamentosRecentes(
            Agendamento novo,
            int quantidade,
            Duration intervalo
    ) {
        List<Agendamento> agendamentos = new ArrayList<>();
        for (int indice = 1; indice <= quantidade; indice++) {
            agendamentos.add(agendamentoDiferente(
                    novo,
                    indice,
                    novo.getCriadoEm().minus(intervalo.multipliedBy(indice))
            ));
        }
        return agendamentos;
    }

    private Agendamento agendamentoDiferente(Agendamento novo, int indice, Instant criadoEm) {
        return AgendamentoFixture.novo()
                .comChaveIdempotencia("existente-" + indice)
                .comPagador(novo.getIdentificadorPagador())
                .comRecebedor(novo.getChavePixRecebedor())
                .comValor(Integer.toString(300 + indice))
                .comPrimeiroPagamento(novo.getDataPrimeiroPagamento().plusDays(indice + 1L))
                .comDataFim(novo.getDataPrimeiroPagamento().plusYears(1).plusDays(indice + 1L))
                .criadoEm(criadoEm)
                .build();
    }
}
