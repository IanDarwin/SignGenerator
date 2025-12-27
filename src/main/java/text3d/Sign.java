package text3d;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.*;

/// A single Sign object, mainly for save/open
/// @author Ian Darwin
///
public record Sign(String text, String fontName, int fontSize, int fontStyle){

    // Secondary constructor
    Sign(String text, Font font) {
        this(text, font.getFontName(), font.getSize(), font.getStyle());
    }

    String toJSON() {
            return String.format("""
                    {
                        "text": "%s",
                        "fontName": "%s",
                        "fontSize": %d,
                        "fontStyle": %d
                    }""", // Note no trailing "," on fontStyle or any final element.
                    text.replaceAll("\n", "\\\\n"), fontName(), fontSize(), fontStyle());
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

