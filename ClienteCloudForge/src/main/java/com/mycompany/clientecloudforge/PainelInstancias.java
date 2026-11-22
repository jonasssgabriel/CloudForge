package com.mycompany.clientecloudforge;

import com.mycompany.cloudforge.Estatisticas;
import com.mycompany.cloudforge.Instancia;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

/**
 * Tabela de instâncias registradas com saúde, barra de carga e ações.
 */
public class PainelInstancias extends JPanel {
    public static final class LinhaInstancia {
        public final String id;
        public final String endereco;
        public String saude;
        public int emAndamento;
        public int limiteSimultaneas;
        public long concluidas;
        public long rejeitadas;
        public double progressoAnimado;
        public double progressoAlvo;

        LinhaInstancia(String id, String endereco) {
            this.id = id;
            this.endereco = endereco;
            this.saude = "VERIFICANDO";
        }
    }

    private final ModeloInstancias modelo = new ModeloInstancias();
    private final JTable tabela = new JTable(modelo);
    private final JScrollPane rolagem;
    private final Timer timerAnimacao;

    public PainelInstancias() {
        super(new BorderLayout());
        setBackground(TemaCloudForge.S1);
        setBorder(new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6));

        configurarTabela();

        rolagem = new JScrollPane(tabela);
        rolagem.setBorder(BorderFactory.createEmptyBorder());
        rolagem.getViewport().setBackground(TemaCloudForge.S1);
        rolagem.setBackground(TemaCloudForge.S1);
        add(rolagem, BorderLayout.CENTER);

