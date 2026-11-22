package com.mycompany.clientecloudforge;

import com.mycompany.cloudforge.Estatisticas;
import com.mycompany.cloudforge.Instancia;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JPanel;

/**
 * Topologia animada com leitura ampla, nós com respiro, fluxo nas linhas e pacotes de alto contraste.
 * Suporta tanto layout lado a lado (janelas largas) quanto faixa horizontal (janelas estreitas).
 */
public class PainelTopologia extends JPanel implements Animador.Animavel {
    private static final int MAX_PACOTES = 60;
    private static final long DURACAO_PACOTE_MS = 400;
    private static final long DURACAO_PULSO_MS = 400;
    private static final long DURACAO_CROSSFADE_MS = 250;
    private static final long DURACAO_LINHA_TRAFEGO_MS = 900;
    private static final long DURACAO_POP_MS = 260;
    private static final long DURACAO_PULSO_NOMES_MS = 450;

    private static final class NoInstancia {
        final String id;
        final int indice;
        String saude = "VERIFICANDO";
        int emAndamento;
        double emAndamentoAnimado;
        int limite;
        Color corAnterior = TemaCloudForge.MUTE;
        Color corAlvo = TemaCloudForge.MUTE;
        double opacidadeAnterior = 1.0;
        double opacidadeAlvo = 1.0;
        long inicioCrossfadeMs;
        long ultimoPulsoMs;
        long ultimoHealthMs;
        long ultimoTrafegoMs;
        long inicioPopMs;
        final Animador.Transicao hoverTrans = new Animador.Transicao(150, 0.0);
        Rectangle area = new Rectangle();

        NoInstancia(String id, int indice) {
            this.id = id;
            this.indice = indice;
        }
    }

    private static final class Pacote {
        final String idInstancia;
        final int indiceInstancia;
        final boolean falha;
        final Color cor;
        final Color corTexto;
        final String rotulo;
        final long inicioMs;
        boolean pulsoDisparado;

        Pacote(String idInstancia, int indiceInstancia, boolean falha, Color cor, Color corTexto, String rotulo) {
            this.idInstancia = idInstancia;
            this.indiceInstancia = indiceInstancia;
            this.falha = falha;
            this.cor = cor;
            this.corTexto = corTexto;
            this.rotulo = rotulo == null ? "" : rotulo;
            this.inicioMs = System.currentTimeMillis();
        }
    }

    private final List<NoInstancia> nos = new ArrayList<>();
    private final List<Pacote> pacotes = new ArrayList<>();
    private String hoverId;
    private long ultimoPulsoNomesMs = 0L;
    private double faseFluxo = 0.0;
    private long ultimoTickMs = System.currentTimeMillis();
    private volatile double taxaReqPorSeg = 1.0;

    public PainelTopologia(Consumer<String> onAbrirHistorico) {
        setBackground(TemaCloudForge.S1);
        setBorder(new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6));
        setPreferredSize(new Dimension(340, 188));
        setMinimumSize(new Dimension(280, 96));

