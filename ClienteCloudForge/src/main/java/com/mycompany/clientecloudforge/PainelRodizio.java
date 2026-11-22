package com.mycompany.clientecloudforge;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;

/**
 * Linha do tempo visual do rodízio (um bloco 24x20 por requisição, com tooltip e taxa).
 */
public class PainelRodizio extends JPanel implements Animador.Animavel {
    public enum TipoBloco { OK, SKIP, ERR }

    private static final class Bloco {
        final TipoBloco tipo;
        final int indiceInstancia;
        final String rotulo;
        final String tooltip;
        final long criadoMs;

        Bloco(TipoBloco tipo, int indiceInstancia, String rotulo, String tooltip) {
            this.tipo = tipo;
            this.indiceInstancia = indiceInstancia;
            this.rotulo = rotulo;
            this.tooltip = tooltip;
            this.criadoMs = System.currentTimeMillis();
        }
    }

    private static final int MAX_BLOCOS = 34;
    private static final int BLOCO_W = 26;
    private static final int BLOCO_H = 22;
    private static final int GAP = 6;
    private static final int PAD = 16;
    private static final long DURACAO_ANIM_MS = 150;
    private static final long JANELA_TAXA_MS = 5000;

    private final List<Bloco> blocos = new ArrayList<>();
    private final Deque<Long> instantesRequisicoes = new ArrayDeque<>();
    private Consumer<String> onAtualizarTaxa;

    public PainelRodizio() {
        setBackground(TemaCloudForge.S1);
        setBorder(new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6));
        setPreferredSize(new Dimension(0, 44));
        setMinimumSize(new Dimension(0, 38));
        ToolTipManager.sharedInstance().registerComponent(this);
        Animador.registrar(this);
    }

    public void setOnAtualizarTaxa(Consumer<String> onAtualizarTaxa) {
        this.onAtualizarTaxa = onAtualizarTaxa;
        notificarTaxa(System.currentTimeMillis());
    }

    @Override
    public boolean atualizarAnimacao(long agora) {
        boolean animando = false;
        for (Bloco b : blocos) {
            if (agora - b.criadoMs < DURACAO_ANIM_MS) {
                animando = true;
                break;
            }
        }
        boolean temTaxaRecente = limparInstantesAntigos(agora);
        notificarTaxa(agora);
        repaint();
        return animando || temTaxaRecente;
    }

    public void adicionarOk(int indiceInstancia, String instancia, String tipoTarefa, long idTarefa, long duracaoMs) {
        String tip = String.format("%s · %s · #%d · %d ms", instancia, tipoTarefa, idTarefa, duracaoMs);
        adicionar(new Bloco(TipoBloco.OK, Math.max(0, indiceInstancia),
                Integer.toString(Math.max(0, indiceInstancia) + 1), tip), true);
    }

    public void adicionarSkip(int indiceInstancia, String instancia, String motivo) {
        String rotulo = indiceInstancia >= 0 ? Integer.toString(indiceInstancia + 1) : "!";
        String tip = (instancia == null || instancia.isBlank() ? "instância" : instancia)
                + " · pulado (" + (motivo == null ? "indisponível" : motivo) + ")";
        adicionar(new Bloco(TipoBloco.SKIP, Math.max(0, indiceInstancia), rotulo, tip), false);
    }

    public void adicionarErro(String motivo) {
        String tip = "Falha: " + (motivo == null ? "erro" : motivo);
        adicionar(new Bloco(TipoBloco.ERR, 0, "x", tip), false);
    }

    private void adicionar(Bloco bloco, boolean contarNaTaxa) {
        blocos.add(bloco);
        if (blocos.size() > MAX_BLOCOS) {
            blocos.remove(0);
        }
        long agora = System.currentTimeMillis();
        if (contarNaTaxa) {
            instantesRequisicoes.addLast(agora);
        }
        limparInstantesAntigos(agora);
        notificarTaxa(agora);
        Animador.acordar();
        repaint();
    }

    public double getTaxaReqPorSeg() {
        limparInstantesAntigos(System.currentTimeMillis());
        return instantesRequisicoes.size() / 5.0;
    }

    private boolean limparInstantesAntigos(long agora) {
        while (!instantesRequisicoes.isEmpty() && agora - instantesRequisicoes.peekFirst() > JANELA_TAXA_MS) {
            instantesRequisicoes.removeFirst();
        }
        return !instantesRequisicoes.isEmpty();
    }

    private void notificarTaxa(long agora) {
        if (onAtualizarTaxa == null) return;
        limparInstantesAntigos(agora);
        double taxa = instantesRequisicoes.size() / 5.0;
        onAtualizarTaxa.accept(String.format(Locale.US, "taxa: %.1f req/s", taxa));
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (blocos.isEmpty()) return null;
        int x = PAD;
        int y = (getHeight() - BLOCO_H) / 2;
        int limiteX = getWidth() - PAD;
        for (Bloco b : blocos) {
            if (x + BLOCO_W > limiteX) break;
            if (e.getX() >= x && e.getX() <= x + BLOCO_W && e.getY() >= y && e.getY() <= y + BLOCO_H) {
                return b.tooltip;
            }
            x += BLOCO_W + GAP;
        }
        return null;
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
            g2.drawString("Sem eventos individuais. Lotes aparecem no resumo e na trilha.", PAD, y);
            g2.dispose();
            return;
        }

        long agora = System.currentTimeMillis();
        int x = PAD;
        int y = (getHeight() - BLOCO_H) / 2;
        int limiteX = getWidth() - PAD;
        Stroke tracejado = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 2f}, 0f);
        Stroke solido = new BasicStroke(1f);

        g2.setFont(TemaCloudForge.monoBold(10.5f));
        FontMetrics fm = g2.getFontMetrics();

        for (Bloco b : blocos) {
            if (x + BLOCO_W > limiteX) break;

            double raw = Animador.isReduzirAnimacoes() ? 1.0
                    : Math.min(1.0, (double) (agora - b.criadoMs) / DURACAO_ANIM_MS);
            double ease = Animador.easeOut(raw);
            double escala = 0.6 + 0.4 * ease;
            float alpha = (float) Math.max(0.05, ease);

            int bw = Math.max(4, (int) Math.round(BLOCO_W * escala));
            int bh = Math.max(4, (int) Math.round(BLOCO_H * escala));
            int bx = x + (BLOCO_W - bw) / 2;
            int by = y + (BLOCO_H - bh) / 2;

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            if (b.tipo == TipoBloco.OK) {
                Color cor = TemaCloudForge.corInstancia(b.indiceInstancia);
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
