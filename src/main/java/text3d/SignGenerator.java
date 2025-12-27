package text3d;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import javax.swing.filechooser.FileFilter;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import com.darwinsys.swingui.FontChooser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 3D Sign Generator - Creates STL files with colored regions for 3D printing
 * @author Original by Claude.ai, guided by Ian Darwin
 */
public class SignGenerator extends JFrame {
    private final JTextArea textArea;
    private final JButton generateSTLButton, generate3MFButton;
    private final JLabel statusLabel;
    private final JPanel infoPanel;
    private Font previewFont, renderFont;

    // Dimensions in mm
    static final double BASE_HEIGHT = 2.0;
    static final double BASE_MARGIN = 5.0;
    static final double LETTER_HEIGHT = 5.0;
    static final double BEVEL_HEIGHT = 0.5;
    static final double SCALE_FACTOR = 0.5;

    // Keys for storing/retrieving the above in Java Preferences
        // Preferences Keys, used by Settings class
    static final String PREF_RENDERER = "renderer";
    static final String PREF_FONT_SIZE = "fontSize";
    static final String PREF_BASE_HEIGHT = "baseHeight";
    static final String PREF_BASE_MARGIN = "baseMargin";
    static final String PREF_LETTER_HEIGHT = "letterHeight";
    static final String PREF_BEVEL_HEIGHT = "bevelHeight";

    // Font settings
    static final String DEFAULT_FONT_NAME = "Arial";
    static final int DEFAULT_FONT_STYLE = Font.BOLD;
    static final int RENDER_FONT_DEFAULT_SIZE = 36;
    static final int PREVIEW_FONT_SIZE = 14;

    public static final String STARTER_TEXT = "Hello\nWORLD";

    final TextToFile geometry =
            new GeminiTextToFile();

