package com.mycompany.clientecloudforge;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Visualização didática dos prazos (deadlines) das duas etapas de cada requisição:
 * (1) Health Check (deadline de 2 s) e (2) ExecutarTarefa (deadline de 5 s).
 *
 * Mantém no máximo 6 linhas visíveis (as mais recentes), descarta automaticamente
 * linhas antigas sob rajadas e só mantém o Timer do Animador ativo enquanto há
 * linhas animando.
 */
public class PainelPrazo extends JPanel implements Animador.Animavel {

    private static final int MAX_LINHAS = 6;
    private static final long DEADLINE_HEALTH_MS = 2000L;
    private static final long DEADLINE_TAREFA_MS = 5000L;
    private static final long DURACAO_CHECK_MS = 250L;
    private static final long DURACAO_SUMIR_OK_MS = 1500L;
    private static final long DURACAO_SUMIR_FALHA_MS = 1800L;
    private static final long DURACAO_FADE_OUT_MS = 240L;

    private static final int PAD_X = 12;
    private static final int CABECALHO_H = 28;
    private static final int ALTURA_LINHA = 22;

    private enum Fase {
        HEALTH,
        AGUARDANDO_TAREFA,
        TAREFA,
        OK,
        ESTOURO,
        PULADO,
        FALHA
    }

    private static final class LinhaPrazo {
        final int id;
        final String instancia;
        final Color corInstancia;
        Fase fase;
        long inicioEtapaMs;
        long healthMs = -1L;
        long tarefaMs = -1L;
        long fimMs = -1L;
        double razaoFinal = 0.0;
        String codigoFalha = "";

        LinhaPrazo(int id, String instancia, Color corInstancia, long agoraMs) {
            this.id = id;
            this.instancia = (instancia == null || instancia.isBlank()) ? "inst" : instancia.trim();
            this.corInstancia = corInstancia;
            this.fase = Fase.HEALTH;
            this.inicioEtapaMs = agoraMs;
        }

        boolean encerrada() {
            return fase == Fase.OK || fase == Fase.ESTOURO || fase == Fase.PULADO || fase == Fase.FALHA;
        }

        long duracaoSaidaMs() {
            return fase == Fase.OK ? DURACAO_SUMIR_OK_MS : DURACAO_SUMIR_FALHA_MS;
        }
    }

    private final AtomicInteger sequenciaId = new AtomicInteger(0);
    private final List<LinhaPrazo> linhas = new ArrayList<>(MAX_LINHAS);
    private final Map<Integer, LinhaPrazo> porId = new HashMap<>(MAX_LINHAS * 2);

    // Relógio injetável apenas no pacote para capturas determinísticas de teste.
    private LongSupplier relogio = System::currentTimeMillis;
    private boolean relogioPersonalizado = false;

