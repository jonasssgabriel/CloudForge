package com.mycompany.clientecloudforge;

import com.mycompany.cloudforge.Estatisticas;
import com.mycompany.cloudforge.Historico;
import com.mycompany.cloudforge.Instancia;
import com.mycompany.cloudforge.RegistroHistorico;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Janela principal do ClienteCloudForge.
 * Organizada em grade de 8 px com layout responsivo para 1000x700, 1100x760 e 1366x768.
 */
public class TelaCloudForge extends JFrame {
    private static final String[] OPCOES_TIPO = {
        "CALCULO · fatorial",
        "PROCESSAMENTO_DADOS",
        "HASH · SHA-256"
    };

    private final String enderecoNomes;
    private volatile ClienteCore core;
    private volatile List<Instancia> ultimasInstancias = List.of();
    private final Map<String, String> ultimasSaudes = new LinkedHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "cloudforge-grpc");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean atualizandoSaude = new AtomicBoolean(false);
    private final PainelEstadoLocal painelEstadoLocal = new PainelEstadoLocal();
    private final PainelPrazo painelPrazo = new PainelPrazo();
    private final JTextArea areaTrilha = new JTextArea();
    private final JScrollPane rolagemTrilha = new JScrollPane(areaTrilha);
    private final JTabbedPane abasEventos = new JTabbedPane();
    private final Map<Integer, StringBuilder> trilhas = new LinkedHashMap<>();
    private final Map<Integer, Integer> prazos = new LinkedHashMap<>();
    private final Map<String, Long> ultimosIds = new LinkedHashMap<>();
    private final Map<String, Estatisticas> ultimasStats = new LinkedHashMap<>();
    private boolean acompanharTrilha = true;
    private long copiarAte;
    private volatile boolean loteEmCurso;
    private long ultimoPacoteLote;
    private long ultimoHealthLote;

    private final JLabel lblMeta = new JLabel();
    private final JLabel lblTaxa = new JLabel("taxa: 0.0 req/s");
    private final PainelInstancias painelInstancias = new PainelInstancias();
    private final PainelTopologia painelTopologia;
    private final PainelRodizio painelRodizio = new PainelRodizio();
    private final PainelLog painelLog = new PainelLog();
    private final PainelToast painelToast = new PainelToast();
    private final PainelResumoConcorrencia painelResumoConc = new PainelResumoConcorrencia();

    private JPanel raiz;
    private JPanel centro;
    private JPanel painelTopo;
    private JPanel cabInstancias;
    private JPanel cabTopologia;
    private JPanel secInstancias;
    private JPanel secTopologia;
    private JPanel secRodizio;
    private JPanel painelColunas;
    private JPanel colunaDireita;
    private JPanel secTarefa;
    private JPanel secResposta;
    private JPanel painelCamposForm;
    private JPanel cardFormulario;
    private JPanel blocoTipo;
    private JPanel blocoEntrada;
    private JPanel gradeBotoes;
    private boolean modoEmpilhado = false;
    private boolean modoCompactoAltura = false;

    private final JComboBox<String> cbxTipo = new JComboBox<>(OPCOES_TIPO);
    private final JTextField txtEntrada = new JTextField("25");
    private final Border bordaEntradaPadrao;
    private final JLabel lblDicaEntrada = new JLabel("número de 0 a 5000");
    private final JTextField txtConcorrencia = new JTextField("50");
    private final BotaoAnimado btnEnviar = new BotaoAnimado("Enviar", true);
    private final BotaoAnimado btnRr6 = new BotaoAnimado("Alternar 6");
    private final BotaoAnimado btnConcorrencia = new BotaoAnimado("Concorrência");
    private final BotaoAnimado btnDemo = new BotaoAnimado("Executar demonstração");
    private final BotaoAnimado btnAtualizar = new BotaoAnimado("Atualizar");
    private final BotaoAnimado btnSaude = new BotaoAnimado("Verificar saúde");

    private PainelRespostaAnimado boxResultado;
    private final ChipInstancia chipInstancia = new ChipInstancia();
    private final JLabel lblMetaResposta = new JLabel("aguardando");
    private final BotaoAnimado btnCopiar = new BotaoAnimado("Copiar");
    private final JTextArea txtResultado = new JTextArea("A resposta mostra qual instância atendeu.");
    private String ultimoTextoResposta = "";
    private final LabelStatusAnimado lblStatus = new LabelStatusAnimado("Pronto.");
    private final JLabel lblAtalhos = new JLabel();

    public TelaCloudForge(String enderecoNomes) {
        super("CloudForge Console");
        this.enderecoNomes = (enderecoNomes == null || enderecoNomes.isBlank()) ? "localhost:50050" : enderecoNomes;
        this.painelTopologia = new PainelTopologia(this::abrirResumoInstancia);
        this.bordaEntradaPadrao = txtEntrada.getBorder();

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1260, 860);
        setMinimumSize(new Dimension(920, 660));
        setLocationRelativeTo(null);

        montarInterface();
        configurarEventos();
        atualizarDicaEValidacaoEntrada();
        atualizarTextoAtalhos();

        // Copiado usa o mesmo relógio das animações, sem outro Timer.
        Animador.registrar(agora -> {
            if (copiarAte == 0) return false;
            if (agora < copiarAte) return true;
            copiarAte = 0;
            btnCopiar.setText("Copiar");
            return false;
        });

        iniciarCoreEmBackground();
    }

    private void montarInterface() {
        raiz = new JPanel(new BorderLayout(0, 12));
        raiz.setBackground(TemaCloudForge.S0);
        raiz.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        setContentPane(raiz);

        getLayeredPane().add(painelToast, JLayeredPane.POPUP_LAYER);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                atualizarLimitesToast();
                atualizarLayoutResponsivo();
            }

            @Override
            public void componentShown(ComponentEvent e) {
                atualizarLimitesToast();
                atualizarLayoutResponsivo();
            }
        });

        // Cabeçalho (entrada 0 ms)
        JPanel cabecalho = new JPanel(new BorderLayout(16, 0));
        cabecalho.setBackground(TemaCloudForge.S0);
        cabecalho.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, TemaCloudForge.DIVIDER),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)));
        cabecalho.add(new WordmarkPanel(), BorderLayout.WEST);

        lblMeta.setFont(TemaCloudForge.mono(12f));
        lblMeta.setForeground(TemaCloudForge.MUTE);
        lblMeta.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
        atualizarTextoMeta(0);
        cabecalho.add(lblMeta, BorderLayout.EAST);
        raiz.add(new PainelEntrada(cabecalho, 0), BorderLayout.NORTH);

        // Miolo com entrada escalonada em 60 ms e ritmo em múltiplos de 8 px
        centro = new JPanel(new GridBagLayout());
        centro.setBackground(TemaCloudForge.S0);

        painelTopo = new JPanel(new GridBagLayout());
        painelTopo.setBackground(TemaCloudForge.S0);
        secInstancias = new PainelEntrada(criarSecaoInstancias(), 60);
        secTopologia = new PainelEntrada(criarSecaoTopologia(), 120);
        secRodizio = new PainelEntrada(criarSecaoRodizio(), 180);

        painelColunas = new JPanel(new BorderLayout(16, 0));
        painelColunas.setBackground(TemaCloudForge.S0);
        JPanel tarefasEEstado = new JPanel(new BorderLayout(0, 10));
        tarefasEEstado.setOpaque(false);
        tarefasEEstado.add(criarSecao("Tarefa", criarPainelFormulario()), BorderLayout.CENTER);
        tarefasEEstado.add(painelEstadoLocal, BorderLayout.SOUTH);
        secTarefa = new PainelEntrada(tarefasEEstado, 240);
        secTarefa.setPreferredSize(new Dimension(380, 0));
        painelColunas.add(secTarefa, BorderLayout.WEST);

        // Coluna direita: Resposta com boa altura acima + Log flexível abaixo
        colunaDireita = new JPanel(new GridBagLayout());
        colunaDireita.setBackground(TemaCloudForge.S0);
        GridBagConstraints dr = new GridBagConstraints();
        dr.gridx = 0;
        dr.weightx = 1.0;

        secResposta = new PainelEntrada(criarSecao("Resposta", criarPainelResposta()), 270);
        dr.gridy = 0;
        dr.weighty = 0.0;
        dr.fill = GridBagConstraints.HORIZONTAL;
        dr.insets = new Insets(0, 0, 10, 0);
        colunaDireita.add(secResposta, dr);

        dr.gridy = 1;
        dr.weighty = 1.0;
        dr.fill = GridBagConstraints.BOTH;
        dr.insets = new Insets(0, 0, 0, 0);
        areaTrilha.setEditable(false);
        areaTrilha.setLineWrap(true);
        areaTrilha.setWrapStyleWord(true);
        areaTrilha.setFont(TemaCloudForge.mono(12f));
        areaTrilha.setForeground(TemaCloudForge.FG);
        areaTrilha.setBackground(TemaCloudForge.S1);
        areaTrilha.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        // Rolagem manual suspende o acompanhamento até o leitor voltar ao fim.
        var barraTrilha = rolagemTrilha.getVerticalScrollBar();
        barraTrilha.addAdjustmentListener(e -> {
            if (e.getValueIsAdjusting()) acompanharTrilha = barraTrilha.getValue()
                    + barraTrilha.getVisibleAmount() >= barraTrilha.getMaximum() - 4;
        });
        rolagemTrilha.addMouseWheelListener(e -> {
            if (e.getWheelRotation() < 0) acompanharTrilha = false;
            else SwingUtilities.invokeLater(() -> acompanharTrilha = barraTrilha.getValue()
                    + barraTrilha.getVisibleAmount() >= barraTrilha.getMaximum() - 4);
        });
        abasEventos.addTab("Trilha por requisição", rolagemTrilha);
        abasEventos.addTab("Prazos ao vivo", painelPrazo);
        abasEventos.addTab("Log", painelLog);
        abasEventos.setPreferredSize(new Dimension(420, 180));
        colunaDireita.add(abasEventos, dr);

        painelColunas.add(colunaDireita, BorderLayout.CENTER);

        organizarTopo(false);
        organizarCentro(false);

        // A rolagem mantém os controles legíveis quando a janela tem pouca altura.
        JScrollPane rolagemCentro = new JScrollPane(centro);
        rolagemCentro.setBorder(BorderFactory.createEmptyBorder());
        rolagemCentro.getVerticalScrollBar().setUnitIncrement(24);
        raiz.add(rolagemCentro, BorderLayout.CENTER);

        // Barra de status + atalhos
        JPanel barraStatus = new JPanel(new BorderLayout(16, 0));
        barraStatus.setBackground(TemaCloudForge.S0);
        barraStatus.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, TemaCloudForge.DIVIDER),
                BorderFactory.createEmptyBorder(8, 0, 0, 0)));
        lblStatus.setFont(TemaCloudForge.mono(12f));
        lblStatus.setForeground(TemaCloudForge.MUTE);
        lblStatus.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        lblAtalhos.setFont(TemaCloudForge.mono(11f));
        lblAtalhos.setForeground(TemaCloudForge.MUTE);
        lblAtalhos.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
        barraStatus.add(lblStatus, BorderLayout.CENTER);
        barraStatus.add(lblAtalhos, BorderLayout.EAST);
        raiz.add(barraStatus, BorderLayout.SOUTH);
    }

    private void atualizarLimitesToast() {
        painelToast.setBounds(0, 0, getContentPane().getWidth(), getContentPane().getHeight());
    }

    /** Alterna entre lado a lado e empilhado (< 1080 px) e ajusta densidade em janelas baixas (< 730 px). */
    private void atualizarLayoutResponsivo() {
        boolean empilhar = getWidth() < 1080;
        boolean compacto = getHeight() < 940 || empilhar;

        if (empilhar != modoEmpilhado) {
            modoEmpilhado = empilhar;
            organizarTopo(empilhar);
        }
        if (compacto != modoCompactoAltura) {
            modoCompactoAltura = compacto;
            aplicarDensidade(compacto);
            organizarCentro(compacto);
        }
    }

    private void organizarCentro(boolean compacto) {
        centro.removeAll();
        int gapSecao = compacto ? 8 : 16;
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridy = 0;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(0, 0, gapSecao, 0);
        centro.add(painelTopo, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, gapSecao, 0);
        centro.add(secRodizio, gbc);

        painelColunas.setMinimumSize(new Dimension(0, 380));
        painelColunas.setPreferredSize(new Dimension(0, 380));
        gbc.gridy = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        centro.add(painelColunas, gbc);

        centro.revalidate();
        centro.repaint();
    }

    private void organizarTopo(boolean empilhar) {
        painelTopo.removeAll();
        GridBagConstraints tg = new GridBagConstraints();
        if (!empilhar) {
            // Cabeçalho de 32 px alinhado com a tabela e altura fixa mínima de 172 px para 2-3 nós respirarem
            cabInstancias.setPreferredSize(new Dimension(0, 32));
            cabTopologia.setPreferredSize(new Dimension(0, 32));
            painelTopologia.setPreferredSize(new Dimension(410, 174));
            painelTopologia.setMinimumSize(new Dimension(260, 174));
            secTopologia.setPreferredSize(new Dimension(410, 214));
            secTopologia.setMinimumSize(new Dimension(260, 214));

            tg.gridy = 0;
            tg.gridx = 0;
            tg.weightx = 0.60;
            tg.weighty = 1.0;
            tg.anchor = GridBagConstraints.NORTH;
            tg.fill = GridBagConstraints.HORIZONTAL;
            tg.insets = new Insets(0, 0, 0, 16);
            painelTopo.add(secInstancias, tg);

            tg.gridx = 1;
            tg.weightx = 0.40;
            tg.fill = GridBagConstraints.BOTH;
            tg.insets = new Insets(0, 0, 0, 0);
            painelTopo.add(secTopologia, tg);
        } else {
            // Abaixo de 1080 px: tabela ocupa largura total e topologia vira faixa horizontal compacta abaixo
            cabInstancias.setPreferredSize(new Dimension(0, 28));
            cabTopologia.setPreferredSize(new Dimension(0, 20));
            painelTopologia.setPreferredSize(new Dimension(0, 78));
            painelTopologia.setMinimumSize(new Dimension(0, 78));
            secTopologia.setPreferredSize(new Dimension(0, 102));
            secTopologia.setMinimumSize(new Dimension(0, 102));

            tg.gridx = 0;
            tg.gridy = 0;
            tg.weightx = 1.0;
            tg.weighty = 0.0;
            tg.fill = GridBagConstraints.HORIZONTAL;
            tg.insets = new Insets(0, 0, 6, 0);
            painelTopo.add(secInstancias, tg);

            tg.gridy = 1;
            tg.insets = new Insets(0, 0, 0, 0);
            painelTopo.add(secTopologia, tg);
        }
        painelTopo.revalidate();
        painelTopo.repaint();
    }

    /** Ajusta margens, altura dos controles e disposição Tipo/Entrada para caber sem cortes em 1000x700. */
    private void aplicarDensidade(boolean compacto) {
        raiz.setBorder(compacto
                ? BorderFactory.createEmptyBorder(10, 16, 8, 16)
                : BorderFactory.createEmptyBorder(14, 16, 10, 16));
        ((BorderLayout) raiz.getLayout()).setVgap(compacto ? 8 : 10);

        painelInstancias.setCompacto(compacto);
        int hBtnTopo = compacto ? 28 : 32;
        btnAtualizar.setPreferredSize(new Dimension(92, hBtnTopo));
        btnSaude.setPreferredSize(new Dimension(128, hBtnTopo));

        painelRodizio.setPreferredSize(new Dimension(0, compacto ? 34 : 42));
        painelRodizio.setMinimumSize(new Dimension(0, compacto ? 34 : 42));

        int hCtrl = compacto ? 32 : 36;
        cbxTipo.setFont(TemaCloudForge.mono(compacto ? 11.5f : 12.5f));
        cbxTipo.setPreferredSize(new Dimension(0, hCtrl));
        txtEntrada.setPreferredSize(new Dimension(0, hCtrl));
        btnEnviar.setPreferredSize(new Dimension(0, hCtrl));
        btnRr6.setPreferredSize(new Dimension(0, hCtrl));
        btnConcorrencia.setPreferredSize(new Dimension(0, hCtrl));
        txtConcorrencia.setPreferredSize(new Dimension(0, hCtrl));
        btnDemo.setPreferredSize(new Dimension(0, hCtrl));
        txtResultado.setFont(TemaCloudForge.mono(compacto ? 11.5f : 12.5f));
        txtResultado.setBorder(compacto
                ? BorderFactory.createEmptyBorder(5, 14, 5, 14)
                : BorderFactory.createEmptyBorder(10, 16, 10, 16));

        cardFormulario.setBorder(BorderFactory.createCompoundBorder(
                new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6),
                compacto ? BorderFactory.createEmptyBorder(10, 14, 10, 14)
                         : BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        secTarefa.setPreferredSize(new Dimension(compacto ? 396 : 380, 0));

        reorganizarCamposFormulario(compacto);
        atualizarAlturaResposta();
        raiz.revalidate();
        raiz.repaint();
    }

    private void reorganizarCamposFormulario(boolean compacto) {
        painelCamposForm.removeAll();
        GridBagConstraints cg = new GridBagConstraints();
        cg.fill = GridBagConstraints.HORIZONTAL;
        cg.anchor = GridBagConstraints.NORTHWEST;

        if (compacto) {
            // Em 1000x700 coloca Tipo (70%) e Entrada (30%) lado a lado sem truncar o combo
            cg.gridy = 0;
            cg.gridx = 0;
            cg.weightx = 0.70;
            cg.insets = new Insets(0, 0, 8, 8);
            painelCamposForm.add(blocoTipo, cg);

            cg.gridx = 1;
            cg.weightx = 0.30;
            cg.insets = new Insets(0, 0, 8, 0);
            painelCamposForm.add(blocoEntrada, cg);

            cg.gridy = 1;
            cg.gridx = 0;
            cg.gridwidth = 2;
            cg.weightx = 1.0;
            cg.insets = new Insets(0, 0, 0, 0);
            painelCamposForm.add(gradeBotoes, cg);
        } else {
            cg.gridx = 0;
            cg.gridwidth = 1;
            cg.weightx = 1.0;

            cg.gridy = 0;
            cg.insets = new Insets(0, 0, 10, 0);
            painelCamposForm.add(blocoTipo, cg);

            cg.gridy = 1;
            cg.insets = new Insets(0, 0, 10, 0);
            painelCamposForm.add(blocoEntrada, cg);

            cg.gridy = 2;
            cg.insets = new Insets(0, 0, 0, 0);
            painelCamposForm.add(gradeBotoes, cg);
        }
        painelCamposForm.revalidate();
    }

    /** Cria rótulo de seção com folga à direita para evitar corte de caracteres em negrito sintético. */
    private JLabel criarTituloSecao(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setFont(TemaCloudForge.sansMedium(13f));
        lbl.setForeground(TemaCloudForge.MUTE);
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        return lbl;
    }

    private JPanel criarSecaoInstancias() {
        JPanel secao = new JPanel(new BorderLayout(0, 6));
        secao.setBackground(TemaCloudForge.S0);

        cabInstancias = new JPanel(new BorderLayout(8, 0));
        cabInstancias.setBackground(TemaCloudForge.S0);
        cabInstancias.setPreferredSize(new Dimension(0, 32));
        cabInstancias.add(criarTituloSecao("Instâncias"), BorderLayout.WEST);

        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acoes.setBackground(TemaCloudForge.S0);
        btnAtualizar.setFont(TemaCloudForge.sans(12f));
        btnAtualizar.setPreferredSize(new Dimension(92, 32));
        btnSaude.setFont(TemaCloudForge.sans(12f));
        btnSaude.setPreferredSize(new Dimension(128, 32));
        acoes.add(btnAtualizar);
        acoes.add(btnSaude);
        cabInstancias.add(acoes, BorderLayout.EAST);

        secao.add(cabInstancias, BorderLayout.NORTH);
        secao.add(painelInstancias, BorderLayout.CENTER);
        return secao;
    }

    /** Cabeçalho de 32 px alinhado exatamente ao topo da tabela de instâncias. */
    private JPanel criarSecaoTopologia() {
        JPanel secao = new JPanel(new BorderLayout(0, 6));
        secao.setBackground(TemaCloudForge.S0);

        cabTopologia = new JPanel(new BorderLayout(8, 0));
        cabTopologia.setBackground(TemaCloudForge.S0);
        cabTopologia.setPreferredSize(new Dimension(0, 32));
        cabTopologia.add(criarTituloSecao("Topologia"), BorderLayout.WEST);

        JLabel dica = new JLabel("clique num nó · histórico");
        dica.setFont(TemaCloudForge.sans(11f));
        dica.setForeground(TemaCloudForge.MUTE);
        dica.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 10));
        cabTopologia.add(dica, BorderLayout.EAST);

        secao.add(cabTopologia, BorderLayout.NORTH);
        secao.add(painelTopologia, BorderLayout.CENTER);
        return secao;
    }

    private JPanel criarSecaoRodizio() {
        JPanel secao = new JPanel(new BorderLayout(0, 6));
        secao.setBackground(TemaCloudForge.S0);

        JPanel cab = new JPanel(new BorderLayout(8, 0));
        cab.setBackground(TemaCloudForge.S0);
        cab.add(criarTituloSecao("Rodízio · últimas requisições"), BorderLayout.WEST);

        lblTaxa.setFont(TemaCloudForge.mono(12f));
        lblTaxa.setForeground(TemaCloudForge.MUTE);
        lblTaxa.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
        cab.add(lblTaxa, BorderLayout.EAST);

        painelRodizio.setOnAtualizarTaxa(lblTaxa::setText);

        secao.add(cab, BorderLayout.NORTH);
        secao.add(painelRodizio, BorderLayout.CENTER);
        JPanel notas = new JPanel(new BorderLayout(12, 0));
        notas.setOpaque(false);
        JLabel legenda = new JLabel("1 e 2 = srv-50051 e srv-50052");
        legenda.setFont(TemaCloudForge.mono(11f));
        legenda.setForeground(TemaCloudForge.MUTE);
        notas.add(legenda, BorderLayout.WEST);
        JToggleButton roteiro = new JToggleButton("Roteiro de falhas");
        roteiro.setFont(TemaCloudForge.sans(12f));
        notas.add(roteiro, BorderLayout.EAST);
        JPanel faixa = new JPanel(new BorderLayout(0, 6));
        faixa.setOpaque(false);
        faixa.add(notas, BorderLayout.NORTH);
        JLabel comandos = new JLabel("No console do servidor: notserving · serving · atraso 6500 · atraso 0");
        comandos.setFont(TemaCloudForge.mono(12f));
        comandos.setVisible(false);
        faixa.add(comandos, BorderLayout.SOUTH);
        roteiro.addActionListener(e -> { comandos.setVisible(roteiro.isSelected()); faixa.revalidate(); });
        secao.add(faixa, BorderLayout.SOUTH);
        return secao;
    }

    private JPanel criarSecao(String titulo, JPanel conteudo) {
        JPanel secao = new JPanel(new BorderLayout(0, 6));
        secao.setBackground(TemaCloudForge.S0);
        secao.add(criarTituloSecao(titulo), BorderLayout.NORTH);
        secao.add(conteudo, BorderLayout.CENTER);
        return secao;
    }

    private JPanel criarPainelFormulario() {
        cardFormulario = new JPanel(new BorderLayout());
        cardFormulario.setBackground(TemaCloudForge.S1);
        cardFormulario.setBorder(BorderFactory.createCompoundBorder(
                new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        // Bloco Tipo
        blocoTipo = new JPanel(new BorderLayout(0, 4));
        blocoTipo.setBackground(TemaCloudForge.S1);
        blocoTipo.add(rotuloCampo("Tipo"), BorderLayout.NORTH);
        cbxTipo.setFont(TemaCloudForge.mono(12.5f));
        cbxTipo.setPreferredSize(new Dimension(0, 36));
        blocoTipo.add(cbxTipo, BorderLayout.CENTER);

        // Bloco Entrada + dica de validação
        blocoEntrada = new JPanel(new BorderLayout(0, 4));
        blocoEntrada.setBackground(TemaCloudForge.S1);
        JPanel linhaRotuloEntrada = new JPanel(new BorderLayout(6, 0));
        linhaRotuloEntrada.setBackground(TemaCloudForge.S1);
        linhaRotuloEntrada.add(rotuloCampo("Entrada"), BorderLayout.WEST);
        lblDicaEntrada.setFont(TemaCloudForge.mono(11f));
        lblDicaEntrada.setForeground(TemaCloudForge.MUTE);
        lblDicaEntrada.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 2));
        linhaRotuloEntrada.add(lblDicaEntrada, BorderLayout.EAST);
        blocoEntrada.add(linhaRotuloEntrada, BorderLayout.NORTH);

        txtEntrada.setFont(TemaCloudForge.mono(12.5f));
        txtEntrada.setPreferredSize(new Dimension(0, 36));
        blocoEntrada.add(txtEntrada, BorderLayout.CENTER);

        // Grade uniforme de botões (3 linhas)
        gradeBotoes = new JPanel(new GridBagLayout());
        gradeBotoes.setBackground(TemaCloudForge.S1);
        GridBagConstraints bg = new GridBagConstraints();
        bg.fill = GridBagConstraints.HORIZONTAL;

        btnEnviar.setFont(TemaCloudForge.sansBold(12.5f));
        btnEnviar.setPreferredSize(new Dimension(0, 36));

        btnRr6.setFont(TemaCloudForge.sansMedium(12.5f));
        btnRr6.setPreferredSize(new Dimension(0, 36));

        btnConcorrencia.setFont(TemaCloudForge.sansMedium(12.5f));
        btnConcorrencia.setPreferredSize(new Dimension(0, 36));
        txtConcorrencia.setFont(TemaCloudForge.mono(12.5f));
        txtConcorrencia.setPreferredSize(new Dimension(0, 36));

        btnDemo.setFont(TemaCloudForge.sansMedium(12.5f));
        btnDemo.setPreferredSize(new Dimension(0, 36));

        bg.gridy = 0;
        bg.gridx = 0;
        bg.weightx = 0.64;
        bg.insets = new Insets(0, 0, 8, 8);
        gradeBotoes.add(btnEnviar, bg);

        bg.gridx = 1;
        bg.weightx = 0.36;
        bg.insets = new Insets(0, 0, 8, 0);
        gradeBotoes.add(btnRr6, bg);

        bg.gridy = 1;
        bg.gridx = 0;
        bg.weightx = 0.64;
        bg.insets = new Insets(0, 0, 8, 8);
        gradeBotoes.add(btnConcorrencia, bg);

        bg.gridx = 1;
        bg.weightx = 0.36;
        bg.insets = new Insets(0, 0, 8, 0);
        gradeBotoes.add(txtConcorrencia, bg);

        bg.gridy = 2;
        bg.gridx = 0;
        bg.gridwidth = 2;
        bg.weightx = 1.0;
        bg.insets = new Insets(0, 0, 0, 0);
        gradeBotoes.add(btnDemo, bg);

        painelCamposForm = new JPanel(new GridBagLayout());
        painelCamposForm.setBackground(TemaCloudForge.S1);
        reorganizarCamposFormulario(false);

        cardFormulario.add(painelCamposForm, BorderLayout.NORTH);
        return cardFormulario;
    }

    /** Painel dedicado para a Resposta (acima do Log), com chip pulsante, Copiar e resumo de concorrência. */
    private JPanel criarPainelResposta() {
        boxResultado = new PainelRespostaAnimado();
        boxResultado.setLayout(new BorderLayout());
        boxResultado.setBackground(TemaCloudForge.S1);
        boxResultado.setBorder(new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6));

        JPanel cabResposta = new JPanel(new BorderLayout(8, 0));
        cabResposta.setBackground(TemaCloudForge.S1);
        cabResposta.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, TemaCloudForge.DIVIDER),
                BorderFactory.createEmptyBorder(4, 16, 4, 12)));

        JPanel infoResposta = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        infoResposta.setBackground(TemaCloudForge.S1);
        lblMetaResposta.setFont(TemaCloudForge.mono(12f));
        lblMetaResposta.setForeground(TemaCloudForge.MUTE);
        lblMetaResposta.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 8));
        infoResposta.add(chipInstancia);
        infoResposta.add(lblMetaResposta);

        btnCopiar.setFont(TemaCloudForge.sans(12f));
        btnCopiar.setPreferredSize(new Dimension(78, 28));
        btnCopiar.setEnabled(false);

        cabResposta.add(infoResposta, BorderLayout.CENTER);
        cabResposta.add(btnCopiar, BorderLayout.EAST);
        boxResultado.add(cabResposta, BorderLayout.NORTH);

        txtResultado.setEditable(false);
        txtResultado.setLineWrap(true);
        txtResultado.setWrapStyleWord(true);
        txtResultado.setBackground(TemaCloudForge.S1);
        txtResultado.setForeground(TemaCloudForge.MUTE);
        txtResultado.setFont(TemaCloudForge.mono(12.5f));
        txtResultado.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        JScrollPane rolRes = new JScrollPane(txtResultado);
        rolRes.setBorder(BorderFactory.createEmptyBorder());
        rolRes.getViewport().setBackground(TemaCloudForge.S1);
        rolRes.setBackground(TemaCloudForge.S1);
        rolRes.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        boxResultado.add(rolRes, BorderLayout.CENTER);

        JPanel wrapResumo = new JPanel(new BorderLayout());
        wrapResumo.setBackground(TemaCloudForge.S1);
        wrapResumo.setBorder(BorderFactory.createEmptyBorder(0, 16, 6, 16));
        wrapResumo.add(painelResumoConc, BorderLayout.CENTER);
        boxResultado.add(wrapResumo, BorderLayout.SOUTH);

        atualizarAlturaResposta();
        return boxResultado;
    }

    /** Garante altura firme para Resposta (e resumo de concorrência) sem deixar o GridBag comprimi-la. */
    private void atualizarAlturaResposta() {
        if (boxResultado == null) return;
        boolean comResumo = painelResumoConc.isVisible();
        int altura = modoCompactoAltura
                ? (comResumo ? 128 : 74)
                : (comResumo ? 142 : 104);
        Dimension dim = new Dimension(0, altura);
        boxResultado.setPreferredSize(dim);
        boxResultado.setMinimumSize(dim);
        boxResultado.revalidate();
        if (secResposta != null) secResposta.revalidate();
        if (colunaDireita != null) {
            colunaDireita.revalidate();
            colunaDireita.repaint();
        }
    }

    private JLabel rotuloCampo(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setFont(TemaCloudForge.sans(12f));
        lbl.setForeground(TemaCloudForge.MUTE);
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        return lbl;
    }

    private void configurarEventos() {
        btnAtualizar.addActionListener(e -> acaoAtualizar());
        btnSaude.addActionListener(e -> acaoVerificarSaude());
        btnEnviar.addActionListener(e -> acaoEnviar());
        btnRr6.addActionListener(e -> acaoRr6());
        btnConcorrencia.addActionListener(e -> acaoConcorrencia());
        btnDemo.addActionListener(e -> acaoDemonstracao());
        btnCopiar.addActionListener(e -> copiarResposta());

        cbxTipo.addActionListener(e -> atualizarDicaEValidacaoEntrada());
        txtEntrada.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { atualizarDicaEValidacaoEntrada(); }
            @Override public void removeUpdate(DocumentEvent e) { atualizarDicaEValidacaoEntrada(); }
            @Override public void changedUpdate(DocumentEvent e) { atualizarDicaEValidacaoEntrada(); }
        });

        txtEntrada.addActionListener(e -> acaoEnviar());

        registrarAtalho(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), "limparLog", painelLog::limpar);
        registrarAtalho(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), "executarRr6", this::acaoRr6);
        registrarAtalho(KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyEvent.CTRL_DOWN_MASK), "focarEntrada", () -> {
            txtEntrada.requestFocusInWindow();
            txtEntrada.selectAll();
        });
        registrarAtalho(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK), "alternarAnimacoes", () -> {
            boolean novo = !Animador.isReduzirAnimacoes();
            Animador.setReduzirAnimacoes(novo);
            atualizarTextoAtalhos();
            painelToast.mostrar(novo ? "Animações reduzidas ativado" : "Animações ativadas", PainelToast.Nivel.INFO);
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {

                Animador.pararTudo();
                executor.shutdownNow();
                ClienteCore c = core;
                if (c != null) {
                    new Thread(c::fechar, "cloudforge-close").start();
                }
            }
        });
    }

    private void atualizarTextoAtalhos() {
        String anim = Animador.isReduzirAnimacoes() ? "Ctrl+M anim: off" : "Ctrl+M anim: on";
        lblAtalhos.setText("Enter enviar · Ctrl+K entrada · Ctrl+R alternar · Ctrl+L log · " + anim);
    }

    private void registrarAtalho(KeyStroke ks, String nome, Runnable acao) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, nome);
        getRootPane().getActionMap().put(nome, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                acao.run();
            }
        });
    }

    private void atualizarDicaEValidacaoEntrada() {
        ClienteCore.TipoTarefa tipo = tipoSelecionado();
        String dica = switch (tipo) {
            case CALCULO -> "0 a 5000";
            case DADOS -> "1, 2, 3.5";
            case HASH -> "qualquer texto";
        };
        txtEntrada.putClientProperty("JTextField.placeholderText", dica);
        boolean valido = entradaValida(tipo, txtEntrada.getText());
        lblDicaEntrada.setText(valido ? dica : "verifique: " + dica);
        lblDicaEntrada.setForeground(valido ? TemaCloudForge.MUTE : TemaCloudForge.WARN);
        if (valido) {
            txtEntrada.setBorder(bordaEntradaPadrao);
        } else {
            txtEntrada.setBorder(BorderFactory.createCompoundBorder(
                    new TemaCloudForge.BordaPainel(TemaCloudForge.WARN, 6),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        }
    }

    private static boolean entradaValida(ClienteCore.TipoTarefa tipo, String texto) {
        String s = texto == null ? "" : texto.trim();
        if (s.isEmpty()) return false;
        return switch (tipo) {
            case CALCULO -> {
                try {
                    int n = Integer.parseInt(s);
                    yield n >= 0 && n <= 5000;
                } catch (NumberFormatException ex) {
                    yield false;
                }
            }
            case DADOS -> {
                String[] partes = s.split(",");
                if (partes.length == 0) yield false;
                for (String p : partes) {
                    try {
                        Double.parseDouble(p.trim());
                    } catch (NumberFormatException ex) {
                        yield false;
                    }
                }
                yield true;
            }
            case HASH -> true;
        };
    }

    private void copiarResposta() {
        if (ultimoTextoResposta.isEmpty()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(ultimoTextoResposta), null);
        btnCopiar.setText("Copiado");
        copiarAte = System.currentTimeMillis() + 1400;
        Animador.acordar();
    }

    private void iniciarCoreEmBackground() {
        painelInstancias.setCarregando(true);
        painelTopologia.animarConsultaNomes();
        executor.submit(() -> {
            AtomicBoolean suprimirLogInicial = new AtomicBoolean(false);
            ClienteCore novoCore = new ClienteCore(enderecoNomes, msg -> {
                if (!suprimirLogInicial.get()) {
                    tratarLogCore(msg);
                }
            });
            novoCore.definirOuvinteEventos(evento -> SwingUtilities.invokeLater(() -> receberEvento(evento)));
            core = novoCore;
            suprimirLogInicial.set(true);
            List<Instancia> instancias = novoCore.atualizar();
            suprimirLogInicial.set(false);
            Map<String, String> saude = novoCore.statusSaude();
            List<Estatisticas> stats = novoCore.estatisticas();
            atualizarGlobal(novoCore);
            SwingUtilities.invokeLater(() -> {
                painelInstancias.setCarregando(false);
                ultimasInstancias = instancias;
                atualizarTextoMeta(instancias.size());
                atualizarPaineisInstancias(instancias, saude, stats);
                painelLog.registrarInfo("Cliente conectado em " + enderecoNomes + " (" + instancias.size() + " instâncias).");

            });
        });
    }

    private void atualizarPaineisInstancias(List<Instancia> instancias, Map<String, String> saude, List<Estatisticas> stats) {
        for (Map.Entry<String, String> e : saude.entrySet()) {
            String id = e.getKey();
            String nova = e.getValue();
            String anterior = ultimasSaudes.put(id, nova);
            if (anterior != null && !anterior.equals(nova)) {
                if ("SERVING".equals(anterior) && !"SERVING".equals(nova)) {
                    painelToast.mostrar(id + " saiu do rodízio (" + nova + ")", PainelToast.Nivel.WARN);
                } else if (!"SERVING".equals(anterior) && "SERVING".equals(nova)) {
                    painelToast.mostrar(id + " voltou ao rodízio", PainelToast.Nivel.OK);
                }
            }
        }
        ultimasStats.clear();
        for (Estatisticas st : stats) {
            ultimasStats.put(st.getInstancia(), st);
            atualizarEstadoLocal(st.getInstancia());
        }
        painelInstancias.atualizarDados(instancias, saude, stats);
        painelTopologia.atualizarNos(instancias, saude, stats);
    }

    private void tratarLogCore(String msg) {
        SwingUtilities.invokeLater(() -> painelLog.registrarInfo(msg));
    }

    private void atualizarEstadoLocal(String id) {
        Estatisticas st = ultimasStats.get(id);
        if (st == null) return;
        painelEstadoLocal.atualizarInstancia(id, Math.max(0, indiceInstancia(id)),
                ultimosIds.getOrDefault(id, 0L), st.getRecebidas(), st.getConcluidas(), st.getRejeitadas());
    }

    // Uma linha por chamada, mantendo também falhas e candidatos anteriores.
    private void receberEvento(ClienteCore.EventoRequisicao e) {
        StringBuilder linha = trilhas.computeIfAbsent(e.req(), n -> new StringBuilder(String.format("R%02d", n)));
        int idx = Math.max(0, indiceInstancia(e.instancia()));
        Integer prazo = prazos.get(e.req());
        switch (e.fase()) {
            case ESCOLHA -> {
                linha.append(" · t").append(e.tentativa()).append(" ").append(e.instancia());
                prazos.put(e.req(), painelPrazo.iniciar(e.instancia(), idx));
            }
            case HEALTH_FIM -> {
                linha.append(" · Health ").append(e.detalhe()).append(" ").append(e.ms()).append(" ms");
                if (prazo != null) painelPrazo.healthConcluido(prazo, e.ms(), "SERVING".equals(e.detalhe()));
                long agora = System.currentTimeMillis();
                if (!loteEmCurso || agora - ultimoHealthLote >= 140) {
                    painelTopologia.animarHealth(e.instancia());
                    ultimoHealthLote = agora;
                }
            }
            case TAREFA_INICIO -> {
                if (prazo != null) painelPrazo.tarefaIniciada(prazo);
            }
            case TAREFA_FIM -> {
                linha.append(" · tarefa OK ").append(e.ms()).append(" ms #").append(e.idTarefa());
                if (prazo != null) painelPrazo.concluido(prazo, e.ms());
                ultimosIds.put(e.instancia(), e.idTarefa());
                atualizarEstadoLocal(e.instancia());
                painelInstancias.registrarResposta(e.instancia(), e.ms());
                // Em lotes, desenha no máximo um pacote real a cada 140 ms.
                long agora = System.currentTimeMillis();
                if (!loteEmCurso || agora - ultimoPacoteLote >= 140) {
                    painelTopologia.animarPacoteOk(e.instancia(), "R" + e.req());
                    ultimoPacoteLote = agora;
                }
                if (!loteEmCurso) painelRodizio.adicionarOk(idx, e.instancia(), e.tipo(), e.idTarefa(), e.ms());
                prazos.remove(e.req());
            }
            case PULADO -> {
                linha.append(" · pulada");
                if (!loteEmCurso) painelRodizio.adicionarSkip(idx, e.instancia(), e.detalhe());
                prazos.remove(e.req());
            }
            case FALHA -> {
                linha.append(" · tarefa ").append(e.detalhe()).append(" ").append(e.ms()).append(" ms");
                if (prazo != null) painelPrazo.falhou(prazo, e.detalhe());
                prazos.remove(e.req());
            }
        }
        // Só acompanha o fim se o leitor já estava lá; não interrompe uma inspeção.
        var barra = rolagemTrilha.getVerticalScrollBar();
        int rolagemAnterior = barra.getValue();
        int cursorAnterior = areaTrilha.getCaretPosition();
        boolean noFim = acompanharTrilha;
        StringBuilder texto = new StringBuilder();
        trilhas.values().forEach(l -> texto.append(l).append('\n'));
        areaTrilha.setText(texto.toString());
        if (noFim) areaTrilha.setCaretPosition(areaTrilha.getDocument().getLength());
        else {
            areaTrilha.setCaretPosition(Math.min(cursorAnterior, areaTrilha.getDocument().getLength()));
            SwingUtilities.invokeLater(() -> barra.setValue(rolagemAnterior));
        }
    }

    private void atualizarGlobal(ClienteCore c) {
        var historico = c.historicoGlobal();
        // O protocolo só comprova o modo global quando há registros publicados.
        Long total = historico != null && historico.getTotal() > 0 ? historico.getTotal() : null;
        SwingUtilities.invokeLater(() -> painelEstadoLocal.definirTotalGlobal(total));
    }

    private int indiceInstancia(String id) {
        List<Instancia> lista = ultimasInstancias;
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    private void atualizarTextoMeta(int qtd) {
        String html = String.format(
                "<html><span style='color:#5C6962'>nomes </span><span style='color:#151515'>%s</span>"
                        + "<span style='color:#5C6962'> &nbsp;·&nbsp; </span><span style='color:#151515'>%d</span>"
                        + "<span style='color:#5C6962'> instâncias registradas &nbsp;·&nbsp; health antes de cada requisição</span></html>",
                enderecoNomes, qtd);
        lblMeta.setText(html);
    }

    private void acaoAtualizar() {
        ClienteCore c = core;
        if (c == null) return;
        painelInstancias.setCarregando(true);
        painelTopologia.animarConsultaNomes();
        executor.submit(() -> {
            List<Instancia> instancias = c.atualizar();
            Map<String, String> saude = c.statusSaude();
            List<Estatisticas> stats = c.estatisticas();
            atualizarGlobal(c);
            SwingUtilities.invokeLater(() -> {
                painelInstancias.setCarregando(false);
                ultimasInstancias = instancias;
                atualizarTextoMeta(instancias.size());
                atualizarPaineisInstancias(instancias, saude, stats);
            });
        });
    }

    private void acaoVerificarSaude() {
        if (core == null) return;
        painelLog.registrarInfo("Verificando saúde das instâncias...");
        verificarSaudeSilencioso();
    }

    private void verificarSaudeSilencioso() {
        ClienteCore c = core;
        if (c == null || !atualizandoSaude.compareAndSet(false, true)) return;
        executor.submit(() -> {
            try {
                List<Instancia> atuais = ultimasInstancias;
                if (atuais.isEmpty()) {
                    atuais = c.atualizar();
                }
                final List<Instancia> lista = atuais;
                Map<String, String> saude = c.statusSaude();
                List<Estatisticas> stats = c.estatisticas();
            atualizarGlobal(c);
                SwingUtilities.invokeLater(() -> {
                    ultimasInstancias = lista;
                    atualizarTextoMeta(lista.size());
                    atualizarPaineisInstancias(lista, saude, stats);
                });
            } finally {
                atualizandoSaude.set(false);
            }
        });
    }

    private ClienteCore.TipoTarefa tipoSelecionado() {
        return switch (cbxTipo.getSelectedIndex()) {
            case 1 -> ClienteCore.TipoTarefa.DADOS;
            case 2 -> ClienteCore.TipoTarefa.HASH;
            default -> ClienteCore.TipoTarefa.CALCULO;
        };
    }

    private void acaoEnviar() {
        ClienteCore c = core;
        if (c == null) return;
        ClienteCore.TipoTarefa tipo = tipoSelecionado();
        String entrada = txtEntrada.getText();
        atualizarDicaEValidacaoEntrada();
        if (!entradaValida(tipo, entrada)) {
            lblStatus.definirTexto("Entrada inválida para " + tipo.name() + ".", TemaCloudForge.WARN);
            txtEntrada.requestFocusInWindow();
            return;
        }
        btnEnviar.setEnabled(false);
        btnEnviar.setCarregando(true);

        executor.submit(() -> {
            ClienteCore.ResultadoTarefa r = c.enviar(tipo, entrada, "gui");
            Map<String, String> saude = c.statusSaude();
            List<Estatisticas> stats = c.estatisticas();
            atualizarGlobal(c);
            SwingUtilities.invokeLater(() -> {
                btnEnviar.setCarregando(false);
                btnEnviar.setEnabled(true);
                registrarResultadoTarefa(r, tipo.name());
                atualizarPaineisInstancias(ultimasInstancias, saude, stats);
            });
        });
    }

    /** Usado pelo modo --screenshot do ClienteGUI. */
    public void clicarRr6() { acaoRr6(); }

    /** Usado pelo modo --screenshot do ClienteGUI com --cenario=conc. */
    public void clicarConcorrencia(int n) {
        txtConcorrencia.setText(Integer.toString(n));
        acaoConcorrencia();
    }

    private void acaoRr6() {
        ClienteCore c = core;
        if (c == null) return;
        btnRr6.setEnabled(false);
        executor.submit(() -> {
            executarRr6(c);
            Map<String, String> saude = c.statusSaude();
            List<Estatisticas> stats = c.estatisticas();
            atualizarGlobal(c);
            SwingUtilities.invokeLater(() -> {
                btnRr6.setEnabled(true);
                atualizarPaineisInstancias(ultimasInstancias, saude, stats);
            });
        });
    }

    private void executarRr6(ClienteCore c) {
        for (int i = 1; i <= 6; i++) {
            ClienteCore.TipoTarefa tipo = switch (i % 3) {
                case 0 -> ClienteCore.TipoTarefa.HASH;
                case 1 -> ClienteCore.TipoTarefa.CALCULO;
                default -> ClienteCore.TipoTarefa.DADOS;
            };
            String entrada = switch (tipo) {
                case HASH -> "texto " + i;
                case CALCULO -> Integer.toString(i * 3);
                case DADOS -> "1,2,3";
            };
            ClienteCore.ResultadoTarefa r = c.enviar(tipo, entrada, "gui-rr6");
            SwingUtilities.invokeLater(() -> registrarResultadoTarefa(r, tipo.name()));
        }
    }

    private void acaoConcorrencia() {
        ClienteCore c = core;
        if (c == null) return;
        int n;
        try {
            n = Integer.parseInt(txtConcorrencia.getText().trim());
        } catch (NumberFormatException ex) {
            painelLog.registrarErro("Quantidade de concorrência inválida.");
            return;
        }
        btnConcorrencia.setEnabled(false);
        lblStatus.definirTexto("Executando concorrência (" + n + " requisições)...", TemaCloudForge.MUTE);

        executor.submit(() -> {
            executarConcorrencia(c, n);
            SwingUtilities.invokeLater(() -> btnConcorrencia.setEnabled(true));
        });
    }

    private void executarConcorrencia(ClienteCore c, int n) {
        loteEmCurso = true;
        ClienteCore.ResumoConcorrencia res;
        try { res = c.concorrencia(n); }
        finally { SwingUtilities.invokeLater(() -> loteEmCurso = false); }
        Map<String, String> saude = c.statusSaude();
        List<Estatisticas> stats = c.estatisticas();
        atualizarGlobal(c);
        SwingUtilities.invokeLater(() -> {
            atualizarPaineisInstancias(ultimasInstancias, saude, stats);
            painelResumoConc.definirResumo(res);
            atualizarAlturaResposta();
            txtResultado.setText("Lote finalizado. Respostas individuais na trilha.");
            String resumoCurto = String.format("concorrência: %d/%d · rej %d · %s",
                    res.concluidas(), res.enviadas(), res.rejeitadas(),
                    res.somaConcluidasInstancias() < 0 ? "inconclusivo" : res.confere() ? "soma confere" : "soma diverge");
            lblStatus.definirTexto(resumoCurto, res.somaConcluidasInstancias() < 0 ? TemaCloudForge.WARN : res.confere() ? TemaCloudForge.GOOD : TemaCloudForge.BAD);
            if (res.confere()) {
                painelLog.registrarOk("concorrência: ", res.concluidas() + "/" + res.enviadas(),
                        TemaCloudForge.corTextoInstancia(0),
                        " concluídas · rejeitadas " + res.rejeitadas() + " · ids repetidos " + res.idsRepetidos());
            } else if (res.somaConcluidasInstancias() < 0) {
                painelLog.registrarInfo(resumoCurto);
            } else {
                painelLog.registrarErro(resumoCurto);
            }
        });
    }

    /** Roteiro automático com legenda ao vivo com crossfade no rodapé para apresentação. */
    private void acaoDemonstracao() {
        ClienteCore c = core;
        if (c == null) return;
        btnDemo.setEnabled(false);
        btnEnviar.setEnabled(false);
        btnRr6.setEnabled(false);
        btnConcorrencia.setEnabled(false);

        executor.submit(() -> {
            try {
                SwingUtilities.invokeLater(() -> {
                    painelLog.registrarInfo("Demonstração 1/3 Round-robin: 6 tarefas");
                    lblStatus.definirTexto("1/3 Round-robin: 6 tarefas", TemaCloudForge.ACCENT);
                });
                executarRr6(c);
                Thread.sleep(700);

                SwingUtilities.invokeLater(() -> {
                    painelLog.registrarInfo("Demonstração 2/3 Concorrência: 50 tarefas");
                    lblStatus.definirTexto("2/3 Concorrência: 50 tarefas", TemaCloudForge.ACCENT);
                });
                executarConcorrencia(c, 50);
                Thread.sleep(400);

                SwingUtilities.invokeLater(() -> {
                    painelLog.registrarInfo("Demonstração 3/3 Conferência concluída.");
                    lblStatus.definirTexto("3/3 Conferência · " + lblStatus.getText(), TemaCloudForge.GOOD);
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                SwingUtilities.invokeLater(() -> {
                    btnDemo.setEnabled(true);
                    btnEnviar.setEnabled(true);
                    btnRr6.setEnabled(true);
                    btnConcorrencia.setEnabled(true);
                });
            }
        });
    }

    private void abrirResumoInstancia(String idInstancia) {
        ClienteCore c = core;
        if (c == null) return;
        executor.submit(() -> {
            List<Estatisticas> todasStats = c.estatisticas();
            List<Historico> todosHists = c.historicos();
            Estatisticas alvoStat = todasStats.stream()
                    .filter(s -> s.getInstancia().equals(idInstancia))
                    .findFirst().orElse(null);
            Historico alvoHist = todosHists.stream()
                    .filter(h -> h.getInstancia().equals(idInstancia))
                    .findFirst().orElse(null);

            SwingUtilities.invokeLater(() -> mostrarDialogoInstancia(idInstancia, alvoStat, alvoHist));
        });
    }

    private void mostrarDialogoInstancia(String id, Estatisticas st, Historico hist) {
        JDialog dlg = new JDialog(this, "Instância " + id, false);
        dlg.setSize(460, 340);
        dlg.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(TemaCloudForge.S0);
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JLabel titulo = new JLabel(id);
        titulo.setFont(TemaCloudForge.monoBold(15f));
        titulo.setForeground(TemaCloudForge.FG);
        root.add(titulo, BorderLayout.NORTH);

        StringBuilder sb = new StringBuilder();
        if (st != null) {
            sb.append("Recebidas: ").append(st.getRecebidas())
              .append("  ·  Concluídas: ").append(st.getConcluidas())
              .append("  ·  Rejeitadas: ").append(st.getRejeitadas())
              .append("\nEm andamento: ").append(st.getEmAndamento())
              .append("/").append(st.getLimiteSimultaneas())
              .append("\n\nÚltimas 5 tarefas:\n");
        } else {
            sb.append("Sem estatísticas disponíveis.\n\n");
        }

        if (hist != null && hist.getRegistrosCount() > 0) {
            List<RegistroHistorico> regs = hist.getRegistrosList();
            int inicio = Math.max(0, regs.size() - 5);
            for (int i = regs.size() - 1; i >= inicio; i--) {
                RegistroHistorico r = regs.get(i);
                sb.append(String.format("#%d  %-19s  %4d ms  %s (%s)%n",
                        r.getIdTarefa(), r.getTipo().name(), r.getDuracaoMs(), r.getCliente(), r.getHorario()));
            }
        } else {
            sb.append("Nenhuma tarefa registrada no histórico.");
        }

        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        area.setBackground(TemaCloudForge.S1);
        area.setForeground(TemaCloudForge.FG);
        area.setFont(TemaCloudForge.mono(12f));
        area.setBorder(BorderFactory.createCompoundBorder(
                new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        root.add(new JScrollPane(area), BorderLayout.CENTER);

        BotaoAnimado fechar = new BotaoAnimado("Fechar");
        fechar.setPreferredSize(new Dimension(96, 34));
        fechar.addActionListener(e -> dlg.dispose());
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rodape.setBackground(TemaCloudForge.S0);
        rodape.add(fechar);
        root.add(rodape, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.setVisible(true);
    }

    private void registrarResultadoTarefa(ClienteCore.ResultadoTarefa r, String tipoRotulo) {
        if (r.ok()) {
            int idx = Math.max(0, indiceInstancia(r.instancia()));
            Color corInst = TemaCloudForge.corInstancia(idx);
            Color corTextoInst = TemaCloudForge.corTextoInstancia(idx);
            chipInstancia.definir(r.instancia(), corInst);
            lblMetaResposta.setText("#" + r.idTarefa() + " · " + r.duracaoMs() + " ms");
            ultimoTextoResposta = r.resultado();
            btnCopiar.setEnabled(!ultimoTextoResposta.isEmpty());
            txtResultado.setForeground(TemaCloudForge.FG);
            txtResultado.setText(r.resultado());
            txtResultado.setCaretPosition(0);
            boxResultado.animarEntrada();

            painelLog.registrarOk("Tarefa #" + r.idTarefa() + " respondida por ", r.instancia(), corTextoInst,
                    " (" + r.duracaoMs() + " ms): " + r.resultado());
        } else {
            painelRodizio.adicionarErro(r.codigoErro());
            chipInstancia.limpar();
            lblMetaResposta.setText(r.codigoErro());
            ultimoTextoResposta = r.erro();
            btnCopiar.setEnabled(false);
            txtResultado.setForeground(TemaCloudForge.BAD);
            txtResultado.setText("Erro (" + r.codigoErro() + "): " + r.erro());
            boxResultado.animarEntrada();
            painelLog.registrarErro("Erro (" + r.codigoErro() + "): " + r.erro());
            if ("UNAVAILABLE".equals(r.codigoErro())) {
                painelToast.mostrar("Nenhum servidor saudável", PainelToast.Nivel.BAD);
            }
        }
    }

    /** Wrapper de entrada escalonada (fade + deslize de 8 px) ao abrir a janela. */
    private static final class PainelEntrada extends JPanel implements Animador.Animavel {
        private final Animador.Transicao trans = new Animador.Transicao(260, 0.0);

        PainelEntrada(JPanel conteudo, long atrasoMs) {
            super(new BorderLayout());
            setOpaque(false);
            add(conteudo, BorderLayout.CENTER);
            Animador.registrar(this);
            trans.iniciar(0.0, 1.0, atrasoMs);
        }

        @Override
        public boolean atualizarAnimacao(long agora) {
            if (trans.ativa(agora)) {
                repaint();
                return true;
            }
            repaint();
            return false;
        }

        @Override
        public void paint(Graphics g) {
            long agora = System.currentTimeMillis();
            double v = trans.valor(agora);
            if (v >= 0.999 || Animador.isReduzirAnimacoes()) {
                super.paint(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            int dy = (int) Math.round((1.0 - v) * 8.0);
            g2.translate(0, dy);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0.05, v)));
            super.paint(g2);
            g2.dispose();
        }
    }

    /** Painel Resposta com entrada fade + deslize de 6 px ao receber resposta. */
    private static final class PainelRespostaAnimado extends JPanel implements Animador.Animavel {
        private final Animador.Transicao trans = new Animador.Transicao(200, 1.0);

        PainelRespostaAnimado() {
            Animador.registrar(this);
        }

        void animarEntrada() {
            if (!Animador.isReduzirAnimacoes()) {
                trans.iniciar(0.0, 1.0);
            }
        }

        @Override
        public boolean atualizarAnimacao(long agora) {
            if (trans.ativa(agora)) {
                repaint();
                return true;
            }
            return false;
        }

        @Override
        public void paint(Graphics g) {
            double v = trans.valor(System.currentTimeMillis());
            if (v >= 0.999 || Animador.isReduzirAnimacoes()) {
                super.paint(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            int dy = (int) Math.round((1.0 - v) * 6.0);
            g2.translate(0, dy);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0.1, v)));
            super.paint(g2);
            g2.dispose();
        }
    }

    /** Rótulo de status com crossfade de 200 ms ao trocar de etapa. */
    private static final class LabelStatusAnimado extends JLabel implements Animador.Animavel {
        private final Animador.Transicao trans = new Animador.Transicao(200, 1.0);

        LabelStatusAnimado(String inicial) {
            super(inicial);
            Animador.registrar(this);
        }

        void definirTexto(String texto, Color cor) {
            setForeground(cor);
            setText(texto);
            if (!Animador.isReduzirAnimacoes()) {
                trans.iniciar(0.25, 1.0);
            }
        }

        @Override
        public boolean atualizarAnimacao(long agora) {
            if (trans.ativa(agora)) {
                repaint();
                return true;
            }
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            double v = trans.valor(System.currentTimeMillis());
            if (v >= 0.999 || Animador.isReduzirAnimacoes()) {
                super.paintComponent(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0.1, v)));
            super.paintComponent(g2);
            g2.dispose();
        }
    }

    /** Chip discreto com a cor da instância e pulso único ao responder. */
    private static final class ChipInstancia extends JPanel implements Animador.Animavel {
        private String texto = "";
        private Color cor = TemaCloudForge.ACCENT;
        private long inicioPulsoMs = 0L;

        ChipInstancia() {
            setOpaque(false);
            setVisible(false);
            Animador.registrar(this);
        }

        void definir(String texto, Color cor) {
            this.texto = texto == null ? "" : texto;
            this.cor = cor == null ? TemaCloudForge.ACCENT : cor;
            this.inicioPulsoMs = System.currentTimeMillis();
            setVisible(!this.texto.isEmpty());
            revalidate();
            Animador.acordar();
            repaint();
        }

        void limpar() {
            this.texto = "";
            setVisible(false);
        }

        @Override
        public boolean atualizarAnimacao(long agora) {
            if (texto.isEmpty() || Animador.isReduzirAnimacoes()) return false;
            if (agora - inicioPulsoMs < 350) {
                repaint();
                return true;
            }
            return false;
        }

        @Override
        public Dimension getPreferredSize() {
            if (texto.isEmpty()) return new Dimension(0, 24);
            FontMetrics fm = getFontMetrics(TemaCloudForge.mono(11.5f));
            return new Dimension(fm.stringWidth(texto) + 24, 24);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (texto.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            Color fundo = TemaCloudForge.S2;
            long dt = System.currentTimeMillis() - inicioPulsoMs;
            if (!Animador.isReduzirAnimacoes() && dt >= 0 && dt < 350) {
                double f = 1.0 - Animador.easeOut((double) dt / 350.0);
                fundo = TemaCloudForge.interpolarCor(TemaCloudForge.S2, cor, f * 0.22);
            }

            g2.setColor(fundo);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
            g2.setColor(cor);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
            g2.fillOval(7, (h - 6) / 2, 6, 6);
            g2.setFont(TemaCloudForge.mono(11.5f));
            g2.setColor(TemaCloudForge.FG);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(texto, 17, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Wordmark "Cloud" (sans bold) + "forge" (serif italic) com reveal da esquerda para a direita (450 ms). */
    private static final class WordmarkPanel extends JPanel implements Animador.Animavel {
        private final Animador.Transicao reveal = new Animador.Transicao(450, 0.0);

        WordmarkPanel() {
            setBackground(TemaCloudForge.S0);
            setPreferredSize(new Dimension(176, 32));
            Animador.registrar(this);
            reveal.iniciar(0.0, 1.0);
        }

        @Override
        public boolean atualizarAnimacao(long agora) {
            if (reveal.ativa(agora)) {
                repaint();
                return true;
            }
            repaint();
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double v = reveal.valor(System.currentTimeMillis());
            if (v < 0.999 && !Animador.isReduzirAnimacoes()) {
                g2.clipRect(0, 0, Math.max(1, (int) Math.round(getWidth() * v)), getHeight());
            }
            g2.setColor(TemaCloudForge.FG);

            g2.setFont(TemaCloudForge.sansBold(24f));
            FontMetrics fmSans = g2.getFontMetrics();
            int yBase = 24;
            g2.drawString("Cloud", 0, yBase);
            int wCloud = fmSans.stringWidth("Cloud");

            g2.setFont(TemaCloudForge.serifItalic(29f));
            g2.drawString("forge", wCloud + 1, yBase);
            g2.dispose();
        }
    }
}
