package com.mycompany.servidorcloudforge;

import com.mycompany.cloudforge.CloudForgeServiceGrpc;
import com.mycompany.cloudforge.Instancia;
import com.mycompany.cloudforge.NomesServiceGrpc;
import com.mycompany.cloudforge.RegistroHistorico;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.HealthStatusManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uso: ServidorCloudForge porta [enderecoNomes] [host] [--global] [--limite=N]
 */
public class ServidorCloudForge {

    private static HealthStatusManager health;
    private static NomesServiceGrpc.NomesServiceBlockingStub nomes;
    private static Instancia eu;
    private static final AtomicBoolean encerrado = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        // Separa flags dos argumentos posicionais (as flags podem estar em qualquer posição)
        List<String> pos = new ArrayList<>();
        boolean global = false;
        int limite = 4;
        for (String a : args) {
            if (a.equals("--global")) {
                global = true;
            } else if (a.startsWith("--limite=")) {
                limite = Integer.parseInt(a.substring("--limite=".length()));
            } else if (!a.startsWith("--")) {
                pos.add(a);
            }
        }
        if (pos.isEmpty()) {
            System.out.println("Uso: ServidorCloudForge <porta> [enderecoNomes=localhost:50050] [host=localhost] [--global] [--limite=N]");
            return;
        }
        int porta = Integer.parseInt(pos.get(0));
        String enderecoNomes = pos.size() > 1 ? pos.get(1) : "localhost:50050";
        String host = pos.size() > 2 ? pos.get(2) : "localhost";
        String id = "srv-" + porta;

        ManagedChannel canalNomes = ManagedChannelBuilder.forTarget(enderecoNomes).usePlaintext().build();
        nomes = NomesServiceGrpc.newBlockingStub(canalNomes);
        eu = Instancia.newBuilder().setId(id).setHost(host).setPorta(porta).build();

        CloudForgeServiceImpl impl = new CloudForgeServiceImpl(id, limite, global, nomes);
        health = new HealthStatusManager();
        Server server = ServerBuilder.forPort(porta)
                .addService(health.getHealthService()) // health: o cliente consulta se estou SERVING
                .addService(impl)
                .build()
                .start();
        definirHealth(ServingStatus.SERVING);
        System.out.println("[" + id + "] no ar na porta " + porta + " (limite=" + limite
                + ", global=" + global + "). Digite 'ajuda' para os comandos.");

        // Registro no serviço de nomes: se estiver fora, só avisa e segue
        try {
            nomes.withDeadlineAfter(2, TimeUnit.SECONDS).registrar(eu);
            System.out.println("[" + id + "] registrado em " + enderecoNomes);
        } catch (Exception e) {
            System.out.println("[" + id + "] ERRO: não consegui registrar no nomes (" + e.getMessage() + ")");
        }

        // Ctrl+C: sai do serviço de nomes e do health antes de morrer
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            encerrar();
            server.shutdown();
        }));

        iniciarConsole(impl, server);
        server.awaitTermination();
        canalNomes.shutdownNow();
    }

    private static void definirHealth(ServingStatus s) {
        health.setStatus("", s); // status geral do servidor
        health.setStatus(CloudForgeServiceGrpc.SERVICE_NAME, s); // status do serviço
    }

    // Executa uma vez só (o hook e o comando 'sair' podem chamar os dois)
    private static void encerrar() {
        if (!encerrado.compareAndSet(false, true)) return;
        try {
            nomes.withDeadlineAfter(2, TimeUnit.SECONDS).desregistrar(eu);
        } catch (Exception e) {
            System.out.println("aviso: não consegui desregistrar (" + e.getMessage() + ")");
        }
        definirHealth(ServingStatus.NOT_SERVING);
    }

    private static void iniciarConsole(CloudForgeServiceImpl impl, Server server) {
        Thread t = new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            // Em EOF (sem terminal) o loop apenas termina; o servidor continua
            while (sc.hasNextLine()) {
                String[] p = sc.nextLine().trim().split("\\s+");
                try {
                    switch (p[0].toLowerCase()) {
                        case "serving":
                            definirHealth(ServingStatus.SERVING);
                            System.out.println("Health: SERVING");
                            break;
                        case "notserving":
                            definirHealth(ServingStatus.NOT_SERVING);
                            System.out.println("Health: NOT_SERVING");
                            break;
                        case "atraso":
                            impl.setAtrasoMs(Long.parseLong(p[1]));
                            System.out.println("Atraso simulado: " + p[1] + " ms");
                            break;
                        case "limite":
                            impl.setLimite(Integer.parseInt(p[1]));
                            System.out.println("Limite de tarefas simultâneas: " + p[1]);
                            break;
                        case "stats":
                            var e = impl.estatisticas();
                            System.out.println("recebidas=" + e.getRecebidas() + " concluídas=" + e.getConcluidas()
                                    + " rejeitadas=" + e.getRejeitadas() + " emAndamento=" + e.getEmAndamento()
                                    + " limite=" + e.getLimiteSimultaneas());
                            break;
                        case "historico":
                            List<RegistroHistorico> h = impl.copiaHistorico();
                            for (int i = Math.max(0, h.size() - 20); i < h.size(); i++) {
                                RegistroHistorico r = h.get(i);
                                System.out.println("#" + r.getIdTarefa() + " " + r.getTipo() + " cliente="
                                        + r.getCliente() + " " + r.getDuracaoMs() + " ms às " + r.getHorario());
                            }
                            break;
                        case "ajuda":
                            System.out.println("Comandos: serving | notserving | atraso <ms> | limite <n> | stats | historico | ajuda | sair");
                            break;
                        case "sair":
                            encerrar();
                            server.shutdown();
                            return;
                        case "":
                            break;
                        default:
                            System.out.println("Comando desconhecido. Digite 'ajuda'.");
                    }
                } catch (RuntimeException ex) {
                    System.out.println("Comando inválido: " + ex.getMessage());
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
