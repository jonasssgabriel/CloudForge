package com.mycompany.servidorcloudforge;

import com.mycompany.cloudforge.CloudForgeServiceGrpc;
import com.mycompany.cloudforge.Estatisticas;
import com.mycompany.cloudforge.Historico;
import com.mycompany.cloudforge.NomesServiceGrpc;
import com.mycompany.cloudforge.RegistroGlobal;
import com.mycompany.cloudforge.RegistroHistorico;
import com.mycompany.cloudforge.TarefaRequest;
import com.mycompany.cloudforge.TarefaResponse;
import com.mycompany.cloudforge.Vazio;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Implementação do serviço CloudForge (uma instância = um servidor).
 *
 * Região crítica: o gRPC atende cada chamada em uma thread diferente, então os
 * contadores e o histórico são compartilhados. Todo acesso a eles usa o MESMO
 * lock e em blocos curtos. O processamento, o sleep e a chamada de rede
 * ficam SEMPRE fora do lock, senão as tarefas andariam uma de cada vez.
 */
public class CloudForgeServiceImpl extends CloudForgeServiceGrpc.CloudForgeServiceImplBase {

    private static final int MAX_FATORIAL = 5000;
    private static final int MAX_HISTORICO = 1000;
    private static final int DIGITOS_COMPLETO = 60;   // até aqui devolve o fatorial inteiro
    private static final long FATIA_MS = 200;         // de quanto em quanto tempo checa cancelamento
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String id;
    private final NomesServiceGrpc.NomesServiceBlockingStub nomes; // usado só com --global
    private final boolean global;

    // Um único lock protege todo o estado abaixo
    private final Object lock = new Object();
    private long proximoIdTarefa = 1;
    private long recebidas = 0;
    private long concluidas = 0;
    private long rejeitadas = 0;
    private int emAndamento = 0;
    private final List<RegistroHistorico> historico = new ArrayList<>();
    private volatile int limite;

    // volatile: o console altera em uma thread e as chamadas leem em outras
    private volatile long atrasoMs = 0;

    public CloudForgeServiceImpl(String id, int limite, boolean global,
            NomesServiceGrpc.NomesServiceBlockingStub nomes) {
        this.id = id;
        setLimite(limite);
        this.global = global;
        this.nomes = nomes;
    }

    public void setAtrasoMs(long ms) {
        if (ms < 0) throw new IllegalArgumentException("atraso não pode ser negativo");
        this.atrasoMs = ms;
    }
    public void setLimite(int n) {
        if (n <= 0) throw new IllegalArgumentException("limite deve ser positivo");
        this.limite = n;
    }

    @Override
    public void executarTarefa(TarefaRequest req, StreamObserver<TarefaResponse> obs) {
        // 1) Valida ANTES de ocupar vaga: entrada ruim não deve gastar capacidade
        String erro = validar(req);
        if (erro != null) {
            System.out.println("[" + id + "] ERRO cliente=" + req.getCliente() + ": " + erro);
            obs.onError(Status.INVALID_ARGUMENT.withDescription(erro).asRuntimeException());
            return;
        }

        // 2) Tenta ocupar uma vaga (bloco curto)
        boolean ocupou;
        synchronized (lock) {
            if (emAndamento < limite) {
                emAndamento++;
                recebidas++;
                ocupou = true;
            } else {
                rejeitadas++;
                ocupou = false;
            }
        }
        if (!ocupou) {
            // Rejeitar é diferente de enfileirar: o servidor não guarda o pedido,
            // o cliente é que decide tentar outra instância.
            System.out.println("[" + id + "] REJEITADA cliente=" + req.getCliente() + " (instância ocupada)");
            obs.onError(Status.RESOURCE_EXHAUSTED.withDescription("instância ocupada").asRuntimeException());
            return;
        }

        long inicio = System.nanoTime();
        boolean concluiu = false;
        try {
            // 3) Atraso simulado em fatias, para perceber se o cliente desistiu (deadline)
            long restante = atrasoMs;
            while (restante > 0) {
                if (Context.current().isCancelled()) {
                    System.out.println("[" + id + "] CANCELADA cliente=" + req.getCliente()
                            + " (cliente desistiu ou deadline estourou)");
                    obs.onError(Status.CANCELLED.withDescription("cancelada pelo cliente").asRuntimeException());
                    return; // o finally libera a vaga
                }
                long fatia = Math.min(FATIA_MS, restante);
                Thread.sleep(fatia);
                restante -= fatia;
            }

            // 4) Processa FORA do lock
            String resultado = processar(req);
            long duracao = (System.nanoTime() - inicio) / 1_000_000;
            String horario = LocalTime.now().format(HORA);

            // 5) Atualiza o estado (bloco curto) e libera a vaga junto
            long idTarefa;
            long total;
            synchronized (lock) {
                idTarefa = proximoIdTarefa++;
                concluidas++;
                total = concluidas;
                emAndamento--;
                concluiu = true; // o finally não libera de novo
                historico.add(RegistroHistorico.newBuilder().setIdTarefa(idTarefa)
                        .setTipo(req.getTipo()).setCliente(req.getCliente())
                        .setDuracaoMs(duracao).setHorario(horario).build());
                if (historico.size() > MAX_HISTORICO) {
                    historico.remove(0);
                }
            }

            System.out.println("[" + id + "] tarefa #" + idTarefa + " " + req.getTipo()
                    + " cliente=" + req.getCliente() + " concluída em " + duracao
                    + " ms (thread " + Thread.currentThread().getName() + ")");

            // Bônus: fora do lock (é chamada de rede). Se falhar, a tarefa não é derrubada.
            if (global && nomes != null) {
                try {
                    nomes.withDeadlineAfter(2, TimeUnit.SECONDS).registrarProcessamento(
                            RegistroGlobal.newBuilder().setInstancia(id).setIdTarefa(idTarefa)
                                    .setTipo(req.getTipo().name()).setCliente(req.getCliente())
                                    .setDuracaoMs(duracao).setHorario(horario).build());
                } catch (Exception e) {
                    System.out.println("[" + id + "] aviso: histórico global indisponível (" + e.getMessage() + ")");
                }
            }

            obs.onNext(TarefaResponse.newBuilder().setIdTarefa(idTarefa).setInstancia(id)
                    .setResultado(resultado).setDuracaoMs(duracao)
                    .setTotalProcessadoNaInstancia(total).build());
            obs.onCompleted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            obs.onError(Status.CANCELLED.withDescription("interrompida").asRuntimeException());
        } catch (RuntimeException e) {
            System.out.println("[" + id + "] ERRO interno: " + e);
            obs.onError(Status.INTERNAL.withDescription("erro interno").asRuntimeException());
        } finally {
            // Garante que a vaga nunca "vaza", aconteça o que acontecer
            if (!concluiu) {
                synchronized (lock) {
                    emAndamento--;
                }
            }
        }
    }

