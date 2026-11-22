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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;

/**
 * Painel visual que comprova a diferença entre estado local (cada instância
 * mantém o seu próprio contador #N de tarefas) e histórico global opcional
 * mantido no serviço de nomes.
 */
public class PainelEstadoLocal extends JPanel implements Animador.Animavel {
    private static final long DURACAO_ANIM_MS = 250L;
    private static final String LEGENDA_L1 = "cada instância guarda o seu próprio contador;";
    private static final String LEGENDA_L2 = "a numeração não é compartilhada";
    private static final String LEGENDA_COMPLETA = LEGENDA_L1 + " " + LEGENDA_L2;
    private static final String TEXTO_GLOBAL_DESLIGADO = "desligado: suba com --global";

    private static final int PAD_X = 12;
    private static final int PAD_TOP = 9;
    private static final int PAD_BOTTOM = 8;
    private static final int ALTURA_CABECALHO = 16;
    private static final int ALTURA_BLOCO_GLOBAL = 26;

    /** Estado e transições animadas de uma instância exibida no painel. */
    private static final class EstadoInstancia {
        final String id;
        Color cor;

        long idTarefaAnterior;
        long idTarefaAtual;
        final Animador.Transicao transicaoOdometro = new Animador.Transicao(DURACAO_ANIM_MS, 1.0);

        long recebidas;
        long concluidas;
        long rejeitadas;

        double concluidasDe;
        double concluidasAlvo;
        double rejeitadasDe;
        double rejeitadasAlvo;
        final Animador.Transicao transicaoBarras = new Animador.Transicao(DURACAO_ANIM_MS, 1.0);

        EstadoInstancia(String id, Color cor) {
            this.id = id;
            this.cor = cor;
        }
    }

    private final Map<String, EstadoInstancia> instancias = new LinkedHashMap<>();
    private Long totalGlobal;
    private double totalGlobalDe;
    private double totalGlobalAlvo;
    private final Animador.Transicao transicaoGlobal = new Animador.Transicao(DURACAO_ANIM_MS, 1.0);
    private boolean animacaoEmCurso;
    private boolean destacado;

