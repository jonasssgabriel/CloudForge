package com.mycompany.clientecloudforge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.JPanel;

/**
 * Resumo visual da concorrência: barra segmentada animada (600 ms) e selo com check Path2D (300 ms).
 */
public class PainelResumoConcorrencia extends JPanel implements Animador.Animavel {
    private static final long DURACAO_BARRA_MS = 600;
    private static final long DURACAO_CHECK_MS = 300;

    private ClienteCore.ResumoConcorrencia resumo;
    private long inicioAnimMs = 0L;

    public PainelResumoConcorrencia() {
        setBackground(TemaCloudForge.S1);
        reaplicarEscala();
        setVisible(false);
        Animador.registrar(this);
    }

    /** Reajusta altura conforme o fator de escala do tema. */
    public void reaplicarEscala() {
        int h = TemaCloudForge.escala(TemaCloudForge.isModoApresentacao() ? 68 : 58);
        setPreferredSize(new Dimension(0, h));
        setMinimumSize(new Dimension(0, h));
        revalidate();
        repaint();
    }

    public void definirResumo(ClienteCore.ResumoConcorrencia resumo) {
        this.resumo = resumo;
        this.inicioAnimMs = System.currentTimeMillis();
        setVisible(resumo != null);
        revalidate();
        Animador.acordar();
        repaint();
    }

    @Override
    public boolean atualizarAnimacao(long agora) {
        if (resumo == null || Animador.isReduzirAnimacoes()) return false;
        long dt = agora - inicioAnimMs;
        if (dt >= 0 && dt <= DURACAO_BARRA_MS + DURACAO_CHECK_MS) {
            repaint();
            return true;
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (resumo == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        long agora = System.currentTimeMillis();
        double progBarra = Animador.isReduzirAnimacoes() ? 1.0
                : Animador.easeOut(Math.min(1.0, (double) (agora - inicioAnimMs) / DURACAO_BARRA_MS));

        int w = getWidth();
        g2.setColor(TemaCloudForge.DIVIDER);
        g2.drawLine(0, 0, w, 0);

        // Linha 1: título + selo com check Path2D (300 ms)
        boolean apres = TemaCloudForge.isModoApresentacao();
        int yLinha1 = TemaCloudForge.escala(apres ? 21 : 18);
        g2.setFont(TemaCloudForge.sansMedium(apres ? 13f : 11.5f));
        g2.setColor(TemaCloudForge.MUTE);
        g2.drawString("Concorrência", 0, yLinha1);

        boolean ok = resumo.confere() && resumo.idsRepetidos() == 0;
        String selo = resumo.somaConcluidasInstancias() < 0 ? "inconclusivo" : ok ? "soma confere" : "soma diverge";
        Color corSelo = resumo.somaConcluidasInstancias() < 0 ? TemaCloudForge.WARN : ok ? TemaCloudForge.GOOD : TemaCloudForge.BAD;
        g2.setFont(apres ? TemaCloudForge.monoBold(15f) : TemaCloudForge.mono(11f));
        FontMetrics fmSelo = g2.getFontMetrics();
        int wSelo = fmSelo.stringWidth(selo);
        int dotDiam = TemaCloudForge.escala(apres ? 12 : 10);
        int xSelo = w - wSelo - 4;
        int xDot = xSelo - dotDiam - 6;
        int yDot = yLinha1 - dotDiam + 1;

        g2.setColor(corSelo);
        g2.fillOval(xDot, yDot, dotDiam, dotDiam);

        if (ok) {
            double progCheck = Animador.isReduzirAnimacoes() ? 1.0
                    : Animador.easeOut(Math.min(1.0, (double) (agora - inicioAnimMs) / DURACAO_CHECK_MS));
            desenharCheck(g2, xDot + dotDiam * 0.22, yDot + dotDiam * 0.50, progCheck);
        }
        g2.setColor(corSelo);
        g2.drawString(selo, xSelo, yLinha1);

        // Linha 2: barra segmentada preenchendo da esquerda para a direita (600 ms)
        int yBarra = TemaCloudForge.escala(apres ? 30 : 26);
        int hBarra = TemaCloudForge.escala(apres ? 7 : 5);
        g2.setColor(TemaCloudForge.S3);
        g2.fillRoundRect(0, yBarra, w, hBarra, 3, 3);

        int total = Math.max(1, resumo.enviadas());
        int wMax = (int) Math.round(w * progBarra);
        int wConc = (int) Math.round((double) resumo.concluidas() / total * wMax);
        int wRej = (int) Math.round((double) resumo.rejeitadas() / total * wMax);
        int wOutras = (int) Math.round((double) resumo.outrasFalhas() / total * wMax);

        int x = 0;
        if (wConc > 0) {
            g2.setColor(TemaCloudForge.GOOD);
            g2.fillRoundRect(x, yBarra, Math.min(w - x, wConc), hBarra, 3, 3);
            x += wConc;
        }
        if (wRej > 0 && x < w) {
            g2.setColor(TemaCloudForge.WARN);
            g2.fillRect(x, yBarra, Math.min(w - x, wRej), hBarra);
            x += wRej;
        }
        if (wOutras > 0 && x < w) {
            g2.setColor(TemaCloudForge.BAD);
            g2.fillRect(x, yBarra, Math.min(w - x, wOutras), hBarra);
        }

        // Linha 3: métricas numéricas (20+ px no modo apresentação)
        g2.setFont(apres ? TemaCloudForge.monoBold(15.2f) : TemaCloudForge.mono(11f));
        g2.setColor(apres ? TemaCloudForge.FG : TemaCloudForge.MUTE);
        String metricas = String.format("env %d · ok %d · rej %d · dup %d",
                resumo.enviadas(), resumo.concluidas(), resumo.rejeitadas(), resumo.idsRepetidos());
        g2.drawString(metricas, 0, TemaCloudForge.escala(apres ? 57 : 48));

        g2.dispose();
    }

    private static void desenharCheck(Graphics2D g2, double x0, double y0, double prog) {
        if (prog <= 0.0) return;
        g2.setColor(TemaCloudForge.ON_ACCENT);
        g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x0, y0);
        if (prog <= 0.4) {
            double k = prog / 0.4;
            p.lineTo(x0 + 1.8 * k, y0 + 1.8 * k);
        } else {
            double k = (prog - 0.4) / 0.6;
            p.lineTo(x0 + 1.8, y0 + 1.8);
            p.lineTo(x0 + 1.8 + 3.2 * k, y0 + 1.8 - 3.6 * k);
        }
        g2.draw(p);
    }
}
