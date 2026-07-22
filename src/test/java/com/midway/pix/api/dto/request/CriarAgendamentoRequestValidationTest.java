package com.midway.pix.api.dto.request;

import com.midway.pix.fixture.CriarAgendamentoRequestFixture;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriarAgendamentoRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void iniciarValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void fecharValidator() {
        validatorFactory.close();
    }

    @Test
    void deveAceitarRequestValido() {
        assertTrue(validator.validate(CriarAgendamentoRequestFixture.valido()).isEmpty());
    }

    @Test
    void deveAceitarCamposOpcionaisAusentes() {
        CriarAgendamentoRequest request = CriarAgendamentoRequestFixture.novo()
                .comDescricao(null)
                .comDataFim(null)
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void deveAceitarLimitesMaximosDeTexto() {
        CriarAgendamentoRequest request = CriarAgendamentoRequestFixture.novo()
                .comPagador("p".repeat(100))
                .comRecebedor("r".repeat(77))
                .comDescricao("d".repeat(140))
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void deveRejeitarPagadorNuloVazioOuAcimaDoLimite() {
        validarCampo(CriarAgendamentoRequestFixture.novo().comPagador(null).build(), "identificadorPagador");
        validarCampo(CriarAgendamentoRequestFixture.novo().comPagador(" ").build(), "identificadorPagador");
        validarCampo(CriarAgendamentoRequestFixture.novo().comPagador("p".repeat(101)).build(), "identificadorPagador");
    }

    @Test
    void deveRejeitarChavePixNulaVaziaOuAcimaDoLimite() {
        validarCampo(CriarAgendamentoRequestFixture.novo().comRecebedor(null).build(), "chavePixRecebedor");
        validarCampo(CriarAgendamentoRequestFixture.novo().comRecebedor("").build(), "chavePixRecebedor");
        validarCampo(CriarAgendamentoRequestFixture.novo().comRecebedor("r".repeat(78)).build(), "chavePixRecebedor");
    }

    @Test
    void deveRejeitarValorNuloZeroNegativoOuMenorQueUmCentavo() {
        validarCampo(CriarAgendamentoRequestFixture.novo().comValor(null).build(), "valor");
        validarCampo(CriarAgendamentoRequestFixture.novo().comValor(BigDecimal.ZERO).build(), "valor");
        validarCampo(CriarAgendamentoRequestFixture.novo().comValor(new BigDecimal("-1.00")).build(), "valor");
        validarCampo(CriarAgendamentoRequestFixture.novo().comValor(new BigDecimal("0.009")).build(), "valor");
    }

    @Test
    void deveAceitarValorDeUmCentavo() {
        CriarAgendamentoRequest request = CriarAgendamentoRequestFixture.novo()
                .comValor(new BigDecimal("0.01"))
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void deveRejeitarDescricaoAcimaDoLimite() {
        validarCampo(
                CriarAgendamentoRequestFixture.novo().comDescricao("d".repeat(141)).build(),
                "descricao"
        );
    }

    @Test
    void deveRejeitarPeriodicidadeNula() {
        validarCampo(CriarAgendamentoRequestFixture.novo().comPeriodicidade(null).build(), "periodicidade");
    }

    @Test
    void deveRejeitarPrimeiroPagamentoNuloHojeOuNoPassado() {
        validarCampo(
                CriarAgendamentoRequestFixture.novo().comPrimeiroPagamento(null).build(),
                "dataPrimeiroPagamento"
        );
        validarCampo(
                CriarAgendamentoRequestFixture.novo().comPrimeiroPagamento(LocalDate.now()).build(),
                "dataPrimeiroPagamento"
        );
        validarCampo(
                CriarAgendamentoRequestFixture.novo().comPrimeiroPagamento(LocalDate.now().minusDays(1)).build(),
                "dataPrimeiroPagamento"
        );
    }

    @Test
    void deveAceitarPrimeiroPagamentoNoDiaSeguinte() {
        CriarAgendamentoRequest request = CriarAgendamentoRequestFixture.novo()
                .comPrimeiroPagamento(LocalDate.now().plusDays(1))
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void deveRejeitarDataFimHojeOuNoPassado() {
        validarCampo(
                CriarAgendamentoRequestFixture.novo().comDataFim(LocalDate.now()).build(),
                "dataFim"
        );
        validarCampo(
                CriarAgendamentoRequestFixture.novo().comDataFim(LocalDate.now().minusDays(1)).build(),
                "dataFim"
        );
    }

    private void validarCampo(CriarAgendamentoRequest request, String campo) {
        Set<ConstraintViolation<CriarAgendamentoRequest>> violacoes = validator.validate(request);

        long quantidade = violacoes.stream()
                .filter(violacao -> violacao.getPropertyPath().toString().equals(campo))
                .count();

        assertEquals(1, quantidade, "Era esperada uma violação no campo " + campo);
    }
}
