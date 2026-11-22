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

    private static final String ENDERECO_PADRAO = "localhost:50050";
    private final String enderecoNomes;
    private final Consumer<String> log;
    private final Object travaLista = new Object();
    private volatile List<Servidor> servidores = List.of();
    private int indice;
    private final Set<String> idsRespondidos = ConcurrentHashMap.newKeySet();
    private final AtomicInteger sequencia = new AtomicInteger();
    private final AtomicInteger idsDuplicados = new AtomicInteger();
    private volatile boolean fechado;

    private static class Servidor {
        final Instancia instancia;
        final ManagedChannel canal;
        final CloudForgeServiceGrpc.CloudForgeServiceBlockingStub stub;
        final HealthGrpc.HealthBlockingStub health;
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
        ListaInstancias resposta;
        ManagedChannel canalNomes = NettyChannelBuilder.forTarget(enderecoNomes).usePlaintext().build();
        try {
            resposta = NomesServiceGrpc.newBlockingStub(canalNomes).withDeadlineAfter(3, TimeUnit.SECONDS).listar(Vazio.newBuilder().build());
        } catch (RuntimeException e) {
            log.accept("Serviço de nomes indisponível; lista de servidores vazia. Use atualizar para tentar novamente.");
            return servidores.stream().map(s -> s.instancia).toList();
        } finally { canalNomes.shutdown(); }        Map<String, Servidor> antigos = new LinkedHashMap<>();
        for (Servidor s : servidores) antigos.put(s.instancia.getId(), s);
        List<Servidor> novos = new ArrayList<>();
        Set<String> vistos = new LinkedHashSet<>();
        for (Instancia i : resposta.getInstanciasList()) {
            if (!vistos.add(i.getId())) continue;
            Servidor existente = antigos.remove(i.getId());
            novos.add(existente != null ? existente : new Servidor(i));
        }
        synchronized (travaLista) {
            servidores = List.copyOf(novos);
            if (!servidores.isEmpty()) indice %= servidores.size(); else indice = 0;
        }
        antigos.values().forEach(s -> s.canal.shutdown());
        log.accept("Instâncias encontradas no serviço de nomes: " + resposta.getInstanciasCount());
        for (Instancia i : resposta.getInstanciasList()) log.accept("  " + i.getId() + "  " + i.getHost() + ":" + i.getPorta());
        return novos.stream().map(s -> s.instancia).toList();
    }

    public List<String> idsConhecidos() { return servidores.stream().map(s -> s.instancia.getId()).toList(); }

    // Só o avanço do índice fica sincronizado; cada verificação de saúde ocorre fora do lock.
    private int avancarIndice(int tamanho) {
        synchronized (travaLista) {
            if (servidores.size() != tamanho || tamanho == 0) return -1;
            int atual = indice;
            indice = (indice + 1) % tamanho;
            return atual;
        }
    }

    private Servidor proximoServidorSaudavel() {
        List<Servidor> copia = servidores;
        for (int i = 0; i < copia.size(); i++) {
            int atual = avancarIndice(copia.size());
            if (atual < 0) return null;
            Servidor s = copia.get(atual);
            try {
                HealthCheckResponse r = s.health.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .check(HealthCheckRequest.newBuilder().setService("").build());
                if (r.getStatus() == HealthCheckResponse.ServingStatus.SERVING) return s;
                log.accept("[CLIENTE] " + s.instancia.getId() + " pulado (" + r.getStatus() + ")");
            } catch (StatusRuntimeException e) {
                String motivo = e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED ? "deadline" : "erro: " + e.getStatus().getCode();
                log.accept("[CLIENTE] " + s.instancia.getId() + " pulado (" + motivo + ")");
            }
        }
        return null;
    }

    public Map<String, String> statusSaude() {
        Map<String, String> resultado = new LinkedHashMap<>();
        for (Servidor s : servidores) {
            try {
                HealthCheckResponse r = s.health.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .check(HealthCheckRequest.newBuilder().setService("").build());
                resultado.put(s.instancia.getId(), r.getStatus().name());
            } catch (RuntimeException e) { resultado.put(s.instancia.getId(), "INDISPONIVEL"); }
        }
        return Collections.unmodifiableMap(resultado);
    }

    public ResultadoTarefa enviar(TipoTarefa tipo, String entrada, String cliente) {
        if (fechado) return falha("Cliente encerrado", "CANCELLED");
        Servidor s = proximoServidorSaudavel();
        if (s == null) {
            atualizar();
            s = proximoServidorSaudavel();
        }
        if (s == null) return falha("Nenhum servidor saudável no momento. Tente novamente.", "UNAVAILABLE");
        for (int tentativa = 0; tentativa < 2; tentativa++) {
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
                return new ResultadoTarefa(true, r.getInstancia(), r.getIdTarefa(), r.getResultado(), r.getDuracaoMs(), "", "");
            } catch (StatusRuntimeException e) {
                Status.Code code = e.getStatus().getCode();
                String msg = mensagemErro(code);
                if (code == Status.Code.INVALID_ARGUMENT) return falha(msg, code.name());
                if (tentativa == 1 || !(code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.UNAVAILABLE || code == Status.Code.RESOURCE_EXHAUSTED)) return falha(msg, code.name());
                // As tarefas são idempotentes, então é seguro tentar uma vez outro servidor.
                log.accept("[CLIENTE] " + s.instancia.getId() + " " + code + " (" + msg + ") Tentando o próximo servidor saudável.");
                s = proximoServidorSaudavel();
                if (s == null) return falha(msg, code.name());
            } catch (RuntimeException e) { return falha("Falha inesperada ao enviar a tarefa.", "UNKNOWN"); }
        }
        return falha("Falha ao enviar a tarefa.", "UNKNOWN");
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
        List<Estatisticas> r = new ArrayList<>();
        for (Servidor s : servidores) try { r.add(s.stub.withDeadlineAfter(3, TimeUnit.SECONDS).obterEstatisticas(Vazio.newBuilder().build())); }
        catch (RuntimeException e) { log.accept("Não foi possível obter estatísticas de " + s.instancia.getId()); }
        return List.copyOf(r);
    }
    public List<Historico> historicos() {
        List<Historico> r = new ArrayList<>();
        for (Servidor s : servidores) try { r.add(s.stub.withDeadlineAfter(3, TimeUnit.SECONDS).obterHistorico(Vazio.newBuilder().build())); }
        catch (RuntimeException e) { log.accept("Não foi possível obter histórico de " + s.instancia.getId()); }
        return List.copyOf(r);
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
        long antes = estatisticas().stream().mapToLong(Estatisticas::getConcluidas).sum();
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
        long depois = estatisticas().stream().mapToLong(Estatisticas::getConcluidas).sum();
        long delta = depois - antes;
        boolean confere = delta == concluidas;
        return new ResumoConcorrencia(quantidade, concluidas, rejeitadas, outras, idsDuplicados.get() - duplicadosAntes, delta, confere);
    }

    public void fechar() {
        fechado = true;
        List<Servidor> copia = servidores;
        servidores = List.of();
        for (Servidor s : copia) s.canal.shutdown();
        for (Servidor s : copia) try { s.canal.awaitTermination(300, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
