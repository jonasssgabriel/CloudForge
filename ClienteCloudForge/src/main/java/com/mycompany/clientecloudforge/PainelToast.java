package com.mycompany.clientecloudforge;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.JComponent;

/**
 * Notificações discretas (toasts) que entram da direita, empilham suave e saem com fade.
 */
public class PainelToast extends JComponent implements Animador.Animavel {
    public enum Nivel { INFO, OK, WARN, BAD }

    private static final long DURACAO_TOTAL_MS = 3000;
    private static final long SLIDE_IN_MS = 250;
    private static final long FADE_OUT_MS = 200;
    private static final int MAX_TOASTS = 3;

    private static final class Toast {
        final String texto;
        final Color cor;
        final long criadoMs;
        double yAtual = -1.0;

        Toast(String texto, Color cor) {
            this.texto = texto;
            this.cor = cor;
            this.criadoMs = System.currentTimeMillis();
        }
    }

    private final List<Toast> toasts = new ArrayList<>();

    public PainelToast() {
        setOpaque(false);
        Animador.registrar(this);
    }

    public void mostrar(String texto, Nivel nivel) {
        if (texto == null || texto.isBlank()) return;
        Color cor = switch (nivel) {
            case OK -> TemaCloudForge.GOOD;
            case WARN -> TemaCloudForge.WARN;
            case BAD -> TemaCloudForge.BAD;
            case INFO -> TemaCloudForge.ACCENT;
        };
        if (!toasts.isEmpty() && toasts.get(toasts.size() - 1).texto.equals(texto)) {
            return;
        }
        if (toasts.size() >= MAX_TOASTS) {
            toasts.remove(0);
        }
        toasts.add(new Toast(texto, cor));
        Animador.acordar();
        repaint();
    }

    @Override
    public boolean atualizarAnimacao(long agora) {
        if (toasts.isEmpty()) return false;
        Iterator<Toast> it = toasts.iterator();
        while (it.hasNext()) {
            if (agora - it.next().criadoMs >= DURACAO_TOTAL_MS) {
                it.remove();
            }
        }
        int yAlvo = getHeight() - 42 - 32;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            if (t.yAtual < 0 || Animador.isReduzirAnimacoes()) {
                t.yAtual = yAlvo;
            } else {
                t.yAtual += (yAlvo - t.yAtual) * 0.25;
                if (Math.abs(yAlvo - t.yAtual) < 0.5) t.yAtual = yAlvo;
            }
            yAlvo -= 40;
        }
        repaint();
        return !toasts.isEmpty();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (toasts.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(TemaCloudForge.sans(12f));
        FontMetrics fm = g2.getFontMetrics();

        long agora = System.currentTimeMillis();
        int xDireita = getWidth() - 20;

        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            long dt = agora - t.criadoMs;
            if (dt < 0 || dt >= DURACAO_TOTAL_MS) continue;

            float alpha = 1.0f;
            int deslizeX = 0;
            if (!Animador.isReduzirAnimacoes()) {
                if (dt < SLIDE_IN_MS) {
                    double ease = Animador.easeOut((double) dt / SLIDE_IN_MS);
                    alpha = (float) ease;
                    deslizeX = (int) Math.round((1.0 - ease) * 32.0);
                } else if (dt > DURACAO_TOTAL_MS - FADE_OUT_MS) {
                    double ease = Animador.easeOut((double) (dt - (DURACAO_TOTAL_MS - FADE_OUT_MS)) / FADE_OUT_MS);
                    alpha = (float) (1.0 - ease);
                }
            }

            int largura = Math.min(TemaCloudForge.escala(440), fm.stringWidth(t.texto) + TemaCloudForge.escala(32));
            int altura = TemaCloudForge.escala(32);
            int x = xDireita - largura + deslizeX;
            int y = t.yAtual < 0 ? (getHeight() - TemaCloudForge.escala(42) - altura - (toasts.size() - 1 - i) * TemaCloudForge.escala(40)) : (int) Math.round(t.yAtual);

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.0f, Math.min(1.0f, alpha))));
            g2.setColor(TemaCloudForge.S1);
            g2.fillRoundRect(x, y, largura, altura, 6, 6);
            g2.setColor(TemaCloudForge.LINE);
            g2.drawRoundRect(x, y, largura, altura, 6, 6);

            g2.setColor(t.cor);
            g2.fillRoundRect(x + 1, y + 4, 3, altura - 8, 2, 2);

            g2.setColor(TemaCloudForge.FG);
            int ty = y + (altura - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(t.texto, x + 12, ty);
        }
        g2.dispose();
    }
}
