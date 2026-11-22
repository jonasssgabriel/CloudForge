package com.mycompany.clientecloudforge;

import com.mycompany.cloudforge.Estatisticas;
import com.mycompany.cloudforge.Historico;
import com.mycompany.cloudforge.HistoricoGlobal;
import com.mycompany.cloudforge.RegistroGlobal;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class ClienteCloudForge {
    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
        String nomes = args.length > 0 ? args[0] : "localhost:50050";
        ClienteCore core = new ClienteCore(nomes, System.out::println);
        System.out.println("Comandos: listar, atualizar, enviar <CALCULO|DADOS|HASH> <entrada>, rr <n>, concorrencia <n>, stats, historico, historicoglobal, ajuda, sair");
        try (Scanner scanner = new Scanner(System.in)) {
            boolean rodando = true;
            while (rodando && scanner.hasNextLine()) {
                System.out.print("> ");
                String linha = scanner.nextLine().trim();
                if (linha.isEmpty()) continue;
                String[] partes = linha.split("\\s+", 3);
                String comando = partes[0].toLowerCase();
                switch (comando) {
                    case "listar" -> core.atualizar();
                    case "atualizar" -> core.atualizar();
                    case "enviar" -> enviar(core, partes);
                    case "rr" -> rodizio(core, partes);
                    case "concorrencia" -> concorrencia(core, partes);
                    case "stats" -> core.estatisticas().forEach(ClienteCloudForge::mostrarEstatistica);
                    case "historico" -> core.historicos().forEach(ClienteCloudForge::mostrarHistorico);
                    case "historicoglobal" -> mostrarGlobal(core.historicoGlobal());
                    case "ajuda" -> System.out.println("listar | atualizar | enviar <CALCULO|DADOS|HASH> <entrada> | rr <n> | concorrencia <n> | stats | historico | historicoglobal | ajuda | sair");
                    case "sair" -> rodando = false;
                    default -> System.out.println("Comando desconhecido. Digite ajuda.");
                }
            }
        } finally { core.fechar(); }
    }

    private static void enviar(ClienteCore core, String[] p) {
        if (p.length < 3) { System.out.println("Uso: enviar <CALCULO|DADOS|HASH> <entrada>"); return; }
        ClienteCore.TipoTarefa tipo = tipo(p[1]);
        if (tipo == null) { System.out.println("Tipo inválido. Use CALCULO, DADOS ou HASH."); return; }
        mostrarResultado(core.enviar(tipo, p[2], "c" + Thread.currentThread().getId()));
    }
    private static ClienteCore.TipoTarefa tipo(String valor) {
        return switch (valor.toUpperCase()) { case "CALCULO" -> ClienteCore.TipoTarefa.CALCULO; case "DADOS", "PROCESSAMENTO_DADOS" -> ClienteCore.TipoTarefa.DADOS; case "HASH" -> ClienteCore.TipoTarefa.HASH; default -> null; };
    }
    private static void rodizio(ClienteCore core, String[] p) {
        int n = numero(p, "rr");
        for (int i = 0; i < n; i++) {
            ClienteCore.TipoTarefa tipo = switch (i % 3) { case 0 -> ClienteCore.TipoTarefa.HASH; case 1 -> ClienteCore.TipoTarefa.CALCULO; default -> ClienteCore.TipoTarefa.DADOS; };
            String entrada = switch (tipo) { case HASH -> "texto " + i; case CALCULO -> Integer.toString(i % 20); case DADOS -> "1,2,3"; };
            mostrarResultado(core.enviar(tipo, entrada, "c" + Thread.currentThread().getId()));
        }
    }
    private static void concorrencia(ClienteCore core, String[] p) {
        ClienteCore.ResumoConcorrencia r = core.concorrencia(numero(p, "concorrencia"));
        System.out.printf("Enviadas: %d, concluídas: %d, rejeitadas: %d, outras falhas: %d%n", r.enviadas(), r.concluidas(), r.rejeitadas(), r.outrasFalhas());
        System.out.println("IDs de tarefa repetidos por instância: " + r.idsRepetidos());
        System.out.println("Soma concluídas nas instâncias (delta): " + r.somaConcluidasInstancias() + " — " + (r.confere() ? "OK" : "ALERTA"));
    }
    private static int numero(String[] p, String comando) {
        try { int n = Integer.parseInt(p[1]); return Math.max(0, n); }
        catch (Exception e) { System.out.println("Uso: " + comando + " <número>"); return 0; }
    }
    private static void mostrarResultado(ClienteCore.ResultadoTarefa r) {
        if (r.ok()) System.out.printf("Tarefa #%d respondida por %s: %s (%d ms)%n", r.idTarefa(), r.instancia(), r.resultado(), r.duracaoMs());
        else System.out.println(r.erro());
    }
    private static void mostrarEstatistica(Estatisticas e) {
        System.out.printf("%s: recebidas=%d concluídas=%d rejeitadas=%d em andamento=%d limite=%d%n", e.getInstancia(), e.getRecebidas(), e.getConcluidas(), e.getRejeitadas(), e.getEmAndamento(), e.getLimiteSimultaneas());
    }
    private static void mostrarHistorico(Historico h) {
        System.out.println(h.getInstancia() + ": " + h.getRegistrosCount() + " registros");
        h.getRegistrosList().forEach(x -> System.out.printf("  #%d %s %s %d ms%n", x.getIdTarefa(), x.getTipo(), x.getHorario(), x.getDuracaoMs()));
    }
    private static void mostrarGlobal(HistoricoGlobal h) {
        if (h == null) { System.out.println("Histórico global indisponível ou desativado."); return; }
        System.out.println("Total global: " + h.getTotal());
        if (h.getRegistrosCount() == 0) { System.out.println("Histórico global vazio (ou --global desligado)."); return; }
        List<RegistroGlobal> lista = h.getRegistrosList();
        for (int i = Math.max(0, lista.size() - 20); i < lista.size(); i++) {
            RegistroGlobal x = lista.get(i);
            System.out.printf("%s #%d %s %s%n", x.getInstancia(), x.getIdTarefa(), x.getTipo(), x.getHorario());
        }
    }
}
