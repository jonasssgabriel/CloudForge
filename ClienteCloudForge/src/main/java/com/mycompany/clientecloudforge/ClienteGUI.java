package com.mycompany.clientecloudforge;

import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Ponto de entrada da GUI do ClienteCloudForge.
 */
public class ClienteGUI {

    public static void main(String[] args) {
        String enderecoNomes = "localhost:50050";
        String caminhoScreenshot = null;
        String cenario = "rr";
        boolean modoApresentacao = false;
        int larguraJanela = -1;
        int alturaJanela = -1;

        for (int i = 0; i < args.length; i++) {
            if ("--screenshot".equals(args[i]) && i + 1 < args.length) {
                caminhoScreenshot = args[++i];
            } else if ("--apresentacao".equals(args[i])) {
                modoApresentacao = true;
            } else if (args[i].startsWith("--cenario=")) {
                cenario = args[i].substring("--cenario=".length());
            } else if ("--cenario".equals(args[i]) && i + 1 < args.length) {
                cenario = args[++i];
            } else if (args[i].startsWith("--tamanho=")) {
                int[] dim = parsearTamanho(args[i].substring("--tamanho=".length()));
                if (dim != null) {
                    larguraJanela = dim[0];
                    alturaJanela = dim[1];
                }
            } else if ("--tamanho".equals(args[i]) && i + 1 < args.length) {
                int[] dim = parsearTamanho(args[++i]);
                if (dim != null) {
                    larguraJanela = dim[0];
                    alturaJanela = dim[1];
                }
            } else if (!args[i].startsWith("--") && "localhost:50050".equals(enderecoNomes)) {
                enderecoNomes = args[i];
            }
        }

        final String alvoNomes = enderecoNomes;
        final String arquivoPng = caminhoScreenshot;
        final String modoCenario = cenario;
        final boolean iniciarApresentacao = modoApresentacao;
        final int wJanela = larguraJanela;
        final int hJanela = alturaJanela;

        TemaCloudForge.aplicar();
        EventQueue.invokeLater(() -> {
            TelaCloudForge tela = new TelaCloudForge(alvoNomes);
            boolean tamanhoExplicito = wJanela > 0 && hJanela > 0;
            if (tamanhoExplicito) {
                tela.setSize(wJanela, hJanela);
                tela.setLocationRelativeTo(null);
            }
            tela.setVisible(true);

            if (iniciarApresentacao && arquivoPng != null && !arquivoPng.isBlank()) {
                agendarCapturaApresentacao(tela, arquivoPng, tamanhoExplicito);
            } else if (iniciarApresentacao) {
                Animador.agendar(800, tela::iniciarModoApresentacao);
            } else if (arquivoPng != null && !arquivoPng.isBlank()) {
                if ("anim".equalsIgnoreCase(modoCenario)) {
                    agendarSequenciaAnimacao(tela, arquivoPng);
                } else {
                    agendarCapturaUnica(tela, arquivoPng, modoCenario);
                }
            }
        });
    }

    /** Aceita formato LxA (ex.: 1100x760 ou 1000x700) para testes de redimensionamento. */
    private static int[] parsearTamanho(String texto) {
        if (texto == null) return null;
        String[] partes = texto.toLowerCase().split("x");
        if (partes.length != 2) return null;
        try {
            int w = Integer.parseInt(partes[0].trim());
            int h = Integer.parseInt(partes[1].trim());
            return (w >= 600 && h >= 500) ? new int[]{w, h} : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Executa a apresentação real e salva 3 momentos (etapas 2, 5 e 8) como -1.png, -2.png e -3.png. */
    private static void agendarCapturaApresentacao(TelaCloudForge tela, String basePng, boolean semMaximizar) {
        String semExt = basePng.endsWith(".png") ? basePng.substring(0, basePng.length() - 4) : basePng;
        Animador.agendar(1500, () -> tela.iniciarModoApresentacao(semMaximizar, true, etapa -> {
            if (etapa == 1) {
                Animador.agendar(420, () -> salvarJanela(tela, new File(semExt + "-1.png")));
            } else if (etapa == 4) {
                Animador.agendar(700, () -> salvarJanela(tela, new File(semExt + "-2.png")));
            } else if (etapa == 7) {
                Animador.agendar(450, () -> {
                    salvarJanela(tela, new File(semExt + "-3.png"));
                    tela.dispose();
                    System.exit(0);
                });
            }
        }));
    }

    private static void agendarCapturaUnica(TelaCloudForge tela, String arquivoPng, String modoCenario) {
        Animador.agendar(2500, () -> {
            if ("conc".equalsIgnoreCase(modoCenario)) tela.clicarConcorrencia(50);
            else tela.clicarRr6();
        });
        int esperaCaptura = "conc".equalsIgnoreCase(modoCenario) ? 10000 : 7500;
        Animador.agendar(esperaCaptura, () -> {
            salvarJanela(tela, new File(arquivoPng));
            tela.dispose();
            System.exit(0);
        });
    }

    private static void agendarSequenciaAnimacao(TelaCloudForge tela, String basePng) {
        Animador.agendar(2500, tela::clicarRr6);
        int[] offsetsMs = {2600, 2700, 2800, 2900};
        String semExt = basePng.endsWith(".png") ? basePng.substring(0, basePng.length() - 4) : basePng;
        for (int i = 0; i < offsetsMs.length; i++) {
            final int frame = i + 1;
            final boolean ultimo = i == offsetsMs.length - 1;
            Animador.agendar(offsetsMs[i], () -> {
                salvarJanela(tela, new File(semExt + "-" + frame + ".png"));
                if (ultimo) { tela.dispose(); System.exit(0); }
            });
        }
    }

    private static void salvarJanela(TelaCloudForge tela, File destino) {
        try {
            BufferedImage img = new BufferedImage(
                    Math.max(1, tela.getWidth()),
                    Math.max(1, tela.getHeight()),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            tela.paint(g2);
            g2.dispose();
            if (destino.getParentFile() != null) {
                destino.getParentFile().mkdirs();
            }
            ImageIO.write(img, "png", destino);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
