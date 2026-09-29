package util;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/** Makes the bundled typefaces available to Renarde's Java2D PNG font resolver. */
@Startup
@ApplicationScoped
public class CardFonts {
    static final Font REGULAR = load("Regular");
    static final Font SEMIBOLD = load("SemiBold");
    static final Font BOLD = load("Bold");

    @PostConstruct
    void register() {
        // Renarde replaces the resolver that imported CSS @font-face rules.
        // Its replacement uses environment fonts, so register ours before rendering.
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        environment.registerFont(REGULAR);
        environment.registerFont(SEMIBOLD);
        environment.registerFont(BOLD);
    }

    private static Font load(String weight) {
        String path = "/META-INF/resources/static/fonts/Poppins-" + weight + ".ttf";
        try (InputStream stream = CardFonts.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing card font: " + path);
            }
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load card font: " + path, e);
        }
    }
}
