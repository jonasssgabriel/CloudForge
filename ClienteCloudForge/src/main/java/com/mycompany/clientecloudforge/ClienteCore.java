package com.mycompany.clientecloudforge;

import com.mycompany.cloudforge.CloudForgeServiceGrpc;
import com.mycompany.cloudforge.Estatisticas;
import com.mycompany.cloudforge.Historico;
import com.mycompany.cloudforge.HistoricoGlobal;
import com.mycompany.cloudforge.Instancia;
import com.mycompany.cloudforge.ListaInstancias;
import com.mycompany.cloudforge.NomesServiceGrpc;
import com.mycompany.cloudforge.TarefaRequest;
import com.mycompany.cloudforge.TarefaResponse;
import com.mycompany.cloudforge.Vazio;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Núcleo da GUI e do console: atualizar, consultar saúde, enviar, obter dados e fechar. */
public class ClienteCore {
    public enum TipoTarefa { CALCULO, DADOS, HASH }
    public record ResultadoTarefa(boolean ok, String instancia, long idTarefa, String resultado,
            long duracaoMs, String erro, String codigoErro) { }
    public record ResumoConcorrencia(int enviadas, int concluidas, int rejeitadas,
            int outrasFalhas, int idsRepetidos, long somaConcluidasInstancias, boolean confere) { }

    public enum Fase { ESCOLHA, HEALTH_FIM, TAREFA_INICIO, TAREFA_FIM, PULADO, FALHA }
    // req identifica a chamada do cliente; idTarefa vem exclusivamente da resposta.
    public record EventoRequisicao(int req, int tentativa, String instancia, Fase fase,
            String detalhe, long ms, long idTarefa, String tipo) { }
    public interface OuvinteEventos { void evento(EventoRequisicao evento); }
    private volatile OuvinteEventos ouvinte;
    private final AtomicInteger sequenciaRequisicao = new AtomicInteger();

    public void definirOuvinteEventos(OuvinteEventos ouvinte) { this.ouvinte = ouvinte; }

    private void emitir(int req, int tentativa, String instancia, Fase fase,
            String detalhe, long ms, long idTarefa, String tipo) {
        OuvinteEventos atual = ouvinte;
        // Nenhum evento é emitido dentro de travaLista. Falha da GUI não altera a RPC.
        if (atual != null) try {
            atual.evento(new EventoRequisicao(req, tentativa, instancia, fase, detalhe, ms, idTarefa, tipo));
        } catch (RuntimeException e) { log.accept("Ouvinte de eventos indisponível: " + e.getClass().getSimpleName()); }
    }

