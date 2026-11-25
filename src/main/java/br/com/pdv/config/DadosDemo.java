package br.com.pdv.config;

import br.com.pdv.dto.Dtos.*;
import br.com.pdv.domain.Praca;
import br.com.pdv.repository.ProdutoRepository;
import br.com.pdv.service.CatalogoService;
import br.com.pdv.service.EstoqueService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Cardápio de hamburgueria para testar a API sem cadastrar nada. Só roda com o banco vazio. */
@Component
@ConditionalOnProperty(name = "pdv.seed", havingValue = "true")
public class DadosDemo implements ApplicationRunner {

    private final ProdutoRepository produtos;
    private final EstoqueService estoque;
    private final CatalogoService catalogo;

    public DadosDemo(ProdutoRepository produtos, EstoqueService estoque, CatalogoService catalogo) {
        this.produtos = produtos;
        this.estoque = estoque;
        this.catalogo = catalogo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (produtos.count() > 0) {
            return;
        }
        Long pao = insumo("Pão brioche", "un", 60, 12, "1.80");
        Long carne = insumo("Carne 180g", "un", 50, 10, "6.50");
        Long queijo = insumo("Queijo cheddar (fatia)", "un", 120, 20, "0.90");
        Long batata = insumo("Batata", "kg", 15, 3, "7.00");
        Long cerveja = insumo("Cerveja long neck", "un", 48, 12, "5.50");
        Long refri = insumo("Refrigerante lata", "un", 48, 12, "2.80");
        Long sorvete = insumo("Sorvete de creme", "kg", 4, 1, "28.00");

        produto("X-Burger", "Lanches", "32.00", Praca.COZINHA, linha(pao, "1"), linha(carne, "1"), linha(queijo, "2"));
        produto("Batata frita", "Porções", "22.00", Praca.COZINHA, linha(batata, "0.3"));
        produto("Cerveja long neck", "Bebidas", "12.00", Praca.BAR, linha(cerveja, "1"));
        produto("Refrigerante lata", "Bebidas", "7.00", Praca.BAR, linha(refri, "1"));
        produto("Sorvete (bola dupla)", "Sobremesas", "15.00", Praca.SOBREMESA, linha(sorvete, "0.12"));

        for (int n = 1; n <= 10; n++) {
            catalogo.criarMesa(new MesaRequest(n));
        }
    }

    private Long insumo(String nome, String un, int qtd, int minimo, String custo) {
        return estoque.criar(new InsumoRequest(nome, un, BigDecimal.valueOf(qtd), BigDecimal.valueOf(minimo),
                new BigDecimal(custo))).id();
    }

    private FichaLinhaRequest linha(Long insumoId, String qtd) {
        return new FichaLinhaRequest(insumoId, new BigDecimal(qtd));
    }

    private void produto(String nome, String categoria, String preco, Praca praca, FichaLinhaRequest... ficha) {
        catalogo.criar(new ProdutoRequest(nome, categoria, new BigDecimal(preco), praca, List.of(ficha)));
    }
}
