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
import javax.swing.Timer;

/**
 * Topologia animada: Cliente -> Serviço de Nomes / Instâncias com pacotes e pulso.
 */
public class PainelTopologia extends JPanel {
    private static final int MAX_PACOTES = 60;
    private static final long DURACAO_PACOTE_MS = 350;
    private static final long DURACAO_PULSO_MS = 400;
    private static final long DURACAO_CROSSFADE_MS = 200;

    private static final class NoInstancia {
        final String id;
        final int indice;
        String saude = "VERIFICANDO";
        int emAndamento;
        int limite;
        Color corAnterior = TemaCloudForge.MUTE;
        Color corAlvo = TemaCloudForge.MUTE;
        long inicioCrossfadeMs;
        long ultimoPulsoMs;
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
        final long inicioMs;
        boolean pulsoDisparado;

        Pacote(String idInstancia, int indiceInstancia, boolean falha, Color cor) {
            this.idInstancia = idInstancia;
            this.indiceInstancia = indiceInstancia;
            this.falha = falha;
            this.cor = cor;
            this.inicioMs = System.currentTimeMillis();
        }
    }

    private final List<NoInstancia> nos = new ArrayList<>();
    private final List<Pacote> pacotes = new ArrayList<>();
    private final Timer timer;
    private String hoverId;

    public PainelTopologia(Consumer<String> onAbrirHistorico) {
        setBackground(TemaCloudForge.S1);
        setBorder(new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6));
        setPreferredSize(new Dimension(380, 178));
        setMinimumSize(new Dimension(320, 170));

