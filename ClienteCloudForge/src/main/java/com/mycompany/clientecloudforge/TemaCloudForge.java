package com.mycompany.clientecloudforge;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.InputStream;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;

/**
 * Paleta clara, fontes e bordas do CloudForge.
 */
public final class TemaCloudForge {
    public static final Color S0 = new Color(0xF4, 0xF4, 0xF2);
    public static final Color S1 = new Color(0xFC, 0xFC, 0xFB);
    public static final Color S2 = new Color(0xEC, 0xED, 0xEB);
    public static final Color S3 = new Color(0xE2, 0xE4, 0xE3);
    public static final Color LINE = new Color(0xD2, 0xD5, 0xD3);
    public static final Color DIVIDER = new Color(0x15, 0x15, 0x15, 18); // 7% de opacidade
    public static final Color FG = new Color(0x15, 0x15, 0x15);
    public static final Color MUTE = new Color(0x5C, 0x69, 0x62);
    public static final Color ACCENT = new Color(0x4F, 0x5F, 0xD6);
    public static final Color ON_ACCENT = new Color(0xFF, 0xFF, 0xFF);
    public static final Color GOOD = new Color(0x1F, 0x7A, 0x43);
    public static final Color WARN = new Color(0x9A, 0x62, 0x12);
    public static final Color BAD = new Color(0xB0, 0x26, 0x1E);

    // Cores das instâncias (ajustadas para contraste WCAG AA >= 4,5:1 sobre claro e com texto branco)
    public static final Color[] CORES_INSTANCIA = {
        new Color(0x4F, 0x5F, 0xD6), // índigo (acento principal)
        new Color(0x12, 0x6C, 0x85), // ciano petróleo (AA: 5,83:1 em S1, 5,98:1 com branco)
        new Color(0x8A, 0x5B, 0x0E)  // âmbar escuro (AA: 5,71:1 em S1, 5,86:1 com branco)
    };

    // Versão mais profunda para textos e rótulos sobre fundos levemente tingidos (log e pacotes)
    public static final Color[] CORES_INSTANCIA_TEXTO = {
        new Color(0x3B, 0x49, 0xB8), // índigo escuro (AA: 5,99:1 mesmo com fundo tingido)
        new Color(0x0F, 0x64, 0x7B), // ciano escuro (AA: 5,10:1 mesmo com fundo tingido)
        new Color(0x7D, 0x52, 0x0B)  // âmbar profundo (AA: 5,60:1 mesmo com fundo tingido)
    };

    public static Color corInstancia(int indice) {
        int i = Math.floorMod(indice, CORES_INSTANCIA.length);
        return CORES_INSTANCIA[i];
    }

    public static Color corTextoInstancia(int indice) {
        int i = Math.floorMod(indice, CORES_INSTANCIA_TEXTO.length);
        return CORES_INSTANCIA_TEXTO[i];
    }

    private static Font baseSans;
    private static Font baseMono;
    private static Font baseSerif;
    private static volatile double fatorEscala = 1.0;

    private TemaCloudForge() {
    }

    /** Retorna o fator de escala atual da interface (1.0 normal, ~1.3 em apresentação). */
    public static double getFatorEscala() {
        return fatorEscala;
    }

    /** Indica se a interface está ampliada para projeção (modo apresentação). */
    public static boolean isModoApresentacao() {
        return fatorEscala > 1.05;
    }

    /** Define o fator de escala global usado pelas fontes e dimensões da interface. */
    public static void setFatorEscala(double fator) {
        fatorEscala = Math.max(1.0, Math.min(2.0, fator));
    }

    /** Converte um valor em pixels aplicando o fator de escala atual do tema. */
    public static int escala(int px) {
        return (int) Math.round(px * fatorEscala);
    }

    /** Converte um tamanho de fonte aplicando o fator de escala atual do tema. */
    public static float escala(float px) {
        return (float) (px * fatorEscala);
    }

