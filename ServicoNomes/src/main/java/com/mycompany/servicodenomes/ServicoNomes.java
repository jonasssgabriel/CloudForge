package com.mycompany.servicodenomes;

import io.grpc.Server;
import io.grpc.ServerBuilder;

/**
 * Serviço de nomes: os servidores se registram aqui e os clientes descobrem onde eles estão.
 * Uso: ServicoNomes [porta]  (padrão 50050)
 */
public class ServicoNomes {

    public static void main(String[] args) throws Exception {
        int porta = 50050;
        if (args.length > 0) {
            porta = Integer.parseInt(args[0]);
        }

        Server server = ServerBuilder.forPort(porta)
                .addService(new NomesServiceImpl())
                .build()
                .start();
        System.out.println("[NOMES] Serviço de nomes ouvindo na porta " + porta);

        // Hook: ao fechar (Ctrl+C), encerra o servidor sem cortar chamadas em andamento
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[NOMES] Encerrando...");
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
