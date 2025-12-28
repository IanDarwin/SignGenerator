package text3d;

import javax.swing.*;
import java.awt.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

import com.darwinsys.swingui.FontChooser;

/**
 * 3D Sign Generator - Creates STL files with colored regions for 3D printing.
 * Sort of a View and ViewModel combined
 * @author Mostly by Ian Darwin
 */
public class SignGenerator extends JFrame {

    private final Preferences prefs = Preferences.userNodeForPackage(SignGenerator.class);

    private final JTextArea textArea;
    private final JButton generateSTLButton, generate3MFButton;
    private final JLabel statusLabel;
    //private final JPanel infoPanel;
    private Font previewFont, renderFont;

    // Dimensions in mm
    static final double DEFAULT_BASE_HEIGHT = 2.0;
    static final double DEFAULT_BASE_MARGIN = 5.0;
    static final double DEFAULT_LETTER_HEIGHT = 5.0;
    static final double DEFAULT_BEVEL_HEIGHT = 0.5;

    double baseHeight, baseMargin , letterHeight, bevelHeight;
    TextAlign textAlignment;
    static final double SCALE_FACTOR = 0.5;

    // Keys for storing/retrieving the above in Java Preferences
    // Used by Settings class
    static final String PREF_RENDERER = "renderer";
    static final String PREF_FONT_SIZE = "fontSize";
    static final String PREF_BASE_HEIGHT = "baseHeight";
    static final String PREF_BASE_MARGIN = "baseMargin";
    static final String PREF_LETTER_HEIGHT = "letterHeight";
    static final String PREF_BEVEL_HEIGHT = "bevelHeight";
    static final String PREF_ALIGNMENT = "alignment";

    // Font settings
    static final String DEFAULT_FONT_NAME = "Arial";
    static final int DEFAULT_FONT_STYLE = Font.BOLD;
    static final int RENDER_FONT_DEFAULT_SIZE = 36;
    static final int PREVIEW_FONT_SIZE = 14;

    // Chosen to be short but exercise both upper and lower case
    public static final String STARTER_TEXT = "Hello\nWORLD";

    String signFilePath;

    FileNameExtensionFilter textFilter = new FileNameExtensionFilter(
        "Text Files", "txt", "text");
    FileNameExtensionFilter signFilter = new FileNameExtensionFilter(
        "SignGenerator Save Files", "sgn");
    FileNameExtensionFilter stlFilter = new FileNameExtensionFilter(
        "STL Files", "stl");
    FileNameExtensionFilter threeMFFilter = new FileNameExtensionFilter(
        "3MF Files", "3mf");

    TextToFile renderer =
            new GeminiTextToFile();

    public SignGenerator() {
        setTitle("3D Sign Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Get defaults from prefs
        baseHeight = prefs.getDouble(PREF_BASE_HEIGHT, DEFAULT_BASE_HEIGHT);
        baseMargin = prefs.getDouble(PREF_BASE_MARGIN, DEFAULT_BASE_MARGIN);
        letterHeight = prefs.getDouble(PREF_LETTER_HEIGHT, DEFAULT_LETTER_HEIGHT);
        bevelHeight = prefs.getDouble(PREF_BEVEL_HEIGHT, DEFAULT_BEVEL_HEIGHT);
        textAlignment = TextAlign.values()[prefs.getInt(PREF_ALIGNMENT, TextAlign.LEFT.ordinal())];

        String renderer = prefs.get(PREF_RENDERER, "C");
        setRenderer(switch(renderer) {
            case "C" -> new ClaudeTextToFile();
            case "G" -> new GeminiTextToFile();
            default -> throw new IllegalStateException("Unexpected value: " + renderer);
        });

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

        JPanel settingsPanel = new SettingsPanel(this);

        add(statusLabel, BorderLayout.NORTH);
        add(inputPanel, BorderLayout.WEST);
        add(settingsPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

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
//        var prefsMI = new JMenuItem("Preferences");
//        editMenu.add(prefsMI);
//        prefsMI.addActionListener(e -> new SettingsDialog(this).setVisible(true));
        return bar;
    }

    void changeFont() {
        FontChooser chooser = new FontChooser(this);
        chooser.setVisible(true); // Blocking
        renderFont = chooser.getSelectedFont();
        if (renderFont != null) {
            previewFont = renderFont.deriveFont((float)PREVIEW_FONT_SIZE);
        }
    }

    private void loadText() {
        System.out.println("loadText");
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Load Text");
        fileChooser.setApproveButtonText("Load sign text");
        fileChooser.setFileFilter(textFilter);
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
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open saved sign");
        chooser.setFileFilter(signFilter);
        if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(this)) {
            try {
                signFilePath = chooser.getSelectedFile().getAbsolutePath();
                String json = Files.readString(Path.of(signFilePath));
                Sign sign = Sign.fromJSON(json);
                renderFont = new Font(sign.fontName(), sign.fontStyle(), sign.fontSize());
                previewFont = renderFont.deriveFont((float)PREVIEW_FONT_SIZE);
                textArea.setFont(previewFont);
                textArea.setText(sign.text());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void saveExisting() {
        if (signFilePath == null) {
            saveFileAs();
        }
        else {
            try {
                Files.writeString(Path.of(signFilePath),
                        new Sign(textArea.getText(), renderFont).toJSON());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void saveFileAs() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Sign As");
        fileChooser.setFileFilter(signFilter);
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

    private void generate(OutputFormat fmt, JButton generateButton) {
        String text = textArea.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter some text", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(fmt == OutputFormat.STL ? stlFilter : threeMFFilter);
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
                    renderer.generateFile(text, renderFont, ffile, fmt, textAlignment);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        statusLabel.setText("Output file generated successfully: " + ffile.getName());
                        JOptionPane.showMessageDialog(SignGenerator.this,
                            "Model file created successfully!\n\nFor multi-color printing:\n" +
                            "1. Base: Z = 0 to " + baseHeight + " mm\n" +
                            "2. Letter body: Z = " + baseHeight + " to " + (baseHeight + DEFAULT_LETTER_HEIGHT - DEFAULT_BEVEL_HEIGHT) + " mm\n" +
                            "3. Letter front (beveled): Z = " + (baseHeight + DEFAULT_LETTER_HEIGHT - DEFAULT_BEVEL_HEIGHT) + " to " + (baseHeight + DEFAULT_LETTER_HEIGHT) + " mm",
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

    ///  SIMPLE ACCESSORS

    JTextArea textArea() { return textArea; }

    void setRenderer(TextToFile renderer) { this.renderer = renderer; }

    void setBaseHeight(double baseHeight) { this.baseHeight = baseHeight; }

    void setBaseMargin(double baseMargin) { this.baseMargin = baseMargin; }

    void setLetterHeight(double letterHeight) { this.letterHeight = letterHeight; }

    void setBevelHeight(double bevelHeight) { this.bevelHeight = bevelHeight; }

    public void setAlignment(TextAlign textAlignment) {
        this.textAlignment = textAlignment;
    }
}
