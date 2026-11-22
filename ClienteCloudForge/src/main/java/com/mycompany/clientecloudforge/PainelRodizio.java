package com.mycompany.clientecloudforge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Linha do tempo visual do rodízio (um bloco 24x20 por requisição).
 */
public class PainelRodizio extends JPanel {
    public enum TipoBloco { OK, SKIP, ERR }

    private static final class Bloco {
        final TipoBloco tipo;
        final int indiceInstancia;
        final String rotulo;
        final long criadoMs;

        Bloco(TipoBloco tipo, int indiceInstancia, String rotulo) {
            this.tipo = tipo;
            this.indiceInstancia = indiceInstancia;
            this.rotulo = rotulo;
            this.criadoMs = System.currentTimeMillis();
        }
    }

    private static final int MAX_BLOCOS = 34;
    private static final int BLOCO_W = 24;
    private static final int BLOCO_H = 20;
    private static final int GAP = 4;
    private static final int PAD = 14;
    private static final long DURACAO_ANIM_MS = 150;

    private final List<Bloco> blocos = new ArrayList<>();
    private final Timer timerAnimacao;

    public PainelRodizio() {
        setBackground(TemaCloudForge.S1);
        setBorder(new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6));
        setPreferredSize(new Dimension(0, 40));
        setMinimumSize(new Dimension(0, 40));

        timerAnimacao = new Timer(16, e -> {
            long agora = System.currentTimeMillis();
            boolean animando = false;
            for (Bloco b : blocos) {
                if (agora - b.criadoMs < DURACAO_ANIM_MS) {
                    animando = true;
                    break;
                }
            }
            repaint();
            if (!animando) {
                ((Timer) e.getSource()).stop();
            }
        });
    }

    public void adicionarOk(int indiceInstancia) {
        adicionar(new Bloco(TipoBloco.OK, Math.max(0, indiceInstancia), Integer.toString(Math.max(0, indiceInstancia) + 1)));
    }

    public void adicionarSkip(int indiceInstancia) {
        String rotulo = indiceInstancia >= 0 ? Integer.toString(indiceInstancia + 1) : "!";
        adicionar(new Bloco(TipoBloco.SKIP, Math.max(0, indiceInstancia), rotulo));
    }

    public void adicionarErro() {
        adicionar(new Bloco(TipoBloco.ERR, 0, "×"));
    }

    private void adicionar(Bloco bloco) {
        blocos.add(bloco);
        if (blocos.size() > MAX_BLOCOS) {
            blocos.remove(0);
        }
        if (!timerAnimacao.isRunning()) {
            timerAnimacao.start();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (blocos.isEmpty()) {
            g2.setFont(TemaCloudForge.mono(12f));
            g2.setColor(TemaCloudForge.MUTE);
            FontMetrics fm = g2.getFontMetrics();
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString("Nenhuma requisição ainda.", PAD, y);
            g2.dispose();
            return;
        }

        long agora = System.currentTimeMillis();
        int x = PAD;
        int y = (getHeight() - BLOCO_H) / 2;
        int limiteX = getWidth() - PAD;
        Stroke tracejado = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 2f}, 0f);
        Stroke solido = new BasicStroke(1f);

        g2.setFont(TemaCloudForge.monoBold(10f));
        FontMetrics fm = g2.getFontMetrics();

        for (Bloco b : blocos) {
            if (x + BLOCO_W > limiteX) break;

            double t = Math.min(1.0, (double) (agora - b.criadoMs) / DURACAO_ANIM_MS);
            int inset = (int) Math.round((1.0 - t) * 2.0);
            int bx = x + inset;
            int by = y + inset;
            int bw = Math.max(4, BLOCO_W - inset * 2);
            int bh = Math.max(4, BLOCO_H - inset * 2);

            if (b.tipo == TipoBloco.OK) {
                Color cor = TemaCloudForge.CORES_INSTANCIA[b.indiceInstancia % TemaCloudForge.CORES_INSTANCIA.length];
                g2.setColor(cor);
                g2.fillRoundRect(bx, by, bw, bh, 4, 4);
                g2.setColor(TemaCloudForge.ON_ACCENT);
            } else if (b.tipo == TipoBloco.SKIP) {
                g2.setColor(TemaCloudForge.WARN);
                g2.setStroke(tracejado);
                g2.drawRoundRect(bx, by, bw - 1, bh - 1, 4, 4);
                g2.setStroke(solido);
            } else {
                g2.setColor(TemaCloudForge.BAD);
                g2.setStroke(solido);
                g2.drawRoundRect(bx, by, bw - 1, bh - 1, 4, 4);
            }

            int tx = x + (BLOCO_W - fm.stringWidth(b.rotulo)) / 2;
            int ty = y + (BLOCO_H - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(b.rotulo, tx, ty);

            x += BLOCO_W + GAP;
        }
        g2.dispose();
    }
}
