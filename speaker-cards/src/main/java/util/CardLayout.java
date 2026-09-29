package util;

import java.awt.Font;
import java.awt.font.FontRenderContext;

import io.quarkus.qute.TemplateExtension;

/** Measures card headings using the same Poppins font as the PNG renderer. */
@TemplateExtension(namespace = "card")
public class CardLayout {
    private static final Font FONT = CardFonts.BOLD;
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);

    public static int fontSize(String text, int preferred, int minimum, int width, int maxLines) {
        if (text == null || text.isBlank()) {
            return preferred;
        }
        for (int size = preferred; size > minimum; size--) {
            if (fits(text, FONT.deriveFont((float) size), width, maxLines)) {
                return size;
            }
        }
        return minimum;
    }

    private static boolean fits(String text, Font font, int width, int maxLines) {
        String line = "";
        int lines = 1;
        for (String word : text.trim().split("\\s+")) {
            if (font.getStringBounds(word, CONTEXT).getWidth() > width) {
                return false;
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.getStringBounds(candidate, CONTEXT).getWidth() > width) {
                if (++lines > maxLines) {
                    return false;
                }
                line = word;
            } else {
                line = candidate;
            }
        }
        return true;
    }

}
