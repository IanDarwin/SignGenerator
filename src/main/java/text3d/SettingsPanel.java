package text3d;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

import static text3d.SignGenerator.*;

/// Settings dialog for the Sign Generator.
///
public class SettingsPanel extends JPanel {

    private final Preferences prefs = Preferences.userNodeForPackage(SettingsPanel.class);
    private final SignGenerator main;

    public SettingsPanel(JFrame parent) {
        main = (SignGenerator)parent;
		var content = this;

        // Load preferences, act on some here, others later.
        String renderer = prefs.get(PREF_RENDERER, "C");
        main.setRenderer(switch(renderer) {
            case "C" -> new ClaudeTextToFile();
            case "G" -> new GeminiTextToFile();
            default -> throw new IllegalStateException("Unexpected value: " + renderer);
        });
        int fontSize = prefs.getInt(PREF_FONT_SIZE, 36);
        double baseHeight = prefs.getDouble(PREF_BASE_HEIGHT, DEFAULT_BASE_HEIGHT);
        double baseMargin = prefs.getDouble(PREF_BASE_MARGIN, DEFAULT_BASE_MARGIN);
        double letterHeight = prefs.getDouble(PREF_LETTER_HEIGHT, DEFAULT_LETTER_HEIGHT);
        double bevelDepth = prefs.getDouble(PREF_BEVEL_HEIGHT, DEFAULT_BEVEL_HEIGHT);

        // GUI Components

        // Choice of Renderer

        ButtonGroup rendererGroup = new ButtonGroup();
        JRadioButton rendererClaude = new JRadioButton("Claude Renderer");
        rendererClaude.addActionListener(e->main.setRenderer(new ClaudeTextToFile()));
        rendererGroup.add(rendererClaude);

        JRadioButton rendererGemini = new JRadioButton("Gemini Renderer");
        rendererGemini.addActionListener(e->main.setRenderer(new GeminiTextToFile()));
        rendererGroup.add(rendererGemini);

        if ("C".equals(renderer)) {
            rendererClaude.setSelected(true);
        } else {
            rendererGemini.setSelected(true);
        }

        // Choice of alignment
        ButtonGroup alignmentGroup = new ButtonGroup();
        JRadioButton alignmentLeft = new JRadioButton("Left");
        alignmentLeft.addActionListener(e->main.setAlignment(TextAlign.LEFT));
        alignmentGroup.add(alignmentLeft);
        JRadioButton alignmentCenter = new JRadioButton("Center");
        alignmentCenter.addActionListener(e->main.setAlignment(TextAlign.CENTER));
        alignmentGroup.add(alignmentCenter);
        JRadioButton alignmentRight = new JRadioButton("Right");
        alignmentRight.addActionListener(e->main.setAlignment(TextAlign.RIGHT));
        alignmentGroup.add(alignmentRight);

        JSpinner fontSizeSpinner = new JSpinner(
                new SpinnerNumberModel(fontSize, 1, 200, 1)
        );

        JSpinner baseHeightSpinner = new JSpinner(
                new SpinnerNumberModel(baseHeight, 1.0, 10, 0.5)
        );
        baseHeightSpinner.addChangeListener(e -> { main.setBaseHeight((double)baseHeightSpinner.getValue());});

        JSpinner baseMarginSpinner = new JSpinner(
                new SpinnerNumberModel(baseMargin, 1.0, 15.0, 0.5)
        );
        baseMarginSpinner.addChangeListener(e -> { main.setBaseMargin((double)baseMarginSpinner.getValue());});

        JSpinner letterHeightSpinner = new JSpinner(
                new SpinnerNumberModel(letterHeight, 1.0, 100, 1)
        );
        letterHeightSpinner.addChangeListener(e -> { main.setLetterHeight((double)letterHeightSpinner.getValue());});

        JSpinner bevelHeightSpinner = new JSpinner(
                new SpinnerNumberModel(bevelDepth, 0.1, 5.0, 0.1)
        );
        bevelHeightSpinner.addChangeListener(e -> { main.setBevelHeight((double)bevelHeightSpinner.getValue());});

        JButton doneButton = new JButton("Done");

        // Layout
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Renderer label
        gbc.gridx = 0;
        gbc.gridy = 0;
        content.add(new JLabel("Renderer:"), gbc);

        // Renderer choice
        gbc.gridx = 1;
        content.add(rendererClaude, gbc);
        gbc.gridy++;
        content.add(rendererGemini, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        var setFontButton = new JButton("Font");
        content.add(setFontButton, gbc);
        setFontButton.addActionListener(e -> main.changeFont());
        gbc.gridx = 1;
        content.add(fontSizeSpinner, gbc);

        // Alignment
        // Renderer label
        gbc.gridx = 0;
        gbc.gridy++;
        content.add(new JLabel("Alignment"), gbc);

        // Renderer choice
        gbc.gridx++;
        JPanel aligners = new JPanel();
        aligners.add(alignmentLeft, gbc);
        aligners.add(alignmentCenter, gbc);
        aligners.add(alignmentRight, gbc);
        content.add(aligners, gbc);

        // Gory detail values
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

        // Save & close
        doneButton.addActionListener(e -> {
            prefs.put(PREF_RENDERER, rendererGemini.isSelected() ? "G" : "C");
            prefs.putInt(PREF_FONT_SIZE, (Integer) fontSizeSpinner.getValue());
            prefs.putDouble(PREF_BASE_HEIGHT, (Double) baseHeightSpinner.getValue());
            prefs.putDouble(PREF_BASE_MARGIN, (Double) baseMarginSpinner.getValue());
            prefs.putDouble(PREF_LETTER_HEIGHT, (Double) letterHeightSpinner.getValue());
            prefs.putDouble(PREF_BEVEL_HEIGHT, (Double) bevelHeightSpinner.getValue());
        });
    }

    // Example usage
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("App");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new SettingsPanel(frame));
			frame.pack();
            frame.setVisible(true);
        });
    }
}
