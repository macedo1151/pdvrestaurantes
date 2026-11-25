package br.com.pdv.web;

import br.com.pdv.domain.Praca;
import br.com.pdv.dto.Dtos.*;
import br.com.pdv.service.CaixaService;
import br.com.pdv.service.KdsService;
import br.com.pdv.service.RelatorioService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** KDS, caixa e relatórios. */
@RestController
@RequestMapping("/api")
public class OperacaoController {

    private final KdsService kds;
    private final CaixaService caixa;
    private final RelatorioService relatorios;

    public OperacaoController(KdsService kds, CaixaService caixa, RelatorioService relatorios) {
        this.kds = kds;
        this.caixa = caixa;
        this.relatorios = relatorios;
    }

    @GetMapping("/kds/{praca}")
    public List<KdsItemDto> fila(@PathVariable Praca praca) {
        return kds.fila(praca);
    }

    @PatchMapping("/itens/{id}/status")
    public KdsItemDto mudarStatus(@PathVariable Long id, @Valid @RequestBody MudarStatusRequest r) {
        return kds.mudarStatus(id, r.status());
    }

    @PostMapping("/caixa/abrir")
    @ResponseStatus(HttpStatus.CREATED)
    public CaixaDto abrirCaixa(@Valid @RequestBody AbrirCaixaRequest r) {
        return caixa.abrir(r);
    }

    @GetMapping("/caixa/atual")
    public CaixaDto caixaAtual() {
        return caixa.atual();
    }

    @PostMapping("/caixa/fechar")
    public CaixaDto fecharCaixa(@Valid @RequestBody FecharCaixaRequest r) {
        return caixa.fechar(r);
    }

    @GetMapping("/relatorios/vendas")
    public VendasDto vendas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return relatorios.vendas(de, ate);
    }
}
