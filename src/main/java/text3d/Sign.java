package text3d;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.*;

/// A single Sign object, mainly for save/open
/// @author Ian Darwin
///
public record Sign(String text, String fontName, int fontSize, int fontStyle,
                   TextAlign alignment, double baseHeight, double baseMargin, double letterHeight, double bevelHeight){

    // Secondary constructor
    Sign(String text, Font font, TextAlign alignment, double baseHeight, double baseMargin, double letterHeight, double bevelHeight) {
        this(text, font.getFontName(), font.getSize(), font.getStyle(),
                alignment, baseHeight, baseMargin, letterHeight, bevelHeight);
    }

    String toJSON() {
            return String.format("""
                    {
                        "text": "%s",
                        "fontName": "%s",
                        "fontSize": %d,
                        "fontStyle": %d,
                        "alignment": "%s",
                        "baseHeight": %f, 
                        "baseMargin": %f, 
                        "letterHeight": %f, 
                        "bevelHeight": %f
                    }""", // Note no trailing "," on final element.
                    text.replaceAll("\n", "\\\\n"), fontName(), fontSize(), fontStyle(),
                        alignment, baseHeight, baseMargin, letterHeight, bevelHeight);
        }

        static Sign fromJSON(String jsonInput) {
            ObjectMapper mapper = new ObjectMapper();

            Sign ret = null;
            try {
                ret = mapper.readValue(jsonInput, Sign.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            return ret;
        }
    }