    @Override
    public void obterEstatisticas(Vazio req, StreamObserver<Estatisticas> obs) {
        obs.onNext(estatisticas());
        obs.onCompleted();
    }

    @Override
    public void obterHistorico(Vazio req, StreamObserver<Historico> obs) {
        obs.onNext(Historico.newBuilder().setInstancia(id).addAllRegistros(copiaHistorico()).build());
        obs.onCompleted();
    }

    // Leitura consistente: todos os campos lidos sob o mesmo lock
    public Estatisticas estatisticas() {
        synchronized (lock) {
            return Estatisticas.newBuilder().setInstancia(id).setRecebidas(recebidas)
                    .setConcluidas(concluidas).setRejeitadas(rejeitadas)
                    .setEmAndamento(emAndamento).setLimiteSimultaneas(limite).build();
        }
    }

    // Copia dentro do lock para ninguém iterar a lista enquanto outra thread altera
    public List<RegistroHistorico> copiaHistorico() {
        synchronized (lock) {
            return new ArrayList<>(historico);
        }
    }

    // ---------- validação e processamento (sem estado compartilhado) ----------

    private String validar(TarefaRequest req) {
        String entrada = req.getEntrada().trim();
        if (entrada.isEmpty()) {
            return "entrada vazia";
        }
        switch (req.getTipo()) {
            case CALCULO:
                try {
                    int n = Integer.parseInt(entrada);
                    if (n < 0) return "N não pode ser negativo";
                    if (n > MAX_FATORIAL) return "N máximo é " + MAX_FATORIAL;
                } catch (NumberFormatException e) {
                    return "número inválido: " + entrada;
                }
                return null;
            case PROCESSAMENTO_DADOS:
                try {
                    lerNumeros(entrada);
                } catch (NumberFormatException e) {
                    return "lista inválida (use números separados por vírgula)";
                }
                return null;
            case HASH:
                return null;
            default:
                return "tipo de tarefa desconhecido";
        }
    }

    private String processar(TarefaRequest req) {
        String entrada = req.getEntrada().trim();
        switch (req.getTipo()) {
            case CALCULO:
                return fatorial(Integer.parseInt(entrada));
            case PROCESSAMENTO_DADOS:
                return resumoDe(lerNumeros(entrada));
            default:
                return sha256(req.getEntrada());
        }
    }

    // Escolha: BigInteger, pois 21! já estoura long. Para N grande o resultado
    // tem milhares de dígitos, então devolvemos só um resumo.
    private String fatorial(int n) {
        BigInteger f = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            f = f.multiply(BigInteger.valueOf(i));
        }
        String s = f.toString();
        if (s.length() <= DIGITOS_COMPLETO) {
            return n + "! = " + s;
        }
        return n + "! = " + s.substring(0, 20) + "... (" + s.length() + " dígitos)";
    }

    private double[] lerNumeros(String entrada) {
        // Preserva campos vazios no final: "1," e "," devem ser entrada inválida.
        String[] partes = entrada.split(",", -1);
        double[] v = new double[partes.length];
        for (int i = 0; i < partes.length; i++) {
            v[i] = Double.parseDouble(partes[i].trim()); // aceita decimais
            if (!Double.isFinite(v[i])) throw new NumberFormatException("número não finito");
        }
        return v;
    }

    private String resumoDe(double[] v) {
        double[] ord = v.clone();
        Arrays.sort(ord);
        double soma = 0;
        for (double x : ord) soma += x;
        return "soma=" + fmt(soma) + ", média=" + fmt(soma / ord.length)
                + ", mínimo=" + fmt(ord[0]) + ", máximo=" + fmt(ord[ord.length - 1])
                + ", ordenada=" + Arrays.stream(ord).mapToObj(this::fmt).toList();
    }

    private String fmt(double d) {
        return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.valueOf(d);
    }

    private String sha256(String texto) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