    public static void aplicar() {
        carregarFontes();
        FlatLightLaf.setup();

        UIManager.put("defaultFont", sans(13f));
        UIManager.put("RootPane.background", S0);
        UIManager.put("Panel.background", S0);
        UIManager.put("Label.foreground", FG);

        UIManager.put("Component.arc", 6);
        UIManager.put("Button.arc", 6);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Component.borderColor", LINE);
        UIManager.put("Component.focusedBorderColor", ACCENT);

        UIManager.put("Button.background", S2);
        UIManager.put("Button.foreground", FG);
        UIManager.put("Button.hoverBackground", S3);
        UIManager.put("Button.pressedBackground", S3);
        UIManager.put("Button.borderColor", LINE);
        UIManager.put("Button.hoverBorderColor", MUTE);

        UIManager.put("TextField.background", S1);
        UIManager.put("TextField.foreground", FG);
        UIManager.put("TextField.caretForeground", FG);
        UIManager.put("ComboBox.background", S1);
        UIManager.put("ComboBox.foreground", FG);
        UIManager.put("ComboBox.buttonBackground", S1);
        UIManager.put("ComboBox.popupBackground", S1);
        UIManager.put("ComboBox.selectionBackground", S2);
        UIManager.put("ComboBox.selectionForeground", FG);

        UIManager.put("Table.background", S1);
        UIManager.put("Table.foreground", FG);
        UIManager.put("Table.selectionBackground", S2);
        UIManager.put("Table.selectionForeground", FG);
        UIManager.put("Table.gridColor", S3);
        UIManager.put("TableHeader.background", S1);
        UIManager.put("TableHeader.foreground", MUTE);
        UIManager.put("TableHeader.bottomSeparatorColor", LINE);

        UIManager.put("ScrollPane.background", S1);
        UIManager.put("ScrollBar.track", S1);
        UIManager.put("ScrollBar.thumb", S3);
        UIManager.put("ScrollBar.hoverThumbColor", MUTE);
        UIManager.put("ScrollBar.width", 8);
    }

    private static void carregarFontes() {
        baseSans = carregarRecurso("/fontes/Inter-Regular.ttf", new Font("Segoe UI", Font.PLAIN, 13));
        baseMono = carregarRecurso("/fontes/AtkinsonHyperlegibleMono-Regular.ttf", new Font("Consolas", Font.PLAIN, 13));
        baseSerif = carregarRecurso("/fontes/InstrumentSerif-Italic.ttf", new Font("Georgia", Font.ITALIC, 28));
    }

    private static Font carregarRecurso(String caminho, Font fallback) {
        try (InputStream is = TemaCloudForge.class.getResourceAsStream(caminho)) {
            if (is == null) return fallback;
            return Font.createFont(Font.TRUETYPE_FONT, is);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static Font sans(float tamanho) {
        return baseSans.deriveFont(Font.PLAIN, escala(tamanho));
    }

    public static Font sansMedium(float tamanho) {
        return baseSans.deriveFont(Font.BOLD, escala(tamanho));
    }

    public static Font sansBold(float tamanho) {
        return baseSans.deriveFont(Font.BOLD, escala(tamanho));
    }

    public static Font mono(float tamanho) {
        return baseMono.deriveFont(Font.PLAIN, escala(tamanho));
    }

    public static Font monoBold(float tamanho) {
        return baseMono.deriveFont(Font.BOLD, escala(tamanho));
    }

    public static Font serifItalic(float tamanho) {
        return baseSerif.deriveFont(Font.ITALIC, escala(tamanho));
    }

    /** Fontes sem multiplicação extra, para elementos já dimensionados em tela cheia (ex.: barra de legenda). */
    public static Font sansFixa(float tamanho) {
        return baseSans.deriveFont(Font.PLAIN, tamanho);
    }

    public static Font sansBoldFixa(float tamanho) {
        return baseSans.deriveFont(Font.BOLD, tamanho);
    }

    public static Font monoFixa(float tamanho) {
        return baseMono.deriveFont(Font.PLAIN, tamanho);
    }

    public static Font monoBoldFixa(float tamanho) {
        return baseMono.deriveFont(Font.BOLD, tamanho);
    }

    public static Font serifItalicFixa(float tamanho) {
        return baseSerif.deriveFont(Font.ITALIC, tamanho);
    }

    /** Curva ease-out cúbica para transições suaves. */
    public static double easeOut(double t) {
        double k = Math.max(0.0, Math.min(1.0, t));
        return 1.0 - Math.pow(1.0 - k, 3);
    }

    /** Interpola suavemente entre duas cores para crossfade. */
    public static Color interpolarCor(Color a, Color b, double t) {
        double k = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k);
        return new Color(r, g, bl);
    }

    /** Borda de 1px com cantos de 6px sem sombra. */
    public static final class BordaPainel extends AbstractBorder {
        private final Color cor;
        private final int raio;

        public BordaPainel(Color cor, int raio) {
            this.cor = cor;
            this.raio = raio;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(cor);
            g2.drawRoundRect(x, y, width - 1, height - 1, raio, raio);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(1, 1, 1, 1);
            return insets;
        }
    }
}
