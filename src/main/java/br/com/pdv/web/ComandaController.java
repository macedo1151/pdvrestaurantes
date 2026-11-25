package br.com.pdv.web;

import br.com.pdv.domain.StatusComanda;
import br.com.pdv.dto.Dtos.*;
import br.com.pdv.service.ComandaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comandas")
public class ComandaController {

    private final ComandaService service;

    public ComandaController(ComandaService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComandaDto abrir(@Valid @RequestBody AbrirComandaRequest r) {
        return service.abrir(r);
    }

    @GetMapping
    public List<ComandaDto> listar(@RequestParam(defaultValue = "ABERTA") StatusComanda status) {
        return service.listar(status);
    }

    @GetMapping("/{id}")
    public ComandaDto obter(@PathVariable Long id) {
        return service.obter(id);
    }

    @PostMapping("/{id}/itens")
    @ResponseStatus(HttpStatus.CREATED)
    public ComandaDto adicionarItem(@PathVariable Long id, @Valid @RequestBody NovoItemRequest r) {
        return service.adicionarItem(id, r);
    }

    @DeleteMapping("/{id}/itens/{itemId}")
    public ComandaDto cancelarItem(@PathVariable Long id, @PathVariable Long itemId) {
        return service.cancelarItem(id, itemId);
    }

    @GetMapping("/{id}/divisao")
    public DivisaoIgualDto dividir(@PathVariable Long id, @RequestParam int pessoas) {
        return service.dividirIgualmente(id, pessoas);
    }

    @PostMapping("/{id}/divisao-itens")
    public DivisaoItensDto dividirPorItens(@PathVariable Long id, @Valid @RequestBody DivisaoItensRequest r) {
        return service.dividirPorItens(id, r);
    }

    @PostMapping("/{id}/pagamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public ComandaDto pagar(@PathVariable Long id, @Valid @RequestBody PagamentoRequest r) {
        return service.pagar(id, r);
    }

    @PostMapping("/{id}/fechar")
    public ComandaDto fechar(@PathVariable Long id) {
        return service.fechar(id);
    }
}
