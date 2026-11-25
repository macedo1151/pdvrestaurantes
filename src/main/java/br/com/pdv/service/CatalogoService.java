package br.com.pdv.service;

import br.com.pdv.domain.Insumo;
import br.com.pdv.domain.ItemFicha;
import br.com.pdv.domain.Mesa;
import br.com.pdv.domain.Produto;
import br.com.pdv.dto.Dtos.*;
import br.com.pdv.repository.InsumoRepository;
import br.com.pdv.repository.MesaRepository;
import br.com.pdv.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Cardápio (com ficha técnica) e mesas. */
@Service
public class CatalogoService {

    private final ProdutoRepository produtos;
    private final InsumoRepository insumos;
    private final MesaRepository mesas;

    public CatalogoService(ProdutoRepository produtos, InsumoRepository insumos, MesaRepository mesas) {
        this.produtos = produtos;
        this.insumos = insumos;
        this.mesas = mesas;
    }

    @Transactional
    public ProdutoDto criar(ProdutoRequest r) {
        if (produtos.existsByNomeIgnoreCase(r.nome())) {
            throw new RegraDeNegocioException("Já existe um produto chamado " + r.nome());
        }
        Produto p = new Produto(r.nome(), r.categoria(), r.preco(), r.praca());
        aplicarFicha(p, r.ficha());
        return ProdutoDto.from(produtos.save(p));
    }

    @Transactional
    public ProdutoDto atualizar(Long id, ProdutoRequest r) {
        Produto p = buscar(id);
        p.atualizar(r.nome(), r.categoria(), r.preco(), r.praca());
        p.getFicha().clear();
        aplicarFicha(p, r.ficha());
        return ProdutoDto.from(p);
    }

    @Transactional
    public void desativar(Long id) {
        buscar(id).desativar();
    }

    @Transactional(readOnly = true)
    public List<ProdutoDto> listar() {
        return produtos.findAll().stream().map(ProdutoDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ProdutoDto obter(Long id) {
        return ProdutoDto.from(buscar(id));
    }

    @Transactional
    public MesaDto criarMesa(MesaRequest r) {
        if (mesas.existsByNumero(r.numero())) {
            throw new RegraDeNegocioException("Já existe a mesa " + r.numero());
        }
        return MesaDto.from(mesas.save(new Mesa(r.numero())));
    }

    @Transactional(readOnly = true)
    public List<MesaDto> listarMesas() {
        return mesas.findAll().stream().map(MesaDto::from).toList();
    }

    private Produto buscar(Long id) {
        return produtos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Produto", id));
    }

    private void aplicarFicha(Produto p, List<FichaLinhaRequest> linhas) {
        Set<Long> repetidos = new HashSet<>();
        for (FichaLinhaRequest l : linhas) {
            if (!repetidos.add(l.insumoId())) {
                throw new RegraDeNegocioException("Insumo " + l.insumoId() + " repetido na ficha técnica");
            }
            Insumo insumo = insumos.findById(l.insumoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Insumo", l.insumoId()));
            p.getFicha().add(new ItemFicha(p, insumo, l.quantidade()));
        }
    }
}
