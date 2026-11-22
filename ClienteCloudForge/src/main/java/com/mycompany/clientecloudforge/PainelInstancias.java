package com.mycompany.clientecloudforge;

import com.mycompany.cloudforge.Estatisticas;
import com.mycompany.cloudforge.Instancia;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

/**
 * Tabela de instâncias com sparkline deslizante, entrada/saída de linhas, count-up e destaque.
 */
public class PainelInstancias extends JPanel implements Animador.Animavel {
    private static final int ALTURA_CABECALHO_NORMAL = 34;
    private static final int ALTURA_LINHA_NORMAL = 40;
    private static final int ALTURA_CABECALHO_COMPACTO = 30;
    private static final int ALTURA_LINHA_COMPACTO = 36;
    private static final int MAX_LINHAS_SEM_ROLAGEM = 4;
    private static final int LARGURA_MIN_TABELA = 720;
    private static final int LARGURA_PREF_TABELA = 768;
    private static final int[] MIN_LARG_BASE = {92, 138, 114, 88, 102, 82, 46, 58};
    private static final int[] PREF_LARG_BASE = {98, 146, 120, 94, 110, 88, 50, 62};

    private boolean modoCompacto = false;
    private int alturaCabecalho = ALTURA_CABECALHO_NORMAL;
    private int alturaLinha = ALTURA_LINHA_NORMAL;

    private static final long DURACAO_CONTAGEM_MS = 250;
    private static final long DURACAO_DESTAQUE_MS = 600;
    private static final long DURACAO_CROSSFADE_MS = 250;
    private static final long DURACAO_ENTRADA_LINHA_MS = 240;
    private static final long DURACAO_SAIDA_LINHA_MS = 200;
    private static final long DURACAO_SPARK_SLIDE_MS = 200;
    private static final int MAX_AMOSTRAS_LATENCIA = 20;
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String[] DICAS_COLUNAS = {
        "Identificador da instância",
        "Endereço host:porta",
        "Estado retornado pelo Health Check",
        "Tarefas em andamento / limite simultâneo",
        "Histórico recente de latência (ms)",
        "Horário da última resposta concluída",
        "Total de tarefas concluídas",
        "Total de tarefas rejeitadas (capacidade)"
    };

    public static final class LinhaInstancia {
        public final String id;
        public final int indice;
        public final String endereco;
        public String saude = "VERIFICANDO";
        public Color corSaudeAnterior = TemaCloudForge.MUTE;
        public Color corSaudeAlvo = TemaCloudForge.MUTE;
        public long inicioCrossfadeMs;

        public int emAndamento;
        public int limiteSimultaneas;
        public double progressoAnimado;
        public double progressoAlvo;

        public long concluidasInicio;
        public long concluidasAlvo;
        public long concluidasExibidas;
        public long rejeitadasInicio;
        public long rejeitadasAlvo;
        public long rejeitadasExibidas;
        public long inicioContagemMs;

        public long ultimoAtendimentoMs;
        public String ultimaRespostaTexto = "";
        public final List<Long> latenciasMs = new ArrayList<>();
        public long inicioSparkSlideMs;

        public long inicioEntradaMs;
        public boolean saindo;
        public long inicioSaidaMs;
        public float alphaLinha = 1.0f;

        LinhaInstancia(String id, int indice, String endereco) {
            this.id = id;
            this.indice = indice;
            this.endereco = endereco;
        }
    }

    private final ModeloInstancias modelo = new ModeloInstancias();
    private final JTable tabela = new JTable(modelo);
    private final JScrollPane rolagem;
    private final Map<String, List<Long>> historicoLatencias = new LinkedHashMap<>();
    private final Map<String, String> ultimasHoras = new LinkedHashMap<>();
    private int hoverRow = -1;
    private boolean carregando = false;
    private long inicioCarregamentoMs = 0;

    public PainelInstancias() {
        super(new BorderLayout());
        setBackground(TemaCloudForge.S1);
        setBorder(new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6));

        configurarTabela();

