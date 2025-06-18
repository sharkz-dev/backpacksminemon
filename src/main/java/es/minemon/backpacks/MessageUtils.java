package es.minemon.backpacks;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidad para procesar colores hexadecimales y sintaxis tipo MiniMessage
 * CORREGIDO: Elimina cursiva por defecto en TODOS los textos generados
 */
public class MessageUtils {

    // Patrones para diferentes tipos de formato
    private static final Pattern HEX_PATTERN = Pattern.compile("<#([0-9a-fA-F]{6})>");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:#([0-9a-fA-F]{6}):#([0-9a-fA-F]{6})>(.*?)</gradient>");
    private static final Pattern BOLD_PATTERN = Pattern.compile("<bold>(.*?)</bold>");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("<italic>(.*?)</italic>");
    private static final Pattern UNDERLINE_PATTERN = Pattern.compile("<underline>(.*?)</underline>");
    private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("<strikethrough>(.*?)</strikethrough>");
    private static final Pattern OBFUSCATED_PATTERN = Pattern.compile("<obfuscated>(.*?)</obfuscated>");
    private static final Pattern RESET_PATTERN = Pattern.compile("</>");

    /**
     * Convierte texto con formato MiniMessage a Text de Minecraft SIN CURSIVA
     */
    public static Text parseText(String input) {
        if (input == null || input.isEmpty()) {
            return Text.empty();
        }

        try {
            return parseComplexText(input, false);
        } catch (Exception e) {
            BackpacksMod.LOGGER.warn("Error parsing message: " + input, e);
            // Fallback a texto plano sin cursiva
            return Text.literal(input).setStyle(Style.EMPTY.withItalic(false));
        }
    }

    /**
     * Convierte texto para lore/tooltips sin cursiva por defecto
     */
    public static Text parseLoreText(String input) {
        if (input == null || input.isEmpty()) {
            return Text.empty();
        }

        try {
            return parseComplexText(input, true);
        } catch (Exception e) {
            BackpacksMod.LOGGER.warn("Error parsing lore message: " + input, e);
            // Fallback a texto plano sin cursiva
            return Text.literal(input).setStyle(Style.EMPTY.withItalic(false));
        }
    }

    /**
     * Procesa colores hexadecimales únicamente
     */
    public static String parseColors(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Procesar colores hexadecimales
        Matcher hexMatcher = HEX_PATTERN.matcher(input);
        while (hexMatcher.find()) {
            String colorCode = hexMatcher.group(1);
            // Convertir a formato legacy para compatibilidad
            String replacement = "§x§" + colorCode.charAt(0) + "§" + colorCode.charAt(1) + "§" +
                    colorCode.charAt(2) + "§" + colorCode.charAt(3) + "§" +
                    colorCode.charAt(4) + "§" + colorCode.charAt(5);
            input = input.replace(hexMatcher.group(0), replacement);
        }

        return input;
    }

    /**
     * Procesa texto complejo con gradientes y formatos
     */
    private static Text parseComplexText(String input, boolean forcedNoItalic) {
        // Procesar gradientes primero
        input = processGradients(input);

        // Luego procesar el resto de formatos con no-cursiva forzada
        return processFormats(input, true);
    }

    /**
     * Procesa gradientes de color
     */
    private static String processGradients(String input) {
        Matcher gradientMatcher = GRADIENT_PATTERN.matcher(input);
        while (gradientMatcher.find()) {
            String startColor = gradientMatcher.group(1);
            String endColor = gradientMatcher.group(2);
            String text = gradientMatcher.group(3);

            String gradientResult = createGradient(text, startColor, endColor);
            input = input.replace(gradientMatcher.group(0), gradientResult);
        }
        return input;
    }

    /**
     * Crea un gradiente entre dos colores
     */
    private static String createGradient(String text, String startHex, String endHex) {
        if (text.length() <= 1) {
            return "<#" + startHex + ">" + text + "</>";
        }

        StringBuilder result = new StringBuilder();
        int length = text.length();

        // Convertir colores hex a RGB
        int startR = Integer.parseInt(startHex.substring(0, 2), 16);
        int startG = Integer.parseInt(startHex.substring(2, 4), 16);
        int startB = Integer.parseInt(startHex.substring(4, 6), 16);

        int endR = Integer.parseInt(endHex.substring(0, 2), 16);
        int endG = Integer.parseInt(endHex.substring(2, 4), 16);
        int endB = Integer.parseInt(endHex.substring(4, 6), 16);

        for (int i = 0; i < length; i++) {
            // Calcular interpolación
            float ratio = (float) i / (length - 1);

            int r = (int) (startR + ratio * (endR - startR));
            int g = (int) (startG + ratio * (endG - startG));
            int b = (int) (startB + ratio * (endB - startB));

            // Asegurar que los valores estén en rango
            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            b = Math.max(0, Math.min(255, b));

            String color = String.format("%02X%02X%02X", r, g, b);
            result.append("<#").append(color).append(">").append(text.charAt(i)).append("</>");
        }

        return result.toString();
    }