        // Animação curta (~150 ms) apenas para a barra de em-andamento.
        timerAnimacao = new Timer(16, e -> {
            boolean mudou = false;
            for (LinhaInstancia l : modelo.linhas) {
                double diff = l.progressoAlvo - l.progressoAnimado;
                if (Math.abs(diff) > 0.005) {
                    l.progressoAnimado += diff * 0.25;
                    mudou = true;
                } else if (l.progressoAnimado != l.progressoAlvo) {
                    l.progressoAnimado = l.progressoAlvo;
                    mudou = true;
                }
            }
            if (mudou) {
                tabela.repaint();
            } else {
                ((Timer) e.getSource()).stop();
            }
        });
    }

    private void configurarTabela() {
        tabela.setBackground(TemaCloudForge.S1);
        tabela.setForeground(TemaCloudForge.FG);
        tabela.setFont(TemaCloudForge.mono(13f));
        tabela.setRowHeight(42);
        tabela.setShowVerticalLines(false);
        tabela.setShowHorizontalLines(true);
        tabela.setGridColor(TemaCloudForge.S3);
        tabela.setIntercellSpacing(new Dimension(0, 1));
        tabela.setRowSelectionAllowed(false);
        tabela.setCellSelectionEnabled(false);
        tabela.setFocusable(false);
        tabela.setFillsViewportHeight(false);
        tabela.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JTableHeader cabecalho = tabela.getTableHeader();
        cabecalho.setReorderingAllowed(false);
        cabecalho.setResizingAllowed(true);
        cabecalho.setBackground(TemaCloudForge.S1);
        cabecalho.setForeground(TemaCloudForge.MUTE);
        cabecalho.setFont(TemaCloudForge.sansMedium(12f));
        cabecalho.setPreferredSize(new Dimension(0, 36));
        cabecalho.setDefaultRenderer(new CabecalhoRenderer());

        TableColumnModel cols = tabela.getColumnModel();
        cols.getColumn(0).setMinWidth(110);
        cols.getColumn(0).setPreferredWidth(115);
        cols.getColumn(1).setMinWidth(150);
        cols.getColumn(1).setPreferredWidth(155);
        cols.getColumn(2).setMinWidth(130);
        cols.getColumn(2).setPreferredWidth(132);
        cols.getColumn(3).setMinWidth(105);
        cols.getColumn(3).setPreferredWidth(115);
        cols.getColumn(4).setMinWidth(68);
        cols.getColumn(4).setPreferredWidth(72);
        cols.getColumn(5).setMinWidth(64);
        cols.getColumn(5).setPreferredWidth(68);

        cols.getColumn(0).setCellRenderer(new TextoMonoRenderer(SwingConstants.LEFT, true));
        cols.getColumn(1).setCellRenderer(new TextoMonoRenderer(SwingConstants.LEFT, false));
        cols.getColumn(2).setCellRenderer(new SaudeRenderer());
        cols.getColumn(3).setCellRenderer(new EmAndamentoRenderer());
        cols.getColumn(4).setCellRenderer(new TextoMonoRenderer(SwingConstants.RIGHT, false));
        cols.getColumn(5).setCellRenderer(new TextoMonoRenderer(SwingConstants.RIGHT, false));
    }

    public void atualizarDados(List<Instancia> instancias, Map<String, String> saude, List<Estatisticas> stats) {
        Map<String, LinhaInstancia> anteriores = new LinkedHashMap<>();
        for (LinhaInstancia l : modelo.linhas) {
            anteriores.put(l.id, l);
        }
        Map<String, Estatisticas> mapaStats = new LinkedHashMap<>();
        for (Estatisticas st : stats) {
            mapaStats.put(st.getInstancia(), st);
        }

        List<LinhaInstancia> novas = new ArrayList<>();
        for (Instancia inst : instancias) {
            LinhaInstancia prev = anteriores.get(inst.getId());
            LinhaInstancia linha = new LinhaInstancia(inst.getId(), inst.getHost() + ":" + inst.getPorta());
            if (prev != null) {
                linha.progressoAnimado = prev.progressoAnimado;
            }
            linha.saude = saude.getOrDefault(inst.getId(), prev != null ? prev.saude : "VERIFICANDO");
            Estatisticas st = mapaStats.get(inst.getId());
            if (st != null) {
                linha.emAndamento = st.getEmAndamento();
                linha.limiteSimultaneas = st.getLimiteSimultaneas();
                linha.concluidas = st.getConcluidas();
                linha.rejeitadas = st.getRejeitadas();
            } else if (prev != null) {
                linha.emAndamento = prev.emAndamento;
                linha.limiteSimultaneas = prev.limiteSimultaneas;
                linha.concluidas = prev.concluidas;
                linha.rejeitadas = prev.rejeitadas;
            }
            linha.progressoAlvo = linha.limiteSimultaneas > 0
                    ? Math.min(1.0, (double) linha.emAndamento / linha.limiteSimultaneas)
                    : 0.0;
            novas.add(linha);
        }

        modelo.definirLinhas(novas);
        ajustarAltura();
        if (!timerAnimacao.isRunning()) {
            timerAnimacao.start();
        }
    }

    private void ajustarAltura() {
        int linhasVisiveis = Math.max(1, Math.min(4, modelo.getRowCount()));
        int altura = 36 + linhasVisiveis * 42 + 2;
        setPreferredSize(new Dimension(0, altura));
        setMinimumSize(new Dimension(0, 36 + 42 + 2));
        revalidate();
    }

    private static final class ModeloInstancias extends AbstractTableModel {
        private static final String[] COLUNAS = {
            "Instância", "Endereço", "Saúde", "Em andamento", "Concluídas", "Rejeitadas"
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
                case 2 -> l.saude;
                case 3 -> l;
                case 4 -> l.concluidas;
                case 5 -> l.rejeitadas;
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
            lbl.setFont(TemaCloudForge.sansMedium(12f));
            lbl.setHorizontalAlignment((column >= 3 && column <= 5) ? SwingConstants.RIGHT : SwingConstants.LEFT);
            lbl.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, TemaCloudForge.LINE),
                    BorderFactory.createEmptyBorder(0, 10, 0, 10)));
            return lbl;
        }
    }

    private static final class TextoMonoRenderer extends DefaultTableCellRenderer {
        private final int alinhamento;
        private final boolean destaqueId;

        TextoMonoRenderer(int alinhamento, boolean destaqueId) {
            this.alinhamento = alinhamento;
            this.destaqueId = destaqueId;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            LinhaInstancia l = ((ModeloInstancias) table.getModel()).linhas.get(row);
            boolean off = !"SERVING".equals(l.saude);
            lbl.setBackground(TemaCloudForge.S1);
            lbl.setForeground(destaqueId && off ? TemaCloudForge.MUTE : TemaCloudForge.FG);
            lbl.setFont(TemaCloudForge.mono(13f));
            lbl.setHorizontalAlignment(alinhamento);
            lbl.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            return lbl;
        }
    }

    private static final class SaudeRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private String status = "";
        private Color cor = TemaCloudForge.MUTE;

        SaudeRenderer() {
            setOpaque(true);
            setBackground(TemaCloudForge.S1);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            status = value == null ? "" : value.toString();
            cor = switch (status) {
                case "SERVING" -> TemaCloudForge.GOOD;
                case "NOT_SERVING" -> TemaCloudForge.WARN;
                case "VERIFICANDO" -> TemaCloudForge.MUTE;
                default -> TemaCloudForge.BAD;
            };
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int yCentro = getHeight() / 2;
            g2.setColor(cor);
            g2.fillOval(14, yCentro - 3, 7, 7);
            g2.setFont(TemaCloudForge.mono(13f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(status, 28, yCentro + fm.getAscent() / 2 - 1);
            g2.dispose();
        }
    }

    private static final class EmAndamentoRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private String texto = "0/0";
        private double progresso = 0.0;

        EmAndamentoRenderer() {
            setOpaque(true);
            setBackground(TemaCloudForge.S1);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            if (value instanceof LinhaInstancia l) {
                texto = l.emAndamento + "/" + l.limiteSimultaneas;
                progresso = l.progressoAnimado;
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int larguraBarra = 56;
            int alturaBarra = 4;
            int xBarra = getWidth() - 14 - larguraBarra;
            int yBarra = (getHeight() - alturaBarra) / 2;

            g2.setFont(TemaCloudForge.mono(13f));
            FontMetrics fm = g2.getFontMetrics();
            int xTexto = xBarra - 10 - fm.stringWidth(texto);
            int yTexto = getHeight() / 2 + fm.getAscent() / 2 - 1;
            g2.setColor(TemaCloudForge.FG);
            g2.drawString(texto, xTexto, yTexto);

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
}
