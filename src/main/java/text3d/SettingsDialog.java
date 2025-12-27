package text3d;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

import static text3d.SignGenerator.*;

public class SettingsDialog extends JDialog {

    private final Preferences prefs = Preferences.userNodeForPackage(SettingsDialog.class);

    public SettingsDialog(Frame owner) {
        super(owner, "Settings", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Load preferences
        String renderer = prefs.get(PREF_RENDERER, "C");
        int fontSize = prefs.getInt(PREF_FONT_SIZE, 36);
        double baseHeight = prefs.getDouble(PREF_BASE_HEIGHT, BASE_HEIGHT);
        double baseMargin = prefs.getDouble(PREF_BASE_MARGIN, BASE_MARGIN);
        double letterHeight = prefs.getDouble(PREF_LETTER_HEIGHT, LETTER_HEIGHT);
        double bevelDepth = prefs.getDouble(PREF_BEVEL_HEIGHT, BEVEL_HEIGHT);

        // Components
        JRadioButton rendererClaude = new JRadioButton("Claude Renderer");
        JRadioButton rendererGemini = new JRadioButton("Gemini Renderer");

        ButtonGroup rendererGroup = new ButtonGroup();
        rendererGroup.add(rendererClaude);
        rendererGroup.add(rendererGemini);

        if ("C".equals(renderer)) {
            rendererClaude.setSelected(true);
        } else {
            rendererGemini.setSelected(true);
        }

        JSpinner fontSizeSpinner = new JSpinner(
                new SpinnerNumberModel(fontSize, 1, 200, 1)
        );

        JSpinner baseHeightSpinner = new JSpinner(
                new SpinnerNumberModel(baseHeight, 1.0, 10, 0.5)
        );

        JSpinner baseMarginSpinner = new JSpinner(
                new SpinnerNumberModel(baseMargin, 1.0, 15.0, 0.5)
        );

        JSpinner letterHeightSpinner = new JSpinner(
                new SpinnerNumberModel(letterHeight, 1.0, 100, 1)
        );

        JSpinner bevelHeightSpinner = new JSpinner(
                new SpinnerNumberModel(bevelDepth, 0.1, 5.0, 0.1)
        );

        JButton doneButton = new JButton("Done");

        // Layout
        JPanel content = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Renderer label
        gbc.gridx = 0;
        gbc.gridy = 0;
        content.add(new JLabel("Renderer:"), gbc);

        // Renderer options
        gbc.gridx = 1;
        content.add(rendererClaude, gbc);

        gbc.gridy++;
        content.add(rendererGemini, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        content.add(new JLabel("Font Size:"), gbc);
        gbc.gridx = 1;
        content.add(fontSizeSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        content.add(new JLabel("Base height (mm):"), gbc);
        gbc.gridx = 1;
        content.add(baseHeightSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        content.add(new JLabel("Base Margin (mm):"), gbc);
        gbc.gridx = 1;
        content.add(baseMarginSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        content.add(new JLabel("Letter height (mm):"), gbc);
        gbc.gridx = 1;
        content.add(letterHeightSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        content.add(new JLabel("Bevel height (mm):"), gbc);
        gbc.gridx = 1;
        content.add(bevelHeightSpinner, gbc);

        // Done button
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        content.add(doneButton, gbc);

        setContentPane(content);

        // Save & close
        doneButton.addActionListener(e -> {
            prefs.put(PREF_RENDERER, rendererGemini.isSelected() ? "G" : "C");
            prefs.putInt(PREF_FONT_SIZE, (Integer) fontSizeSpinner.getValue());
            prefs.putDouble(PREF_BASE_HEIGHT, (Double) baseHeightSpinner.getValue());
            prefs.putDouble(PREF_BASE_MARGIN, (Double) baseMarginSpinner.getValue());
            prefs.putDouble(PREF_LETTER_HEIGHT, (Double) letterHeightSpinner.getValue());
            prefs.putDouble(PREF_BEVEL_HEIGHT, (Double) bevelHeightSpinner.getValue());
            dispose();
        });

        pack();
        setLocationRelativeTo(owner);
    }

    // Example usage
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("App");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(300, 200);

            JButton settingsButton = new JButton("Settings");
            settingsButton.addActionListener(e ->
                    new SettingsDialog(frame).setVisible(true)
            );

            frame.add(settingsButton);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