        // Um único Timer (~16 ms) anima pacotes, pulsos e crossfade da saúde.
        timer = new Timer(16, e -> {
            long agora = System.currentTimeMillis();
            boolean ativo = false;

            Iterator<Pacote> it = pacotes.iterator();
            while (it.hasNext()) {
                Pacote p = it.next();
                long decorrido = agora - p.inicioMs;
                if (decorrido >= DURACAO_PACOTE_MS) {
                    if (!p.falha && !p.pulsoDisparado) {
                        p.pulsoDisparado = true;
                        NoInstancia no = buscarNo(p.idInstancia);
                        if (no != null) no.ultimoPulsoMs = agora;
                    }
                    it.remove();
                } else {
                    ativo = true;
                }
            }

            for (NoInstancia no : nos) {
                if (agora - no.ultimoPulsoMs < DURACAO_PULSO_MS) ativo = true;
                if (agora - no.inicioCrossfadeMs < DURACAO_CROSSFADE_MS) ativo = true;
            }

            repaint();
            if (!ativo) {
                ((Timer) e.getSource()).stop();
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                String id = noEm(e.getPoint());
                if ((id == null && hoverId != null) || (id != null && !id.equals(hoverId))) {
                    hoverId = id;
                    setCursor(id != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverId != null) {
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

    public void pararTimer() {
        timer.stop();
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
                no.inicioCrossfadeMs = prev.inicioCrossfadeMs;
                no.ultimoPulsoMs = prev.ultimoPulsoMs;
                no.emAndamento = prev.emAndamento;
                no.limite = prev.limite;
            }
            String st = saude.getOrDefault(inst.getId(), prev != null ? prev.saude : "VERIFICANDO");
            no.saude = st;
            Color novaCor = corParaSaude(st);
            if (!novaCor.equals(no.corAlvo)) {
                no.corAnterior = corSaudeAtual(no, agora);
                no.corAlvo = novaCor;
                no.inicioCrossfadeMs = agora;
            }
            Estatisticas est = mapaStats.get(inst.getId());
            if (est != null) {
                no.emAndamento = est.getEmAndamento();
                no.limite = est.getLimiteSimultaneas();
            }
            nos.add(no);
        }
        garantirTimer();
        repaint();
    }

    public void animarPacoteOk(String idInstancia) {
        NoInstancia no = buscarNo(idInstancia);
        int idx = no != null ? no.indice : 0;
        Color cor = TemaCloudForge.CORES_INSTANCIA[idx % TemaCloudForge.CORES_INSTANCIA.length];
        adicionarPacote(new Pacote(idInstancia, idx, false, cor));
    }

    public void animarPacoteSkip(String idInstancia, boolean erroGrave) {
        NoInstancia no = buscarNo(idInstancia);
        int idx = no != null ? no.indice : 0;
        Color cor = erroGrave ? TemaCloudForge.BAD : TemaCloudForge.WARN;
        adicionarPacote(new Pacote(idInstancia, idx, true, cor));
    }

    private void adicionarPacote(Pacote p) {
        if (pacotes.size() >= MAX_PACOTES) {
            pacotes.remove(0);
        }
        pacotes.add(p);
        garantirTimer();
    }

    private void garantirTimer() {
        if (!timer.isRunning()) timer.start();
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
        double t = Math.min(1.0, (double) (agora - no.inicioCrossfadeMs) / DURACAO_CROSSFADE_MS);
        return TemaCloudForge.interpolarCor(no.corAnterior, no.corAlvo, t);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        long agora = System.currentTimeMillis();

        // Posições base proporcionais à largura do painel
        Rectangle boxNomes = new Rectangle(14, 10, 122, 26);
        Rectangle boxCliente = new Rectangle(14, Math.max(76, h / 2 + 2), 76, 36);

        Stroke solido = new BasicStroke(1f);
        Stroke tracejado = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 8f, new float[]{4f, 3f}, 0f);

        // Nó Serviço de Nomes no alto
        g2.setColor(TemaCloudForge.S2);
        g2.fillRoundRect(boxNomes.x, boxNomes.y, boxNomes.width, boxNomes.height, 6, 6);
        g2.setColor(TemaCloudForge.LINE);
        g2.drawRoundRect(boxNomes.x, boxNomes.y, boxNomes.width, boxNomes.height, 6, 6);
        g2.setFont(TemaCloudForge.mono(10.5f));
        g2.setColor(TemaCloudForge.MUTE);
        desenharCentralizado(g2, "serviço de nomes", boxNomes);

        // Nó Cliente à esquerda
        g2.setColor(TemaCloudForge.S2);
        g2.fillRoundRect(boxCliente.x, boxCliente.y, boxCliente.width, boxCliente.height, 6, 6);
        g2.setColor(TemaCloudForge.LINE);
        g2.drawRoundRect(boxCliente.x, boxCliente.y, boxCliente.width, boxCliente.height, 6, 6);
        g2.setFont(TemaCloudForge.monoBold(11f));
        g2.setColor(TemaCloudForge.FG);
        desenharCentralizado(g2, "cliente", boxCliente);

        // Linha tracejada "onde estão" do Serviço de Nomes para a coluna de instâncias
        int xNoInst = Math.max(150, (int) Math.round(w * 0.45));
        int wNoInst = Math.max(120, w - xNoInst - 14);
        g2.setColor(TemaCloudForge.LINE);
        g2.setStroke(tracejado);
        int yLinhaNomes = boxNomes.y + boxNomes.height / 2;
        g2.drawLine(boxNomes.x + boxNomes.width, yLinhaNomes, xNoInst - 8, yLinhaNomes);
        g2.setStroke(solido);
        g2.setFont(TemaCloudForge.sans(10f));
        g2.setColor(TemaCloudForge.MUTE);
        g2.drawString("onde estão", boxNomes.x + boxNomes.width + 6, yLinhaNomes - 4);

        if (nos.isEmpty()) {
            g2.setFont(TemaCloudForge.mono(12f));
            g2.setColor(TemaCloudForge.MUTE);
            g2.drawString("Sem instâncias", xNoInst + 8, h / 2 + 4);
            g2.dispose();
            return;
        }

        // Posiciona instâncias empilhadas à direita abaixo do serviço de nomes
        int qtd = nos.size();
        int espaco = 8;
        int topoUtil = 44;
        int baseUtil = h - 26;
        int hDisponivel = Math.max(40, baseUtil - topoUtil - (qtd - 1) * espaco);
        int hNo = Math.min(54, Math.max(38, hDisponivel / qtd));
        int totalH = qtd * hNo + (qtd - 1) * espaco;
        int yInicio = topoUtil + Math.max(0, (baseUtil - topoUtil - totalH) / 2);
        int cxCliente = boxCliente.x + boxCliente.width;
        int cyCliente = boxCliente.y + boxCliente.height / 2;

        // Linhas finas de 1px ligando cliente a cada instância
        for (int i = 0; i < qtd; i++) {
            NoInstancia no = nos.get(i);
            int yNo = yInicio + i * (hNo + 10);
            no.area.setBounds(xNoInst, yNo, wNoInst, hNo);

            int cyNo = yNo + hNo / 2;
            g2.setColor(TemaCloudForge.LINE);
            g2.setStroke(solido);
            g2.drawLine(cxCliente, cyCliente, xNoInst, cyNo);
        }

        // Desenha nós das instâncias + pulsos
        for (NoInstancia no : nos) {
            Rectangle r = no.area;
            boolean hovered = no.id.equals(hoverId);

            // Pulso suave ao chegar pacote
            long dtPulso = agora - no.ultimoPulsoMs;
            if (dtPulso >= 0 && dtPulso < DURACAO_PULSO_MS) {
                double tp = (double) dtPulso / DURACAO_PULSO_MS;
                int exp = (int) Math.round(tp * 7.0);
                float alpha = (float) ((1.0 - tp) * 0.55);
                Color corInst = TemaCloudForge.CORES_INSTANCIA[no.indice % TemaCloudForge.CORES_INSTANCIA.length];
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.setColor(corInst);
                g2.drawRoundRect(r.x - exp, r.y - exp, r.width + exp * 2, r.height + exp * 2, 8, 8);
                g2.setComposite(AlphaComposite.SrcOver);
            }

            g2.setColor(hovered ? TemaCloudForge.S2 : TemaCloudForge.S1);
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
            g2.setColor(hovered ? TemaCloudForge.ACCENT : TemaCloudForge.LINE);
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6);

            // Bolinha de saúde com crossfade de 200 ms
            Color corSaude = corSaudeAtual(no, agora);
            int yTexto = r.y + r.height / 2 - 2;
            g2.setColor(corSaude);
            g2.fillOval(r.x + 10, yTexto - 6, 7, 7);

            // ID da instância
            g2.setFont(TemaCloudForge.mono(11.5f));
            g2.setColor(TemaCloudForge.FG);
            g2.drawString(no.id, r.x + 23, yTexto + 1);

            // Mini-barra de em-andamento abaixo do ID dentro do nó
            int xBarra = r.x + 23;
            int wBarra = Math.max(30, r.width - 35);
            int hBarra = 4;
            int yBarra = r.y + r.height - 11;
            g2.setColor(TemaCloudForge.S3);
            g2.fillRoundRect(xBarra, yBarra, wBarra, hBarra, 2, 2);
            if (no.limite > 0 && no.emAndamento > 0) {
                int fill = Math.max(3, (int) Math.round(wBarra * Math.min(1.0, (double) no.emAndamento / no.limite)));
                g2.setColor(TemaCloudForge.CORES_INSTANCIA[no.indice % TemaCloudForge.CORES_INSTANCIA.length]);
                g2.fillRoundRect(xBarra, yBarra, fill, hBarra, 2, 2);
            }
        }

        // Desenha pacotes viajando (ease-out 350 ms)
        for (Pacote p : pacotes) {
            NoInstancia alvo = buscarNo(p.idInstancia);
            if (alvo == null) continue;
            double raw = Math.min(1.0, (double) (agora - p.inicioMs) / DURACAO_PACOTE_MS);
            int tx = alvo.area.x;
            int ty = alvo.area.y + alvo.area.height / 2;

            if (!p.falha) {
                double ease = 1.0 - Math.pow(1.0 - raw, 3);
                int px = (int) Math.round(cxCliente + (tx - cxCliente) * ease);
                int py = (int) Math.round(cyCliente + (ty - cyCliente) * ease);
                g2.setColor(p.cor);
                g2.fillOval(px - 4, py - 4, 8, 8);
            } else {
                // Pacote tracejado vai até a metade e retorna com "×"
                double pos = raw <= 0.5 ? (raw * 2.0) * 0.52 : (1.0 - raw) * 2.0 * 0.52;
                int px = (int) Math.round(cxCliente + (tx - cxCliente) * pos);
                int py = (int) Math.round(cyCliente + (ty - cyCliente) * pos);
                float alpha = (float) Math.max(0.2, 1.0 - raw * 0.7);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.setColor(p.cor);
                g2.setStroke(tracejado);
                g2.drawOval(px - 5, py - 5, 10, 10);
                g2.setStroke(solido);
                g2.setFont(TemaCloudForge.monoBold(10f));
                g2.drawString("×", px - 3, py - 8);
                g2.setComposite(AlphaComposite.SrcOver);
            }
        }

        // Dica sutil no rodapé do painel
        g2.setFont(TemaCloudForge.sans(10f));
        g2.setColor(TemaCloudForge.MUTE);
        g2.drawString("clique num nó para ver histórico", 16, h - 10);

        g2.dispose();
    }

    private static void desenharCentralizado(Graphics2D g2, String texto, Rectangle r) {
        FontMetrics fm = g2.getFontMetrics();
        int x = r.x + (r.width - fm.stringWidth(texto)) / 2;
        int y = r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(texto, x, y);
    }
}