    public PainelEstadoLocal() {
        setBackground(TemaCloudForge.S1);
        setBorder(new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6));
        reaplicarEscala();
        Animador.registrar(this);
    }

    /** Reajusta as dimensões preferidas quando a escala do tema muda. */
    public void reaplicarEscala() {
        int altPref = TemaCloudForge.isModoApresentacao() ? 204 : 160;
        int altMin = TemaCloudForge.isModoApresentacao() ? 196 : 154;
        setPreferredSize(new Dimension(TemaCloudForge.escala(420), altPref));
        setMinimumSize(new Dimension(TemaCloudForge.escala(360), altMin));
        revalidate();
        repaint();
    }

    /** Destaca discretamente a borda durante a etapa Estado local da apresentação. */
    public void setDestacado(boolean destacado) {
        if (this.destacado != destacado) {
            this.destacado = destacado;
            setBorder(new TemaCloudForge.BordaPainel(
                    destacado ? TemaCloudForge.ACCENT : TemaCloudForge.LINE, 6));
            repaint();
        }
    }

    /**
     * Atualiza (ou adiciona) o estado local de uma instância e dispara
     * a rolagem de 250 ms do odômetro quando o #id da tarefa muda.
     *
     * @param id         identificador da instância
     * @param cor        índice na paleta de TemaCloudForge (0..63) ou inteiro RGB
     * @param idTarefa   último número de tarefa (#N) emitido pela instância
     * @param recebidas  total de tarefas recebidas pela instância
     * @param concluidas total de tarefas concluídas com sucesso
     * @param rejeitadas total de tarefas rejeitadas por capacidade
     */
    public synchronized void atualizarInstancia(String id, int cor, long idTarefa,
            long recebidas, long concluidas, long rejeitadas) {
        if (id == null || id.isBlank()) {
            return;
        }
        long idSeguro = Math.max(0L, idTarefa);
        long recSeguro = Math.max(0L, recebidas);
        long concSeguro = Math.max(0L, concluidas);
        long rejSeguro = Math.max(0L, rejeitadas);
        Color corResolvida = resolverCor(cor);

        long agora = System.currentTimeMillis();
        EstadoInstancia est = instancias.get(id);
        if (est == null) {
            est = new EstadoInstancia(id, corResolvida);
            est.idTarefaAnterior = idSeguro > 0 ? Math.max(0L, idSeguro - 1L) : 0L;
            est.idTarefaAtual = idSeguro;
            if (idSeguro > 0) {
                est.transicaoOdometro.iniciar(0.0, 1.0);
            } else {
                est.transicaoOdometro.definirImediato(1.0);
            }

            est.recebidas = recSeguro;
            est.concluidas = concSeguro;
            est.rejeitadas = rejSeguro;
            est.concluidasDe = 0.0;
            est.concluidasAlvo = concSeguro;
            est.rejeitadasDe = 0.0;
            est.rejeitadasAlvo = rejSeguro;
            if (concSeguro > 0 || rejSeguro > 0) {
                est.transicaoBarras.iniciar(0.0, 1.0);
            } else {
                est.transicaoBarras.definirImediato(1.0);
            }
            instancias.put(id, est);
        } else {
            est.cor = corResolvida;
            if (idSeguro != est.idTarefaAtual) {
                est.idTarefaAnterior = est.idTarefaAtual;
                est.idTarefaAtual = idSeguro;
                est.transicaoOdometro.iniciar(0.0, 1.0);
            }
            if (concSeguro != est.concluidas || rejSeguro != est.rejeitadas) {
                double pAtual = est.transicaoBarras.valor(agora);
                est.concluidasDe = est.concluidasDe + (est.concluidasAlvo - est.concluidasDe) * pAtual;
                est.rejeitadasDe = est.rejeitadasDe + (est.rejeitadasAlvo - est.rejeitadasDe) * pAtual;
                est.concluidasAlvo = concSeguro;
                est.rejeitadasAlvo = rejSeguro;
                est.transicaoBarras.iniciar(0.0, 1.0);
            }
            est.recebidas = recSeguro;
            est.concluidas = concSeguro;
            est.rejeitadas = rejSeguro;
        }

        animacaoEmCurso = true;
        Animador.acordar();
        repaint();
    }

    /**
     * Define o total mantido pelo histórico global no serviço de nomes.
     * Quando null, exibe o aviso "desligado: suba com --global".
     */
    public synchronized void definirTotalGlobal(Long totalOuNull) {
        long agora = System.currentTimeMillis();
        if (totalOuNull == null) {
            this.totalGlobal = null;
            this.totalGlobalDe = 0.0;
            this.totalGlobalAlvo = 0.0;
            this.transicaoGlobal.definirImediato(1.0);
        } else {
            long novo = Math.max(0L, totalOuNull);
            if (this.totalGlobal == null) {
                this.totalGlobalDe = 0.0;
                this.totalGlobalAlvo = novo;
                if (novo > 0) {
                    this.transicaoGlobal.iniciar(0.0, 1.0);
                } else {
                    this.transicaoGlobal.definirImediato(1.0);
                }
            } else if (novo != this.totalGlobal) {
                double pAtual = this.transicaoGlobal.valor(agora);
                this.totalGlobalDe = this.totalGlobalDe + (this.totalGlobalAlvo - this.totalGlobalDe) * pAtual;
                this.totalGlobalAlvo = novo;
                this.transicaoGlobal.iniciar(0.0, 1.0);
            }
            this.totalGlobal = novo;
        }
        animacaoEmCurso = true;
        Animador.acordar();
        repaint();
    }

    /** Limpa todas as instâncias e retorna o painel ao estado vazio inicial. */
    public synchronized void limpar() {
        instancias.clear();
        totalGlobal = null;
        totalGlobalDe = 0.0;
        totalGlobalAlvo = 0.0;
        transicaoGlobal.definirImediato(1.0);
        animacaoEmCurso = false;
        repaint();
    }

    @Override
    public boolean atualizarAnimacao(long agoraMs) {
        boolean ativa = false;
        synchronized (this) {
            for (EstadoInstancia est : instancias.values()) {
                if (est.transicaoOdometro.ativa(agoraMs) || est.transicaoBarras.ativa(agoraMs)) {
                    ativa = true;
                    break;
                }
            }
            if (!ativa && transicaoGlobal.ativa(agoraMs)) {
                ativa = true;
            }
            if (ativa) {
                animacaoEmCurso = true;
                repaint();
                return true;
            }
            if (animacaoEmCurso) {
                // Garante um último quadro cravado no valor final (t = 1.0).
                animacaoEmCurso = false;
                repaint();
            }
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        List<EstadoInstancia> lista;
        Long globalAtual;
        double globalDe;
        double globalAlvo;
        long agora = System.currentTimeMillis();
        double progGlobal;

        synchronized (this) {
            lista = new ArrayList<>(instancias.values());
            globalAtual = this.totalGlobal;
            globalDe = this.totalGlobalDe;
            globalAlvo = this.totalGlobalAlvo;
            progGlobal = this.transicaoGlobal.valor(agora);
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int padX = TemaCloudForge.escala(PAD_X);
        int padTop = TemaCloudForge.escala(PAD_TOP);
        int padBottom = TemaCloudForge.escala(PAD_BOTTOM);
        int altCab = TemaCloudForge.escala(ALTURA_CABECALHO);
        int altBlocoGlobal = TemaCloudForge.escala(ALTURA_BLOCO_GLOBAL);
        int larguraUtil = Math.max(120, w - padX * 2);

        // 1. Cabeçalho compacto + destaque quando duas ou mais instâncias exibem o mesmo #id
        desenharCabecalho(g2, lista, padX, padTop, larguraUtil);

        // Calcula o espaço reservado no rodapé (legenda fixa + bloco global)
        float tamLeg = TemaCloudForge.isModoApresentacao() ? 11.5f : 10f;
        g2.setFont(TemaCloudForge.sans(tamLeg));
        FontMetrics fmLeg = g2.getFontMetrics();
        boolean legendaDuasLinhas = fmLeg.stringWidth(LEGENDA_COMPLETA) > larguraUtil;
        int alturaLegenda = legendaDuasLinhas ? fmLeg.getHeight() * 2 - 2 : fmLeg.getHeight();
        int yLegendaTopo = h - padBottom - alturaLegenda;
        int yBlocoGlobal = yLegendaTopo - 5 - altBlocoGlobal;
        int yDivisor = yBlocoGlobal - 4;

        // 2. Área principal: estado vazio ou linhas por instância
        int yAreaInicio = padTop + altCab + 4;
        int alturaArea = Math.max(24, yDivisor - 4 - yAreaInicio);

        if (lista.isEmpty()) {
            desenharEstadoVazio(g2, padX, yAreaInicio, larguraUtil, alturaArea);
        } else {
            desenharLinhasInstancias(g2, lista, agora, padX, yAreaInicio, larguraUtil, alturaArea);
        }

        // 3. Divisor fino + bloco "histórico global (bônus)"
        g2.setColor(TemaCloudForge.DIVIDER);
        g2.drawLine(padX, yDivisor, padX + larguraUtil, yDivisor);
        desenharBlocoGlobal(g2, globalAtual, globalDe, globalAlvo, progGlobal,
                padX, yBlocoGlobal, larguraUtil, altBlocoGlobal);

        // 4. Legenda fixa explicando a independência dos contadores locais
        desenharLegenda(g2, fmLeg, legendaDuasLinhas, padX, yLegendaTopo, larguraUtil);

        g2.dispose();
    }

    private void desenharCabecalho(Graphics2D g2, List<EstadoInstancia> lista, int x, int y, int largura) {
        g2.setFont(TemaCloudForge.sansMedium(11f));
        FontMetrics fmTit = g2.getFontMetrics();
        int yTexto = y + fmTit.getAscent();
        g2.setColor(TemaCloudForge.FG);
        g2.drawString("Estado local por instância", x, yTexto);

        Long idSimultaneo = encontrarIdSimultaneo(lista);
        if (idSimultaneo != null) {
            String selo = "#" + idSimultaneo + " simultâneo";
            g2.setFont(TemaCloudForge.monoBold(10f));
            FontMetrics fmSelo = g2.getFontMetrics();
            int wSelo = fmSelo.stringWidth(selo) + TemaCloudForge.escala(12);
            int hSelo = TemaCloudForge.escala(16);
            int xSelo = x + largura - wSelo;
            int ySelo = Math.max(2, y - 1);

            g2.setColor(comAlfa(TemaCloudForge.ACCENT, 26));
            g2.fillRoundRect(xSelo, ySelo, wSelo, hSelo, 4, 4);
            g2.setColor(comAlfa(TemaCloudForge.ACCENT, 120));
            g2.drawRoundRect(xSelo, ySelo, wSelo - 1, hSelo - 1, 4, 4);
            g2.setColor(TemaCloudForge.ACCENT);
            g2.drawString(selo, xSelo + 6, ySelo + (hSelo - fmSelo.getHeight()) / 2 + fmSelo.getAscent());
        } else {
            String aux = lista.isEmpty() ? "aguardando instâncias" : "contagem independente";
            g2.setFont(TemaCloudForge.mono(10f));
            FontMetrics fmAux = g2.getFontMetrics();
            g2.setColor(TemaCloudForge.MUTE);
            g2.drawString(aux, x + largura - fmAux.stringWidth(aux), yTexto);
        }
    }

    private void desenharEstadoVazio(Graphics2D g2, int x, int y, int largura, int altura) {
        Stroke anterior = g2.getStroke();
        g2.setColor(TemaCloudForge.S2);
        g2.fillRoundRect(x, y, largura, altura, 5, 5);
        g2.setColor(TemaCloudForge.LINE);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{4f, 3f}, 0f));
        g2.drawRoundRect(x, y, largura - 1, altura - 1, 5, 5);
        g2.setStroke(anterior);

        // Mini moldura geométrica de odômetro vazio (#0)
        int boxW = 34;
        int boxH = 22;
        int boxX = x + 12;
        int boxY = y + (altura - boxH) / 2;
        g2.setColor(TemaCloudForge.S1);
        g2.fillRoundRect(boxX, boxY, boxW, boxH, 4, 4);
        g2.setColor(TemaCloudForge.LINE);
        g2.drawRoundRect(boxX, boxY, boxW - 1, boxH - 1, 4, 4);

        g2.setFont(TemaCloudForge.monoBold(11f));
        FontMetrics fmBox = g2.getFontMetrics();
        String zero = "#0";
        g2.setColor(TemaCloudForge.MUTE);
        g2.drawString(zero, boxX + (boxW - fmBox.stringWidth(zero)) / 2,
                boxY + (boxH - fmBox.getHeight()) / 2 + fmBox.getAscent());

        int tx = boxX + boxW + 10;
        g2.setFont(TemaCloudForge.sansMedium(11.5f));
        FontMetrics fmL1 = g2.getFontMetrics();
        g2.setFont(TemaCloudForge.mono(10f));
        FontMetrics fmL2 = g2.getFontMetrics();

        int blocoTextoH = fmL1.getHeight() + fmL2.getHeight() + 1;
        int yL1 = y + (altura - blocoTextoH) / 2 + fmL1.getAscent();

        g2.setFont(TemaCloudForge.sansMedium(11.5f));
        g2.setColor(TemaCloudForge.FG);
        g2.drawString("Nenhuma instância com tarefas", tx, yL1);

        g2.setFont(TemaCloudForge.mono(10f));
        g2.setColor(TemaCloudForge.MUTE);
        String sub = ajustarTexto(g2.getFontMetrics(),
                "envie tarefas para ver o #id local de cada servidor", largura - (tx - x) - 8);
        g2.drawString(sub, tx, yL1 + fmL2.getHeight() + 1);
    }

    private void desenharLinhasInstancias(Graphics2D g2, List<EstadoInstancia> lista,
            long agora, int x, int y, int largura, int altura) {
        int qtd = lista.size();
        int gap = qtd >= 3 ? 3 : 5;
        int minLinha = TemaCloudForge.escala(22);
        int maxVisiveis = Math.max(1, Math.min(qtd, (altura + gap) / minLinha));
        int alturaLinha = Math.min(TemaCloudForge.escala(30), (altura - gap * (maxVisiveis - 1)) / maxVisiveis);
        int alturaTotal = maxVisiveis * alturaLinha + (maxVisiveis - 1) * gap;
        int yAtual = y + Math.max(0, (altura - alturaTotal) / 2);

        // Largura do chip proporcional à largura do painel para manter colunas alinhadas
        int wChip = Math.min(TemaCloudForge.escala(118), Math.max(TemaCloudForge.escala(86), largura / 4));
        int wOdometro = TemaCloudForge.escala(TemaCloudForge.isModoApresentacao() ? 70 : 54);
        int gapCol = TemaCloudForge.escala(8);

        for (int i = 0; i < maxVisiveis; i++) {
            EstadoInstancia est = lista.get(i);
            desenharLinhaInstancia(g2, est, agora, x, yAtual, largura, alturaLinha, wChip, wOdometro, gapCol);
            yAtual += alturaLinha + gap;
        }
    }

    private void desenharLinhaInstancia(Graphics2D g2, EstadoInstancia est, long agora,
            int x, int y, int largura, int altura, int wChip, int wOdometro, int gapCol) {
        // 1. Chip na cor da instância
        int hChip = Math.min(TemaCloudForge.escala(26), altura - 2);
        int yChip = y + (altura - hChip) / 2;
        g2.setColor(est.cor);
        g2.fillRoundRect(x, yChip, wChip, hChip, 5, 5);

        g2.setFont(TemaCloudForge.monoBold(TemaCloudForge.isModoApresentacao() ? 13.5f : 11f));
        FontMetrics fmChip = g2.getFontMetrics();
        String idTexto = ajustarTexto(fmChip, est.id, wChip - 12);
        g2.setColor(TemaCloudForge.ON_ACCENT);
        g2.drawString(idTexto, x + 6, yChip + (hChip - fmChip.getHeight()) / 2 + fmChip.getAscent());

        // 2. Odômetro animado do contador #id
        int xOdo = x + wChip + gapCol;
        int hOdo = Math.min(TemaCloudForge.escala(28), altura);
        int yOdo = y + (altura - hOdo) / 2;
        desenharOdometro(g2, est, agora, xOdo, yOdo, wOdometro, hOdo);

        // 3. Mini-barras concluídas / rejeitadas + contadores compactos
        int xBarras = xOdo + wOdometro + gapCol + 2;
        int wBarras = Math.max(60, x + largura - xBarras);
        desenharMiniBarras(g2, est, agora, xBarras, y, wBarras, altura);
    }

    private void desenharOdometro(Graphics2D g2, EstadoInstancia est, long agora,
            int x, int y, int w, int h) {
        double p = est.transicaoOdometro.valor(agora);
        Color corBorda = p < 0.999
                ? TemaCloudForge.interpolarCor(est.cor, TemaCloudForge.LINE, p)
                : TemaCloudForge.LINE;

        g2.setColor(TemaCloudForge.S2);
        g2.fillRoundRect(x, y, w, h, 5, 5);
        g2.setColor(corBorda);
        g2.drawRoundRect(x, y, w - 1, h - 1, 5, 5);

        String antigo = est.idTarefaAnterior == 0 ? "?" : Long.toString(est.idTarefaAnterior);
        String atual = est.idTarefaAtual == 0 ? "?" : Long.toString(est.idTarefaAtual);

        // Centraliza o conjunto '#' + dígitos (20+ px no modo apresentação para leitura de longe)
        float tamHash = TemaCloudForge.isModoApresentacao() ? 14.5f : 12f;
        float tamDig = TemaCloudForge.isModoApresentacao() ? 15.5f : 12.5f;
        g2.setFont(TemaCloudForge.monoBold(tamHash));
        FontMetrics fmHash = g2.getFontMetrics();
        g2.setFont(TemaCloudForge.monoBold(tamDig));
        FontMetrics fmDigMedida = g2.getFontMetrics();

        int wHash = fmHash.stringWidth("#");
        int wDigMax = Math.max(fmDigMedida.stringWidth(antigo), fmDigMedida.stringWidth(atual));
        int wGrupo = wHash + 1 + wDigMax;
        int xHash = x + Math.max(5, (w - wGrupo) / 2);
        int yBaseHash = y + (h - fmHash.getHeight()) / 2 + fmHash.getAscent();

        g2.setFont(TemaCloudForge.monoBold(tamHash));
        g2.setColor(TemaCloudForge.MUTE);
        g2.drawString("#", xHash, yBaseHash);

        // Área recortada onde os dígitos rolam verticalmente
        int xDig = xHash + wHash + 1;

        Graphics2D gClip = (Graphics2D) g2.create();
        gClip.clipRect(x + 2, y + 2, w - 4, h - 4);
        gClip.setFont(TemaCloudForge.monoBold(tamDig));
        FontMetrics fmDig = gClip.getFontMetrics();
        int yBaseDig = y + (h - fmDig.getHeight()) / 2 + fmDig.getAscent();

        if (p >= 0.999 || est.idTarefaAnterior == est.idTarefaAtual) {
            gClip.setColor(TemaCloudForge.FG);
            gClip.drawString(atual, xDig, yBaseDig);
        } else {
            int direcao = est.idTarefaAtual >= est.idTarefaAnterior ? 1 : -1;
            double desloc = p * h;
            float alfaSaida = (float) Math.max(0.0, Math.min(1.0, 1.0 - p));
            float alfaEntrada = (float) Math.max(0.0, Math.min(1.0, p));
            Color corAtivo = TemaCloudForge.interpolarCor(est.cor, TemaCloudForge.FG, p);

            if (antigo.length() == atual.length()) {
                // Rola apenas os dígitos que mudaram, mantendo dígitos iguais fixos
                int cx = xDig;
                for (int i = 0; i < atual.length(); i++) {
                    String ca = antigo.substring(i, i + 1);
                    String cn = atual.substring(i, i + 1);
                    int cw = fmDig.stringWidth(cn);
                    if (ca.equals(cn)) {
                        gClip.setComposite(AlphaComposite.SrcOver);
                        gClip.setColor(TemaCloudForge.FG);
                        gClip.drawString(cn, cx, yBaseDig);
                    } else {
                        gClip.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alfaSaida));
                        gClip.setColor(TemaCloudForge.MUTE);
                        gClip.drawString(ca, cx, (int) Math.round(yBaseDig - direcao * desloc));

                        gClip.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alfaEntrada));
                        gClip.setColor(corAtivo);
                        gClip.drawString(cn, cx, (int) Math.round(yBaseDig + direcao * (h - desloc)));
                    }
                    cx += cw;
                }
            } else {
                // Quando o número de dígitos muda (ex.: 9 -> 10), rola o valor inteiro
                int txAnt = xDig + Math.max(0, (wDigMax - fmDig.stringWidth(antigo)) / 2);
                int txNov = xDig + Math.max(0, (wDigMax - fmDig.stringWidth(atual)) / 2);

                gClip.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alfaSaida));
                gClip.setColor(TemaCloudForge.MUTE);
                gClip.drawString(antigo, txAnt, (int) Math.round(yBaseDig - direcao * desloc));

                gClip.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alfaEntrada));
                gClip.setColor(corAtivo);
                gClip.drawString(atual, txNov, (int) Math.round(yBaseDig + direcao * (h - desloc)));
            }
        }
        gClip.dispose();
    }

    private void desenharMiniBarras(Graphics2D g2, EstadoInstancia est, long agora,
            int x, int y, int largura, int altura) {
        double p = est.transicaoBarras.valor(agora);
        double concAnim = est.concluidasDe + (est.concluidasAlvo - est.concluidasDe) * p;
        double rejAnim = est.rejeitadasDe + (est.rejeitadasAlvo - est.rejeitadasDe) * p;

        // Linha superior: contadores compactos (ok / rej / rec)
        g2.setFont(TemaCloudForge.mono(TemaCloudForge.isModoApresentacao() ? 12.2f : 10f));
        FontMetrics fm = g2.getFontMetrics();
        int yTexto = y + fm.getAscent() + 1;

        String txtOk = "ok " + est.concluidas;
        String txtRej = "rej " + est.rejeitadas;
        String txtRec = "rec " + est.recebidas;

        int cx = x;
        g2.setColor(est.concluidas > 0 ? TemaCloudForge.GOOD : TemaCloudForge.MUTE);
        g2.drawString(txtOk, cx, yTexto);
        cx += fm.stringWidth(txtOk) + 8;

        g2.setColor(est.rejeitadas > 0 ? TemaCloudForge.WARN : TemaCloudForge.MUTE);
        g2.drawString(txtRej, cx, yTexto);

        int wRec = fm.stringWidth(txtRec);
        if (cx + fm.stringWidth(txtRej) + 8 + wRec <= x + largura) {
            g2.setColor(TemaCloudForge.MUTE);
            g2.drawString(txtRec, x + largura - wRec, yTexto);
        }

        // Linha inferior: trilho com mini-barras concluídas (verde) e rejeitadas (âmbar)
        int hBarra = 5;
        int yBarra = y + altura - hBarra - 2;
        g2.setColor(TemaCloudForge.S3);
        g2.fillRoundRect(x, yBarra, largura, hBarra, 3, 3);

        double base = Math.max(1.0, Math.max(est.recebidas, concAnim + rejAnim));
        int wConc = (int) Math.round((concAnim / base) * largura);
        int wRej = (int) Math.round((rejAnim / base) * largura);

        if (est.concluidas > 0 && wConc == 0) {
            wConc = 3;
        }
        if (est.rejeitadas > 0 && wRej == 0) {
            wRej = 3;
        }
        if (wConc + wRej > largura) {
            int excesso = wConc + wRej - largura;
            if (wConc >= wRej) {
                wConc = Math.max(0, wConc - excesso);
            } else {
                wRej = Math.max(0, wRej - excesso);
            }
        }

        int bx = x;
        if (wConc > 0) {
            g2.setColor(TemaCloudForge.GOOD);
            g2.fillRoundRect(bx, yBarra, wConc, hBarra, 3, 3);
            bx += wConc;
        }
        if (wRej > 0) {
            int folga = (wConc > 0 && bx + 1 < x + largura) ? 1 : 0;
            int wRejFinal = Math.max(1, Math.min(x + largura - (bx + folga), wRej - folga));
            g2.setColor(TemaCloudForge.WARN);
            g2.fillRoundRect(bx + folga, yBarra, wRejFinal, hBarra, 3, 3);
        }
    }

    private void desenharBlocoGlobal(Graphics2D g2, Long globalAtual, double globalDe,
            double globalAlvo, double progGlobal, int x, int y, int largura, int altura) {
        boolean ativo = globalAtual != null;

        g2.setColor(TemaCloudForge.S2);
        g2.fillRoundRect(x, y, largura, altura, 5, 5);
        g2.setColor(ativo ? comAlfa(TemaCloudForge.ACCENT, 110) : TemaCloudForge.LINE);
        g2.drawRoundRect(x, y, largura - 1, altura - 1, 5, 5);

        // Filete lateral índigo quando o histórico global está ativo
        g2.setColor(ativo ? TemaCloudForge.ACCENT : TemaCloudForge.LINE);
        g2.fillRoundRect(x + 1, y + 4, 3, altura - 8, 2, 2);

        g2.setFont(TemaCloudForge.sansMedium(10.5f));
        FontMetrics fmRotulo = g2.getFontMetrics();
        int yTexto = y + (altura - fmRotulo.getHeight()) / 2 + fmRotulo.getAscent();
        g2.setColor(ativo ? TemaCloudForge.FG : TemaCloudForge.MUTE);
        String rotulo = "histórico global (bônus)";
        g2.drawString(rotulo, x + 10, yTexto);

        int xLimiteValor = x + 10 + fmRotulo.stringWidth(rotulo) + 8;
        int wDispValor = Math.max(40, x + largura - 8 - xLimiteValor);

        if (!ativo) {
            g2.setFont(TemaCloudForge.mono(10f));
            FontMetrics fmVal = g2.getFontMetrics();
            String msg = ajustarTexto(fmVal, TEXTO_GLOBAL_DESLIGADO, wDispValor);
            g2.setColor(TemaCloudForge.MUTE);
            g2.drawString(msg, x + largura - 8 - fmVal.stringWidth(msg),
                    y + (altura - fmVal.getHeight()) / 2 + fmVal.getAscent());
        } else {
            long exibido = Math.round(globalDe + (globalAlvo - globalDe) * progGlobal);
            String num = Long.toString(exibido);
            String prefixo = "total no nomes: ";

            g2.setFont(TemaCloudForge.monoBold(11.5f));
            FontMetrics fmNum = g2.getFontMetrics();
            int wNum = fmNum.stringWidth(num);

            g2.setFont(TemaCloudForge.mono(10f));
            FontMetrics fmPre = g2.getFontMetrics();
            int wPre = fmPre.stringWidth(prefixo);

            int xNum = x + largura - 8 - wNum;
            int yNum = y + (altura - fmNum.getHeight()) / 2 + fmNum.getAscent();

            if (wPre + wNum <= wDispValor) {
                g2.setColor(TemaCloudForge.MUTE);
                g2.drawString(prefixo, xNum - wPre,
                        y + (altura - fmPre.getHeight()) / 2 + fmPre.getAscent());
            }
            g2.setFont(TemaCloudForge.monoBold(11.5f));
            g2.setColor(TemaCloudForge.ACCENT);
            g2.drawString(num, xNum, yNum);
        }
    }

    private void desenharLegenda(Graphics2D g2, FontMetrics fm, boolean duasLinhas,
            int x, int yTopo, int largura) {
        g2.setFont(TemaCloudForge.sans(TemaCloudForge.isModoApresentacao() ? 11.5f : 10f));
        g2.setColor(TemaCloudForge.MUTE);
        if (!duasLinhas) {
            g2.drawString(LEGENDA_COMPLETA, x, yTopo + fm.getAscent());
        } else {
            int y1 = yTopo + fm.getAscent();
            g2.drawString(ajustarTexto(fm, LEGENDA_L1, largura), x, y1);
            g2.drawString(ajustarTexto(fm, LEGENDA_L2, largura), x, y1 + fm.getHeight() - 2);
        }
    }

    /** Retorna o #id comum caso duas ou mais instâncias tenham o mesmo contador positivo. */
    private static Long encontrarIdSimultaneo(List<EstadoInstancia> lista) {
        if (lista.size() < 2) {
            return null;
        }
        Map<Long, Integer> contagem = new HashMap<>();
        for (EstadoInstancia est : lista) {
            if (est.idTarefaAtual <= 0) {
                continue;
            }
            int n = contagem.getOrDefault(est.idTarefaAtual, 0) + 1;
            if (n >= 2) {
                return est.idTarefaAtual;
            }
            contagem.put(est.idTarefaAtual, n);
        }
        return null;
    }

    private static Color resolverCor(int cor) {
        if (cor >= 0 && cor < 64) {
            return TemaCloudForge.CORES_INSTANCIA[cor % TemaCloudForge.CORES_INSTANCIA.length];
        }
        if (cor < 0 && cor >= -64) {
            return TemaCloudForge.ACCENT;
        }
        return new Color(cor);
    }

    private static Color comAlfa(Color base, int alfa) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(0, Math.min(255, alfa)));
    }

    private static String ajustarTexto(FontMetrics fm, String texto, int larguraMax) {
        if (texto == null || larguraMax <= 0) {
            return "";
        }
        if (fm.stringWidth(texto) <= larguraMax) {
            return texto;
        }
        String reticencias = "...";
        int wRet = fm.stringWidth(reticencias);
        if (wRet >= larguraMax) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (fm.stringWidth(sb.toString() + c) + wRet > larguraMax) {
                break;
            }
            sb.append(c);
        }
        return sb + reticencias;
    }
}
