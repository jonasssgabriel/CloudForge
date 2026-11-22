package com.mycompany.clientecloudforge;

import com.mycompany.cloudforge.Estatisticas;
import com.mycompany.cloudforge.Historico;
import com.mycompany.cloudforge.Instancia;
import com.mycompany.cloudforge.RegistroHistorico;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Janela principal do ClienteCloudForge.
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

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "cloudforge-grpc");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean atualizandoSaude = new AtomicBoolean(false);
    private final Timer timerSaude;

    private final JLabel lblMeta = new JLabel();
    private final PainelInstancias painelInstancias = new PainelInstancias();
    private final PainelTopologia painelTopologia;
    private final PainelRodizio painelRodizio = new PainelRodizio();
    private final PainelLog painelLog = new PainelLog();

    private final JComboBox<String> cbxTipo = new JComboBox<>(OPCOES_TIPO);
    private final JTextField txtEntrada = new JTextField("25");
    private final JTextField txtConcorrencia = new JTextField("200");
    private final JButton btnEnviar = new JButton("Enviar");
    private final JButton btnRr6 = new JButton("rr 6");
    private final JButton btnConcorrencia = new JButton("Concorrência");
    private final JButton btnDemo = new JButton("Executar demonstração");
    private final JButton btnAtualizar = new JButton("Atualizar");
    private final JButton btnSaude = new JButton("Verificar saúde");
    private final JTextArea txtResultado = new JTextArea("A resposta mostra qual instância atendeu.");
    private final JLabel lblStatus = new JLabel("Pronto.");

    public TelaCloudForge(String enderecoNomes) {
        super("CloudForge Console");
        this.enderecoNomes = (enderecoNomes == null || enderecoNomes.isBlank()) ? "localhost:50050" : enderecoNomes;
        this.painelTopologia = new PainelTopologia(this::abrirResumoInstancia);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 760);
        setMinimumSize(new Dimension(920, 640));
        setLocationRelativeTo(null);

        montarInterface();
        configurarEventos();

        timerSaude = new Timer(3000, e -> verificarSaudeSilencioso());
        timerSaude.setRepeats(true);

        iniciarCoreEmBackground();
    }

    private void montarInterface() {
        JPanel raiz = new JPanel(new BorderLayout(0, 12));
        raiz.setBackground(TemaCloudForge.S0);
        raiz.setBorder(BorderFactory.createEmptyBorder(16, 22, 12, 22));
        setContentPane(raiz);

        // Cabeçalho
        JPanel cabecalho = new JPanel(new BorderLayout(16, 0));
        cabecalho.setBackground(TemaCloudForge.S0);
        cabecalho.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, TemaCloudForge.LINE),
                BorderFactory.createEmptyBorder(0, 0, 10, 0)));
        cabecalho.add(new WordmarkPanel(), BorderLayout.WEST);

        lblMeta.setFont(TemaCloudForge.mono(12f));
        lblMeta.setForeground(TemaCloudForge.MUTE);
        atualizarTextoMeta(0);
        cabecalho.add(lblMeta, BorderLayout.EAST);
        raiz.add(cabecalho, BorderLayout.NORTH);

        // Miolo: (Instâncias + Topologia) -> Rodízio -> (Tarefa + Log)
        JPanel centro = new JPanel(new GridBagLayout());
        centro.setBackground(TemaCloudForge.S0);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Linha superior: Tabela (62%) à esquerda + Topologia animada (38%) à direita
        JPanel topo = new JPanel(new GridBagLayout());
        topo.setBackground(TemaCloudForge.S0);
        GridBagConstraints tg = new GridBagConstraints();
        tg.gridy = 0;

        tg.gridx = 0;
        tg.weightx = 0.62;
        tg.weighty = 1.0;
        tg.anchor = GridBagConstraints.NORTH;
        tg.fill = GridBagConstraints.HORIZONTAL;
        tg.insets = new Insets(0, 0, 0, 16);
        topo.add(criarSecaoInstancias(), tg);

        JPanel secTopo = criarSecao("Topologia", painelTopologia);
        secTopo.setPreferredSize(new Dimension(0, 192));
        tg.gridx = 1;
        tg.weightx = 0.38;
        tg.fill = GridBagConstraints.BOTH;
        tg.insets = new Insets(0, 0, 0, 0);
        topo.add(secTopo, tg);

        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 12, 0);
        centro.add(topo, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 12, 0);
        centro.add(criarSecao("Rodízio · últimas requisições", painelRodizio), gbc);

        gbc.gridy = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);

        JPanel colunas = new JPanel(new BorderLayout(16, 0));
        colunas.setBackground(TemaCloudForge.S0);
        JPanel secTarefa = criarSecao("Tarefa", criarPainelFormulario());
        secTarefa.setPreferredSize(new Dimension(346, 0));
        colunas.add(secTarefa, BorderLayout.WEST);
        colunas.add(criarSecao("Log", painelLog), BorderLayout.CENTER);
        centro.add(colunas, gbc);

        raiz.add(centro, BorderLayout.CENTER);

        // Barra de status
        JPanel barraStatus = new JPanel(new BorderLayout());
        barraStatus.setBackground(TemaCloudForge.S0);
        barraStatus.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, TemaCloudForge.LINE),
                BorderFactory.createEmptyBorder(8, 0, 0, 0)));
        lblStatus.setFont(TemaCloudForge.mono(12f));
        lblStatus.setForeground(TemaCloudForge.MUTE);
        JLabel lblAtalhos = new JLabel("Enter enviar · Ctrl+L limpar log");
        lblAtalhos.setFont(TemaCloudForge.mono(11f));
        lblAtalhos.setForeground(TemaCloudForge.MUTE);
        barraStatus.add(lblStatus, BorderLayout.WEST);
        barraStatus.add(lblAtalhos, BorderLayout.EAST);
        raiz.add(barraStatus, BorderLayout.SOUTH);
    }

    private JPanel criarSecaoInstancias() {
        JPanel secao = new JPanel(new BorderLayout(0, 6));
        secao.setBackground(TemaCloudForge.S0);

        JPanel cab = new JPanel(new BorderLayout());
        cab.setBackground(TemaCloudForge.S0);
        JLabel lbl = new JLabel("Instâncias");
        lbl.setFont(TemaCloudForge.sansMedium(13f));
        lbl.setForeground(TemaCloudForge.MUTE);
        cab.add(lbl, BorderLayout.WEST);

        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        acoes.setBackground(TemaCloudForge.S0);
        btnAtualizar.setFont(TemaCloudForge.sans(12f));
        btnAtualizar.setPreferredSize(new Dimension(92, 30));
        btnSaude.setFont(TemaCloudForge.sans(12f));
        btnSaude.setPreferredSize(new Dimension(126, 30));
        acoes.add(btnAtualizar);
        acoes.add(btnSaude);
        cab.add(acoes, BorderLayout.EAST);

        secao.add(cab, BorderLayout.NORTH);
        secao.add(painelInstancias, BorderLayout.CENTER);
        return secao;
    }

    private JPanel criarSecao(String titulo, JPanel conteudo) {
        JPanel secao = new JPanel(new BorderLayout(0, 6));
        secao.setBackground(TemaCloudForge.S0);
        JLabel lbl = new JLabel(titulo);
        lbl.setFont(TemaCloudForge.sansMedium(13f));
        lbl.setForeground(TemaCloudForge.MUTE);
        secao.add(lbl, BorderLayout.NORTH);
        secao.add(conteudo, BorderLayout.CENTER);
        return secao;
    }

    private JPanel criarPainelFormulario() {
        JPanel painel = new JPanel(new BorderLayout());
        painel.setBackground(TemaCloudForge.S1);
        painel.setBorder(BorderFactory.createCompoundBorder(
                new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        JPanel pilha = new JPanel();
        pilha.setLayout(new BoxLayout(pilha, BoxLayout.Y_AXIS));
        pilha.setBackground(TemaCloudForge.S1);

        pilha.add(rotuloCampo("Tipo"));
        pilha.add(Box.createVerticalStrut(3));
        cbxTipo.setFont(TemaCloudForge.mono(13f));
        cbxTipo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        cbxTipo.setPreferredSize(new Dimension(0, 34));
        cbxTipo.setAlignmentX(LEFT_ALIGNMENT);
        pilha.add(cbxTipo);
        pilha.add(Box.createVerticalStrut(8));

        pilha.add(rotuloCampo("Entrada"));
        pilha.add(Box.createVerticalStrut(3));
        txtEntrada.setFont(TemaCloudForge.mono(13f));
        txtEntrada.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        txtEntrada.setPreferredSize(new Dimension(0, 34));
        txtEntrada.setAlignmentX(LEFT_ALIGNMENT);
        pilha.add(txtEntrada);
        pilha.add(Box.createVerticalStrut(10));

        // Grade limpa de botões (todos altura 36 px e mesma fonte)
        JPanel gradeBotoes = new JPanel(new GridBagLayout());
        gradeBotoes.setBackground(TemaCloudForge.S1);
        gradeBotoes.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints bg = new GridBagConstraints();
        bg.fill = GridBagConstraints.HORIZONTAL;

        btnEnviar.setBackground(TemaCloudForge.ACCENT);
        btnEnviar.setForeground(TemaCloudForge.ON_ACCENT);
        btnEnviar.setFont(TemaCloudForge.sansBold(12.5f));
        btnEnviar.setPreferredSize(new Dimension(0, 36));

        btnRr6.setFont(TemaCloudForge.sansMedium(12.5f));
        btnRr6.setPreferredSize(new Dimension(0, 36));

        btnConcorrencia.setFont(TemaCloudForge.sansMedium(12.5f));
        btnConcorrencia.setPreferredSize(new Dimension(0, 36));
        txtConcorrencia.setFont(TemaCloudForge.mono(13f));
        txtConcorrencia.setPreferredSize(new Dimension(0, 36));

        btnDemo.setFont(TemaCloudForge.sansMedium(12.5f));
        btnDemo.setPreferredSize(new Dimension(0, 36));

        // Linha 1: Enviar (maior) + rr 6
        bg.gridy = 0;
        bg.gridx = 0;
        bg.weightx = 0.64;
        bg.insets = new Insets(0, 0, 8, 8);
        gradeBotoes.add(btnEnviar, bg);

        bg.gridx = 1;
        bg.weightx = 0.36;
        bg.insets = new Insets(0, 0, 8, 0);
        gradeBotoes.add(btnRr6, bg);

        // Linha 2: Concorrência + campo N
        bg.gridy = 1;
        bg.gridx = 0;
        bg.weightx = 0.64;
        bg.insets = new Insets(0, 0, 8, 8);
        gradeBotoes.add(btnConcorrencia, bg);

        bg.gridx = 1;
        bg.weightx = 0.36;
        bg.insets = new Insets(0, 0, 8, 0);
        gradeBotoes.add(txtConcorrencia, bg);

        // Linha 3: Executar demonstração (largura total)
        bg.gridy = 2;
        bg.gridx = 0;
        bg.gridwidth = 2;
        bg.weightx = 1.0;
        bg.insets = new Insets(0, 0, 0, 0);
        gradeBotoes.add(btnDemo, bg);

        gradeBotoes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 128));
        pilha.add(gradeBotoes);
        pilha.add(Box.createVerticalStrut(12));

        // Resultado da última tarefa
        JPanel boxResultado = new JPanel(new BorderLayout());
        boxResultado.setBackground(TemaCloudForge.S1);
        boxResultado.setAlignmentX(LEFT_ALIGNMENT);
        boxResultado.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, TemaCloudForge.LINE),
                BorderFactory.createEmptyBorder(10, 0, 0, 0)));

        txtResultado.setEditable(false);
        txtResultado.setLineWrap(true);
        txtResultado.setWrapStyleWord(true);
        txtResultado.setRows(3);
        txtResultado.setBackground(TemaCloudForge.S1);
        txtResultado.setForeground(TemaCloudForge.MUTE);
        txtResultado.setFont(TemaCloudForge.mono(12f));
        txtResultado.setBorder(BorderFactory.createEmptyBorder());
        boxResultado.add(txtResultado, BorderLayout.CENTER);
        pilha.add(boxResultado);

        painel.add(pilha, BorderLayout.CENTER);
        return painel;
    }

    private JLabel rotuloCampo(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setFont(TemaCloudForge.sans(12f));
        lbl.setForeground(TemaCloudForge.MUTE);
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        return lbl;
    }

    private void configurarEventos() {
        btnAtualizar.addActionListener(e -> acaoAtualizar());
        btnSaude.addActionListener(e -> acaoVerificarSaude());
        btnEnviar.addActionListener(e -> acaoEnviar());
        btnRr6.addActionListener(e -> acaoRr6());
        btnConcorrencia.addActionListener(e -> acaoConcorrencia());
        btnDemo.addActionListener(e -> acaoDemonstracao());

        // Enter na entrada envia a tarefa
        txtEntrada.addActionListener(e -> acaoEnviar());

        // Ctrl+L limpa o log
        KeyStroke ctrlL = KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlL, "limparLog");
        getRootPane().getActionMap().put("limparLog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                painelLog.limpar();
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                timerSaude.stop();
                painelTopologia.pararTimer();
                executor.shutdownNow();
                ClienteCore c = core;
                if (c != null) {
                    new Thread(c::fechar, "cloudforge-close").start();
                }
            }
        });
    }

    private void iniciarCoreEmBackground() {
        executor.submit(() -> {
            AtomicBoolean suprimirLogInicial = new AtomicBoolean(false);
            ClienteCore novoCore = new ClienteCore(enderecoNomes, msg -> {
                if (!suprimirLogInicial.get()) {
                    tratarLogCore(msg);
                }
            });
            core = novoCore;
            suprimirLogInicial.set(true);
            List<Instancia> instancias = novoCore.atualizar();
            suprimirLogInicial.set(false);
            Map<String, String> saude = novoCore.statusSaude();
            List<Estatisticas> stats = novoCore.estatisticas();
            SwingUtilities.invokeLater(() -> {
                ultimasInstancias = instancias;
                atualizarTextoMeta(instancias.size());
                atualizarPaineisInstancias(instancias, saude, stats);
                painelLog.registrarInfo("Cliente conectado em " + enderecoNomes + " (" + instancias.size() + " instâncias).");
                timerSaude.start();
            });
        });
    }

    private void atualizarPaineisInstancias(List<Instancia> instancias, Map<String, String> saude, List<Estatisticas> stats) {
        painelInstancias.atualizarDados(instancias, saude, stats);
        painelTopologia.atualizarNos(instancias, saude, stats);
    }

    private void tratarLogCore(String msg) {
        SwingUtilities.invokeLater(() -> {
            if (msg.contains("pulado (")) {
                painelLog.registrarSkip(msg);
                String id = extrairIdPulado(msg);
                painelRodizio.adicionarSkip(indiceInstancia(id));
                painelTopologia.animarPacoteSkip(id, msg.contains("deadline") || msg.contains("UNAVAILABLE"));
            } else if (msg.contains("ID repetido") || msg.contains("indisponível")) {
                painelLog.registrarErro(msg);
            } else {
                painelLog.registrarInfo(msg);
            }
        });
    }

    private static String extrairIdPulado(String msg) {
        int i = msg.indexOf("[CLIENTE] ");
        if (i < 0) return "";
        String resto = msg.substring(i + 10);
        int esp = resto.indexOf(' ');
        return esp > 0 ? resto.substring(0, esp) : "";
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
        executor.submit(() -> {
            List<Instancia> instancias = c.atualizar();
            Map<String, String> saude = c.statusSaude();
            List<Estatisticas> stats = c.estatisticas();
            SwingUtilities.invokeLater(() -> {
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
        btnEnviar.setEnabled(false);

        executor.submit(() -> {
            ClienteCore.ResultadoTarefa r = c.enviar(tipo, entrada, "gui");
            Map<String, String> saude = c.statusSaude();
            List<Estatisticas> stats = c.estatisticas();
            SwingUtilities.invokeLater(() -> {
                btnEnviar.setEnabled(true);
                registrarResultadoTarefa(r);
                atualizarPaineisInstancias(ultimasInstancias, saude, stats);
            });
        });
    }

    /** Usado só pelo modo --screenshot do ClienteGUI para mostrar a tela com atividade. */
    public void clicarRr6() { acaoRr6(); }

    private void acaoRr6() {
        ClienteCore c = core;
        if (c == null) return;
        btnRr6.setEnabled(false);
        executor.submit(() -> {
            executarRr6(c);
            Map<String, String> saude = c.statusSaude();
            List<Estatisticas> stats = c.estatisticas();
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
            SwingUtilities.invokeLater(() -> registrarResultadoTarefa(r));
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
        lblStatus.setForeground(TemaCloudForge.MUTE);
        lblStatus.setText("Executando concorrência (" + n + " requisições)...");

        executor.submit(() -> {
            executarConcorrencia(c, n);
            SwingUtilities.invokeLater(() -> btnConcorrencia.setEnabled(true));
        });
    }

    private void executarConcorrencia(ClienteCore c, int n) {
        ClienteCore.ResumoConcorrencia res = c.concorrencia(n);
        Map<String, String> saude = c.statusSaude();
        List<Estatisticas> stats = c.estatisticas();
        SwingUtilities.invokeLater(() -> {
            atualizarPaineisInstancias(ultimasInstancias, saude, stats);
            int anims = Math.min(20, Math.max(0, res.concluidas()));
            List<Instancia> lista = ultimasInstancias;
            for (int i = 0; i < anims && !lista.isEmpty(); i++) {
                painelTopologia.animarPacoteOk(lista.get(i % lista.size()).getId());
            }
            String resumo = String.format(
                    "concorrência: enviadas %d · concluídas %d · rejeitadas %d · outras %d · ids repetidos %d · %s",
                    res.enviadas(), res.concluidas(), res.rejeitadas(), res.outrasFalhas(),
                    res.idsRepetidos(), res.confere() ? "soma confere" : "soma diverge");
            lblStatus.setForeground(res.confere() ? TemaCloudForge.GOOD : TemaCloudForge.BAD);
            lblStatus.setText(resumo);
            if (res.confere()) {
                painelLog.registrarOk("concorrência: ", res.concluidas() + "/" + res.enviadas(),
                        " concluídas · rejeitadas " + res.rejeitadas() + " · ids repetidos " + res.idsRepetidos());
            } else {
                painelLog.registrarErro(resumo);
            }
        });
    }

    /** Roteiro automático: rr 6 -> pausa -> concorrência 50 -> mostra conferência. */
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
                    painelLog.registrarInfo("Demonstração iniciada: rr 6...");
                    lblStatus.setForeground(TemaCloudForge.MUTE);
                    lblStatus.setText("Demonstração: executando rr 6...");
                });
                executarRr6(c);
                Thread.sleep(700);
                SwingUtilities.invokeLater(() -> {
                    painelLog.registrarInfo("Demonstração: executando concorrência 50...");
                    lblStatus.setText("Demonstração: executando concorrência 50...");
                });
                executarConcorrencia(c, 50);
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

        JButton fechar = new JButton("Fechar");
        fechar.setPreferredSize(new Dimension(96, 36));
        fechar.addActionListener(e -> dlg.dispose());
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rodape.setBackground(TemaCloudForge.S0);
        rodape.add(fechar);
        root.add(rodape, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.setVisible(true);
    }

    private void registrarResultadoTarefa(ClienteCore.ResultadoTarefa r) {
        if (r.ok()) {
            int idx = indiceInstancia(r.instancia());
            painelRodizio.adicionarOk(Math.max(0, idx));
            painelTopologia.animarPacoteOk(r.instancia());
            String resumo = "Tarefa #" + r.idTarefa() + " · " + r.instancia() + " (" + r.duracaoMs() + " ms)\n" + r.resultado();
            txtResultado.setForeground(TemaCloudForge.FG);
            txtResultado.setText(resumo);
            painelLog.registrarOk("Tarefa #" + r.idTarefa() + " respondida por ", r.instancia(),
                    " (" + r.duracaoMs() + " ms): " + r.resultado());
        } else {
            painelRodizio.adicionarErro();
            txtResultado.setForeground(TemaCloudForge.BAD);
            txtResultado.setText("Erro (" + r.codigoErro() + "): " + r.erro());
            painelLog.registrarErro("Erro (" + r.codigoErro() + "): " + r.erro());
        }
    }

    /** Wordmark "Cloud" (sans bold) + "forge" (serif italic). */
    private static final class WordmarkPanel extends JPanel {
        WordmarkPanel() {
            setBackground(TemaCloudForge.S0);
            setPreferredSize(new Dimension(170, 32));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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
