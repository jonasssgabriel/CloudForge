package com.mycompany.clientecloudforge;

import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.Timer;

/**
 * Ponto de entrada da GUI do ClienteCloudForge.
 */
public class ClienteGUI {

    public static void main(String[] args) {
        String enderecoNomes = "localhost:50050";
        String caminhoScreenshot = null;

        for (int i = 0; i < args.length; i++) {
            if ("--screenshot".equals(args[i]) && i + 1 < args.length) {
                caminhoScreenshot = args[++i];
            } else if (!args[i].startsWith("--") && "localhost:50050".equals(enderecoNomes)) {
                enderecoNomes = args[i];
            }
        }

        final String alvoNomes = enderecoNomes;
        final String arquivoPng = caminhoScreenshot;

        TemaCloudForge.aplicar();
        EventQueue.invokeLater(() -> {
            TelaCloudForge tela = new TelaCloudForge(alvoNomes);
            tela.setVisible(true);

            if (arquivoPng != null && !arquivoPng.isBlank()) {
                Timer disparo = new Timer(3000, e -> tela.clicarRr6());
                disparo.setRepeats(false);
                disparo.start();
                Timer timer = new Timer(5500, e -> {
                    try {
                        BufferedImage img = new BufferedImage(
                                Math.max(1, tela.getWidth()),
                                Math.max(1, tela.getHeight()),
                                BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2 = img.createGraphics();
                        tela.paint(g2);
                        g2.dispose();
                        File destino = new File(arquivoPng);
                        if (destino.getParentFile() != null) {
                            destino.getParentFile().mkdirs();
                        }
                        ImageIO.write(img, "png", destino);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        tela.dispose();
                        System.exit(0);
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        });
    }
}
