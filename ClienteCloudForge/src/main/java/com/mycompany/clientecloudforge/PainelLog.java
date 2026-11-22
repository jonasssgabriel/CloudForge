package com.mycompany.clientecloudforge;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Painel de log monoespaçado colorido com rolagem automática.
 */
public class PainelLog extends JPanel {
    private final JTextPane area = new JTextPane();
    private final StyledDocument doc;
    private final Style estiloOk;
    private final Style estiloDestaque;
    private final Style estiloSkip;
    private final Style estiloErro;
    private final Style estiloMudo;

    public PainelLog() {
        super(new BorderLayout());
        setBackground(TemaCloudForge.S1);
        setBorder(new TemaCloudForge.BordaPainel(TemaCloudForge.LINE, 6));

        JPanel cabecalho = new JPanel(new BorderLayout());
        cabecalho.setBackground(TemaCloudForge.S1);
        cabecalho.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, TemaCloudForge.LINE),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        JLabel lblEsq = new JLabel("cliente");
        lblEsq.setFont(TemaCloudForge.sans(13f));
        lblEsq.setForeground(TemaCloudForge.MUTE);

        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        direita.setBackground(TemaCloudForge.S1);
        JLabel lblDir = new JLabel("deadline: health 2 s · tarefa 5 s");
        lblDir.setFont(TemaCloudForge.mono(12f));
        lblDir.setForeground(TemaCloudForge.MUTE);

        JButton btnLimpar = new JButton("Limpar log");
        btnLimpar.setFont(TemaCloudForge.sans(12f));
        btnLimpar.setPreferredSize(new Dimension(92, 26));
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
        area.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        doc = area.getStyledDocument();
        estiloOk = criarEstilo("ok", TemaCloudForge.FG);
        estiloDestaque = criarEstilo("destaque", TemaCloudForge.ACCENT);
        estiloSkip = criarEstilo("skip", TemaCloudForge.WARN);
        estiloErro = criarEstilo("erro", TemaCloudForge.BAD);
        estiloMudo = criarEstilo("mudo", TemaCloudForge.MUTE);

        JScrollPane rolagem = new JScrollPane(area);
        rolagem.setBorder(BorderFactory.createEmptyBorder());
        rolagem.getViewport().setBackground(TemaCloudForge.S1);
        rolagem.setBackground(TemaCloudForge.S1);
        add(rolagem, BorderLayout.CENTER);
    }

    private Style criarEstilo(String nome, Color cor) {
        Style s = area.addStyle(nome, null);
        StyleConstants.setForeground(s, cor);
        StyleConstants.setFontFamily(s, TemaCloudForge.mono(12f).getFamily());
        StyleConstants.setFontSize(s, 12);
        return s;
    }

    public void registrarOk(String prefixo, String instancia, String sufixo) {
        inserir(prefixo, estiloOk, false);
        inserir(instancia, estiloDestaque, false);
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
        } catch (BadLocationException ignored) {
        }
    }

    private void inserir(String texto, Style estilo, boolean quebrarLinha) {
        try {
            doc.insertString(doc.getLength(), quebrarLinha ? texto + "\n" : texto, estilo);
            area.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
        }
    }
}
