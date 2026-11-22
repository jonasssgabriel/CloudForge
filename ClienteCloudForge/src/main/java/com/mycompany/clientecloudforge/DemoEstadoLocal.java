package com.mycompany.clientecloudforge;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Demonstração independente de PainelEstadoLocal.
 * Sem argumentos: abre janela interativa para testar animações.
 * Com "--exportar <pasta>": gera estado-local-1.png (vazio) e estado-local-2.png (#1/#1 e depois #7/#4).
 */
public final class DemoEstadoLocal {
    private DemoEstadoLocal() {
    }

    public static void main(String[] args) throws Exception {
        TemaCloudForge.aplicar();

        if (args.length >= 2 && "--exportar".equals(args[0])) {
            exportarImagens(new File(args[1]));
            return;
        }

        SwingUtilities.invokeLater(DemoEstadoLocal::abrirJanela);
    }

    private static void exportarImagens(File pastaDestino) throws Exception {
        if (!pastaDestino.exists() && !pastaDestino.mkdirs()) {
            throw new IllegalStateException("Não foi possível criar a pasta: " + pastaDestino);
        }

        // Desativa interpolação temporal para capturar quadros finais nítidos no PNG.
        Animador.setReduzirAnimacoes(true);

        // Imagem 1: estado vazio (420 x 160 px)
        PainelEstadoLocal painelVazio = new PainelEstadoLocal();
        painelVazio.limpar();
        BufferedImage img1 = renderizarCartaoUnico(painelVazio, 420, 160);
        ImageIO.write(img1, "png", new File(pastaDestino, "estado-local-1.png"));

        // Imagem 2: 2 instâncias em #1/#1 (360 px, global desligado) e depois #7/#4 (440 px, global 11)
        PainelEstadoLocal painelMesmoId = new PainelEstadoLocal();
        painelMesmoId.atualizarInstancia("srv-50051", 0, 1, 1, 1, 0);
        painelMesmoId.atualizarInstancia("srv-50052", 1, 1, 1, 1, 0);
        painelMesmoId.definirTotalGlobal(null);

        PainelEstadoLocal painelEvoluido = new PainelEstadoLocal();
        painelEvoluido.atualizarInstancia("srv-50051", 0, 1, 1, 1, 0);
        painelEvoluido.atualizarInstancia("srv-50052", 1, 1, 1, 1, 0);
        painelEvoluido.atualizarInstancia("srv-50051", 0, 7, 8, 7, 1);
        painelEvoluido.atualizarInstancia("srv-50052", 1, 4, 5, 4, 1);
        painelEvoluido.definirTotalGlobal(11L);

        BufferedImage img2 = renderizarComparacao(painelMesmoId, 360, 160, painelEvoluido, 440, 160);
        ImageIO.write(img2, "png", new File(pastaDestino, "estado-local-2.png"));

        Animador.pararTudo();
        System.out.println("PNGs gerados em: " + pastaDestino.getAbsolutePath());
    }

    private static BufferedImage renderizarCartaoUnico(PainelEstadoLocal painel, int largura, int altura) {
        int margem = 16;
        JPanel cena = new JPanel(new BorderLayout());
        cena.setBackground(TemaCloudForge.S0);
        cena.setBorder(BorderFactory.createEmptyBorder(margem, margem, margem, margem));
        painel.setPreferredSize(new Dimension(largura, altura));
        cena.add(painel, BorderLayout.CENTER);
        cena.setSize(largura + margem * 2, altura + margem * 2);
        cena.doLayout();
        painel.doLayout();
        return pintarImagem(cena);
    }

    private static BufferedImage renderizarComparacao(PainelEstadoLocal topo, int wTopo, int hTopo,
            PainelEstadoLocal base, int wBase, int hBase) {
        int margem = 16;
        JPanel cena = new JPanel(new GridBagLayout());
        cena.setBackground(TemaCloudForge.S0);
        cena.setBorder(BorderFactory.createEmptyBorder(margem, margem, margem, margem));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel rotulo1 = new JLabel("Antes (360 px): duas instâncias com #1 ao mesmo tempo");
        rotulo1.setFont(TemaCloudForge.mono(11f));
        rotulo1.setForeground(TemaCloudForge.MUTE);
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 6, 0);
        cena.add(rotulo1, gbc);

        topo.setPreferredSize(new Dimension(wTopo, hTopo));
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 14, 0);
        cena.add(topo, gbc);

        JLabel rotulo2 = new JLabel("Depois (440 px): #7 e #4 independentes + histórico global (11)");
        rotulo2.setFont(TemaCloudForge.mono(11f));
        rotulo2.setForeground(TemaCloudForge.MUTE);
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 6, 0);
        cena.add(rotulo2, gbc);

        base.setPreferredSize(new Dimension(wBase, hBase));
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 0, 0);
        cena.add(base, gbc);

        Dimension pref = cena.getPreferredSize();
        cena.setSize(pref);
        cena.doLayout();
        topo.doLayout();
        base.doLayout();
        return pintarImagem(cena);
    }

    private static BufferedImage pintarImagem(JPanel raiz) {
        BufferedImage img = new BufferedImage(raiz.getWidth(), raiz.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        raiz.printAll(g2);
        g2.dispose();
        return img;
    }

    private static void abrirJanela() {
        JFrame janela = new JFrame("DemoEstadoLocal");
        janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel raiz = new JPanel(new BorderLayout(0, 10));
        raiz.setBackground(TemaCloudForge.S0);
        raiz.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        PainelEstadoLocal painel = new PainelEstadoLocal();
        painel.setPreferredSize(new Dimension(420, 160));
        raiz.add(painel, BorderLayout.CENTER);

        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controles.setBackground(TemaCloudForge.S0);

        JButton btnVazio = new JButton("Vazio");
        btnVazio.addActionListener(e -> painel.limpar());

        JButton btnUm = new JButton("#1 / #1");
        btnUm.addActionListener(e -> {
            painel.atualizarInstancia("srv-50051", 0, 1, 1, 1, 0);
            painel.atualizarInstancia("srv-50052", 1, 1, 1, 1, 0);
            painel.definirTotalGlobal(null);
        });

        JButton btnSeteQuatro = new JButton("#7 / #4 + global");
        btnSeteQuatro.addActionListener(e -> {
            painel.atualizarInstancia("srv-50051", 0, 7, 8, 7, 1);
            painel.atualizarInstancia("srv-50052", 1, 4, 5, 4, 1);
            painel.definirTotalGlobal(11L);
        });

        controles.add(btnVazio);
        controles.add(btnUm);
        controles.add(btnSeteQuatro);
        raiz.add(controles, BorderLayout.SOUTH);

        janela.setContentPane(raiz);
        janela.pack();
        janela.setLocationRelativeTo(null);
        janela.setVisible(true);
    }
}
