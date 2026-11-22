package com.mycompany.clientecloudforge;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;

/**
 * Botão com hover suave (120 ms), escala 0,97 ao pressionar (80 ms) e barra opcional de progresso.
 */
public class BotaoAnimado extends JButton implements Animador.Animavel {
    private final Animador.Transicao hoverTrans = new Animador.Transicao(120, 0.0);
    private final Animador.Transicao pressTrans = new Animador.Transicao(80, 0.0);
    private final boolean primario;
    private boolean carregando = false;
    private long inicioCarregamentoMs = 0L;

    public BotaoAnimado(String texto) {
        this(texto, false);
    }

    public BotaoAnimado(String texto, boolean primario) {
        super(texto);
        this.primario = primario;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(true);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Animador.registrar(this);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) hoverTrans.iniciar(hoverTrans.valor(System.currentTimeMillis()), 1.0);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverTrans.iniciar(hoverTrans.valor(System.currentTimeMillis()), 0.0);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) pressTrans.iniciar(pressTrans.valor(System.currentTimeMillis()), 1.0);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressTrans.iniciar(pressTrans.valor(System.currentTimeMillis()), 0.0);
            }
        });
    }

    public void setCarregando(boolean carregando) {
        this.carregando = carregando;
        if (carregando) {
            this.inicioCarregamentoMs = System.currentTimeMillis();
            Animador.acordar();
        }
        repaint();
    }

    @Override
    public boolean atualizarAnimacao(long agoraMs) {
        boolean ativo = hoverTrans.ativa(agoraMs) || pressTrans.ativa(agoraMs) || carregando;
        if (ativo) {
            repaint();
        }
        return ativo;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        long agora = System.currentTimeMillis();
        double h = hoverTrans.valor(agora);
        double p = pressTrans.valor(agora);
        double escala = 1.0 - 0.03 * p;

        int w = getWidth();
        int hBtn = getHeight();
        g2.translate(w / 2.0, hBtn / 2.0);
        g2.scale(escala, escala);
        g2.translate(-w / 2.0, -hBtn / 2.0);

        Color base = primario ? TemaCloudForge.ACCENT : TemaCloudForge.S2;
        Color hover = primario ? TemaCloudForge.interpolarCor(TemaCloudForge.ACCENT, TemaCloudForge.FG, 0.14) : TemaCloudForge.S3;
        Color bg = isEnabled() ? TemaCloudForge.interpolarCor(base, hover, h) : TemaCloudForge.S2;

        g2.setColor(bg);
        g2.fillRoundRect(0, 0, w - 1, hBtn - 1, 6, 6);

        if (!primario) {
            Color borda = isFocusOwner() ? TemaCloudForge.ACCENT : TemaCloudForge.interpolarCor(TemaCloudForge.LINE, TemaCloudForge.MUTE, h);
            g2.setColor(borda);
            g2.drawRoundRect(0, 0, w - 1, hBtn - 1, 6, 6);
        } else if (isFocusOwner()) {
            g2.setColor(TemaCloudForge.FG);
            g2.drawRoundRect(0, 0, w - 1, hBtn - 1, 6, 6);
        }

        g2.setFont(getFont());
        g2.setColor(isEnabled() ? (primario ? TemaCloudForge.ON_ACCENT : TemaCloudForge.FG) : TemaCloudForge.MUTE);
        FontMetrics fm = g2.getFontMetrics();
        String txt = getText();
        int tx = (w - fm.stringWidth(txt)) / 2;
        int ty = (hBtn - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(txt, tx, ty);

        // Barra fina indeterminada de 2 px no rodapé do botão enquanto aguarda resposta
        if (carregando && !Animador.isReduzirAnimacoes()) {
            double ciclo = ((agora - inicioCarregamentoMs) % 900) / 900.0;
            int bw = Math.max(24, w / 3);
            int bx = (int) Math.round(-bw + (w + bw) * Animador.easeOut(ciclo));
            g2.setColor(primario ? TemaCloudForge.ON_ACCENT : TemaCloudForge.ACCENT);
            g2.fillRect(Math.max(2, bx), hBtn - 3, Math.min(bw, w - 4 - Math.max(2, bx)), 2);
        }

        g2.dispose();
    }
}