    public SignGenerator() {
        setTitle("3D Sign Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        setJMenuBar(createMenuBar());

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel instructionLabel = new JLabel("Enter text for your 3D sign:");
        textArea = new JTextArea(5, 40);
        previewFont = new Font(DEFAULT_FONT_NAME, DEFAULT_FONT_STYLE, PREVIEW_FONT_SIZE);
        textArea.setFont(previewFont);
        textArea.setText(STARTER_TEXT);
        textArea.setLineWrap(false);

        JScrollPane scrollPane = new JScrollPane(textArea);
        inputPanel.add(instructionLabel, BorderLayout.NORTH);
        inputPanel.add(scrollPane, BorderLayout.CENTER);

        // Create a default font
        renderFont = new Font(DEFAULT_FONT_NAME, DEFAULT_FONT_STYLE, RENDER_FONT_DEFAULT_SIZE);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        var setFontButton = new JButton("Change font");
        setFontButton.addActionListener(e -> changeFont());
        buttonPanel.add(setFontButton);

        generateSTLButton = new JButton("Generate STL File");
        generateSTLButton.addActionListener(
                e -> generate(OutputFormat.STL, generateSTLButton));
        buttonPanel.add(generateSTLButton);

        generate3MFButton = new JButton("Generate 3MF File");
        generate3MFButton.addActionListener(
                e -> generate(OutputFormat.THREEMF, generate3MFButton));
        buttonPanel.add(generate3MFButton);

        statusLabel = new JLabel("Ready to generate");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        infoPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        infoPanel.setBorder(BorderFactory.createTitledBorder("3D Print Settings"));
        updatePanels();

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
        rightPanel.add(infoPanel, BorderLayout.NORTH);

        add(inputPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        add(statusLabel, BorderLayout.NORTH);
        add(rightPanel, BorderLayout.EAST);

        pack();
        setLocationRelativeTo(null);
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        var newSign = new JMenuItem("New Sign");
        newSign.addActionListener(e -> textArea.setText(STARTER_TEXT));
        fileMenu.add(newSign);
        fileMenu.addSeparator();
        var load = new JMenuItem("Load Text...");
        load.addActionListener(e -> loadText());
        fileMenu.add(load);
        fileMenu.addSeparator();
        var open = new JMenuItem("Open...");
        open.addActionListener(e -> openFile());
        fileMenu.add(open);
        var save = new JMenuItem("Save");
        save.addActionListener(e -> saveExisting());
        fileMenu.add(save);
        var saveAs = new JMenuItem("Save As...");
        saveAs.addActionListener(e -> saveFileAs());
        fileMenu.add(saveAs);
        fileMenu.addSeparator();
        var exit = new JMenuItem("Exit");
        exit.addActionListener(e -> System.exit(0));
        fileMenu.add(exit);
        bar.add(fileMenu);

        JMenu editMenu = new JMenu("Edit");
        bar.add(editMenu);
        var prefsMI = new JMenuItem("Preferences");
        editMenu.add(prefsMI);
        prefsMI.addActionListener(e -> new SettingsDialog(this).setVisible(true));
        return bar;
    }

    private void changeFont() {
        FontChooser chooser = new FontChooser(this);
        chooser.setVisible(true); // Blocking
        renderFont = chooser.getSelectedFont();
        if (renderFont != null) {
            previewFont = renderFont.deriveFont((float)PREVIEW_FONT_SIZE);
            updatePanels();
        }
    }

    private static class TextFileFilter extends FileFilter {
        @Override
        public boolean accept(File pathName) {
            return pathName.getName().endsWith(".txt");
        }

        @Override
        public String getDescription() {
            return "Text filter";
        }
    }

    private void loadText() {
        System.out.println("loadText");
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Load Text");
        fileChooser.setApproveButtonText("Load sign text");
        fileChooser.setFileFilter(new TextFileFilter());
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path target = fileChooser.getSelectedFile().toPath();
            try {
                String text = Files.readString(target);
                textArea.setText(text);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser("Load sign");
        if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(this)) {
            try {
                String json = Files.readString(Path.of(chooser.getSelectedFile().getAbsolutePath()));
                Sign sign = Sign.fromJSON(json);
                System.out.println("sign = " + sign);
                textArea.setText(sign.text());
                renderFont = new Font(sign.fontName(), sign.fontStyle(), sign.fontSize());
                previewFont = renderFont.deriveFont((float)PREVIEW_FONT_SIZE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void saveExisting() {
        System.out.println("saveExisting: Not written yet");
    }

    private void saveFileAs() {
        JFileChooser fileChooser = new JFileChooser("Save Sign As");
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                final String name = fileChooser.getSelectedFile().getAbsolutePath();
                Files.writeString(Path.of(name),
                        new Sign(textArea.getText(), renderFont).toJSON());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void updatePanels() {
        textArea.setFont(previewFont);
        infoPanel.removeAll();
        infoPanel.add(new JLabel(
                String.format("Font: %s Bold Size %dpt",
                        renderFont.getName(), renderFont.getSize())));
        infoPanel.add(new JLabel("Base height: " + BASE_HEIGHT + " mm"));
        infoPanel.add(new JLabel("Letter height: " + LETTER_HEIGHT + " mm"));
        infoPanel.add(new JLabel("Bevel depth: " + BEVEL_HEIGHT + " mm"));
        pack();
    }

    private void generate(OutputFormat fmt, JButton generateButton) {
        String text = textArea.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter some text", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save " + fmt.name() + " File");
        fileChooser.setSelectedFile(new File("sign" + fmt.ext()));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(fmt.ext())) {
                file = new File(file.getAbsolutePath() + fmt.ext());
            }
            final File ffile = file;

            generateButton.setEnabled(false);
            statusLabel.setText("Generating "+ fmt.name() + "...");

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    geometry.generateFile(text, renderFont, ffile, fmt, TextAlign.LEFT);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        statusLabel.setText("Output file generated successfully: " + ffile.getName());
                        JOptionPane.showMessageDialog(SignGenerator.this,
                            "Model file created successfully!\n\nFor multi-color printing:\n" +
                            "1. Base: Z = 0 to " + BASE_HEIGHT + " mm\n" +
                            "2. Letter body: Z = " + BASE_HEIGHT + " to " + (BASE_HEIGHT + LETTER_HEIGHT - BEVEL_HEIGHT) + " mm\n" +
                            "3. Letter front (beveled): Z = " + (BASE_HEIGHT + LETTER_HEIGHT - BEVEL_HEIGHT) + " to " + (BASE_HEIGHT + LETTER_HEIGHT) + " mm",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        statusLabel.setText("Error: " + ex.getMessage());
                        JOptionPane.showMessageDialog(SignGenerator.this,
                            "Error generating file: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                    } finally {
                        generateButton.setEnabled(true);
                    }
                }
            };
            worker.execute();
        }
    }
}