        Animador.registrar(this);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                String id = noEm(e.getPoint());
                if ((id == null && hoverId != null) || (id != null && !id.equals(hoverId))) {
                    String antigo = hoverId;
                    hoverId = id;
                    if (antigo != null) {
                        NoInstancia nAnt = buscarNo(antigo);
                        if (nAnt != null) nAnt.hoverTrans.iniciar(nAnt.hoverTrans.valor(System.currentTimeMillis()), 0.0);
                    }
                    if (id != null) {
                        NoInstancia nNovo = buscarNo(id);
                        if (nNovo != null) nNovo.hoverTrans.iniciar(nNovo.hoverTrans.valor(System.currentTimeMillis()), 1.0);
                    }
                    setCursor(id != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverId != null) {
                    NoInstancia nAnt = buscarNo(hoverId);
                    if (nAnt != null) nAnt.hoverTrans.iniciar(nAnt.hoverTrans.valor(System.currentTimeMillis()), 0.0);
                    hoverId = null;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1 && onAbrirHistorico != null) {
                    String id = noEm(e.getPoint());
                    if (id != null) onAbrirHistorico.accept(id);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setTaxaReqPorSeg(double taxa) {
        this.taxaReqPorSeg = Math.max(0.2, taxa);
    }

    /** Anel de 140 ms, disparado apenas ao terminar um Health.Check real. */
    public void animarHealth(String instancia) {
        NoInstancia no = buscarNo(instancia);
        if (no == null) return;
        no.ultimoHealthMs = System.currentTimeMillis();
        Animador.acordar();
        repaint();
    }

    public void animarConsultaNomes() {
        ultimoPulsoNomesMs = System.currentTimeMillis();
        Animador.acordar();
        repaint();
    }

    @Override
    public boolean atualizarAnimacao(long agora) {
        long dtFrame = Math.max(1L, Math.min(50L, agora - ultimoTickMs));
        ultimoTickMs = agora;
        boolean ativo = false;

        Iterator<Pacote> it = pacotes.iterator();
        while (it.hasNext()) {
            Pacote p = it.next();
            long decorrido = agora - p.inicioMs;
            if (decorrido >= (Animador.isReduzirAnimacoes() ? 40L : DURACAO_PACOTE_MS)) {
                if (!p.falha && !p.pulsoDisparado) {
                    p.pulsoDisparado = true;
                    NoInstancia no = buscarNo(p.idInstancia);
                    if (no != null) {
                        no.ultimoPulsoMs = agora;
                        ativo = true;
                    }
                }
                it.remove();
            } else {
                ativo = true;
            }
        }

        boolean algumTrafego = !pacotes.isEmpty();
        for (NoInstancia no : nos) {
            if (agora - no.ultimoHealthMs < 140) ativo = true;
            if (agora - no.ultimoPulsoMs < DURACAO_PULSO_MS) ativo = true;
            if (agora - no.inicioCrossfadeMs < DURACAO_CROSSFADE_MS) ativo = true;
            if (agora - no.ultimoTrafegoMs < DURACAO_LINHA_TRAFEGO_MS) {
                ativo = true;
                algumTrafego = true;
            }
            if (agora - no.inicioPopMs < DURACAO_POP_MS) ativo = true;
            if (no.hoverTrans.ativa(agora)) ativo = true;

            double diff = no.emAndamento - no.emAndamentoAnimado;
            if (Math.abs(diff) > 0.02) {
                no.emAndamentoAnimado += diff * 0.25;
                ativo = true;
            } else if (no.emAndamentoAnimado != no.emAndamento) {
                no.emAndamentoAnimado = no.emAndamento;
                ativo = true;
            }
        }

        if (algumTrafego && !Animador.isReduzirAnimacoes()) {
            double vel = 0.025 + Math.min(0.14, taxaReqPorSeg * 0.015);
            faseFluxo = (faseFluxo + vel * dtFrame) % 1000.0;
        }

        if (agora - ultimoPulsoNomesMs < DURACAO_PULSO_NOMES_MS) {
            ativo = true;
        }


        repaint();
        return ativo;
    }

    public void atualizarNos(List<Instancia> instancias, Map<String, String> saude, List<Estatisticas> stats) {
        Map<String, NoInstancia> antigos = new LinkedHashMap<>();
        for (NoInstancia n : nos) antigos.put(n.id, n);

        Map<String, Estatisticas> mapaStats = new LinkedHashMap<>();
        for (Estatisticas s : stats) mapaStats.put(s.getInstancia(), s);

        nos.clear();
        long agora = System.currentTimeMillis();

        for (int i = 0; i < instancias.size(); i++) {
            Instancia inst = instancias.get(i);
            NoInstancia prev = antigos.get(inst.getId());
            NoInstancia no = new NoInstancia(inst.getId(), i);
            if (prev != null) {
                no.corAnterior = corSaudeAtual(prev, agora);
                no.corAlvo = prev.corAlvo;
                no.opacidadeAnterior = opacidadeAtual(prev, agora);
                no.opacidadeAlvo = prev.opacidadeAlvo;
                no.inicioCrossfadeMs = prev.inicioCrossfadeMs;
                no.ultimoPulsoMs = prev.ultimoPulsoMs;
                no.ultimoHealthMs = prev.ultimoHealthMs;
                no.ultimoTrafegoMs = prev.ultimoTrafegoMs;
                no.inicioPopMs = prev.inicioPopMs;
                no.emAndamento = prev.emAndamento;
                no.emAndamentoAnimado = prev.emAndamentoAnimado;
                no.limite = prev.limite;
            }
            String stAntiga = prev != null ? prev.saude : "VERIFICANDO";
            String st = saude.getOrDefault(inst.getId(), stAntiga);
            no.saude = st;

            Color novaCor = corParaSaude(st);
            // Mantém contraste AA legível mesmo quando fora do rodízio
            double novaOp = ("NOT_SERVING".equals(st) || "INDISPONIVEL".equals(st)) ? 0.84 : 1.0;
            if (!novaCor.equals(no.corAlvo) || Math.abs(novaOp - no.opacidadeAlvo) > 0.01) {
                no.corAnterior = corSaudeAtual(no, agora);
                no.corAlvo = novaCor;
                no.opacidadeAnterior = opacidadeAtual(no, agora);
                no.opacidadeAlvo = novaOp;
                no.inicioCrossfadeMs = agora;
                if (!"SERVING".equals(stAntiga) && "SERVING".equals(st) && prev != null) {
                    no.inicioPopMs = agora;
                }
            }

            Estatisticas est = mapaStats.get(inst.getId());
            if (est != null) {
                no.emAndamento = est.getEmAndamento();
                no.limite = est.getLimiteSimultaneas();
            }
            nos.add(no);
        }
        Animador.acordar();
        repaint();
    }

    public void animarPacoteOk(String idInstancia, String rotulo) {
        NoInstancia no = buscarNo(idInstancia);
        int idx = no != null ? no.indice : 0;
        if (no != null) no.ultimoTrafegoMs = System.currentTimeMillis();
        Color cor = TemaCloudForge.corInstancia(idx);
        Color corTexto = TemaCloudForge.corTextoInstancia(idx);
        adicionarPacote(new Pacote(idInstancia, idx, false, cor, corTexto, rotulo));
    }

    public void animarPacoteSkip(String idInstancia, boolean erroGrave, String rotulo) {
        NoInstancia no = buscarNo(idInstancia);
        int idx = no != null ? no.indice : 0;
        if (no != null) no.ultimoTrafegoMs = System.currentTimeMillis();
        Color cor = erroGrave ? TemaCloudForge.BAD : TemaCloudForge.WARN;
        adicionarPacote(new Pacote(idInstancia, idx, true, cor, cor, rotulo));
    }

    private void adicionarPacote(Pacote p) {
        if (pacotes.size() >= MAX_PACOTES) {
            pacotes.remove(0);
        }
        pacotes.add(p);
        Animador.acordar();
    }

    private boolean ehPacoteMaisRecente(Pacote alvo) {
        for (int i = pacotes.size() - 1; i >= 0; i--) {
            Pacote p = pacotes.get(i);
            if (!p.falha && p.idInstancia.equals(alvo.idInstancia)) {
                return p == alvo;
            }
        }
        return false;
    }

    private NoInstancia buscarNo(String id) {
        for (NoInstancia n : nos) {
            if (n.id.equals(id)) return n;
        }
        return null;
    }

    private String noEm(Point p) {
        for (NoInstancia n : nos) {
            if (n.area.contains(p)) return n.id;
        }
        return null;
    }

    private static Color corParaSaude(String st) {
        return switch (st) {
            case "SERVING" -> TemaCloudForge.GOOD;
            case "NOT_SERVING" -> TemaCloudForge.WARN;
            case "VERIFICANDO" -> TemaCloudForge.MUTE;
            default -> TemaCloudForge.BAD;
        };
    }

    private static Color corSaudeAtual(NoInstancia no, long agora) {
        if (Animador.isReduzirAnimacoes()) return no.corAlvo;
        double t = Math.min(1.0, (double) (agora - no.inicioCrossfadeMs) / DURACAO_CROSSFADE_MS);
        return TemaCloudForge.interpolarCor(no.corAnterior, no.corAlvo, Animador.easeOut(t));
    }

    private static double opacidadeAtual(NoInstancia no, long agora) {
        if (Animador.isReduzirAnimacoes()) return no.opacidadeAlvo;
        double t = Math.min(1.0, (double) (agora - no.inicioCrossfadeMs) / DURACAO_CROSSFADE_MS);
        double ease = Animador.easeOut(t);
        return no.opacidadeAnterior + (no.opacidadeAlvo - no.opacidadeAnterior) * ease;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        long agora = System.currentTimeMillis();

        // Faixa horizontal quando a topologia fica empilhada abaixo da tabela (ex.: 1000x700)
        boolean faixaHorizontal = w > 540 && h < 145;

        Stroke solido = new BasicStroke(1.2f);
        Stroke tracejado = new BasicStroke(1.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 8f, new float[]{4f, 3f}, 0f);

        long dtNomes = agora - ultimoPulsoNomesMs;
        double fatorNomes = (!Animador.isReduzirAnimacoes() && dtNomes >= 0 && dtNomes < DURACAO_PULSO_NOMES_MS)
                ? 1.0 - Animador.easeOut((double) dtNomes / DURACAO_PULSO_NOMES_MS)
                : 0.0;
        Color corLinhaNomes = TemaCloudForge.interpolarCor(TemaCloudForge.MUTE, TemaCloudForge.ACCENT, fatorNomes * 0.75);

        Rectangle boxNomes;
        Rectangle boxCliente;
        int xNoInst;
        int wNoInst;
        int topoUtil;
        int baseUtil;
        int espacoNos;

        if (faixaHorizontal) {
            boxNomes = new Rectangle(20, (h - 28) / 2, 132, 28);
            int xCli = Math.max(boxNomes.x + boxNomes.width + 92, (int) Math.round(w * 0.28));
            boxCliente = new Rectangle(xCli, (h - 32) / 2, 84, 32);
            wNoInst = Math.min(280, Math.max(180, (int) Math.round(w * 0.30)));
            xNoInst = Math.max(boxCliente.x + boxCliente.width + 120, w - 20 - wNoInst);
            wNoInst = Math.max(150, w - 20 - xNoInst);
            topoUtil = 6;
            baseUtil = h - 6;
            espacoNos = 6;
        } else {
            boxNomes = new Rectangle(16, 12, 126, 26);
            wNoInst = Math.min(216, Math.max(134, (int) Math.round(w * 0.43)));
            xNoInst = w - 16 - wNoInst;
            topoUtil = 52;
            baseUtil = h - 12;
            espacoNos = 10;
            int yCliCentro = topoUtil + (baseUtil - topoUtil) / 2;
            boxCliente = new Rectangle(16, yCliCentro - 18, 76, 36);
        }

        // Calcula áreas das instâncias sem nunca extrapolar baseUtil
        int qtd = nos.size();
        int yInicioNos = topoUtil;
        if (qtd > 0) {
            int espacoDisp = Math.max(28, baseUtil - topoUtil - (qtd - 1) * espacoNos);
            int hMaxNo = faixaHorizontal ? 34 : 52;
            int hCabivel = espacoDisp / qtd;
            int hNo = Math.min(hMaxNo, Math.max(22, hCabivel));
            int totalH = qtd * hNo + (qtd - 1) * espacoNos;
            yInicioNos = topoUtil + Math.max(0, (baseUtil - topoUtil - totalH) / 2);

            if (!faixaHorizontal) {
                int yCliCentro = yInicioNos + totalH / 2;
                int yCli = Math.max(topoUtil, Math.min(baseUtil - 36, yCliCentro - 18));
                boxCliente = new Rectangle(16, yCli, 76, 36);
            }

            for (int i = 0; i < qtd; i++) {
                int yNo = yInicioNos + i * (hNo + espacoNos);
                nos.get(i).area.setBounds(xNoInst, yNo, wNoInst, hNo);
            }
        }

        // Nó Serviço de Nomes (texto FG para alta legibilidade)
        g2.setColor(TemaCloudForge.S2);
        g2.fillRoundRect(boxNomes.x, boxNomes.y, boxNomes.width, boxNomes.height, 6, 6);
        g2.setColor(TemaCloudForge.interpolarCor(TemaCloudForge.LINE, TemaCloudForge.ACCENT, fatorNomes * 0.6));
        g2.setStroke(solido);
        g2.drawRoundRect(boxNomes.x, boxNomes.y, boxNomes.width, boxNomes.height, 6, 6);
        g2.setFont(TemaCloudForge.mono(11f));
        g2.setColor(TemaCloudForge.FG);
        desenharCentralizado(g2, "serviço de nomes", boxNomes);

        // Nó Cliente
        g2.setColor(TemaCloudForge.S2);
        g2.fillRoundRect(boxCliente.x, boxCliente.y, boxCliente.width, boxCliente.height, 6, 6);
        g2.setColor(TemaCloudForge.LINE);
        g2.drawRoundRect(boxCliente.x, boxCliente.y, boxCliente.width, boxCliente.height, 6, 6);
        g2.setFont(TemaCloudForge.monoBold(11.5f));
        g2.setColor(TemaCloudForge.FG);
        desenharCentralizado(g2, "cliente", boxCliente);

        // Conector "onde estão" com ponto de origem e seta sutil
        if (faixaHorizontal) {
            int x1 = boxNomes.x + boxNomes.width;
            int x2 = boxCliente.x - 4;
            int yL = boxNomes.y + boxNomes.height / 2;
            g2.setColor(corLinhaNomes);
            g2.fillOval(x1 - 2, yL - 2, 4, 4);
            g2.setStroke(tracejado);
            g2.drawLine(x1 + 2, yL, x2 - 4, yL);
            g2.setStroke(solido);
            desenharSetaDireita(g2, x2, yL, corLinhaNomes);

            g2.setFont(TemaCloudForge.sans(10.5f));
            g2.setColor(TemaCloudForge.MUTE);
            FontMetrics fmO = g2.getFontMetrics();
            int xRot = x1 + Math.max(6, (x2 - x1 - fmO.stringWidth("onde estão")) / 2);
            g2.drawString("onde estão", xRot, yL - 6);
        } else {
            int x1 = boxNomes.x + boxNomes.width;
            int yL = boxNomes.y + boxNomes.height / 2;
            int xCurva = xNoInst + wNoInst / 2;
            int yAlvo = (qtd > 0 ? yInicioNos : topoUtil + 8) - 4;

            g2.setColor(corLinhaNomes);
            g2.fillOval(x1 - 2, yL - 2, 4, 4);
            g2.setStroke(tracejado);
            g2.drawLine(x1 + 2, yL, xCurva, yL);
            if (yAlvo > yL + 4) {
                g2.drawLine(xCurva, yL, xCurva, yAlvo - 3);
                g2.setStroke(solido);
                desenharSetaBaixo(g2, xCurva, yAlvo, corLinhaNomes);
            } else {
                g2.setStroke(solido);
                desenharSetaDireita(g2, xCurva, yL, corLinhaNomes);
            }

            g2.setFont(TemaCloudForge.sans(10.5f));
            g2.setColor(TemaCloudForge.MUTE);
            FontMetrics fmO = g2.getFontMetrics();
            int xRot = x1 + Math.max(6, (xCurva - x1 - fmO.stringWidth("onde estão")) / 2);
            g2.drawString("onde estão", xRot, yL - 5);
        }

        if (nos.isEmpty()) {
            g2.setFont(TemaCloudForge.mono(12f));
            g2.setColor(TemaCloudForge.MUTE);
            g2.drawString("Sem instâncias", xNoInst + 8, h / 2 + 4);
            g2.dispose();
            return;
        }

        int cxCliente = boxCliente.x + boxCliente.width;
        int cyCliente = boxCliente.y + boxCliente.height / 2;

        // Linhas ligando cliente a cada instância (com fluxo animado enquanto há tráfego)
        for (NoInstancia no : nos) {
            int cyNo = no.area.y + no.area.height / 2;
            boolean fora = "NOT_SERVING".equals(no.saude) || "INDISPONIVEL".equals(no.saude);
            long dtTraf = agora - no.ultimoTrafegoMs;
            double fatorTraf = dtTraf >= 0 && dtTraf < DURACAO_LINHA_TRAFEGO_MS
                    ? 1.0 - Animador.easeOut((double) dtTraf / DURACAO_LINHA_TRAFEGO_MS)
                    : 0.0;
            Color corLinha = TemaCloudForge.interpolarCor(TemaCloudForge.LINE, TemaCloudForge.MUTE, fatorTraf);

            g2.setColor(corLinha);
            g2.setStroke(fora ? tracejado : solido);
            g2.drawLine(cxCliente, cyCliente, xNoInst - 2, cyNo);

            // Ponto sutil na chegada ao nó
            if (!fora) {
                g2.fillOval(xNoInst - 4, cyNo - 2, 4, 4);
            }

            // Tracinhos animados na direção do servidor enquanto há tráfego ativo
            if (!fora && fatorTraf > 0.04 && !Animador.isReduzirAnimacoes()) {
                Color corInst = TemaCloudForge.corInstancia(no.indice);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (fatorTraf * 0.6)));
                g2.setColor(corInst);
                float offset = (float) (12.0f - (faseFluxo % 12.0f));
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER,
                        8f, new float[]{4f, 8f}, offset));
                g2.drawLine(cxCliente, cyCliente, xNoInst - 2, cyNo);
                g2.setComposite(AlphaComposite.SrcOver);
            }
        }
        g2.setStroke(solido);



        // Desenha nós das instâncias + pop + pulsos + hover suave
        for (NoInstancia no : nos) {
            Rectangle r = no.area;
            if (!Animador.isReduzirAnimacoes() && agora - no.ultimoHealthMs < 140) {
                g2.setColor(TemaCloudForge.ACCENT);
                g2.drawOval(r.x - 8, r.y + r.height / 2 - 11, 22, 22);
                g2.setFont(TemaCloudForge.monoBold(11f));
                g2.drawString("H", r.x - 2, r.y + r.height / 2 + 4);
            }
            boolean fora = "NOT_SERVING".equals(no.saude) || "INDISPONIVEL".equals(no.saude);
            double hov = no.hoverTrans.valor(agora);

            // Pop único de escala (1.0 -> 1.04 -> 1.0) ao voltar ao rodízio
            double escalaPop = 1.0;
            long dtPop = agora - no.inicioPopMs;
            if (!Animador.isReduzirAnimacoes() && no.inicioPopMs > 0 && dtPop >= 0 && dtPop < DURACAO_POP_MS) {
                double tp = (double) dtPop / DURACAO_POP_MS;
                escalaPop = 1.0 + 0.04 * Math.sin(tp * Math.PI);
            }

            // Pulso suave ao chegar pacote
            long dtPulso = agora - no.ultimoPulsoMs;
            if (!Animador.isReduzirAnimacoes() && dtPulso >= 0 && dtPulso < DURACAO_PULSO_MS) {
                double tp = Animador.easeOut((double) dtPulso / DURACAO_PULSO_MS);
                int exp = (int) Math.round(tp * 6.0);
                float alpha = (float) ((1.0 - tp) * 0.55);
                Color corInst = TemaCloudForge.corInstancia(no.indice);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.setColor(corInst);
                g2.drawRoundRect(r.x - exp, r.y - exp, r.width + exp * 2, r.height + exp * 2, 8, 8);
                g2.setComposite(AlphaComposite.SrcOver);
            }

            Graphics2D gn = (Graphics2D) g2.create();
            if (escalaPop != 1.0) {
                gn.translate(r.getCenterX(), r.getCenterY());
                gn.scale(escalaPop, escalaPop);
                gn.translate(-r.getCenterX(), -r.getCenterY());
            }

            float op = (float) opacidadeAtual(no, agora);
            gn.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, op));

            Color fundoNo = TemaCloudForge.interpolarCor(fora ? TemaCloudForge.S2 : TemaCloudForge.S1, TemaCloudForge.S2, hov);
            Color bordaNo = TemaCloudForge.interpolarCor(TemaCloudForge.LINE, TemaCloudForge.ACCENT, hov);
            gn.setColor(fundoNo);
            gn.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
            gn.setColor(bordaNo);
            gn.setStroke(fora ? tracejado : solido);
            gn.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6);
            gn.setStroke(solido);

            // Cor de saúde vem da última consulta real.
            Color corSaude = corSaudeAtual(no, agora);
            int yTexto = r.height >= 40 ? (r.y + r.height / 2 - 2) : (r.y + r.height / 2 + 1);
            int dotX = r.x + 12;
            int dotY = yTexto - 6;



            gn.setColor(corSaude);
            gn.fillOval(dotX, dotY, 7, 7);

            // ID da instância
            gn.setFont(TemaCloudForge.mono(12f));
            gn.setColor(fora ? TemaCloudForge.MUTE : TemaCloudForge.FG);
            gn.drawString(no.id, r.x + 26, yTexto + 1);

            // Mini-barra de em-andamento animada suavemente
            if (r.height >= 30) {
                int xBarra = r.x + 26;
                int wBarra = Math.max(32, r.width - 38);
                int hBarra = 4;
                int yBarra = r.y + r.height - 8;
                gn.setColor(TemaCloudForge.S3);
                gn.fillRoundRect(xBarra, yBarra, wBarra, hBarra, 2, 2);
                if (no.limite > 0 && no.emAndamentoAnimado > 0.01) {
                    int fill = Math.max(3, (int) Math.round(wBarra * Math.min(1.0, no.emAndamentoAnimado / no.limite)));
                    gn.setColor(TemaCloudForge.corInstancia(no.indice));
                    gn.fillRoundRect(xBarra, yBarra, fill, hBarra, 2, 2);
                }
            }
            gn.dispose();
        }

        // Desenha pacotes viajando com rastro (4 posições anteriores) e rótulo de alto contraste
        for (Pacote p : pacotes) {
            NoInstancia alvo = buscarNo(p.idInstancia);
            if (alvo == null) continue;
            double raw = Math.min(1.0, (double) (agora - p.inicioMs) / DURACAO_PACOTE_MS);
            int tx = alvo.area.x;
            int ty = alvo.area.y + alvo.area.height / 2;

            if (!p.falha) {
                for (int r = 4; r >= 1; r--) {
                    double rawRastro = Math.max(0.0, raw - r * 0.06);
                    if (rawRastro <= 0.0) continue;
                    double easeR = Animador.easeOut(rawRastro);
                    int rx = (int) Math.round(cxCliente + (tx - cxCliente) * easeR);
                    int ry = (int) Math.round(cyCliente + (ty - cyCliente) * easeR);
                    float alphaR = (float) (0.35 * (1.0 - r / 5.0));
                    int diam = Math.max(3, 8 - r);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaR));
                    g2.setColor(p.cor);
                    g2.fillOval(rx - diam / 2, ry - diam / 2, diam, diam);
                }
                g2.setComposite(AlphaComposite.SrcOver);

                double ease = Animador.easeOut(raw);
                int px = (int) Math.round(cxCliente + (tx - cxCliente) * ease);
                int py = (int) Math.round(cyCliente + (ty - cyCliente) * ease);
                g2.setColor(p.cor);
                g2.fillOval(px - 4, py - 4, 8, 8);

                // Posiciona rótulo acima da linha superior e abaixo da linha inferior para evitar sobreposição
                if (!p.rotulo.isEmpty() && ehPacoteMaisRecente(p)) {
                    boolean acima = (alvo.indice == 0);
                    desenharRotuloPacote(g2, p.rotulo, px, py, acima, p.corTexto, w, h);
                }
            } else {
                double pos = raw <= 0.5 ? Animador.easeOut(raw * 2.0) * 0.52 : (1.0 - raw) * 2.0 * 0.52;
                int px = (int) Math.round(cxCliente + (tx - cxCliente) * pos);
                int py = (int) Math.round(cyCliente + (ty - cyCliente) * pos);
                float alpha = (float) Math.max(0.25, 1.0 - raw * 0.65);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.setColor(p.cor);
                g2.setStroke(tracejado);
                g2.drawOval(px - 5, py - 5, 10, 10);
                g2.setStroke(solido);
                g2.setFont(TemaCloudForge.monoBold(10f));
                g2.drawString("x", px - 3, py - 8);
                g2.setComposite(AlphaComposite.SrcOver);
            }
        }

        g2.dispose();
    }

    private static void desenharSetaBaixo(Graphics2D g2, int x, int y, Color cor) {
        g2.setColor(cor);
        int[] xs = {x - 4, x + 4, x};
        int[] ys = {y - 5, y - 5, y};
        g2.fillPolygon(xs, ys, 3);
    }

    private static void desenharSetaDireita(Graphics2D g2, int x, int y, Color cor) {
        g2.setColor(cor);
        int[] xs = {x - 5, x - 5, x};
        int[] ys = {y - 4, y + 4, y};
        g2.fillPolygon(xs, ys, 3);
    }

    private static void desenharRotuloPacote(Graphics2D g2, String texto, int px, int py, boolean acima,
            Color corTexto, int wPainel, int hPainel) {
        g2.setFont(TemaCloudForge.monoBold(10f));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(texto);
        int bw = tw + 10;
        int bh = fm.getHeight() + 4;

        int bx = Math.max(6, Math.min(wPainel - bw - 6, px - bw / 2));
        int by = acima ? (py - bh - 7) : (py + 8);
        by = Math.max(6, Math.min(hPainel - bh - 6, by));

        g2.setColor(TemaCloudForge.S1);
        g2.fillRoundRect(bx, by, bw, bh, 4, 4);
        g2.setColor(corTexto);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(bx, by, bw, bh, 4, 4);
        int ty = by + (bh - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(texto, bx + 5, ty);
    }

    private static void desenharCentralizado(Graphics2D g2, String texto, Rectangle r) {
        FontMetrics fm = g2.getFontMetrics();
        int x = r.x + (r.width - fm.stringWidth(texto)) / 2;
        int y = r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(texto, x, y);
    }
}
