package com.mycompany.clientecloudforge;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Painel de log monoespaçado colorido com rolagem suave e destaque de selo que esmaece.
 */
public class PainelLog extends JPanel implements Animador.Animavel {
    private static final long DURACAO_LINHA_MS = 180;
    private static final long DURACAO_SELO_MS = 600;

    /** Guarda cada selo de instância cujo destaque de fundo está esmaecendo. */
    private static final class SeloAnimado {
        final int inicio;
        final int tamanho;
        final Color corFundo;
        final long inicioMs;

        SeloAnimado(int inicio, int tamanho, Color corFundo, long inicioMs) {
            this.inicio = inicio;
            this.tamanho = tamanho;
            this.corFundo = corFundo;
            this.inicioMs = inicioMs;
        }
    }

    private long ultimaLinhaMs = 0L;
    private final List<SeloAnimado> selosAtivos = new ArrayList<>();

    private final JScrollPane rolagem;
    private int scrollAlvo = 0;
    private double scrollAtual = 0.0;
    private boolean rolandoSuave = false;

    private final JTextPane area = new JTextPane() {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getDocument().getLength() == 0) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(TemaCloudForge.mono(12f));
                g2.setColor(TemaCloudForge.MUTE);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("Nenhum evento registrado ainda.", 16, 16 + fm.getAscent());
                g2.dispose();
                return;
            }
            if (!Animador.isReduzirAnimacoes()) {
                long dt = System.currentTimeMillis() - ultimaLinhaMs;
                if (dt >= 0 && dt < DURACAO_LINHA_MS) {
                    // Véu sutil que esmaece na última linha para simular entrada suave
                    try {
                        Rectangle r = modelToView2D(getDocument().getLength()).getBounds();
                        float alpha = (float) ((1.0 - Animador.easeOut((double) dt / DURACAO_LINHA_MS)) * 0.55);
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                        g2.setColor(TemaCloudForge.S1);
                        g2.fillRect(0, Math.max(0, r.y - 2), getWidth(), r.height + 6);
                        g2.dispose();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    };

    private final StyledDocument doc;
    private final Style estiloOk;
    private final Style estiloSkip;
    private final Style estiloErro;
    private final Style estiloMudo;

    public PainelLog() {
        super(new BorderLayout());
        setBackground(TemaCloudForge.S1);
        setBorder(new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6));

        JPanel cabecalho = new JPanel(new BorderLayout(8, 0));
        cabecalho.setBackground(TemaCloudForge.S1);
        cabecalho.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, TemaCloudForge.DIVIDER),
                BorderFactory.createEmptyBorder(4, 16, 4, 12)));

        JLabel lblEsq = new JLabel("cliente");
        lblEsq.setFont(TemaCloudForge.sans(12.5f));
        lblEsq.setForeground(TemaCloudForge.MUTE);
        lblEsq.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        direita.setBackground(TemaCloudForge.S1);
        JLabel lblDir = new JLabel("deadline: health 2 s · tarefa 5 s");
        lblDir.setFont(TemaCloudForge.mono(11.5f));
        lblDir.setForeground(TemaCloudForge.MUTE);
        lblDir.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));

        BotaoAnimado btnLimpar = new BotaoAnimado("Limpar log");
        btnLimpar.setFont(TemaCloudForge.sans(12f));
        btnLimpar.setPreferredSize(new Dimension(94, 28));
        btnLimpar.addActionListener(e -> limpar());

        direita.add(lblDir);
        direita.add(btnLimpar);

        cabecalho.add(lblEsq, BorderLayout.WEST);
        cabecalho.add(direita, BorderLayout.EAST);
        add(cabecalho, BorderLayout.NORTH);

        area.setEditable(false);
        area.setBackground(TemaCloudForge.S1);
        area.setForeground(TemaCloudForge.FG);
        area.setFont(TemaCloudForge.mono(12f));
        area.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        doc = area.getStyledDocument();
        estiloOk = criarEstilo("ok", TemaCloudForge.FG);
        estiloSkip = criarEstilo("skip", TemaCloudForge.WARN);
        estiloErro = criarEstilo("erro", TemaCloudForge.BAD);
        estiloMudo = criarEstilo("mudo", TemaCloudForge.MUTE);

        rolagem = new JScrollPane(area);
        rolagem.setBorder(BorderFactory.createEmptyBorder());
        rolagem.getViewport().setBackground(TemaCloudForge.S1);
        rolagem.setBackground(TemaCloudForge.S1);
        rolagem.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(rolagem, BorderLayout.CENTER);

        // Altura preferida modesta para que o GridBagLayout não comprima Resposta ou Topologia
        setPreferredSize(new Dimension(320, 120));
        setMinimumSize(new Dimension(240, 88));

        Animador.registrar(this);
    }

    private Style criarEstilo(String nome, Color cor) {
        Style s = area.addStyle(nome, null);
        StyleConstants.setForeground(s, cor);
        StyleConstants.setFontFamily(s, TemaCloudForge.mono(12f).getFamily());
        StyleConstants.setFontSize(s, 12);
        return s;
    }

    public void registrarOk(String prefixo, String instancia, String sufixo) {
        registrarOk(prefixo, instancia, TemaCloudForge.corTextoInstancia(0), sufixo);
    }

    public void registrarOk(String prefixo, String instancia, Color corInstancia, String sufixo) {
        inserir(prefixo, estiloOk, false);
        int inicio = doc.getLength();
        int tam = instancia.length();
        Color corTexto = corInstancia == null ? TemaCloudForge.corTextoInstancia(0) : corInstancia;

        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, corTexto);
        StyleConstants.setBold(attrs, true);
        StyleConstants.setFontFamily(attrs, TemaCloudForge.mono(12f).getFamily());
        StyleConstants.setFontSize(attrs, 12);
        if (!Animador.isReduzirAnimacoes()) {
            StyleConstants.setBackground(attrs, TemaCloudForge.interpolarCor(TemaCloudForge.S1, corTexto, 0.12));
            selosAtivos.add(new SeloAnimado(inicio, tam, corTexto, System.currentTimeMillis()));
            Animador.acordar();
        }
        try {
            doc.insertString(doc.getLength(), instancia, attrs);
        } catch (BadLocationException ignored) {
        }
        inserir(sufixo, estiloOk, true);
    }

    public void registrarSkip(String mensagem) {
        inserir(mensagem, estiloSkip, true);
    }

    public void registrarErro(String mensagem) {
        inserir(mensagem, estiloErro, true);
    }

    public void registrarInfo(String mensagem) {
        inserir(mensagem, estiloMudo, true);
    }

    public void limpar() {
        try {
            doc.remove(0, doc.getLength());
            selosAtivos.clear();
            repaint();
        } catch (BadLocationException ignored) {
        }
    }

    private void inserir(String texto, Style estilo, boolean quebrarLinha) {
        try {
            doc.insertString(doc.getLength(), quebrarLinha ? texto + "\n" : texto, estilo);
            if (quebrarLinha) {
                ultimaLinhaMs = System.currentTimeMillis();
                agendarRolagemSuave();
            }
        } catch (BadLocationException ignored) {
        }
    }

    private void agendarRolagemSuave() {
        JScrollBar barra = rolagem.getVerticalScrollBar();
        if (Animador.isReduzirAnimacoes()) {
            barra.setValue(barra.getMaximum());
            return;
        }
        scrollAtual = barra.getValue();
        scrollAlvo = Math.max(0, barra.getMaximum() - barra.getVisibleAmount());
        rolandoSuave = true;
        Animador.acordar();
    }

    @Override
    public boolean atualizarAnimacao(long agora) {
        boolean ativo = false;

        if (!Animador.isReduzirAnimacoes() && agora - ultimaLinhaMs < DURACAO_LINHA_MS) {
            area.repaint();
            ativo = true;
        }

        // Esmaece individualmente o fundo de cada selo inserido no log
        if (!selosAtivos.isEmpty()) {
            Iterator<SeloAnimado> it = selosAtivos.iterator();
            while (it.hasNext()) {
                SeloAnimado s = it.next();
                if (s.inicio + s.tamanho > doc.getLength()) {
                    it.remove();
                    continue;
                }
                long dtSelo = agora - s.inicioMs;
                if (!Animador.isReduzirAnimacoes() && dtSelo >= 0 && dtSelo < DURACAO_SELO_MS) {
                    double f = 1.0 - Animador.easeOut((double) dtSelo / DURACAO_SELO_MS);
                    Color bg = TemaCloudForge.interpolarCor(TemaCloudForge.S1, s.corFundo, f * 0.12);
                    SimpleAttributeSet attrs = new SimpleAttributeSet();
                    StyleConstants.setBackground(attrs, bg);
                    doc.setCharacterAttributes(s.inicio, s.tamanho, attrs, false);
                    ativo = true;
                } else {
                    SimpleAttributeSet attrs = new SimpleAttributeSet();
                    StyleConstants.setBackground(attrs, TemaCloudForge.S1);
                    doc.setCharacterAttributes(s.inicio, s.tamanho, attrs, false);
                    it.remove();
                }
            }
        }

        if (rolandoSuave) {
            JScrollBar barra = rolagem.getVerticalScrollBar();
            scrollAlvo = Math.max(0, barra.getMaximum() - barra.getVisibleAmount());
            double diff = scrollAlvo - scrollAtual;
            if (Math.abs(diff) <= 1.0) {
                barra.setValue(scrollAlvo);
                rolandoSuave = false;
            } else {
                scrollAtual += diff * 0.3;
                barra.setValue((int) Math.round(scrollAtual));
                ativo = true;
            }
        }

        return ativo;
    }
}