    private static long decorrido(long inicio) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
    }

    private static final String ENDERECO_PADRAO = "localhost:50050";
    private final String enderecoNomes;
    private final Consumer<String> log;
    private final Object travaLista = new Object();
    private volatile List<Servidor> servidores = List.of();
    private int indice;
    private final Set<String> idsRespondidos = ConcurrentHashMap.newKeySet();
    private final AtomicInteger idsDuplicados = new AtomicInteger();
    private volatile boolean fechado;

    private static class Servidor {
        final Instancia instancia;
        final ManagedChannel canal;
        final CloudForgeServiceGrpc.CloudForgeServiceBlockingStub stub;
        final HealthGrpc.HealthBlockingStub health;
        // Protegidos por travaLista: só fecha um canal retirado depois do último uso.
        int usos;
        boolean retirado;
        Servidor(Instancia i) {
            instancia = i;
            canal = NettyChannelBuilder.forAddress(i.getHost(), i.getPorta()).usePlaintext().build();
            stub = CloudForgeServiceGrpc.newBlockingStub(canal);
            health = HealthGrpc.newBlockingStub(canal);
        }
    }

    public ClienteCore(String enderecoNomes, Consumer<String> log) {
        this.enderecoNomes = (enderecoNomes == null || enderecoNomes.isBlank()) ? ENDERECO_PADRAO : enderecoNomes;
        this.log = log == null ? s -> { } : log;
        atualizar();
    }

    public List<Instancia> atualizar() {
        List<Servidor> base = reservarServidores();
        List<Servidor> criados = new ArrayList<>();
        boolean publicou = false;
        try {
            if (fechado) return List.of();
            ListaInstancias resposta;
            ManagedChannel canalNomes = NettyChannelBuilder.forTarget(enderecoNomes).usePlaintext().build();
            try {
                resposta = NomesServiceGrpc.newBlockingStub(canalNomes).withDeadlineAfter(3, TimeUnit.SECONDS).listar(Vazio.newBuilder().build());
            } catch (RuntimeException e) {
                log.accept("Serviço de nomes indisponível; lista anterior mantida. Use atualizar para tentar novamente.");
                return servidores.stream().map(s -> s.instancia).toList();
            } finally { canalNomes.shutdown(); }
            Map<String, Servidor> antigos = new LinkedHashMap<>();
            for (Servidor s : base) antigos.put(s.instancia.getId(), s);
            List<Servidor> novos = new ArrayList<>();
            Set<String> vistos = new LinkedHashSet<>();
            for (Instancia i : resposta.getInstanciasList()) {
                if (!vistos.add(i.getId())) continue;
                Servidor existente = antigos.get(i.getId());
                if (existente != null && existente.instancia.equals(i)) {
                    antigos.remove(i.getId());
                    novos.add(existente);
                } else {
                    Servidor novo = new Servidor(i);
                    criados.add(novo);
                    novos.add(novo);
                }
            }
            synchronized (travaLista) {
                // Outra atualização ou fechar venceu: não publica uma lista baseada em estado antigo.
                if (fechado || servidores != base) return servidores.stream().map(s -> s.instancia).toList();
                servidores = List.copyOf(novos);
                antigos.values().forEach(s -> s.retirado = true);
                if (!servidores.isEmpty()) indice %= servidores.size(); else indice = 0;
                publicou = true;
            }
            log.accept("Instâncias encontradas no serviço de nomes: " + resposta.getInstanciasCount());
            for (Instancia i : resposta.getInstanciasList()) log.accept("  " + i.getId() + "  " + i.getHost() + ":" + i.getPorta());
            return novos.stream().map(s -> s.instancia).toList();
        } finally {
            if (!publicou) criados.forEach(s -> s.canal.shutdown());
            liberarServidores(base);
        }
    }

    public List<String> idsConhecidos() { return servidores.stream().map(s -> s.instancia.getId()).toList(); }

    private List<Servidor> reservarServidores() {
        synchronized (travaLista) {
            List<Servidor> copia = servidores;
            copia.forEach(s -> s.usos++);
            return copia;
        }
    }

    private void liberarServidores(List<Servidor> copia) {
        List<Servidor> fechar = new ArrayList<>();
        synchronized (travaLista) {
            for (Servidor s : copia) {
                s.usos--;
                if (s.retirado && s.usos == 0) fechar.add(s);
            }
        }
        // Encerrar transporte também fica fora da região crítica.
        fechar.forEach(s -> s.canal.shutdown());
    }

    // Só o avanço do índice fica sincronizado; cada verificação de saúde ocorre fora do lock.
    private int avancarIndice(int tamanho) {
        synchronized (travaLista) {
            if (tamanho == 0) return -1;
            int atual = indice % tamanho;
            indice = (atual + 1) % tamanho;
            return atual;
        }
    }

    private Servidor proximoServidorSaudavel(List<Servidor> copia, Servidor ignorar, int req, int tentativa, String tipo) {
        int inicio = avancarIndice(copia.size());
        // Cada varredura visita cada candidato uma vez, mesmo com outras threads avançando.
        for (int i = 0; i < copia.size(); i++) {
            int atual = (inicio + i) % copia.size();
            Servidor s = copia.get(atual);
            if (s == ignorar) continue;
            String id = s.instancia.getId();
            emitir(req, tentativa, id, Fase.ESCOLHA, "Health iniciado", 0, 0, tipo);
            long inicioHealth = System.nanoTime();
            try {
                HealthCheckResponse r = s.health.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .check(HealthCheckRequest.newBuilder().setService("").build());
                emitir(req, tentativa, id, Fase.HEALTH_FIM, r.getStatus().name(), decorrido(inicioHealth), 0, tipo);
                if (r.getStatus() == HealthCheckResponse.ServingStatus.SERVING) return s;
                emitir(req, tentativa, id, Fase.PULADO, r.getStatus().name(), 0, 0, tipo);
                log.accept("[CLIENTE] " + s.instancia.getId() + " pulado (" + r.getStatus() + ")");
            } catch (StatusRuntimeException e) {
                emitir(req, tentativa, id, Fase.HEALTH_FIM, e.getStatus().getCode().name(), decorrido(inicioHealth), 0, tipo);
                emitir(req, tentativa, id, Fase.PULADO, e.getStatus().getCode().name(), 0, 0, tipo);
                String motivo = e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED ? "deadline" : "erro: " + e.getStatus().getCode();
                log.accept("[CLIENTE] " + s.instancia.getId() + " pulado (" + motivo + ")");
            }
        }
        return null;
    }

    public Map<String, String> statusSaude() {
        List<Servidor> copia = reservarServidores();
        try {
            Map<String, String> resultado = new LinkedHashMap<>();
            for (Servidor s : copia) {
                try {
                    HealthCheckResponse r = s.health.withDeadlineAfter(2, TimeUnit.SECONDS)
                            .check(HealthCheckRequest.newBuilder().setService("").build());
                    resultado.put(s.instancia.getId(), r.getStatus().name());
                } catch (RuntimeException e) { resultado.put(s.instancia.getId(), "INDISPONIVEL"); }
            }
            return Collections.unmodifiableMap(resultado);
        } finally { liberarServidores(copia); }
    }

    public ResultadoTarefa enviar(TipoTarefa tipo, String entrada, String cliente) {
        int req = sequenciaRequisicao.incrementAndGet();
        if (fechado) return falha("Cliente encerrado", "CANCELLED");
        List<Servidor> copia = reservarServidores();
        try {
            Servidor s = proximoServidorSaudavel(copia, null, req, 1, tipo.name());
            if (s == null) {
                liberarServidores(copia);
                copia = List.of(); // não libera duas vezes se atualizar falhar
                atualizar();
                copia = reservarServidores();
                s = proximoServidorSaudavel(copia, null, req, 1, tipo.name());
            }
            if (s == null) {
                emitir(req, 1, "", Fase.FALHA, "UNAVAILABLE: nenhum servidor saudável", 0, 0, tipo.name());
                return falha("Nenhum servidor saudável no momento. Tente novamente.", "UNAVAILABLE");
            }
            for (int tentativa = 0; tentativa < 2; tentativa++) {
                long inicio = System.nanoTime();
                emitir(req, tentativa + 1, s.instancia.getId(), Fase.TAREFA_INICIO, "ExecutarTarefa", 0, 0, tipo.name());
                try {
                    com.mycompany.cloudforge.TipoTarefa t = switch (tipo) {
                        case CALCULO -> com.mycompany.cloudforge.TipoTarefa.CALCULO;
                        case DADOS -> com.mycompany.cloudforge.TipoTarefa.PROCESSAMENTO_DADOS;
                        case HASH -> com.mycompany.cloudforge.TipoTarefa.HASH;
                    };
                    TarefaResponse r = s.stub.withDeadlineAfter(5, TimeUnit.SECONDS).executarTarefa(
                            TarefaRequest.newBuilder().setTipo(t).setEntrada(entrada).setCliente(cliente == null ? "cliente" : cliente).build());
                    if (!idsRespondidos.add(r.getInstancia() + "#" + r.getIdTarefa())) {
                        idsDuplicados.incrementAndGet();
                        log.accept("[CLIENTE] ID repetido: " + r.getInstancia() + "#" + r.getIdTarefa());
                    }
                    emitir(req, tentativa + 1, r.getInstancia(), Fase.TAREFA_FIM, "OK", r.getDuracaoMs(), r.getIdTarefa(), tipo.name());
                    return new ResultadoTarefa(true, r.getInstancia(), r.getIdTarefa(), r.getResultado(), r.getDuracaoMs(), "", "");
                } catch (StatusRuntimeException e) {
                    Status.Code code = e.getStatus().getCode();
                    emitir(req, tentativa + 1, s.instancia.getId(), Fase.FALHA, code.name(), decorrido(inicio), 0, tipo.name());
                    String msg = mensagemErro(code);
                    if (code == Status.Code.INVALID_ARGUMENT) return falha(msg, code.name());
                    if (tentativa == 1 || !(code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.UNAVAILABLE || code == Status.Code.RESOURCE_EXHAUSTED)) return falha(msg, code.name());
                    // O cálculo pode ser repetido; contadores e histórico não têm garantia de execução única.
                    log.accept("[CLIENTE] " + s.instancia.getId() + " " + code + " (" + msg + ") Tentando o próximo servidor saudável.");
                    s = proximoServidorSaudavel(copia, s, req, tentativa + 2, tipo.name());
                    if (s == null) return falha(msg, code.name());
                } catch (RuntimeException e) {
                    emitir(req, tentativa + 1, s.instancia.getId(), Fase.FALHA, "UNKNOWN", decorrido(inicio), 0, tipo.name());
                    return falha("Falha inesperada ao enviar a tarefa.", "UNKNOWN");
                }
            }
            return falha("Falha ao enviar a tarefa.", "UNKNOWN");
        } finally { liberarServidores(copia); }
    }

    private ResultadoTarefa falha(String erro, String codigo) { return new ResultadoTarefa(false, "", 0, "", 0, erro, codigo); }
    private String mensagemErro(Status.Code c) {
        return switch (c) {
            case DEADLINE_EXCEEDED -> "Tempo limite excedido; tente novamente.";
            case UNAVAILABLE -> "Servidor indisponível no momento.";
            case RESOURCE_EXHAUSTED -> "Servidor sem capacidade; tente novamente.";
            case INVALID_ARGUMENT -> "Entrada inválida: confira os dados informados.";
            default -> "Falha na tarefa (" + c + ").";
        };
    }

    public List<Estatisticas> estatisticas() {
        List<Servidor> copia = reservarServidores();
        try {
            List<Estatisticas> r = new ArrayList<>();
            for (Servidor s : copia) try { r.add(s.stub.withDeadlineAfter(3, TimeUnit.SECONDS).obterEstatisticas(Vazio.newBuilder().build())); }
            catch (RuntimeException e) { log.accept("Não foi possível obter estatísticas de " + s.instancia.getId()); }
            return List.copyOf(r);
        } finally { liberarServidores(copia); }
    }
    public List<Historico> historicos() {
        List<Servidor> copia = reservarServidores();
        try {
            List<Historico> r = new ArrayList<>();
            for (Servidor s : copia) try { r.add(s.stub.withDeadlineAfter(3, TimeUnit.SECONDS).obterHistorico(Vazio.newBuilder().build())); }
            catch (RuntimeException e) { log.accept("Não foi possível obter histórico de " + s.instancia.getId()); }
            return List.copyOf(r);
        } finally { liberarServidores(copia); }
    }
    public HistoricoGlobal historicoGlobal() {
        ManagedChannel c = NettyChannelBuilder.forTarget(enderecoNomes).usePlaintext().build();
        try {
            return NomesServiceGrpc.newBlockingStub(c).withDeadlineAfter(3, TimeUnit.SECONDS).obterHistoricoGlobal(Vazio.newBuilder().build());
        } catch (RuntimeException e) { return null; }
        finally { c.shutdown(); }
    }
    public ResumoConcorrencia concorrencia(int n) {
        int quantidade = Math.max(0, n);
        int duplicadosAntes = idsDuplicados.get();
        List<String> esperadas = idsConhecidos();
        Map<String, Long> antes = new LinkedHashMap<>();
        for (Estatisticas e : estatisticas()) antes.put(e.getInstancia(), e.getConcluidas());
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(quantidade, 32)));
        List<Future<ResultadoTarefa>> futuros = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            final int k = i;
            futuros.add(pool.submit(() -> {
                TipoTarefa tipo = switch (k % 3) { case 0 -> TipoTarefa.HASH; case 1 -> TipoTarefa.CALCULO; default -> TipoTarefa.DADOS; };
                String entrada = switch (tipo) { case HASH -> "texto " + k; case CALCULO -> Integer.toString(k % 20); case DADOS -> "1,2,3"; };
                return enviar(tipo, entrada, "c" + Thread.currentThread().getId());
            }));
        }
        pool.shutdown();
        int concluidas = 0, rejeitadas = 0, outras = 0;
        for (Future<ResultadoTarefa> f : futuros) try {
            ResultadoTarefa r = f.get();
            if (r.ok()) concluidas++; else if ("RESOURCE_EXHAUSTED".equals(r.codigoErro())) rejeitadas++; else outras++;
        } catch (Exception e) { outras++; }
        try { pool.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Map<String, Long> depois = new LinkedHashMap<>();
        for (Estatisticas e : estatisticas()) depois.put(e.getInstancia(), e.getConcluidas());
        // Só compara a mesma população. Uma instância caída não tem contador final igual a zero.
        boolean comparavel = antes.keySet().equals(new LinkedHashSet<>(esperadas))
                && antes.keySet().equals(depois.keySet())
                && esperadas.equals(idsConhecidos());
        long delta = 0;
        for (Map.Entry<String, Long> e : antes.entrySet()) {
            Long fim = depois.get(e.getKey());
            if (fim == null || fim < e.getValue()) comparavel = false;
            else delta += fim - e.getValue();
        }
        if (!comparavel) {
            log.accept("Comparação inconclusiva: faltam estatísticas ou houve mudança/reinício de instância. Delta indisponível (-1).");
            delta = -1; // sentinela: preserva o contrato usado pela GUI
        }
        boolean confere = comparavel && delta == concluidas && idsDuplicados.get() == duplicadosAntes;
        return new ResumoConcorrencia(quantidade, concluidas, rejeitadas, outras, idsDuplicados.get() - duplicadosAntes, delta, confere);
    }

    public void fechar() {
        List<Servidor> fechar = new ArrayList<>();
        synchronized (travaLista) {
            fechado = true;
            for (Servidor s : servidores) {
                s.retirado = true;
                if (s.usos == 0) fechar.add(s);
            }
            servidores = List.of();
            indice = 0;
        }
        fechar.forEach(s -> s.canal.shutdown());
    }
}
