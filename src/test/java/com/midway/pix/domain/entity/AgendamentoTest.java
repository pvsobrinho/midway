package com.midway.pix.domain.entity;

import com.midway.pix.fixture.AgendamentoFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgendamentoTest {

    private static final Instant CRIADO_EM = AgendamentoFixture.DATA_CRIACAO;
    private static final Instant DEPOIS = CRIADO_EM.plusSeconds(60);

    @Test
    void deveCriarAgendamentoPendenteSemResultadoDeRisco() {
        Agendamento agendamento = Agendamento.criar(
                UUID.randomUUID(),
                "chave-001",
                "cliente-123",
                "recebedor@exemplo.com",
                new BigDecimal("250.00"),
                "Mensalidade",
                Periodicidade.MENSAL,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2027, 8, 1),
                CRIADO_EM
        );

        assertEquals(StatusAgendamento.PENDENTE_ANALISE, agendamento.getStatus());
        assertNull(agendamento.getStatusRisco());
        assertTrue(agendamento.getMotivoAnalise().contains("antifraude"));
        assertEquals(CRIADO_EM, agendamento.getCriadoEm());
        assertNull(agendamento.getAnalisadoEm());
    }

    @Test
    void deveDefinirMotivoPadraoParaRevisaoManualSemMotivo() {
        Agendamento agendamento = AgendamentoFixture.novo()
                .comSituacao(StatusAgendamento.PENDENTE_ANALISE, StatusRisco.REVISAO_MANUAL, null)
                .build();

        assertTrue(agendamento.getMotivoAnalise().contains("revis"));
    }

    @Test
    void deveAtivarQuandoAnaliseForAprovada() {
        Agendamento agendamento = AgendamentoFixture.valido();

        agendamento.registrarAnalise(StatusRisco.APROVADO, "Sem indício de risco", null, DEPOIS);

        assertEquals(StatusAgendamento.ATIVO, agendamento.getStatus());
        assertEquals(StatusRisco.APROVADO, agendamento.getStatusRisco());
        assertEquals(DEPOIS, agendamento.getAnalisadoEm());
    }

    @Test
    void deveRejeitarQuandoAnaliseForRejeitada() {
        Agendamento agendamento = AgendamentoFixture.valido();

        agendamento.registrarAnalise(StatusRisco.REJEITADO, "Valor acima do limite", null, DEPOIS);

        assertEquals(StatusAgendamento.REJEITADO, agendamento.getStatus());
        assertEquals(StatusRisco.REJEITADO, agendamento.getStatusRisco());
    }

    @Test
    void deveManterPendenteQuandoExigirRevisaoManual() {
        Agendamento agendamento = AgendamentoFixture.valido();
        Instant bloqueadoAte = DEPOIS.plusSeconds(86_400);

        agendamento.registrarAnalise(
                StatusRisco.REVISAO_MANUAL,
                "Volume suspeito",
                bloqueadoAte,
                DEPOIS
        );

        assertEquals(StatusAgendamento.PENDENTE_ANALISE, agendamento.getStatus());
        assertEquals(StatusRisco.REVISAO_MANUAL, agendamento.getStatusRisco());
        assertEquals(bloqueadoAte, agendamento.getBloqueadoAte());
    }

    @Test
    void naoDeveRegistrarAnaliseSemResultadoMotivoOuDataValidos() {
        Agendamento agendamento = AgendamentoFixture.valido();

        assertThrows(NullPointerException.class, () -> agendamento.registrarAnalise(null, "Motivo", null, DEPOIS));
        assertThrows(IllegalArgumentException.class, () -> agendamento.registrarAnalise(StatusRisco.APROVADO, " ", null, DEPOIS));
        assertThrows(
                IllegalArgumentException.class,
                () -> agendamento.registrarAnalise(
                        StatusRisco.APROVADO,
                        "Aprovado",
                        null,
                        CRIADO_EM.minusSeconds(1)
                )
        );
    }

    @Test
    void naoDeveAceitarDataFimAnteriorAoPrimeiroPagamento() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AgendamentoFixture.novo()
                        .comPrimeiroPagamento(LocalDate.of(2026, 8, 2))
                        .comDataFim(LocalDate.of(2026, 8, 1))
                        .build()
        );
    }

    @Test
    void deveAceitarDataFimIgualAoPrimeiroPagamentoOuAusente() {
        LocalDate data = LocalDate.of(2026, 8, 1);

        assertEquals(data, AgendamentoFixture.novo().comPrimeiroPagamento(data).comDataFim(data).build().getDataFim());
        assertNull(AgendamentoFixture.novo().comDataFim(null).build().getDataFim());
    }

    @Test
    void naoDeveAceitarCamposObrigatoriosOuValoresInvalidos() {
        assertThrows(NullPointerException.class, () -> AgendamentoFixture.novo().comId(null).build());
        assertThrows(IllegalArgumentException.class, () -> AgendamentoFixture.novo().comChaveIdempotencia(" ").build());
        assertThrows(IllegalArgumentException.class, () -> AgendamentoFixture.novo().comPagador(" ").build());
        assertThrows(IllegalArgumentException.class, () -> AgendamentoFixture.novo().comRecebedor("").build());
        assertThrows(NullPointerException.class, () -> AgendamentoFixture.novo().comPeriodicidade(null).build());
        assertThrows(NullPointerException.class, () -> AgendamentoFixture.novo().comPrimeiroPagamento(null).build());
        assertThrows(NullPointerException.class, () -> AgendamentoFixture.novo().comSituacao(null, null, null).build());
        assertThrows(NullPointerException.class, () -> AgendamentoFixture.novo().comValor((BigDecimal) null).build());
        assertThrows(IllegalArgumentException.class, () -> AgendamentoFixture.novo().comValor("0.00").build());
        assertThrows(IllegalArgumentException.class, () -> AgendamentoFixture.novo().comValor("-0.01").build());
    }

    @Test
    void deveAceitarDescricaoNulaEValorPositivoMinimo() {
        Agendamento agendamento = AgendamentoFixture.novo()
                .comDescricao(null)
                .comValor("0.01")
                .build();

        assertNull(agendamento.getDescricao());
        assertEquals(new BigDecimal("0.01"), agendamento.getValor());
        assertNotNull(agendamento.getId());
    }
}
