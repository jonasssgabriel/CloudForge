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
    public static final Color FG = new Color(0x15, 0x15, 0x15);
    public static final Color MUTE = new Color(0x5C, 0x69, 0x62);
    public static final Color ACCENT = new Color(0x4F, 0x5F, 0xD6);
    public static final Color ON_ACCENT = new Color(0xFF, 0xFF, 0xFF);
    public static final Color GOOD = new Color(0x1F, 0x7A, 0x43);
    public static final Color WARN = new Color(0x9A, 0x62, 0x12);
    public static final Color BAD = new Color(0xB0, 0x26, 0x1E);

    public static final Color[] CORES_INSTANCIA = {
        new Color(0x4F, 0x5F, 0xD6),
        new Color(0x1B, 0x8A, 0xA8),
        new Color(0xB0, 0x7A, 0x1A)
    };

    private static Font baseSans;
    private static Font baseMono;
    private static Font baseSerif;

    private TemaCloudForge() {
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
        baseSans = new Font("Segoe UI", Font.PLAIN, 13);
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
        return baseSans.deriveFont(Font.PLAIN, tamanho);
    }

    public static Font sansMedium(float tamanho) {
        return baseSans.deriveFont(Font.BOLD, tamanho);
    }

    public static Font sansBold(float tamanho) {
        return baseSans.deriveFont(Font.BOLD, tamanho);
    }

    public static Font mono(float tamanho) {
        return baseMono.deriveFont(Font.PLAIN, tamanho);
    }

    public static Font monoBold(float tamanho) {
        return baseMono.deriveFont(Font.BOLD, tamanho);
    }

    public static Font serifItalic(float tamanho) {
        return baseSerif.deriveFont(Font.ITALIC, tamanho);
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
