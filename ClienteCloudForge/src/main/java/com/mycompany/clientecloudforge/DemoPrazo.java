package com.mycompany.clientecloudforge;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Demonstração independente do PainelPrazo.
 *
 * Uso interativo: execute sem argumentos para abrir a janela com simulações.
 * Uso para captura: execute com "--capturar <pasta>" para gerar prazo-1.png
 * (andamento), prazo-2.png (estouro de deadline) e prazo-3.png (concluído),
 * além de validar uma rajada de 200 chamadas.
 */
public final class DemoPrazo {

    private DemoPrazo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && "--capturar".equals(args[0])) {
            File pasta = new File(args[1]);
            SwingUtilities.invokeAndWait(() -> {
                TemaCloudForge.aplicar();
                try {
                    gerarCapturasETesteRajada(pasta);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            Animador.pararTudo();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            TemaCloudForge.aplicar();
            abrirJanelaInterativa();
        });
    }

    private static void abrirJanelaInterativa() {
        JFrame frame = new JFrame("Demo PainelPrazo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(TemaCloudForge.S0);
        frame.setLayout(new BorderLayout(8, 8));

        PainelPrazo painel = new PainelPrazo();
        painel.setPreferredSize(new Dimension(480, 172));

        JPanel centro = new JPanel(new BorderLayout());
        centro.setBackground(TemaCloudForge.S0);
        centro.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        centro.add(painel, BorderLayout.CENTER);
        frame.add(centro, BorderLayout.CENTER);

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        botoes.setBackground(TemaCloudForge.S0);

        JButton btnNormal = new JButton("Req OK");
        btnNormal.addActionListener(e -> simularNormal(painel, "inst-1", 0, 750));

        JButton btnDeadline = new JButton("Estouro 5s");
        btnDeadline.addActionListener(e -> simularEstouro(painel));

        JButton btnPulado = new JButton("Indisponível");
        btnPulado.addActionListener(e -> simularPulado(painel));

        JButton btnRajada = new JButton("Rajada 200");
        btnRajada.addActionListener(e -> simularRajada200(painel));

        JButton btnLimpar = new JButton("Limpar");
        btnLimpar.addActionListener(e -> painel.limpar());

        botoes.add(btnNormal);
        botoes.add(btnDeadline);
        botoes.add(btnPulado);
        botoes.add(btnRajada);
        botoes.add(btnLimpar);
        frame.add(botoes, BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void simularNormal(PainelPrazo painel, String inst, int cor, int duracaoTarefaMs) {
        int id = painel.iniciar(inst, cor);
        agendar(180, () -> {
            painel.healthConcluido(id, 4, true);
            painel.tarefaIniciada(id);
            agendar(duracaoTarefaMs, () -> painel.concluido(id, duracaoTarefaMs));
        });
    }

    private static void simularEstouro(PainelPrazo painel) {
        int id = painel.iniciar("inst-2", 1);
        agendar(160, () -> {
            painel.healthConcluido(id, 5, true);
            painel.tarefaIniciada(id);
            agendar(5050, () -> {
                painel.falhou(id, "DEADLINE_EXCEEDED");
                simularNormal(painel, "inst-3", 2, 420);
            });
        });
    }

    private static void simularPulado(PainelPrazo painel) {
        int id = painel.iniciar("inst-1", 0);
        agendar(220, () -> painel.falhou(id, "UNAVAILABLE"));
    }

    private static void simularRajada200(PainelPrazo painel) {
        for (int i = 0; i < 200; i++) {
            int idx = i;
            int id = painel.iniciar("inst-" + ((idx % 3) + 1), idx % 3);
            painel.healthConcluido(id, 2 + (idx % 6), true);
            painel.tarefaIniciada(id);
            if (idx < 194) {
                painel.concluido(id, 25 + (idx % 40));
            } else {
                agendar(600 + (idx - 194) * 180, () -> painel.concluido(id, 41 + (idx % 30)));
            }
        }
    }

    private static void agendar(int atrasoMs, Runnable acao) {
        Animador.agendar(atrasoMs, acao);
    }

    private static void gerarCapturasETesteRajada(File pasta) throws IOException {
        if (!pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("Não foi possível criar a pasta: " + pasta);
        }

        AtomicLong relogio = new AtomicLong(10_000L);
        PainelPrazo painel = new PainelPrazo();
        painel.definirRelogioParaTeste(relogio::get);
        painel.setSize(480, 172);
        painel.doLayout();

        // Teste de rajada com 200 chamadas: valida limite de 6 linhas e desempenho
        long t0 = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            int id = painel.iniciar("inst-" + ((i % 3) + 1), i % 3);
            relogio.addAndGet(2);
            painel.healthConcluido(id, 3, true);
            painel.tarefaIniciada(id);
            relogio.addAndGet(5);
            painel.concluido(id, 38 + (i % 15));
        }
        long rajadaUs = (System.nanoTime() - t0) / 1000L;
        int visiveisAposRajada = painel.quantidadeVisivel();
        painel.limpar();

        // Instante 1: andamento (faixas indigo, warn >60%, bad >90%, health em curso e pulado)
        relogio.set(20_000L);
        int a1 = painel.iniciar("inst-1", 0);
        painel.healthConcluido(a1, 3, true);
        painel.tarefaIniciada(a1); // vai estar em 4650 ms (93% -> bad)

        relogio.set(21_050L);
        int a2 = painel.iniciar("inst-2", 1);
        painel.healthConcluido(a2, 4, true);
        painel.tarefaIniciada(a2); // vai estar em 3600 ms (72% -> warn)

        relogio.set(23_250L);
        int a3 = painel.iniciar("inst-3", 2);
        painel.healthConcluido(a3, 3, true);
        painel.tarefaIniciada(a3); // vai estar em 1400 ms (28% -> indigo)

        relogio.set(24_609L);
        int a4 = painel.iniciar("inst-1", 0);
        painel.healthConcluido(a4, 3, true);
        painel.tarefaIniciada(a4); // vai estar em 41 ms (texto "health 3 ms · tarefa 41 ms / 5000")

        relogio.set(23_950L);
        int a5 = painel.iniciar("inst-2", 1); // em Health Check: 700 ms / 2000 (35% -> indigo)

        relogio.set(24_650L);
        salvarPng(painel, new File(pasta, "prazo-1.png"));

        // Instante 2: estouro de deadline + pulado (UNAVAILABLE/NOT_SERVING) + retry ativo
        painel.limpar();
        relogio.set(30_000L);
        int b1 = painel.iniciar("inst-1", 0);
        painel.healthConcluido(b1, 4, true);
        painel.tarefaIniciada(b1);
        relogio.set(35_000L);
        painel.falhou(b1, "DEADLINE_EXCEEDED");

        relogio.set(35_050L);
        int b2 = painel.iniciar("inst-2", 1);
        relogio.set(35_053L);
        painel.healthConcluido(b2, 3, false);
        painel.falhou(b2, "NOT_SERVING");

        relogio.set(35_060L);
        int b3 = painel.iniciar("inst-3", 2);
        relogio.set(35_062L);
        painel.falhou(b3, "UNAVAILABLE");

        relogio.set(35_080L);
        int b4 = painel.iniciar("inst-1", 0);
        painel.healthConcluido(b4, 2, true);
        painel.tarefaIniciada(b4);

        relogio.set(35_320L);
        salvarPng(painel, new File(pasta, "prazo-2.png"));

        // Instante 3: concluído (check Path2D completo em 250 ms, antes de sumir em 1,5 s)
        painel.limpar();
        relogio.set(40_000L);
        int c1 = painel.iniciar("inst-1", 0);
        painel.healthConcluido(c1, 3, true);
        painel.tarefaIniciada(c1);
        relogio.set(40_041L);
        painel.concluido(c1, 41);

        int c2 = painel.iniciar("inst-2", 1);
        painel.healthConcluido(c2, 4, true);
        painel.tarefaIniciada(c2);
        relogio.set(40_420L);
        painel.concluido(c2, 379);

        int c3 = painel.iniciar("inst-3", 2);
        painel.healthConcluido(c3, 2, true);
        painel.tarefaIniciada(c3);
        relogio.set(40_520L);
        painel.concluido(c3, 100);

        // Avança 260 ms após o último concluído: check de 250 ms já completo, ainda visível (< 1500 ms)
        relogio.set(40_780L);
        salvarPng(painel, new File(pasta, "prazo-3.png"));

        // Verifica remoção automática em 1,5 s e parada do Animador quando vazio
        relogio.set(42_500L);
        boolean aindaAnimando = painel.atualizarAnimacao(relogio.get());
        System.out.printf("OK rajada200=%d us visiveisAposRajada=%d visiveisAposExpirar=%d animandoAposVazio=%s%n",
                rajadaUs, visiveisAposRajada, painel.quantidadeVisivel(), aindaAnimando);
    }

    private static void salvarPng(PainelPrazo painel, File destino) throws IOException {
        int pad = 12;
        int w = painel.getWidth() + pad * 2;
        int h = painel.getHeight() + pad * 2;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(TemaCloudForge.S0);
        g2.fillRect(0, 0, w, h);
        g2.translate(pad, pad);
        painel.paint(g2);
        g2.dispose();
        ImageIO.write(img, "png", destino);
    }
}