    /**
     * CORREGIDO: Procesa formatos siempre sin cursiva por defecto
     */
    private static Text processFormats(String input, boolean forceNoItalic) {
        MutableText result = Text.empty();
        StringBuilder currentText = new StringBuilder();
        // SIEMPRE iniciar sin cursiva
        Style currentStyle = Style.EMPTY.withItalic(false);

        int i = 0;
        while (i < input.length()) {
            // Buscar inicio de tag
            if (input.charAt(i) == '<') {
                // Añadir texto acumulado
                if (currentText.length() > 0) {
                    MutableText textPart = Text.literal(currentText.toString()).setStyle(currentStyle);
                    result.append(textPart);
                    currentText.setLength(0);
                }

                // Procesar tag
                int tagEnd = input.indexOf('>', i);
                if (tagEnd != -1) {
                    String tag = input.substring(i, tagEnd + 1);
                    currentStyle = processTag(tag, currentStyle);
                    i = tagEnd + 1;
                } else {
                    currentText.append(input.charAt(i));
                    i++;
                }
            } else {
                currentText.append(input.charAt(i));
                i++;
            }
        }

        // Añadir texto restante
        if (currentText.length() > 0) {
            MutableText textPart = Text.literal(currentText.toString()).setStyle(currentStyle);
            result.append(textPart);
        }

        return result;
    }

    /**
     * CORREGIDO: Procesa un tag individual manteniendo siempre sin cursiva
     */
    private static Style processTag(String tag, Style currentStyle) {
        // Color hexadecimal
        Matcher hexMatcher = HEX_PATTERN.matcher(tag);
        if (hexMatcher.matches()) {
            String hexColor = hexMatcher.group(1);
            try {
                int color = Integer.parseInt(hexColor, 16);
                // SIEMPRE mantener sin cursiva
                return currentStyle.withColor(TextColor.fromRgb(color)).withItalic(false);
            } catch (NumberFormatException e) {
                return currentStyle.withItalic(false);
            }
        }

        // Formatos de texto - TODOS mantienen sin cursiva
        switch (tag.toLowerCase()) {
            case "<bold>":
                return currentStyle.withBold(true).withItalic(false);
            case "</bold>":
                return currentStyle.withBold(false).withItalic(false);
            case "<italic>":
                // Solo aquí permitir cursiva si se solicita explícitamente
                return currentStyle.withItalic(true);
            case "</italic>":
                return currentStyle.withItalic(false);
            case "<underline>":
                return currentStyle.withUnderline(true).withItalic(false);
            case "</underline>":
                return currentStyle.withUnderline(false).withItalic(false);
            case "<strikethrough>":
                return currentStyle.withStrikethrough(true).withItalic(false);
            case "</strikethrough>":
                return currentStyle.withStrikethrough(false).withItalic(false);
            case "<obfuscated>":
                return currentStyle.withObfuscated(true).withItalic(false);
            case "</obfuscated>":
                return currentStyle.withObfuscated(false).withItalic(false);
            case "</>":
                // Reset pero manteniendo sin cursiva
                return Style.EMPTY.withItalic(false);
            default:
                return currentStyle.withItalic(false);
        }
    }

    /**
     * Convierte códigos de color legacy (§) a hex
     */
    public static String legacyToHex(String input) {
        if (input == null) return null;

        return input
                .replace("§0", "<#000000>")
                .replace("§1", "<#0000AA>")
                .replace("§2", "<#00AA00>")
                .replace("§3", "<#00AAAA>")
                .replace("§4", "<#AA0000>")
                .replace("§5", "<#AA00AA>")
                .replace("§6", "<#FFAA00>")
                .replace("§7", "<#AAAAAA>")
                .replace("§8", "<#555555>")
                .replace("§9", "<#5555FF>")
                .replace("§a", "<#55FF55>")
                .replace("§b", "<#55FFFF>")
                .replace("§c", "<#FF5555>")
                .replace("§d", "<#FF55FF>")
                .replace("§e", "<#FFFF55>")
                .replace("§f", "<#FFFFFF>")
                .replace("§l", "<bold>")
                .replace("§o", "<italic>")
                .replace("§n", "<underline>")
                .replace("§m", "<strikethrough>")
                .replace("§k", "<obfuscated>")
                .replace("§r", "</>");
    }

    /**
     * Procesa texto con colores legacy y los convierte a hex SIN CURSIVA
     */
    public static Text parseLegacyText(String input) {
        if (input == null || input.isEmpty()) {
            return Text.empty();
        }

        // Convertir legacy a formato hex y luego procesar
        String converted = legacyToHex(input);
        return parseText(converted);
    }

    /**
     * Procesa texto legacy para lore sin cursiva
     */
    public static Text parseLegacyLoreText(String input) {
        if (input == null || input.isEmpty()) {
            return Text.empty();
        }

        String converted = legacyToHex(input);
        return parseLoreText(converted);
    }