        rolagem = new JScrollPane(tabela) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                desenharEstadoVazioOuCarregando(g, getWidth(), getHeight());
            }
        };
        rolagem.setBorder(BorderFactory.createEmptyBorder());
        rolagem.getViewport().setBackground(TemaCloudForge.S1);
        rolagem.setBackground(TemaCloudForge.S1);
        rolagem.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rolagem.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        add(rolagem, BorderLayout.CENTER);

        ajustarAltura();
        Animador.registrar(this);
    }

    public void setCarregando(boolean carregando) {
        this.carregando = carregando;
        if (carregando) {
            this.inicioCarregamentoMs = System.currentTimeMillis();
            Animador.acordar();
        }
        repaint();
    }

    /** Alterna altura compacta das linhas em janelas menores (ex.: 1000x700). */
    public void setCompacto(boolean compacto) {
        this.modoCompacto = compacto;
        reaplicarEscala();
    }

    /** Reaplica escala atual do tema às fontes, alturas de linha e larguras das colunas. */
    public void reaplicarEscala() {
        int novoCab = TemaCloudForge.escala(modoCompacto ? ALTURA_CABECALHO_COMPACTO : ALTURA_CABECALHO_NORMAL);
        int novaLinha = TemaCloudForge.escala(modoCompacto ? ALTURA_LINHA_COMPACTO : ALTURA_LINHA_NORMAL);
        alturaCabecalho = novoCab;
        alturaLinha = novaLinha;
        tabela.setFont(TemaCloudForge.mono(12f));
        tabela.setRowHeight(alturaLinha);
        JTableHeader cab = tabela.getTableHeader();
        cab.setFont(TemaCloudForge.sansMedium(11.5f));
        cab.setPreferredSize(new Dimension(0, alturaCabecalho));

        TableColumnModel cols = tabela.getColumnModel();
        for (int i = 0; i < MIN_LARG_BASE.length; i++) {
            cols.getColumn(i).setMinWidth(TemaCloudForge.escala(MIN_LARG_BASE[i]));
            cols.getColumn(i).setPreferredWidth(TemaCloudForge.escala(PREF_LARG_BASE[i]));
        }
        for (int i = 0; i < modelo.linhas.size(); i++) {
            LinhaInstancia l = modelo.linhas.get(i);
            if (!l.saindo && l.inicioEntradaMs == 0L) {
                tabela.setRowHeight(i, alturaLinha);
            }
        }
        ajustarAltura();
        cab.revalidate();
        cab.repaint();
        tabela.revalidate();
        tabela.repaint();
    }

    /** Registra uma resposta concluída com sucesso para sparkline deslizante, destaque e horário. */
    public void registrarResposta(String idInstancia, long duracaoMs) {
        if (idInstancia == null || idInstancia.isBlank()) return;
        List<Long> lista = historicoLatencias.computeIfAbsent(idInstancia, k -> new ArrayList<>());
        lista.add(Math.max(0L, duracaoMs));
        if (lista.size() > MAX_AMOSTRAS_LATENCIA) {
            lista.remove(0);
        }
        String hora = LocalTime.now().format(FMT_HORA);
        ultimasHoras.put(idInstancia, hora);

        long agora = System.currentTimeMillis();
        for (LinhaInstancia l : modelo.linhas) {
            if (l.id.equals(idInstancia)) {
                l.ultimoAtendimentoMs = agora;
                l.ultimaRespostaTexto = hora;
                l.inicioSparkSlideMs = agora;
                l.latenciasMs.clear();
                l.latenciasMs.addAll(lista);
                break;
            }
        }
        Animador.acordar();
        tabela.repaint();
    }

    @Override
    public boolean atualizarAnimacao(long agora) {
        boolean ativo = carregando;
        boolean precisaRemover = false;

        for (int i = 0; i < modelo.linhas.size(); i++) {
            LinhaInstancia l = modelo.linhas.get(i);

            // Entrada / saída de linha (altura crescendo + fade)
            if (l.saindo) {
                double t = Animador.isReduzirAnimacoes() ? 1.0
                        : Math.min(1.0, (double) (agora - l.inicioSaidaMs) / DURACAO_SAIDA_LINHA_MS);
                double ease = Animador.easeOut(t);
                l.alphaLinha = (float) Math.max(0.0, 1.0 - ease);
                int h = Math.max(1, (int) Math.round(alturaLinha * (1.0 - ease)));
                tabela.setRowHeight(i, h);
                if (t >= 1.0) {
                    precisaRemover = true;
                } else {
                    ativo = true;
                }
            } else if (l.inicioEntradaMs > 0) {
                double t = Animador.isReduzirAnimacoes() ? 1.0
                        : Math.min(1.0, (double) (agora - l.inicioEntradaMs) / DURACAO_ENTRADA_LINHA_MS);
                double ease = Animador.easeOut(t);
                l.alphaLinha = (float) ease;
                int h = Math.max(8, (int) Math.round(alturaLinha * ease));
                tabela.setRowHeight(i, h);
                if (t >= 1.0) {
                    l.inicioEntradaMs = 0L;
                    l.alphaLinha = 1.0f;
                    tabela.setRowHeight(i, alturaLinha);
                } else {
                    ativo = true;
                }
            }

            double diff = l.progressoAlvo - l.progressoAnimado;
            if (!Animador.isReduzirAnimacoes() && Math.abs(diff) > 0.005) {
                l.progressoAnimado += diff * 0.25;
                ativo = true;
            } else if (l.progressoAnimado != l.progressoAlvo) {
                l.progressoAnimado = l.progressoAlvo;
                ativo = true;
            }

            long dtCont = agora - l.inicioContagemMs;
            if (!Animador.isReduzirAnimacoes() && dtCont >= 0 && dtCont < DURACAO_CONTAGEM_MS) {
                double t = Animador.easeOut((double) dtCont / DURACAO_CONTAGEM_MS);
                l.concluidasExibidas = Math.round(l.concluidasInicio + (l.concluidasAlvo - l.concluidasInicio) * t);
                l.rejeitadasExibidas = Math.round(l.rejeitadasInicio + (l.rejeitadasAlvo - l.rejeitadasInicio) * t);
                ativo = true;
            } else if (l.concluidasExibidas != l.concluidasAlvo || l.rejeitadasExibidas != l.rejeitadasAlvo) {
                l.concluidasExibidas = l.concluidasAlvo;
                l.rejeitadasExibidas = l.rejeitadasAlvo;
                ativo = true;
            }

            if (agora - l.ultimoAtendimentoMs < DURACAO_DESTAQUE_MS) ativo = true;
            if (agora - l.inicioCrossfadeMs < DURACAO_CROSSFADE_MS) ativo = true;
            if (agora - l.inicioSparkSlideMs < DURACAO_SPARK_SLIDE_MS) ativo = true;
        }

        if (precisaRemover) {
            List<LinhaInstancia> restantes = new ArrayList<>(modelo.linhas);
            Iterator<LinhaInstancia> it = restantes.iterator();
            while (it.hasNext()) {
                LinhaInstancia l = it.next();
                if (l.saindo && agora - l.inicioSaidaMs >= DURACAO_SAIDA_LINHA_MS) {
                    it.remove();
                }
            }
            modelo.definirLinhas(restantes);
            ajustarAltura();
        }



        if (ativo) {
            tabela.repaint();
            repaint();
        }
        return ativo;
    }

    private void configurarTabela() {
        tabela.setBackground(TemaCloudForge.S1);
        tabela.setForeground(TemaCloudForge.FG);
        tabela.setFont(TemaCloudForge.mono(12f));
        tabela.setRowHeight(alturaLinha);
        tabela.setShowVerticalLines(false);
        tabela.setShowHorizontalLines(true);
        tabela.setGridColor(TemaCloudForge.DIVIDER);
        tabela.setIntercellSpacing(new Dimension(0, 1));
        tabela.setRowSelectionAllowed(true);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.setCellSelectionEnabled(false);
        tabela.setFocusable(true);
        tabela.setFillsViewportHeight(true);
        tabela.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Cabeçalho com tooltips descritivos para nomes curtos (OK, Rejeit., Últ. resp.)
        JTableHeader cabecalho = new JTableHeader(tabela.getColumnModel()) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int col = columnAtPoint(e.getPoint());
                return (col >= 0 && col < DICAS_COLUNAS.length) ? DICAS_COLUNAS[col] : null;
            }
        };
        tabela.setTableHeader(cabecalho);
        cabecalho.setReorderingAllowed(false);
        cabecalho.setResizingAllowed(true);
        cabecalho.setBackground(TemaCloudForge.S1);
        cabecalho.setForeground(TemaCloudForge.MUTE);
        cabecalho.setFont(TemaCloudForge.sansMedium(11.5f));
        cabecalho.setPreferredSize(new Dimension(0, alturaCabecalho));
        cabecalho.setDefaultRenderer(new CabecalhoRenderer());

        // Larguras calculadas pelas métricas reais da fonte para nunca truncar srv-50051 / localhost:50051
        int[] minLarg = {92, 138, 114, 88, 102, 82, 46, 58};
        int[] prefLarg = {98, 146, 120, 94, 110, 88, 50, 62};
        TableColumnModel cols = tabela.getColumnModel();
        for (int i = 0; i < minLarg.length; i++) {
            cols.getColumn(i).setMinWidth(minLarg[i]);
            cols.getColumn(i).setPreferredWidth(prefLarg[i]);
        }

        cols.getColumn(0).setCellRenderer(new TextoMonoRenderer(SwingConstants.LEFT, true, true));
        cols.getColumn(1).setCellRenderer(new TextoMonoRenderer(SwingConstants.LEFT, false, false));
        cols.getColumn(2).setCellRenderer(new SaudeRenderer());
        cols.getColumn(3).setCellRenderer(new EmAndamentoRenderer());
        cols.getColumn(4).setCellRenderer(new SparklineRenderer());
        cols.getColumn(5).setCellRenderer(new UltimaRespostaRenderer());
        cols.getColumn(6).setCellRenderer(new TextoMonoRenderer(SwingConstants.RIGHT, false, false));
        cols.getColumn(7).setCellRenderer(new TextoMonoRenderer(SwingConstants.RIGHT, false, false));

        MouseAdapter hoverAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int r = tabela.rowAtPoint(e.getPoint());
                if (r != hoverRow) {
                    hoverRow = r;
                    tabela.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverRow != -1) {
                    hoverRow = -1;
                    tabela.repaint();
                }
            }
        };
        tabela.addMouseMotionListener(hoverAdapter);
        tabela.addMouseListener(hoverAdapter);
    }

    public void atualizarDados(List<Instancia> instancias, Map<String, String> saude, List<Estatisticas> stats) {
        Map<String, LinhaInstancia> anteriores = new LinkedHashMap<>();
        for (LinhaInstancia l : modelo.linhas) {
            if (!l.saindo) anteriores.put(l.id, l);
        }
        Map<String, Estatisticas> mapaStats = new LinkedHashMap<>();
        for (Estatisticas st : stats) {
            mapaStats.put(st.getInstancia(), st);
        }

        long agora = System.currentTimeMillis();
        int linhaSelecionada = tabela.getSelectedRow();
        String idSelecionado = (linhaSelecionada >= 0 && linhaSelecionada < modelo.linhas.size())
                ? modelo.linhas.get(linhaSelecionada).id : null;

        boolean tinhaLinhas = !anteriores.isEmpty();
        List<LinhaInstancia> novas = new ArrayList<>();
        for (int i = 0; i < instancias.size(); i++) {
            Instancia inst = instancias.get(i);
            LinhaInstancia prev = anteriores.remove(inst.getId());
            LinhaInstancia linha = new LinhaInstancia(inst.getId(), i, inst.getHost() + ":" + inst.getPorta());

            if (prev != null) {
                linha.progressoAnimado = prev.progressoAnimado;
                linha.corSaudeAnterior = corSaudeAtual(prev, agora);
                linha.corSaudeAlvo = prev.corSaudeAlvo;
                linha.inicioCrossfadeMs = prev.inicioCrossfadeMs;
                linha.concluidasExibidas = prev.concluidasExibidas;
                linha.rejeitadasExibidas = prev.rejeitadasExibidas;
                linha.concluidasAlvo = prev.concluidasAlvo;
                linha.rejeitadasAlvo = prev.rejeitadasAlvo;
                linha.ultimoAtendimentoMs = prev.ultimoAtendimentoMs;
                linha.inicioSparkSlideMs = prev.inicioSparkSlideMs;
                linha.inicioEntradaMs = prev.inicioEntradaMs;
                linha.alphaLinha = prev.alphaLinha;
            } else if (tinhaLinhas && !Animador.isReduzirAnimacoes()) {
                linha.inicioEntradaMs = agora;
                linha.alphaLinha = 0.05f;
            }

            List<Long> lats = historicoLatencias.get(inst.getId());
            if (lats != null) {
                linha.latenciasMs.addAll(lats);
            }
            linha.ultimaRespostaTexto = ultimasHoras.getOrDefault(inst.getId(), "");

            linha.saude = saude.getOrDefault(inst.getId(), prev != null ? prev.saude : "VERIFICANDO");
            Color novaCor = corParaSaude(linha.saude);
            if (!novaCor.equals(linha.corSaudeAlvo)) {
                linha.corSaudeAnterior = corSaudeAtual(linha, agora);
                linha.corSaudeAlvo = novaCor;
                linha.inicioCrossfadeMs = agora;
            }

            Estatisticas st = mapaStats.get(inst.getId());
            long novaConcluidas = prev != null ? prev.concluidasAlvo : 0;
            long novaRejeitadas = prev != null ? prev.rejeitadasAlvo : 0;
            if (st != null) {
                linha.emAndamento = st.getEmAndamento();
                linha.limiteSimultaneas = st.getLimiteSimultaneas();
                novaConcluidas = st.getConcluidas();
                novaRejeitadas = st.getRejeitadas();
            } else if (prev != null) {
                linha.emAndamento = prev.emAndamento;
                linha.limiteSimultaneas = prev.limiteSimultaneas;
            }

            if (prev == null) {
                linha.concluidasInicio = novaConcluidas;
                linha.concluidasAlvo = novaConcluidas;
                linha.concluidasExibidas = novaConcluidas;
                linha.rejeitadasInicio = novaRejeitadas;
                linha.rejeitadasAlvo = novaRejeitadas;
                linha.rejeitadasExibidas = novaRejeitadas;
            } else if (novaConcluidas != prev.concluidasAlvo || novaRejeitadas != prev.rejeitadasAlvo) {
                linha.concluidasInicio = prev.concluidasExibidas;
                linha.concluidasAlvo = novaConcluidas;
                linha.rejeitadasInicio = prev.rejeitadasExibidas;
                linha.rejeitadasAlvo = novaRejeitadas;
                linha.inicioContagemMs = agora;
                if (novaConcluidas > prev.concluidasAlvo) {
                    linha.ultimoAtendimentoMs = agora;
                }
            }

            linha.progressoAlvo = linha.limiteSimultaneas > 0
                    ? Math.min(1.0, (double) linha.emAndamento / linha.limiteSimultaneas)
                    : 0.0;
            novas.add(linha);
        }

        // Linhas removidas encolhem e somem suavemente
        if (!Animador.isReduzirAnimacoes()) {
            for (LinhaInstancia removida : anteriores.values()) {
                removida.saindo = true;
                removida.inicioSaidaMs = agora;
                novas.add(removida);
            }
        }

        modelo.definirLinhas(novas);
        for (int i = 0; i < novas.size(); i++) {
            LinhaInstancia l = novas.get(i);
            if (!l.saindo && l.inicioEntradaMs == 0L) {
                tabela.setRowHeight(i, alturaLinha);
            }
        }
        if (idSelecionado != null) {
            for (int i = 0; i < novas.size(); i++) {
                if (novas.get(i).id.equals(idSelecionado) && !novas.get(i).saindo) {
                    tabela.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
        ajustarAltura();
        Animador.acordar();
    }

    /** Ajusta a altura exata da tabela às linhas presentes e só exibe barra vertical acima de 4 instâncias. */
    private void ajustarAltura() {
        int qtd = modelo.getRowCount();
        int linhasVisiveis = Math.max(2, Math.min(MAX_LINHAS_SEM_ROLAGEM, qtd));
        int alturaCorpo = linhasVisiveis * alturaLinha;
        int alturaTotal = alturaCabecalho + alturaCorpo + 2;

        rolagem.setVerticalScrollBarPolicy(qtd > MAX_LINHAS_SEM_ROLAGEM
                ? ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                : ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        tabela.setPreferredScrollableViewportSize(new Dimension(TemaCloudForge.escala(LARGURA_PREF_TABELA), alturaCorpo));
        setPreferredSize(new Dimension(TemaCloudForge.escala(LARGURA_PREF_TABELA), alturaTotal));
        setMinimumSize(new Dimension(TemaCloudForge.escala(LARGURA_MIN_TABELA), alturaTotal));
        revalidate();
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (carregando && !Animador.isReduzirAnimacoes()) {
            Graphics2D g2 = (Graphics2D) g.create();
            long decorrido = System.currentTimeMillis() - inicioCarregamentoMs;
            double ciclo = (decorrido % 1100) / 1100.0;
            int w = getWidth();
            int barraW = Math.max(48, w / 4);
            int x = (int) Math.round(-barraW + (w + barraW) * Animador.easeOut(ciclo));
            g2.setColor(TemaCloudForge.ACCENT);
            g2.fillRect(Math.max(1, x), 1, Math.min(barraW, w - 2 - Math.max(1, x)), 2);
            g2.dispose();
        }
    }

    private void desenharEstadoVazioOuCarregando(Graphics g, int w, int h) {
        if (modelo.getRowCount() > 0) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        String msg = carregando
                ? "Consultando serviço de nomes..."
                : "Nenhuma instância registrada. Suba um servidor e clique em Atualizar.";
        g2.setFont(TemaCloudForge.sans(12.5f));
        g2.setColor(TemaCloudForge.MUTE);
        FontMetrics fm = g2.getFontMetrics();
        int x = Math.max(16, (w - fm.stringWidth(msg)) / 2);
        int y = alturaCabecalho + Math.max(24, (h - alturaCabecalho) / 2 + fm.getAscent() / 2);
        g2.drawString(msg, x, y);
        g2.dispose();
    }

    private Color fundoLinha(int row, LinhaInstancia l) {
        Color base = (row == hoverRow) ? TemaCloudForge.S2 : TemaCloudForge.S1;
        long dt = System.currentTimeMillis() - l.ultimoAtendimentoMs;
        if (!Animador.isReduzirAnimacoes() && l.ultimoAtendimentoMs > 0 && dt >= 0 && dt < DURACAO_DESTAQUE_MS) {
            double fator = (1.0 - Animador.easeOut((double) dt / DURACAO_DESTAQUE_MS)) * 0.14;
            Color corInst = TemaCloudForge.corInstancia(l.indice);
            return TemaCloudForge.interpolarCor(base, corInst, fator);
        }
        return base;
    }

    private static Color corParaSaude(String st) {
        return switch (st) {
            case "SERVING" -> TemaCloudForge.GOOD;
            case "NOT_SERVING" -> TemaCloudForge.WARN;
            case "VERIFICANDO" -> TemaCloudForge.MUTE;
            default -> TemaCloudForge.BAD;
        };
    }

    private static Color corSaudeAtual(LinhaInstancia l, long agora) {
        if (Animador.isReduzirAnimacoes()) return l.corSaudeAlvo;
        double t = Math.min(1.0, (double) (agora - l.inicioCrossfadeMs) / DURACAO_CROSSFADE_MS);
        return TemaCloudForge.interpolarCor(l.corSaudeAnterior, l.corSaudeAlvo, Animador.easeOut(t));
    }

    private static final class ModeloInstancias extends AbstractTableModel {
        private static final String[] COLUNAS = {
            "Instância", "Endereço", "Saúde", "Andamento", "Latência", "Últ. resp.", "OK", "Rejeit."
        };
        private List<LinhaInstancia> linhas = List.of();

        void definirLinhas(List<LinhaInstancia> novas) {
            this.linhas = novas;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return linhas.size();
        }

        @Override
        public int getColumnCount() {
            return COLUNAS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUNAS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LinhaInstancia l = linhas.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> l.id;
                case 1 -> l.endereco;
                case 2 -> l;
                case 3 -> l;
                case 4 -> l;
                case 5 -> l;
                case 6 -> l.concluidasExibidas;
                case 7 -> l.rejeitadasExibidas;
                default -> l;
            };
        }
    }

    private static final class CabecalhoRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            lbl.setBackground(TemaCloudForge.S1);
            lbl.setForeground(TemaCloudForge.MUTE);
            lbl.setFont(TemaCloudForge.sansMedium(TemaCloudForge.isModoApresentacao() ? 12.5f : 11.5f));
            lbl.setHorizontalAlignment(column >= 6 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            lbl.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, TemaCloudForge.DIVIDER),
                    BorderFactory.createEmptyBorder(0, 8, 0, 8)));
            return lbl;
        }
    }

    private final class TextoMonoRenderer extends DefaultTableCellRenderer {
        private final int alinhamento;
        private final boolean destaqueId;
        private final boolean desenharBarraSelecao;

        TextoMonoRenderer(int alinhamento, boolean destaqueId, boolean desenharBarraSelecao) {
            this.alinhamento = alinhamento;
            this.destaqueId = destaqueId;
            this.desenharBarraSelecao = desenharBarraSelecao;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            LinhaInstancia l = modelo.linhas.get(row);
            boolean off = !"SERVING".equals(l.saude);
            lbl.setBackground(fundoLinha(row, l));
            Color fg = destaqueId && off ? TemaCloudForge.MUTE : TemaCloudForge.FG;
            lbl.setForeground(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), Math.round(l.alphaLinha * 255)));
            boolean destaqueProjecao = TemaCloudForge.isModoApresentacao() && (destaqueId || column >= 6);
            lbl.setFont(destaqueProjecao
                    ? TemaCloudForge.monoBold(15.2f)
                    : TemaCloudForge.mono(TemaCloudForge.isModoApresentacao() ? 12.5f : 12f));
            lbl.setHorizontalAlignment(alinhamento);
            if (desenharBarraSelecao && isSelected) {
                lbl.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 2, 0, 0, TemaCloudForge.FG),
                        BorderFactory.createEmptyBorder(0, 6, 0, 8)));
            } else {
                lbl.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            }
            return lbl;
        }
    }

    private final class SaudeRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private String status = "";
        private Color cor = TemaCloudForge.MUTE;
        private float alphaLinha = 1.0f;

        SaudeRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            if (value instanceof LinhaInstancia l) {
                status = l.saude;
                cor = corSaudeAtual(l, System.currentTimeMillis());
                alphaLinha = l.alphaLinha;
                setBackground(fundoLinha(row, l));
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int yCentro = getHeight() / 2;
            int dotDiam = TemaCloudForge.escala(7);
            int dotX = TemaCloudForge.escala(8);
            int dotY = yCentro - dotDiam / 2;

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaLinha));
            g2.setColor(cor);
            g2.fillOval(dotX, dotY, dotDiam, dotDiam);
            g2.setFont(TemaCloudForge.isModoApresentacao()
                    ? TemaCloudForge.monoBold(12.5f)
                    : TemaCloudForge.mono(11.5f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(status, dotX + dotDiam + TemaCloudForge.escala(6), yCentro + fm.getAscent() / 2 - 1);
            g2.dispose();
        }
    }

    private final class EmAndamentoRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private String texto = "0/0";
        private double progresso = 0.0;
        private float alphaLinha = 1.0f;

        EmAndamentoRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            if (value instanceof LinhaInstancia l) {
                texto = l.emAndamento + "/" + l.limiteSimultaneas;
                progresso = l.progressoAnimado;
                alphaLinha = l.alphaLinha;
                setBackground(fundoLinha(row, l));
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaLinha));

            g2.setFont(TemaCloudForge.mono(TemaCloudForge.isModoApresentacao() ? 12.5f : 12f));
            FontMetrics fm = g2.getFontMetrics();
            int xTexto = 8;
            int yTexto = getHeight() / 2 + fm.getAscent() / 2 - 1;
            g2.setColor(TemaCloudForge.FG);
            g2.drawString(texto, xTexto, yTexto);

            int xBarra = xTexto + fm.stringWidth(texto) + 8;
            int larguraBarra = Math.max(20, getWidth() - xBarra - 8);
            int alturaBarra = 4;
            int yBarra = (getHeight() - alturaBarra) / 2;

            g2.setColor(TemaCloudForge.S3);
            g2.fillRoundRect(xBarra, yBarra, larguraBarra, alturaBarra, 2, 2);

            int preenchido = (int) Math.round(larguraBarra * Math.max(0.0, Math.min(1.0, progresso)));
            if (preenchido > 0) {
                g2.setColor(TemaCloudForge.FG);
                g2.fillRoundRect(xBarra, yBarra, preenchido, alturaBarra, 2, 2);
            }
            g2.dispose();
        }
    }

    /** Desenha o sparkline das últimas ~20 latências deslizando 1 posição a cada resposta. */
    private final class SparklineRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private List<Long> amostras = List.of();
        private Color corInstancia = TemaCloudForge.ACCENT;
        private long inicioSlideMs = 0L;
        private float alphaLinha = 1.0f;

        SparklineRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            if (value instanceof LinhaInstancia l) {
                amostras = l.latenciasMs;
                corInstancia = TemaCloudForge.corInstancia(l.indice);
                inicioSlideMs = l.inicioSparkSlideMs;
                alphaLinha = l.alphaLinha;
                setBackground(fundoLinha(row, l));
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaLinha));

            if (amostras == null || amostras.isEmpty()) {
                g2.setFont(TemaCloudForge.mono(11.5f));
                g2.setColor(TemaCloudForge.MUTE);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("sem dados", 8, getHeight() / 2 + fm.getAscent() / 2 - 1);
                g2.dispose();
                return;
            }

            long ultimo = amostras.get(amostras.size() - 1);
            String txtValor = ultimo + " ms";
            g2.setFont(TemaCloudForge.mono(11.5f));
            FontMetrics fm = g2.getFontMetrics();
            int wValor = fm.stringWidth(txtValor);
            int xValor = getWidth() - 8 - wValor;
            int yValor = getHeight() / 2 + fm.getAscent() / 2 - 1;
            g2.setColor(TemaCloudForge.FG);
            g2.drawString(txtValor, xValor, yValor);

            int xGraf = 8;
            int wGraf = Math.max(20, xValor - xGraf - 8);
            int hGraf = TemaCloudForge.escala(18);
            int yGraf = (getHeight() - hGraf) / 2;

            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            for (long v : amostras) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
            double faixa = Math.max(1.0, (double) (max - min));

            int n = amostras.size();
            double passo = n > 1 ? (double) wGraf / (n - 1) : 0.0;
            double slide = 0.0;
            if (!Animador.isReduzirAnimacoes() && n > 1) {
                long dtSlide = System.currentTimeMillis() - inicioSlideMs;
                if (dtSlide >= 0 && dtSlide < DURACAO_SPARK_SLIDE_MS) {
                    slide = (1.0 - Animador.easeOut((double) dtSlide / DURACAO_SPARK_SLIDE_MS)) * passo;
                }
            }

            Path2D.Double caminho = new Path2D.Double();
            double ultX = xGraf;
            double ultY = yGraf + hGraf / 2.0;

            g2.setClip(xGraf - 2, yGraf - 3, wGraf + 6, hGraf + 6);
            for (int i = 0; i < n; i++) {
                double px = (n == 1 ? xGraf + wGraf : xGraf + passo * i) + slide;
                double norm = (max == min) ? 0.5 : (amostras.get(i) - min) / faixa;
                double py = yGraf + hGraf - norm * hGraf;
                if (i == 0) {
                    caminho.moveTo(px, py);
                } else {
                    caminho.lineTo(px, py);
                }
                ultX = px;
                ultY = py;
            }

            g2.setColor(corInstancia);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(caminho);

            g2.fillOval((int) Math.round(ultX) - 2, (int) Math.round(ultY) - 2, 5, 5);
            g2.dispose();
        }
    }

    private final class UltimaRespostaRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            LinhaInstancia l = modelo.linhas.get(row);
            lbl.setBackground(fundoLinha(row, l));
            lbl.setFont(TemaCloudForge.mono(TemaCloudForge.isModoApresentacao() ? 12.5f : 11.5f));
            lbl.setHorizontalAlignment(SwingConstants.LEFT);
            lbl.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            if (l.ultimaRespostaTexto == null || l.ultimaRespostaTexto.isEmpty()) {
                lbl.setForeground(new Color(TemaCloudForge.MUTE.getRed(), TemaCloudForge.MUTE.getGreen(),
                        TemaCloudForge.MUTE.getBlue(), Math.round(l.alphaLinha * 255)));
                lbl.setText("sem dados");
            } else {
                lbl.setForeground(new Color(TemaCloudForge.FG.getRed(), TemaCloudForge.FG.getGreen(),
                        TemaCloudForge.FG.getBlue(), Math.round(l.alphaLinha * 255)));
                lbl.setText(l.ultimaRespostaTexto);
            }
            return lbl;
        }
    }
}
