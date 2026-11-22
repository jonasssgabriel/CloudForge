package com.mycompany.servicodenomes;

import com.mycompany.cloudforge.HistoricoGlobal;
import com.mycompany.cloudforge.Instancia;
import com.mycompany.cloudforge.ListaInstancias;
import com.mycompany.cloudforge.NomesServiceGrpc;
import com.mycompany.cloudforge.RegistroGlobal;
import com.mycompany.cloudforge.Resposta;
import com.mycompany.cloudforge.Vazio;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NomesServiceImpl extends NomesServiceGrpc.NomesServiceImplBase {

    private static final int MAX_HISTORICO = 5000;

    // O gRPC atende várias chamadas em threads diferentes. Usamos synchronized em métodos
    // curtos (em vez de ConcurrentHashMap) porque é mais simples de entender e suficiente aqui.
    // O Map é só acessado pelos métodos sincronizados abaixo.
    private final Map<String, Instancia> instancias = new LinkedHashMap<>();

    private final List<RegistroGlobal> historico = new ArrayList<>();
    private long totalRecebido = 0;

    // ---------- acesso ao mapa (curto e sincronizado) ----------

    private synchronized void guardar(Instancia i) {
        instancias.put(i.getId(), i); // mesmo id substitui o anterior
    }

    private synchronized boolean remover(String id) {
        return instancias.remove(id) != null;
    }

    private synchronized List<Instancia> copiaInstancias() {
        return new ArrayList<>(instancias.values());
    }

    // ---------- RPCs ----------

    @Override
    public void registrar(Instancia req, StreamObserver<Resposta> out) {
        if (req.getId().isEmpty() || req.getPorta() <= 0) {
            out.onError(Status.INVALID_ARGUMENT
                    .withDescription("id vazio ou porta inválida").asRuntimeException());
            return;
        }
        guardar(req);
        System.out.println("[NOMES] Registrada: " + req.getId() + " em "
                + req.getHost() + ":" + req.getPorta());
        responder(out, true, "Registrada");
    }

    @Override
    public void desregistrar(Instancia req, StreamObserver<Resposta> out) {
        if (req.getId().isEmpty()) {
            out.onError(Status.INVALID_ARGUMENT
                    .withDescription("id vazio").asRuntimeException());
            return;
        }
        boolean existia = remover(req.getId());
        System.out.println("[NOMES] Removida: " + req.getId());
        responder(out, existia, existia ? "Removida" : "Instância não encontrada");
    }

    @Override
    public void listar(Vazio req, StreamObserver<ListaInstancias> out) {
        // Propositalmente NÃO há health check: se um servidor cair sem desregistrar,
        // ele continua na lista e o cliente é quem trata a falha ao tentar conectar.
        List<Instancia> lista = copiaInstancias();
        System.out.println("[NOMES] Listar -> " + lista.size() + " instâncias");
        out.onNext(ListaInstancias.newBuilder().addAllInstancias(lista).build());
        out.onCompleted();
    }

    // ---------- BÔNUS: histórico global ----------

    @Override
    public void registrarProcessamento(RegistroGlobal req, StreamObserver<Resposta> out) {
        synchronized (historico) {
            historico.add(req);
            totalRecebido++;
            // Mantém só os últimos 5000 para não crescer sem limite
            if (historico.size() > MAX_HISTORICO) {
                historico.remove(0);
            }
        }
        System.out.println("[NOMES] Histórico global: +1 (" + req.getInstancia()
                + " #" + req.getIdTarefa() + ")");
        responder(out, true, "Registrado");
    }

    @Override
    public void obterHistoricoGlobal(Vazio req, StreamObserver<HistoricoGlobal> out) {
        HistoricoGlobal.Builder b = HistoricoGlobal.newBuilder();
        synchronized (historico) {
            b.setTotal(totalRecebido).addAllRegistros(historico);
        }
        out.onNext(b.build());
        out.onCompleted();
    }

    private void responder(StreamObserver<Resposta> out, boolean ok, String msg) {
        out.onNext(Resposta.newBuilder().setOk(ok).setMensagem(msg).build());
        out.onCompleted();
    }
}
