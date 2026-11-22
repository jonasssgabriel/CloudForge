package com.mycompany.clientecloudforge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.Timer;

/**
 * Motor central de animações (~16 ms) com ease-out cúbico, Transicao reutilizável e Ctrl+M.
 */
public final class Animador {
    /** Recebe o instante atual em ms; retorna true se ainda precisa continuar animando. */
    public interface Animavel {
        boolean atualizarAnimacao(long agoraMs);
    }

    /** Utilitário reutilizável para interpolar um valor escalar com ease-out cúbico. */
    public static final class Transicao {
        private final long duracaoMs;
        private double de;
        private double para;
        private long inicioMs;
        private boolean iniciada;

        public Transicao(long duracaoMs) {
            this(duracaoMs, 0.0);
        }

        public Transicao(long duracaoMs, double valorInicial) {
            this.duracaoMs = Math.max(1L, duracaoMs);
            this.de = valorInicial;
            this.para = valorInicial;
        }

        public void iniciar(double de, double para) {
            iniciar(de, para, 0L);
        }

        public void iniciar(double de, double para, long atrasoMs) {
            this.de = de;
            this.para = para;
            this.inicioMs = System.currentTimeMillis() + Math.max(0L, atrasoMs);
            this.iniciada = true;
            acordar();
        }

        public void definirImediato(double valor) {
            this.de = valor;
            this.para = valor;
            this.iniciada = false;
        }

        public double progresso(long agoraMs) {
            if (!iniciada || reduzirAnimacoes) return 1.0;
            if (agoraMs < inicioMs) return 0.0;
            return Math.min(1.0, (double) (agoraMs - inicioMs) / duracaoMs);
        }

        public double valor(long agoraMs) {
            double p = easeOut(progresso(agoraMs));
            return de + (para - de) * p;
        }

        public boolean ativa(long agoraMs) {
            if (!iniciada || reduzirAnimacoes) return false;
            return agoraMs < inicioMs + duracaoMs;
        }

        public double alvo() {
            return para;
        }
    }

    private static volatile boolean reduzirAnimacoes = Boolean.getBoolean("awt.reduceMotion");
    private static final List<Animavel> ouvintes = new CopyOnWriteArrayList<>();
    private static final Timer timer = new Timer(16, e -> pulsar());
    private static boolean acordadoNoPulso = false;

    private Animador() {
    }

    public static double easeOut(double t) {
        double k = Math.max(0.0, Math.min(1.0, t));
        return 1.0 - Math.pow(1.0 - k, 3);
    }

    public static boolean isReduzirAnimacoes() {
        return reduzirAnimacoes;
    }

    public static void setReduzirAnimacoes(boolean reduzir) {
        reduzirAnimacoes = reduzir;
        acordar();
    }

    public static void registrar(Animavel animavel) {
        if (animavel != null && !ouvintes.contains(animavel)) {
            ouvintes.add(animavel);
        }
    }

    public static void remover(Animavel animavel) {
        ouvintes.remove(animavel);
        if (ouvintes.isEmpty() && timer.isRunning()) {
            timer.stop();
        }
    }

    public static void acordar() {
        acordadoNoPulso = true;
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    public static void agendar(long atrasoMs, Runnable acao) {
        long quando = System.currentTimeMillis() + atrasoMs;
        Animavel evento = new Animavel() {
            public boolean atualizarAnimacao(long agora) {
                if (agora < quando) return true;
                remover(this);
                acao.run();
                return false;
            }
        };
        registrar(evento);
        acordar();
    }

    public static void pararTudo() {
        ouvintes.clear();
        timer.stop();
    }

    private static void pulsar() {
        acordadoNoPulso = false;
        long agora = System.currentTimeMillis();
        boolean algumAtivo = false;
        for (Animavel a : ouvintes) {
            if (a.atualizarAnimacao(agora)) {
                algumAtivo = true;
            }
        }
        if (!algumAtivo && !acordadoNoPulso) {
            timer.stop();
        }
    }
}