    public PainelPrazo() {
        setBackground(TemaCloudForge.S1);
        setBorder(new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6));
        reaplicarEscala();
        Animador.registrar(this);
    }

    /** Reajusta dimensões do painel conforme a escala atual do tema. */
    public void reaplicarEscala() {
        setMinimumSize(new Dimension(TemaCloudForge.escala(380), TemaCloudForge.escala(166)));
        setPreferredSize(new Dimension(TemaCloudForge.escala(460), TemaCloudForge.escala(172)));
        revalidate();
        repaint();
    }

    /**
     * Inicia o acompanhamento de uma nova requisição na etapa de Health Check (2 s).
     * Devolve o identificador da linha para atualizações subsequentes.
     */
    public int iniciar(String instancia, int cor) {
        int id = sequenciaId.incrementAndGet();
        Color corResolvida = resolverCor(cor);
        executarNaEdt(() -> {
            long agora = agoraMs();
            LinhaPrazo nova = new LinhaPrazo(id, instancia, corResolvida, agora);
            if (linhas.size() >= MAX_LINHAS) {
                descartarMaisAntiga();
            }
            linhas.add(nova);
            porId.put(id, nova);
            Animador.acordar();
            repaint();
        });
        return id;
    }

    /**
     * Registra a conclusão da etapa de Health Check.
     * Quando ok=false, a linha é marcada como "pulado" e descartada após o intervalo de saída.
     */
    public void healthConcluido(int id, long ms, boolean ok) {
        executarNaEdt(() -> {
            LinhaPrazo linha = porId.get(id);
            if (linha == null) return;
            long agora = agoraMs();
            linha.healthMs = Math.max(0L, ms);
            if (ok) {
                if (linha.fase == Fase.HEALTH) {
                    linha.fase = Fase.AGUARDANDO_TAREFA;
                    linha.razaoFinal = limitarRazao((double) linha.healthMs / DEADLINE_HEALTH_MS);
                }
            } else {
                linha.fase = Fase.PULADO;
                linha.fimMs = agora;
                linha.razaoFinal = limitarRazao((double) linha.healthMs / DEADLINE_HEALTH_MS);
            }
            Animador.acordar();
            repaint();
        });
    }

    /**
     * Marca o início da etapa ExecutarTarefa (deadline de 5 s).
     */
    public void tarefaIniciada(int id) {
        executarNaEdt(() -> {
            LinhaPrazo linha = porId.get(id);
            if (linha == null) return;
            linha.fase = Fase.TAREFA;
            linha.inicioEtapaMs = agoraMs();
            linha.fimMs = -1L;
            Animador.acordar();
            repaint();
        });
    }

    /**
     * Marca a requisição como concluída com sucesso (check em Path2D de 250 ms e remoção em 1,5 s).
     */
    public void concluido(int id, long ms) {
        executarNaEdt(() -> {
            LinhaPrazo linha = porId.get(id);
            if (linha == null) return;
            long agora = agoraMs();
            linha.tarefaMs = Math.max(0L, ms);
            linha.razaoFinal = Math.max(0.03, limitarRazao((double) linha.tarefaMs / DEADLINE_TAREFA_MS));
            linha.fase = Fase.OK;
            linha.fimMs = agora;
            Animador.acordar();
            repaint();
        });
    }

    /**
     * Marca falha na requisição: DEADLINE_EXCEEDED estoura a barra até o fim e exibe
     * "tentando o próximo"; UNAVAILABLE/NOT_SERVING exibe "pulado".
     */
    public void falhou(int id, String codigo) {
        executarNaEdt(() -> {
            LinhaPrazo linha = porId.get(id);
            if (linha == null) return;
            long agora = agoraMs();
            String norm = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
            linha.codigoFalha = codigo == null ? "erro" : codigo.trim();

            if (norm.contains("DEADLINE")) {
                linha.fase = Fase.ESTOURO;
                linha.razaoFinal = 1.0;
                if (linha.healthMs < 0 && linha.tarefaMs < 0) {
                    linha.healthMs = DEADLINE_HEALTH_MS;
                } else {
                    linha.tarefaMs = DEADLINE_TAREFA_MS;
                }
            } else if (norm.contains("UNAVAILABLE") || norm.contains("NOT_SERVING")) {
                linha.fase = Fase.PULADO;
                linha.razaoFinal = calcularRazaoAtual(linha, agora);
            } else {
                linha.fase = Fase.FALHA;
                linha.razaoFinal = Math.max(0.06, calcularRazaoAtual(linha, agora));
            }
            linha.fimMs = agora;
            Animador.acordar();
            repaint();
        });
    }

    /**
     * Remove todas as linhas imediatamente e retorna ao estado vazio.
     */
    public void limpar() {
        executarNaEdt(() -> {
            linhas.clear();
            porId.clear();
            repaint();
        });
    }

    @Override
    public boolean atualizarAnimacao(long agoraSistemaMs) {
        if (linhas.isEmpty()) {
            return false;
        }
        long agora = relogioPersonalizado ? relogio.getAsLong() : agoraSistemaMs;
        Iterator<LinhaPrazo> it = linhas.iterator();
        while (it.hasNext()) {
            LinhaPrazo linha = it.next();
            if (linha.encerrada() && linha.fimMs >= 0 && agora - linha.fimMs >= linha.duracaoSaidaMs()) {
                it.remove();
                porId.remove(linha.id);
            }
        }
        repaint();
        return !linhas.isEmpty();
    }

    /** Permite avançar tempo de forma determinística na DemoPrazo (uso restrito ao pacote). */
    void definirRelogioParaTeste(LongSupplier relogio) {
        if (relogio == null) {
            this.relogio = System::currentTimeMillis;
            this.relogioPersonalizado = false;
        } else {
            this.relogio = relogio;
            this.relogioPersonalizado = true;
        }
    }

    /** Quantidade atual de linhas visíveis (auxiliar de teste no pacote). */
    int quantidadeVisivel() {
        return linhas.size();
    }

    private long agoraMs() {
        return relogio.getAsLong();
    }

    private void descartarMaisAntiga() {
        if (linhas.isEmpty()) return;
        // Dá preferência a remover uma linha já encerrada; se todas estiverem ativas, remove a mais antiga.
        int idxRemover = 0;
        for (int i = 0; i < linhas.size(); i++) {
            if (linhas.get(i).encerrada()) {
                idxRemover = i;
                break;
            }
        }
        LinhaPrazo removida = linhas.remove(idxRemover);
        porId.remove(removida.id);
    }

    private static void executarNaEdt(Runnable acao) {
        if (SwingUtilities.isEventDispatchThread()) {
            acao.run();
        } else {
            SwingUtilities.invokeLater(acao);
        }
    }

    private static Color resolverCor(int cor) {
        if (cor >= 0 && cor < 256) {
            return TemaCloudForge.CORES_INSTANCIA[cor % TemaCloudForge.CORES_INSTANCIA.length];
        }
        return new Color(cor & 0xFFFFFF);
    }

    private static double limitarRazao(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double calcularRazaoAtual(LinhaPrazo linha, long agora) {
        if (linha.fase == Fase.HEALTH) {
            return limitarRazao((double) Math.max(0L, agora - linha.inicioEtapaMs) / DEADLINE_HEALTH_MS);
        }
        if (linha.fase == Fase.TAREFA) {
            return limitarRazao((double) Math.max(0L, agora - linha.inicioEtapaMs) / DEADLINE_TAREFA_MS);
        }
        return linha.razaoFinal;
    }

    private static Color corPorRazao(double razao) {
        if (razao > 0.90) return TemaCloudForge.BAD;
        if (razao > 0.60) return TemaCloudForge.WARN;
        return TemaCloudForge.ACCENT;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        desenharCabecalho(g2, w);

        if (linhas.isEmpty()) {
            desenharEstadoVazio(g2, w, h);
            g2.dispose();
            return;
        }

        long agora = agoraMs();
        int padX = TemaCloudForge.escala(PAD_X);
        int cabH = TemaCloudForge.escala(CABECALHO_H);
        int altLinha = TemaCloudForge.escala(ALTURA_LINHA);
        boolean compacto = w < TemaCloudForge.escala(420);
        int wUtil = Math.max(120, w - padX * 2);
        int chipW = TemaCloudForge.escala(compacto ? 58 : 74);
        int textoColW = compacto ? Math.min(TemaCloudForge.escala(198), Math.max(TemaCloudForge.escala(165), wUtil - chipW - 96))
                                 : Math.min(TemaCloudForge.escala(248), Math.max(TemaCloudForge.escala(200), (int) Math.round(wUtil * 0.45)));
        int barraX = padX + chipW + TemaCloudForge.escala(8);
        int barraW = Math.max(54, wUtil - chipW - TemaCloudForge.escala(18) - textoColW);
        int textoX = barraX + barraW + TemaCloudForge.escala(10);
        int textoMaxW = Math.max(60, w - padX - textoX);

        Font fonteChip = TemaCloudForge.monoBold(compacto ? 9.2f : 9.8f);
        Font fonteMono = TemaCloudForge.mono(compacto ? 9.5f : 10.2f);
        Font fonteMonoBold = TemaCloudForge.monoBold(compacto ? 9.5f : 10.2f);

        int yBase = cabH + 5;
        for (int i = 0; i < linhas.size(); i++) {
            int rowY = yBase + i * altLinha;
            if (rowY + altLinha > h - 3) break;
            desenharLinha(g2, linhas.get(i), agora, rowY, altLinha, padX, chipW, barraX, barraW, textoX, textoMaxW,
                    fonteChip, fonteMono, fonteMonoBold);
        }

        g2.dispose();
    }

    private void desenharCabecalho(Graphics2D g2, int w) {
        int padX = TemaCloudForge.escala(PAD_X);
        int cabH = TemaCloudForge.escala(CABECALHO_H);
        g2.setFont(TemaCloudForge.sansMedium(11.5f));
        FontMetrics fmTit = g2.getFontMetrics();
        int yTxt = (cabH - fmTit.getHeight()) / 2 + fmTit.getAscent();
        g2.setColor(TemaCloudForge.FG);
        g2.drawString("Prazos e Health Check", padX, yTxt);

        String legenda = "health 2 s · tarefa 5 s";
        g2.setFont(TemaCloudForge.mono(10f));
        g2.setColor(TemaCloudForge.MUTE);
        FontMetrics fmLeg = g2.getFontMetrics();
        g2.drawString(legenda, w - padX - fmLeg.stringWidth(legenda), yTxt);

        g2.setColor(TemaCloudForge.DIVIDER);
        g2.drawLine(padX, cabH, w - padX, cabH);
    }

    private void desenharEstadoVazio(Graphics2D g2, int w, int h) {
        int areaTop = TemaCloudForge.escala(CABECALHO_H) + 4;
        int cy = areaTop + (h - areaTop) / 2;

        // Ícone geométrico minimalista de relógio/prazo (sem emoji)
        int r = TemaCloudForge.escala(9);
        int cx = w / 2;
        int iconeY = cy - TemaCloudForge.escala(18);
        g2.setColor(TemaCloudForge.LINE);
        g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawOval(cx - r, iconeY - r, r * 2, r * 2);
        Path2D.Double ponteiros = new Path2D.Double();
        ponteiros.moveTo(cx, iconeY - TemaCloudForge.escala(5));
        ponteiros.lineTo(cx, iconeY);
        ponteiros.lineTo(cx + TemaCloudForge.escala(4), iconeY + 2);
        g2.draw(ponteiros);

        g2.setFont(TemaCloudForge.sans(12f));
        g2.setColor(TemaCloudForge.MUTE);
        FontMetrics fm = g2.getFontMetrics();
        String titulo = "Nenhuma requisição em andamento";
        g2.drawString(titulo, (w - fm.stringWidth(titulo)) / 2, cy + TemaCloudForge.escala(8));

        g2.setFont(TemaCloudForge.mono(10f));
        FontMetrics fmSub = g2.getFontMetrics();
        String sub = "Health Check (2 s) e ExecutarTarefa (5 s) aparecem aqui";
        g2.drawString(sub, (w - fmSub.stringWidth(sub)) / 2, cy + TemaCloudForge.escala(24));
    }

    private void desenharLinha(
            Graphics2D g2,
            LinhaPrazo linha,
            long agora,
            int rowY,
            int altLinha,
            int padX,
            int chipW,
            int barraX,
            int barraW,
            int textoX,
            int textoMaxW,
            Font fonteChip,
            Font fonteMono,
            Font fonteMonoBold) {

        // Calcula transparência suave apenas na saída final da linha
        float alpha = 1.0f;
        if (linha.encerrada() && linha.fimMs >= 0 && !Animador.isReduzirAnimacoes()) {
            long dtFim = agora - linha.fimMs;
            long inicioFade = linha.duracaoSaidaMs() - DURACAO_FADE_OUT_MS;
            if (dtFim > inicioFade) {
                double p = Animador.easeOut((double) (dtFim - inicioFade) / DURACAO_FADE_OUT_MS);
                alpha = (float) Math.max(0.0, 1.0 - p);
            }
        }

        Graphics2D gl = (Graphics2D) g2.create();
        gl.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        int cy = rowY + altLinha / 2;

        // 1. Chip da instância
        int hChip = TemaCloudForge.escala(16);
        desenharChip(gl, linha, padX, cy - hChip / 2, chipW, hChip, fonteChip);

        // 2. Barra fina de progresso em relação ao deadline da etapa + marcador do limite
        double razao = calcularRazaoAtual(linha, agora);
        int hBarra = TemaCloudForge.escala(6);
        desenharBarra(gl, linha, razao, barraX, cy - hBarra / 2, barraW, hBarra);

        // 3. Texto mono e indicador de estado à direita
        desenharTextoEstado(gl, linha, agora, razao, rowY, altLinha, cy, textoX, textoMaxW, fonteMono, fonteMonoBold);

        gl.dispose();
    }

    private void desenharChip(Graphics2D g2, LinhaPrazo linha, int x, int y, int w, int h, Font fonteChip) {
        Color base = linha.corInstancia;
        Color fundo = new Color(base.getRed(), base.getGreen(), base.getBlue(), 22);
        Color borda = new Color(base.getRed(), base.getGreen(), base.getBlue(), 95);

        g2.setColor(fundo);
        g2.fillRoundRect(x, y, w, h, 4, 4);
        g2.setColor(borda);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(x, y, w - 1, h - 1, 4, 4);

        // Ponto indicador da cor da instância
        g2.setColor(base);
        g2.fillOval(x + 5, y + (h - 5) / 2, 5, 5);

        g2.setFont(fonteChip);
        g2.setColor(TemaCloudForge.FG);
        FontMetrics fm = g2.getFontMetrics();
        String texto = truncar(linha.instancia, fm, w - 16);
        int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(texto, x + 13, ty);
    }

    private void desenharBarra(Graphics2D g2, LinhaPrazo linha, double razao, int x, int y, int w, int h) {
        // Trilha de fundo da barra
        g2.setColor(TemaCloudForge.S3);
        g2.fillRoundRect(x, y, w, h, 3, 3);

        // Marcas sutis de 60% e 90% na trilha para leitura didática dos limiares
        int x60 = x + (int) Math.round(w * 0.60);
        int x90 = x + (int) Math.round(w * 0.90);
        g2.setColor(TemaCloudForge.S1);
        g2.drawLine(x60, y, x60, y + h - 1);
        g2.drawLine(x90, y, x90, y + h - 1);

        // Preenchimento conforme a fase
        if (linha.fase == Fase.PULADO) {
            Stroke anterior = g2.getStroke();
            g2.setColor(TemaCloudForge.WARN);
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{3f, 2f}, 0f));
            g2.drawRoundRect(x, y, w - 1, h - 1, 3, 3);
            g2.setStroke(anterior);
        } else if (linha.fase == Fase.ESTOURO) {
            // Estoura até o fim da barra (passa 2 px além do marcador)
            g2.setColor(TemaCloudForge.BAD);
            g2.fillRoundRect(x, y, w + 2, h, 3, 3);
        } else {
            Color corBarra;
            if (linha.fase == Fase.OK) {
                corBarra = TemaCloudForge.GOOD;
            } else if (linha.fase == Fase.FALHA) {
                corBarra = TemaCloudForge.BAD;
            } else {
                corBarra = corPorRazao(razao);
            }
            int larguraFill = Math.max(3, (int) Math.round(w * razao));
            g2.setColor(corBarra);
            g2.fillRoundRect(x, y, Math.min(w, larguraFill), h, 3, 3);
        }

        // Marcador vertical do limite (100% do deadline)
        Color corMarcador = switch (linha.fase) {
            case ESTOURO, FALHA -> TemaCloudForge.BAD;
            case OK -> TemaCloudForge.GOOD;
            case PULADO -> TemaCloudForge.WARN;
            default -> razao > 0.90 ? TemaCloudForge.BAD : TemaCloudForge.MUTE;
        };
        g2.setColor(corMarcador);
        g2.setStroke(new BasicStroke(linha.fase == Fase.ESTOURO ? 2.0f : 1.4f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(x + w, y - 3, x + w, y + h + 3);
    }

    private void desenharTextoEstado(
            Graphics2D g2,
            LinhaPrazo linha,
            long agora,
            double razao,
            int rowY,
            int altLinha,
            int cy,
            int textoX,
            int textoMaxW,
            Font fonteMono,
            Font fonteMonoBold) {

        g2.setFont(fonteMono);
        FontMetrics fm = g2.getFontMetrics();
        int ty = rowY + (altLinha - fm.getHeight()) / 2 + fm.getAscent();

        switch (linha.fase) {
            case HEALTH -> {
                long dec = Math.min(DEADLINE_HEALTH_MS, Math.max(0L, agora - linha.inicioEtapaMs));
                String txt = "health " + dec + " ms / " + DEADLINE_HEALTH_MS;
                g2.setColor(razao > 0.60 ? corPorRazao(razao) : TemaCloudForge.FG);
                g2.drawString(truncar(txt, fm, textoMaxW), textoX, ty);
            }
            case AGUARDANDO_TAREFA -> {
                String txt = "health " + Math.max(0L, linha.healthMs) + " ms · pronto";
                g2.setColor(TemaCloudForge.MUTE);
                g2.drawString(truncar(txt, fm, textoMaxW), textoX, ty);
            }
            case TAREFA -> {
                long dec = Math.min(DEADLINE_TAREFA_MS, Math.max(0L, agora - linha.inicioEtapaMs));
                String prefixo = linha.healthMs >= 0 ? ("health " + linha.healthMs + " ms · ") : "";
                String sufixo = "tarefa " + dec + " ms / " + DEADLINE_TAREFA_MS;

                if (fm.stringWidth(prefixo + sufixo) <= textoMaxW) {
                    g2.setColor(TemaCloudForge.MUTE);
                    g2.drawString(prefixo, textoX, ty);
                    int xSuf = textoX + fm.stringWidth(prefixo);
                    g2.setColor(razao > 0.60 ? corPorRazao(razao) : TemaCloudForge.FG);
                    g2.drawString(sufixo, xSuf, ty);
                } else {
                    g2.setColor(razao > 0.60 ? corPorRazao(razao) : TemaCloudForge.FG);
                    g2.drawString(truncar(prefixo + sufixo, fm, textoMaxW), textoX, ty);
                }
            }
            case OK -> {
                // Selo "OK" com check desenhado em Path2D (250 ms) alinhado à direita
                g2.setFont(fonteMonoBold);
                FontMetrics fmBold = g2.getFontMetrics();
                int okTextW = fmBold.stringWidth("OK");
                int seloW = 11 + 4 + okTextW;
                int xSelo = textoX + textoMaxW - seloW;

                String tempos = linha.healthMs >= 0
                        ? ("health " + linha.healthMs + " ms · tarefa " + linha.tarefaMs + " ms")
                        : ("tarefa " + linha.tarefaMs + " ms");
                g2.setFont(fonteMono);
                g2.setColor(TemaCloudForge.MUTE);
                g2.drawString(truncar(tempos, fm, Math.max(30, xSelo - textoX - 6)), textoX, ty);

                int yCirc = cy - 5;
                g2.setColor(TemaCloudForge.GOOD);
                g2.fillOval(xSelo, yCirc, 11, 11);

                double progCheck = Animador.isReduzirAnimacoes() ? 1.0
                        : Animador.easeOut(Math.min(1.0, (double) Math.max(0L, agora - linha.fimMs) / DURACAO_CHECK_MS));
                desenharCheck(g2, xSelo + 2.6, yCirc + 5.6, progCheck);

                g2.setFont(fonteMonoBold);
                g2.setColor(TemaCloudForge.GOOD);
                g2.drawString("OK", xSelo + 15, ty);
            }
            case ESTOURO -> {
                String prefixo = linha.healthMs >= 0 && linha.tarefaMs >= 0
                        ? ("health " + linha.healthMs + " ms · ")
                        : (linha.healthMs >= 0 ? ("health " + linha.healthMs + " ms · ") : "");
                desenharPrefixoERotulo(g2, prefixo, "tentando o próximo", TemaCloudForge.BAD,
                        textoX, ty, textoMaxW, fonteMono, fonteMonoBold);
            }
            case PULADO -> {
                String prefixo = linha.healthMs >= 0 ? ("health " + linha.healthMs + " ms · ") : "";
                desenharPrefixoERotulo(g2, prefixo, "pulado", TemaCloudForge.WARN,
                        textoX, ty, textoMaxW, fonteMono, fonteMonoBold);
            }
            case FALHA -> {
                String rotulo = linha.codigoFalha.isBlank() ? "falhou" : linha.codigoFalha.toLowerCase(Locale.ROOT);
                String prefixo = linha.healthMs >= 0 ? ("health " + linha.healthMs + " ms · ") : "";
                desenharPrefixoERotulo(g2, prefixo, rotulo, TemaCloudForge.BAD,
                        textoX, ty, textoMaxW, fonteMono, fonteMonoBold);
            }
        }
    }

    private void desenharPrefixoERotulo(
            Graphics2D g2,
            String prefixo,
            String rotulo,
            Color corRotulo,
            int textoX,
            int ty,
            int textoMaxW,
            Font fonteMono,
            Font fonteMonoBold) {

        g2.setFont(fonteMono);
        FontMetrics fm = g2.getFontMetrics();
        g2.setFont(fonteMonoBold);
        FontMetrics fmBold = g2.getFontMetrics();

        int wRot = fmBold.stringWidth(rotulo);
        int wPre = fm.stringWidth(prefixo);
        if (!prefixo.isEmpty() && wPre + wRot <= textoMaxW) {
            g2.setFont(fonteMono);
            g2.setColor(TemaCloudForge.MUTE);
            g2.drawString(prefixo, textoX, ty);
            g2.setFont(fonteMonoBold);
            g2.setColor(corRotulo);
            g2.drawString(rotulo, textoX + wPre, ty);
        } else {
            g2.setFont(fonteMonoBold);
            g2.setColor(corRotulo);
            g2.drawString(truncar(rotulo, fmBold, textoMaxW), textoX, ty);
        }
    }

    /** Desenha o traço do check em Path2D acompanhando o progresso (0..1) em 250 ms. */
    private static void desenharCheck(Graphics2D g2, double x0, double y0, double prog) {
        if (prog <= 0.0) return;
        g2.setColor(TemaCloudForge.ON_ACCENT);
        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x0, y0);
        if (prog <= 0.4) {
            double k = prog / 0.4;
            p.lineTo(x0 + 2.0 * k, y0 + 2.0 * k);
        } else {
            double k = (prog - 0.4) / 0.6;
            p.lineTo(x0 + 2.0, y0 + 2.0);
            p.lineTo(x0 + 2.0 + 3.8 * k, y0 + 2.0 - 4.2 * k);
        }
        g2.draw(p);
    }

    private static String truncar(String texto, FontMetrics fm, int maxW) {
        if (texto == null) return "";
        if (fm.stringWidth(texto) <= maxW) return texto;
        String reticencias = "...";
        int wRet = fm.stringWidth(reticencias);
        int n = texto.length();
        while (n > 0 && fm.stringWidth(texto.substring(0, n)) + wRet > maxW) {
            n--;
        }
        return n <= 0 ? "" : texto.substring(0, n) + reticencias;
    }
}