    /**
     * Crea texto con color hexadecimal simple SIN CURSIVA
     */
    public static Text colored(String text, String hexColor) {
        try {
            int color = Integer.parseInt(hexColor.replace("#", ""), 16);
            return Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)).withItalic(false));
        } catch (NumberFormatException e) {
            return Text.literal(text).setStyle(Style.EMPTY.withItalic(false));
        }
    }

    /**
     * Crea texto con color para lore (sin cursiva)
     */
    public static Text coloredLore(String text, String hexColor) {
        return colored(text, hexColor); // Ahora es lo mismo
    }

    /**
     * Crea texto con gradiente SIN CURSIVA
     */
    public static Text gradient(String text, String startHex, String endHex) {
        String gradientText = "<gradient:#" + startHex.replace("#", "") + ":#" + endHex.replace("#", "") + ">" + text + "</gradient>";
        return parseText(gradientText);
    }

    /**
     * Crea texto con gradiente para lore (sin cursiva)
     */
    public static Text gradientLore(String text, String startHex, String endHex) {
        return gradient(text, startHex, endHex); // Ahora es lo mismo
    }

    /**
     * Aplica formato bold SIN CURSIVA
     */
    public static Text bold(String text) {
        return parseText("<bold>" + text + "</bold>");
    }

    /**
     * Aplica formato bold para lore (sin cursiva)
     */
    public static Text boldLore(String text) {
        return bold(text); // Ahora es lo mismo
    }

    /**
     * Aplica formato italic (único caso donde SÍ se permite cursiva)
     */
    public static Text italic(String text) {
        return parseText("<italic>" + text + "</italic>");
    }

    /**
     * Aplica formato underline SIN CURSIVA
     */
    public static Text underline(String text) {
        return parseText("<underline>" + text + "</underline>");
    }

    /**
     * Aplica formato underline para lore (sin cursiva)
     */
    public static Text underlineLore(String text) {
        return underline(text); // Ahora es lo mismo
    }

    /**
     * Combina múltiples textos con estilos
     */
    public static Text combine(Text... texts) {
        MutableText result = Text.empty();
        for (Text text : texts) {
            result.append(text);
        }
        return result;
    }

    /**
     * Crea texto de éxito (verde) SIN CURSIVA
     */
    public static Text success(String text) {
        return colored(text, "d8a2d8");
    }

    /**
     * Crea texto de éxito para lore (sin cursiva)
     */
    public static Text successLore(String text) {
        return success(text); // Ahora es lo mismo
    }

    /**
     * Crea texto de error (rojo-rosado) SIN CURSIVA
     */
    public static Text error(String text) {
        return colored(text, "e6a3e6");
    }

    /**
     * Crea texto de error para lore (sin cursiva)
     */
    public static Text errorLore(String text) {
        return error(text); // Ahora es lo mismo
    }

    /**
     * Crea texto de advertencia (amarillo pastel) SIN CURSIVA
     */
    public static Text warning(String text) {
        return colored(text, "f5d5a0");
    }

    /**
     * Crea texto de advertencia para lore (sin cursiva)
     */
    public static Text warningLore(String text) {
        return warning(text); // Ahora es lo mismo
    }

    /**
     * Crea texto de información (morado claro) SIN CURSIVA
     */
    public static Text info(String text) {
        return colored(text, "c8a8e9");
    }

    /**
     * Crea texto de información para lore (sin cursiva)
     */
    public static Text infoLore(String text) {
        return info(text); // Ahora es lo mismo
    }

    /**
     * Crea texto secundario (gris) SIN CURSIVA
     */
    public static Text secondary(String text) {
        return colored(text, "9a9a9a");
    }

    /**
     * Crea texto secundario para lore (sin cursiva)
     */
    public static Text secondaryLore(String text) {
        return secondary(text); // Ahora es lo mismo
    }

    /**
     * Procesa mensaje de configuración con placeholders SIN CURSIVA
     */
    public static Text parseConfigMessage(String key, Object... args) {
        String message = ConfigManager.getMessage(key, args);
        return parseText(message);
    }

    /**
     * Procesa mensaje de configuración para lore (sin cursiva)
     */
    public static Text parseConfigLoreMessage(String key, Object... args) {
        return parseConfigMessage(key, args); // Ahora es lo mismo
    }

    /**
     * Valida si un string contiene sintaxis de color válida
     */
    public static boolean isValidColorSyntax(String input) {
        if (input == null) return false;

        try {
            parseText(input);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Limpia todos los códigos de color de un texto
     */
    public static String stripColors(String input) {
        if (input == null) return null;

        return input
                .replaceAll("<#[0-9a-fA-F]{6}>", "")
                .replaceAll("<gradient:#[0-9a-fA-F]{6}:#[0-9a-fA-F]{6}>.*?</gradient>", "$1")
                .replaceAll("</?(?:bold|italic|underline|strikethrough|obfuscated)>", "")
                .replaceAll("</>", "")
                .replaceAll("§[0-9a-fklmnor]", "");
    }

    /**
     * Convierte Text de Minecraft a string plano
     */
    public static String toPlainString(Text text) {
        return text.getString();
    }
}