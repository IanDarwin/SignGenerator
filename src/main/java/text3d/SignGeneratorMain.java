package text3d;

import javax.swing.*;

/// Simple main program for Sign/Stamp Generator
public class SignGeneratorMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SignGenerator generator = new SignGenerator();
			if (args.length > 0) {
                generator.textArea().setText("");
				for (String a : args) {
					generator.textArea().append(a + "\n");
				}
			}
            generator.setVisible(true);
        });
    }
}
