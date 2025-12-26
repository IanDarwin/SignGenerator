package text3d;

import javax.swing.*;

public class SignGeneratorMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SignGenerator generator = new SignGenerator();
            generator.setVisible(true);
        });
    }
}
