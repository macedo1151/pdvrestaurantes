package br.com.pdv.service;

import br.com.pdv.domain.Praca;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Avisa as telas do KDS, em tempo real (Server-Sent Events), quando a fila da praça delas muda.
 *
 * O aviso só sai depois do commit: se a transação for desfeita (estoque insuficiente, por exemplo), ninguém é
 * avisado de uma mudança que não aconteceu, e a tela que buscar a fila logo depois já enxerga o dado gravado.
 * O evento não carrega a fila, só avisa que ela mudou: a tela consulta a API, que continua sendo a única fonte.
 */
@Component
public class KdsEventos implements SmartLifecycle {

    private volatile boolean rodando;

    private static final Logger log = LoggerFactory.getLogger(KdsEventos.class);
    /** Passado esse tempo o navegador reconecta sozinho (EventSource), o que também limpa conexões mortas. */
    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<Praca, Set<SseEmitter>> assinantes = new EnumMap<>(Praca.class);

    public KdsEventos() {
        for (Praca p : Praca.values()) {
            assinantes.put(p, new CopyOnWriteArraySet<>());
        }
    }

    public SseEmitter assinar(Praca praca) {
        SseEmitter emissor = new SseEmitter(TIMEOUT_MS);
        Set<SseEmitter> grupo = assinantes.get(praca);
        grupo.add(emissor);
        emissor.onCompletion(() -> grupo.remove(emissor));
        emissor.onTimeout(() -> grupo.remove(emissor));
        emissor.onError(e -> grupo.remove(emissor));
        try {
            // confirma a assinatura: a tela já pode buscar a fila sem risco de perder uma mudança no intervalo
            emissor.send(SseEmitter.event().name("conectado").data(praca.name()));
        } catch (IOException | IllegalStateException e) {
            grupo.remove(emissor);
        }
        return emissor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void aoMudarAFila(FilaMudou evento) {
        enviar(evento.praca(), SseEmitter.event().name("fila").data(evento.praca().name()));
    }

    /** Comentário SSE periódico: mantém a conexão viva atrás de proxies e derruba assinantes que já saíram. */
    @Scheduled(fixedRate = 25_000)
    void batimento() {
        for (Praca p : Praca.values()) {
            enviar(p, SseEmitter.event().comment("keep-alive"));
        }
    }

    // ---------- ciclo de vida ----------

    @Override
    public void start() {
        rodando = true;
    }

    /**
     * Ao desligar o servidor, encerra os streams abertos. O Spring Boot desliga o Tomcat de forma graciosa,
     * esperando as requisições ativas terminarem, e um stream SSE nunca termina sozinho: sem isto cada deploy
     * esperaria o tempo limite inteiro (30 s). O navegador reconecta ao novo servidor por conta própria.
     */
    @Override
    public void stop() {
        rodando = false;
        for (Set<SseEmitter> grupo : assinantes.values()) {
            for (SseEmitter emissor : grupo) {
                try {
                    emissor.complete();
                } catch (RuntimeException ignorado) {
                    // já estava encerrado
                }
            }
            grupo.clear();
        }
    }

    @Override
    public boolean isRunning() {
        return rodando;
    }

    /** Fase mais alta que a do desligamento gracioso do servidor web: este para primeiro. */
    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE;
    }

    private void enviar(Praca praca, SseEmitter.SseEventBuilder evento) {
        for (SseEmitter emissor : assinantes.get(praca)) {
            try {
                emissor.send(evento);
            } catch (IOException | IllegalStateException e) {
                assinantes.get(praca).remove(emissor);
                log.debug("Assinante do KDS {} removido: {}", praca, e.toString());
            }
        }
    }
}
