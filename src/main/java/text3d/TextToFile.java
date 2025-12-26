package text3d;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public interface TextToFile {
    void generateFile(String text, Font font, File file, OutputFormat format) throws IOException;
}
