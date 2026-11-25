package br.com.pdv.web;

import br.com.pdv.dto.Dtos.*;
import br.com.pdv.service.CatalogoService;
import br.com.pdv.service.EstoqueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Cadastros: produtos (com ficha técnica), insumos e mesas. */
@RestController
@RequestMapping("/api")
public class CardapioController {

    private final CatalogoService catalogo;
    private final EstoqueService estoque;

    public CardapioController(CatalogoService catalogo, EstoqueService estoque) {
        this.catalogo = catalogo;
        this.estoque = estoque;
    }

    @PostMapping("/produtos")
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoDto criarProduto(@Valid @RequestBody ProdutoRequest r) {
        return catalogo.criar(r);
    }

    @GetMapping("/produtos")
    public List<ProdutoDto> produtos() {
        return catalogo.listar();
    }

    @GetMapping("/produtos/{id}")
    public ProdutoDto produto(@PathVariable Long id) {
        return catalogo.obter(id);
    }

    @PutMapping("/produtos/{id}")
    public ProdutoDto atualizarProduto(@PathVariable Long id, @Valid @RequestBody ProdutoRequest r) {
        return catalogo.atualizar(id, r);
    }

    @DeleteMapping("/produtos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativarProduto(@PathVariable Long id) {
        catalogo.desativar(id);
    }

    @PostMapping("/insumos")
    @ResponseStatus(HttpStatus.CREATED)
    public InsumoDto criarInsumo(@Valid @RequestBody InsumoRequest r) {
        return estoque.criar(r);
    }

    @GetMapping("/insumos")
    public List<InsumoDto> insumos() {
        return estoque.listar();
    }

    @GetMapping("/insumos/alertas")
    public List<InsumoDto> alertas() {
        return estoque.alertas();
    }

    @PostMapping("/insumos/{id}/entrada")
    public InsumoDto entrada(@PathVariable Long id, @Valid @RequestBody EntradaEstoqueRequest r) {
        return estoque.entrada(id, r);
    }

    @PostMapping("/mesas")
    @ResponseStatus(HttpStatus.CREATED)
    public MesaDto criarMesa(@Valid @RequestBody MesaRequest r) {
        return catalogo.criarMesa(r);
    }

    @GetMapping("/mesas")
    public List<MesaDto> mesas() {
        return catalogo.listarMesas();
    }
}
