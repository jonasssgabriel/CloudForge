package com.mycompany.clientecloudforge;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * Barra superior grande do modo apresentação: título da etapa em Instrument Serif
 * itálico, legenda em Inter 32 px fixa (com quebra segura em até 2 linhas),
 * crossfade via Animador central e barra de progresso fina das 8 etapas.
 */
public class PainelApresentacao extends JPanel implements Animador.Animavel {
    private static final int TOTAL_ETAPAS = 8;
    private static final long DURACAO_CROSSFADE_MS = 320L;
    private static final int ALTURA_BARRA_PROGRESSO = 5;
    private static final int PAD_HORIZONTAL = 24;
    private static final float TAMANHO_LEGENDA_PX = 32f;
    private static final float TAMANHO_TITULO_PX = 38f;

    private String tituloAtual = "";
    private String legendaAtual = "";
    private Color corAtual = TemaCloudForge.ACCENT;

    private String tituloAnterior = "";
    private String legendaAnterior = "";
    private Color corAnterior = TemaCloudForge.ACCENT;

    private final Animador.Transicao crossfade = new Animador.Transicao(DURACAO_CROSSFADE_MS, 1.0);
    private double progresso = 0.0;
    private boolean pausado = false;
    private int ciclo = 1;

    public PainelApresentacao() {
        setBackground(TemaCloudForge.S1);
        setBorder(new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6));
        setPreferredSize(new Dimension(0, 92));
        setMinimumSize(new Dimension(0, 88));
        setVisible(false);
        Animador.registrar(this);
    }

    /** Troca o título e a frase da legenda com crossfade dirigido pelo Animador central. */
    public void definirLegenda(String titulo, String legenda, Color corDestaque) {
        String novoTit = titulo == null ? "" : titulo;
        String novaLeg = legenda == null ? "" : legenda;
        Color novaCor = corDestaque == null ? TemaCloudForge.ACCENT : corDestaque;

        if (novoTit.equals(tituloAtual) && novaLeg.equals(legendaAtual) && novaCor.equals(corAtual)) {
            return;
        }
        tituloAnterior = tituloAtual;
        legendaAnterior = legendaAtual;
        corAnterior = corAtual;

        tituloAtual = novoTit;
        legendaAtual = novaLeg;
        corAtual = novaCor;

        if (Animador.isReduzirAnimacoes() || (tituloAnterior.isEmpty() && legendaAnterior.isEmpty())) {
            crossfade.definirImediato(1.0);
        } else {
            crossfade.iniciar(0.0, 1.0);
        }
        Animador.acordar();
        repaint();
    }

    /** Atualiza o progresso geral das 8 etapas (0.0 a 1.0). */
    public void definirProgresso(double fracao) {
        double lim = Math.max(0.0, Math.min(1.0, fracao));
        if (Math.abs(lim - progresso) > 0.0005) {
            progresso = lim;
            repaint();
        }
    }

    public void definirPausado(boolean pausado) {
        if (this.pausado != pausado) {
            this.pausado = pausado;
            repaint();
        }
    }

    public void definirCiclo(int ciclo) {
        if (this.ciclo != ciclo) {
            this.ciclo = Math.max(1, ciclo);
            repaint();
        }
    }

    public void reiniciar() {
        tituloAtual = "";
        legendaAtual = "";
        tituloAnterior = "";
        legendaAnterior = "";
        corAtual = TemaCloudForge.ACCENT;
        corAnterior = TemaCloudForge.ACCENT;
        progresso = 0.0;
        pausado = false;
        ciclo = 1;
        crossfade.definirImediato(1.0);
        repaint();
    }

    @Override
    public boolean atualizarAnimacao(long agoraMs) {
        if (!isVisible()) return false;
        if (crossfade.ativa(agoraMs)) {
            repaint();
            return true;
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int hConteudo = h - ALTURA_BARRA_PROGRESSO - 1;
        long agora = System.currentTimeMillis();
        double v = Animador.isReduzirAnimacoes() ? 1.0 : crossfade.valor(agora);

        // Filete lateral sólido indicando o estado da etapa atual (acento, aviso ou erro real)
        Color corFilete = TemaCloudForge.interpolarCor(corAnterior, corAtual, v);
        g2.setColor(corFilete);
        g2.fillRect(1, 1, 5, Math.max(1, hConteudo));

        // Selo discreto à direita (PAUSADO ou número do ciclo quando repete)
        int reservaDireita = desenharSeloDireita(g2, w, hConteudo);
        int xInicio = PAD_HORIZONTAL;
        int larguraUtil = Math.max(200, w - xInicio - reservaDireita - PAD_HORIZONTAL);

        // Crossfade entre legenda anterior e legenda atual
        if (v < 0.999 && (!tituloAnterior.isEmpty() || !legendaAnterior.isEmpty())) {
            float alphaAnt = (float) Math.max(0.0, Math.min(1.0, 1.0 - v));
            if (alphaAnt > 0.01f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaAnt));
                desenharConteudo(g2, tituloAnterior, legendaAnterior, corAnterior, xInicio, hConteudo, larguraUtil);
            }
            float alphaNovo = (float) Math.max(0.0, Math.min(1.0, v));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaNovo));
            desenharConteudo(g2, tituloAtual, legendaAtual, corAtual, xInicio, hConteudo, larguraUtil);
            g2.setComposite(AlphaComposite.SrcOver);
        } else {
            desenharConteudo(g2, tituloAtual, legendaAtual, corAtual, xInicio, hConteudo, larguraUtil);
        }

        // Barra fina de progresso das 8 etapas na base da barra de legenda
        int yBarra = h - ALTURA_BARRA_PROGRESSO - 1;
        int wBarra = Math.max(1, w - 2);
        g2.setColor(TemaCloudForge.S3);
        g2.fillRect(1, yBarra, wBarra, ALTURA_BARRA_PROGRESSO);

        int wPreenchido = (int) Math.round(wBarra * progresso);
        if (wPreenchido > 0) {
            g2.setColor(corFilete);
            g2.fillRect(1, yBarra, Math.min(wBarra, wPreenchido), ALTURA_BARRA_PROGRESSO);
        }

        // Marcações discretas entre as 8 etapas
        g2.setColor(TemaCloudForge.S1);
        for (int i = 1; i < TOTAL_ETAPAS; i++) {
            int xm = 1 + (int) Math.round(wBarra * (double) i / TOTAL_ETAPAS);
            g2.fillRect(xm, yBarra, 2, ALTURA_BARRA_PROGRESSO);
        }

        g2.dispose();
    }

    private int desenharSeloDireita(Graphics2D g2, int w, int hConteudo) {
        String texto = pausado ? "PAUSADO" : (ciclo > 1 ? "ciclo " + ciclo : "");
        if (texto.isEmpty()) return 0;

        Font f = TemaCloudForge.monoBoldFixa(14f);
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
        int padX = 12;
        int bw = fm.stringWidth(texto) + padX * 2;
        int bh = 30;
        int bx = w - bw - PAD_HORIZONTAL;
        int by = (hConteudo - bh) / 2;

        Color corBorda = pausado ? TemaCloudForge.WARN : TemaCloudForge.LINE;
        Color corTexto = pausado ? TemaCloudForge.WARN : TemaCloudForge.MUTE;
        g2.setColor(TemaCloudForge.S2);
        g2.fillRoundRect(bx, by, bw, bh, 6, 6);
        g2.setColor(corBorda);
        g2.drawRoundRect(bx, by, bw - 1, bh - 1, 6, 6);
        g2.setColor(corTexto);
        int ty = by + (bh - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(texto, bx + padX, ty);
        return bw + 16;
    }

    private void desenharConteudo(Graphics2D g2, String titulo, String legenda, Color corTitulo,
            int xInicio, int hConteudo, int larguraUtil) {
        int xLegenda = xInicio;

        // Título da etapa em Instrument Serif itálico grande
        if (titulo != null && !titulo.isEmpty()) {
            Font fTit = TemaCloudForge.serifItalicFixa(TAMANHO_TITULO_PX);
            g2.setFont(fTit);
            FontMetrics fmTit = g2.getFontMetrics();
            int yTit = (hConteudo - fmTit.getHeight()) / 2 + fmTit.getAscent();
            g2.setColor(corTitulo);
            g2.drawString(titulo, xInicio, yTit);

            int wTit = fmTit.stringWidth(titulo);
            int xDiv = xInicio + wTit + 20;
            g2.setColor(TemaCloudForge.LINE);
            g2.fillRect(xDiv, Math.max(14, hConteudo / 2 - 22), 1, 44);
            xLegenda = xDiv + 20;
        }

        if (legenda == null || legenda.isEmpty()) return;

        int largDisp = Math.max(160, (xInicio + larguraUtil) - xLegenda);

        // Legenda sempre em Inter 32 px (dentro de 30 a 34 px), quebrando em até 2 linhas se necessário
        Font fLeg = TemaCloudForge.sansFixa(TAMANHO_LEGENDA_PX);
        g2.setFont(fLeg);
        FontMetrics fmLeg = g2.getFontMetrics();
        g2.setColor(TemaCloudForge.FG);

        List<String> linhas = quebrarAteDuasLinhas(legenda, fmLeg, largDisp);
        if (linhas.size() == 1) {
            int yLeg = (hConteudo - fmLeg.getHeight()) / 2 + fmLeg.getAscent();
            g2.drawString(linhas.get(0), xLegenda, yLeg);
        } else {
            int espacoEntre = 2;
            int totalH = fmLeg.getHeight() * 2 + espacoEntre;
            int y1 = (hConteudo - totalH) / 2 + fmLeg.getAscent();
            g2.drawString(linhas.get(0), xLegenda, y1);
            g2.drawString(linhas.get(1), xLegenda, y1 + fmLeg.getHeight() + espacoEntre);
        }
    }

    /** Quebra o texto em no máximo 2 linhas medindo ambas e truncando com reticências sem laço infinito. */
    private static List<String> quebrarAteDuasLinhas(String texto, FontMetrics fm, int largMax) {
        List<String> resultado = new ArrayList<>(2);
        String limpo = texto == null ? "" : texto.trim();
        if (limpo.isEmpty()) return resultado;
        if (fm.stringWidth(limpo) <= largMax) {
            resultado.add(limpo);
            return resultado;
        }

        String[] palavras = limpo.split("\\s+");
        StringBuilder l1 = new StringBuilder();
        int idx = 0;
        while (idx < palavras.length) {
            String tentativa = l1.isEmpty() ? palavras[idx] : l1 + " " + palavras[idx];
            if (fm.stringWidth(tentativa) <= largMax) {
                l1.setLength(0);
                l1.append(tentativa);
                idx++;
            } else {
                break;
            }
        }

        // Caso a primeira palavra sozinha já ultrapasse largMax
        if (l1.isEmpty()) {
            resultado.add(truncarSeguro(palavras[0], fm, largMax));
            idx = 1;
        } else {
            resultado.add(l1.toString());
        }

        if (idx < palavras.length) {
            StringBuilder resto = new StringBuilder();
            for (int i = idx; i < palavras.length; i++) {
                if (!resto.isEmpty()) resto.append(' ');
                resto.append(palavras[i]);
            }
            resultado.add(truncarSeguro(resto.toString(), fm, largMax));
        }
        return resultado;
    }

    /** Reduz um índice sobre a string original (imutável) até caber com reticências. */
    private static String truncarSeguro(String origem, FontMetrics fm, int largMax) {
        if (origem == null || origem.isEmpty()) return "";
        if (fm.stringWidth(origem) <= largMax) return origem;
        String ret = "...";
        if (fm.stringWidth(ret) >= largMax) return "";
        int fim = origem.length() - 1;
        while (fim > 0 && fm.stringWidth(origem.substring(0, fim) + ret) > largMax) {
            fim--;
        }
        return fim <= 0 ? "" : origem.substring(0, fim).trim() + ret;
    }
}
