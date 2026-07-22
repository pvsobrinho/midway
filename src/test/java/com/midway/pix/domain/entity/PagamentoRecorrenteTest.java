package com.midway.pix.domain.entity;

import com.midway.pix.fixture.PagamentoRecorrenteFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PagamentoRecorrenteTest {

    @Test
    void deveCriarPagamentoAgendadoComDadosEssenciais() {
        UUID id = UUID.randomUUID();
        UUID agendamentoId = UUID.randomUUID();
        Instant criadoEm = PagamentoRecorrenteFixture.DATA_CRIACAO;

        PagamentoRecorrente pagamento = PagamentoRecorrente.agendar(
                id,
                agendamentoId,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 8, 1),
                criadoEm
        );

        assertEquals(id, pagamento.getId());
        assertEquals(agendamentoId, pagamento.getAgendamentoId());
        assertEquals(new BigDecimal("100.00"), pagamento.getValor());
        assertEquals(LocalDate.of(2026, 8, 1), pagamento.getDataAgendada());
        assertEquals(criadoEm, pagamento.getCriadoEm());
    }

    @Test
    void naoDeveAceitarCamposObrigatoriosInvalidos() {
        assertThrows(NullPointerException.class, () -> PagamentoRecorrenteFixture.novo().comId(null).build());
        assertThrows(NullPointerException.class, () -> PagamentoRecorrenteFixture.novo().comAgendamentoId(null).build());
        assertThrows(NullPointerException.class, () -> PagamentoRecorrenteFixture.novo().comValor(null).build());
        assertThrows(NullPointerException.class, () -> PagamentoRecorrenteFixture.novo().comDataAgendada(null).build());
        assertThrows(NullPointerException.class, () -> PagamentoRecorrenteFixture.novo().criadoEm(null).build());
    }

    @Test
    void naoDeveAceitarValorNaoPositivo() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PagamentoRecorrenteFixture.novo().comValor(BigDecimal.ZERO).build()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PagamentoRecorrenteFixture.novo().comValor(new BigDecimal("-0.01")).build()
        );
    }
}
