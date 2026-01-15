package text3d;

import javax.swing.*;
import java.awt.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.net.URL;
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

    // GUI Controls
    final JTextArea textArea;
    private final JButton generateSTLButton, generate3MFButton;
    private final JLabel statusLabel;
    private final JLabel fontNameLabel;
    private final JSpinner fontSizeSpinner, baseHeightSpinner, baseMarginSpinner, letterHeightSpinner, bevelHeightSpinner;
    private final JRadioButton alignmentLeft, alignmentCenter, alignmentRight;
    private Font previewFont, renderFont;

    // DEFAULT Dimensions in mm
    static final double DEFAULT_BASE_HEIGHT = 2.0;
    static final double DEFAULT_BASE_MARGIN = 5.0;
    static final double DEFAULT_LETTER_HEIGHT = 5.0;
    static final double DEFAULT_BEVEL_HEIGHT = 0.5;

    // DEFAULT Font settings
    static final String DEFAULT_FONT_NAME = "Sans";
    public static final int DEFAULT_RENDER_FONT_SIZE = 36;
    static final int DEFAULT_FONT_STYLE = Font.BOLD;
    static final int PREVIEW_FONT_SIZE = 14;

    double baseHeight, baseMargin, letterHeight, bevelHeight;
    String fontName;
    int fontSize;
    TextAlign textAlignment;
    static final double SCALE_FACTOR = 0.5;
    boolean resetNeeded = false;

    // Keys for storing/retrieving the above in Java Preferences
    // Used by Settings class
    static final String PREF_RENDERER = "renderer";
    static final String PREF_FONT_NAME = "fontName";
    static final String PREF_FONT_SIZE = "fontSize";
    static final String PREF_BASE_HEIGHT = "baseHeight";
    static final String PREF_BASE_MARGIN = "baseMargin";
    static final String PREF_LETTER_HEIGHT = "letterHeight";
    static final String PREF_BEVEL_HEIGHT = "bevelHeight";
    static final String PREF_ALIGNMENT = "alignment";

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
            new ClaudeTextToFile();

    public SignGenerator() {
        setTitle("3D Sign Generator");
        URL imageURL = getClass().getResource("/images/icon.png");
        setIconImage(Toolkit.getDefaultToolkit().getImage(imageURL));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Get defaults from prefs
        baseHeight = prefs.getDouble(PREF_BASE_HEIGHT, DEFAULT_BASE_HEIGHT);
        baseMargin = prefs.getDouble(PREF_BASE_MARGIN, DEFAULT_BASE_MARGIN);
        letterHeight = prefs.getDouble(PREF_LETTER_HEIGHT, DEFAULT_LETTER_HEIGHT);
        bevelHeight = prefs.getDouble(PREF_BEVEL_HEIGHT, DEFAULT_BEVEL_HEIGHT);
        textAlignment = TextAlign.values()[prefs.getInt(PREF_ALIGNMENT, TextAlign.LEFT.ordinal())];
        fontName = prefs.get(PREF_FONT_NAME, DEFAULT_FONT_NAME);
        fontSize = prefs.getInt(PREF_FONT_SIZE, DEFAULT_RENDER_FONT_SIZE);

        String renderer = prefs.get(PREF_RENDERER, "C");
        setRenderer(switch(renderer) {
            case "C" -> new ClaudeTextToFile();
            case "G" -> new GeminiTextToFile();
            default -> throw new IllegalStateException("Unexpected value: " + renderer);
        });
        fontNameLabel = new JLabel(fontName);

        setJMenuBar(createMenuBar());

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel instructionLabel = new JLabel("Enter text for your 3D sign:");
        textArea = new JTextArea(5, 40);
        previewFont = new Font(DEFAULT_FONT_NAME, DEFAULT_FONT_STYLE, PREVIEW_FONT_SIZE);
        textArea.setFont(previewFont);
        textArea.setText(STARTER_TEXT);
        textArea.setLineWrap(false);

        inputPanel.add(instructionLabel, BorderLayout.NORTH);
        inputPanel.add(textArea, BorderLayout.CENTER);

        // Create a default font
        renderFont = new Font(DEFAULT_FONT_NAME, DEFAULT_FONT_STYLE, DEFAULT_RENDER_FONT_SIZE);

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

        // The main configuration panel
        JPanel settingsPanel = new JPanel();

        // Choice of Renderer
        ButtonGroup rendererGroup = new ButtonGroup();
        JRadioButton rendererClaude = new JRadioButton("Claude Renderer");
        rendererClaude.addActionListener(e->setRenderer(new ClaudeTextToFile()));
        rendererGroup.add(rendererClaude);

        JRadioButton rendererGemini = new JRadioButton("Gemini Renderer");
        rendererGemini.addActionListener(e->setRenderer(new GeminiTextToFile()));
        rendererGroup.add(rendererGemini);

        JRadioButton rendererFreeType = new JRadioButton("FreeType Renderer");
        rendererFreeType.addActionListener(e->setRenderer(new FreeTypeRenderer()));
        rendererGroup.add(rendererFreeType);

        switch(renderer) {
            case "C":
                rendererClaude.setSelected(true); break;
            case "F":
                rendererGemini.setSelected(true); break;
            case "G":
                rendererFreeType.setSelected(true); break;
            default:
                System.out.println("INVALID: renderer = " + renderer);
        }

        // Choice of alignment
        ButtonGroup alignmentGroup = new ButtonGroup();
        alignmentLeft = new JRadioButton("Left");
        alignmentLeft.addActionListener(e->setAlignment(TextAlign.LEFT));
        alignmentGroup.add(alignmentLeft);
        alignmentCenter = new JRadioButton("Center");
        alignmentCenter.addActionListener(e->setAlignment(TextAlign.CENTER));
        alignmentGroup.add(alignmentCenter);
        alignmentRight = new JRadioButton("Right");
        alignmentRight.addActionListener(e->setAlignment(TextAlign.RIGHT));
        alignmentGroup.add(alignmentRight);

        fontSizeSpinner = new JSpinner(
                new SpinnerNumberModel(fontSize, 1, 200, 1)
        );

        if (resetNeeded) {
            baseHeight = baseMargin = letterHeight = bevelHeight = 1.0;
        }

        baseHeightSpinner = new JSpinner(
                new SpinnerNumberModel(baseHeight, 1.0, 10, 0.5)
        );
        baseHeightSpinner.addChangeListener(e -> setBaseHeight((double)baseHeightSpinner.getValue()));
        baseMarginSpinner = new JSpinner(
                new SpinnerNumberModel(baseMargin, 1.0, 15.0, 0.5)
        );
        baseMarginSpinner.addChangeListener(e -> setBaseMargin((double)baseMarginSpinner.getValue()));

        letterHeightSpinner = new JSpinner(
                new SpinnerNumberModel(letterHeight, 1.0, 100, 1)
        );
        letterHeightSpinner.addChangeListener(e -> setLetterHeight((double)letterHeightSpinner.getValue()));

        bevelHeightSpinner = new JSpinner(
                new SpinnerNumberModel(bevelHeight, 0.1, 5.0, 0.1)
        );
        bevelHeightSpinner.addChangeListener(e -> setBevelHeight((double)bevelHeightSpinner.getValue()));

        // Layout
        settingsPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Renderer label
        gbc.gridx = 0;
        gbc.gridy = 0;
        settingsPanel.add(new JLabel("Renderer:"), gbc);

        // Renderer choice
        gbc.gridx = 1;
        settingsPanel.add(rendererClaude, gbc);
        gbc.gridy++;
        settingsPanel.add(rendererGemini, gbc);
        gbc.gridy++;
        settingsPanel.add(rendererFreeType, gbc);

        // Font
        gbc.gridx = 0;
        gbc.gridy++;
        var setFontButton = new JButton("Font...");
        settingsPanel.add(setFontButton, gbc);
        setFontButton.addActionListener(e -> changeFont());
        gbc.gridx = 1;
        var fontInfoPanel = new JPanel();
        fontInfoPanel.add(new JLabel("Name:"));
        fontInfoPanel.add(fontNameLabel);
        fontInfoPanel.add(fontSizeSpinner, gbc);
        settingsPanel.add(fontInfoPanel, gbc);

        // Alignment
        gbc.gridx = 0;
        gbc.gridy++;
        settingsPanel.add(new JLabel("Alignment"), gbc);

        // Renderer choice
        gbc.gridx++;
        JPanel aligners = new JPanel();
        aligners.add(alignmentLeft, gbc);
        aligners.add(alignmentCenter, gbc);
        aligners.add(alignmentRight, gbc);
        settingsPanel.add(aligners, gbc);

        // Gory detail values
        gbc.gridx = 0;
        gbc.gridy++;
        settingsPanel.add(new JLabel("Base height (mm):"), gbc);
        gbc.gridx = 1;
        settingsPanel.add(baseHeightSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        settingsPanel.add(new JLabel("Base Margin (mm):"), gbc);
        gbc.gridx = 1;
        settingsPanel.add(baseMarginSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        settingsPanel.add(new JLabel("Letter height (mm):"), gbc);
        gbc.gridx = 1;
        settingsPanel.add(letterHeightSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        settingsPanel.add(new JLabel("Bevel height (mm):"), gbc);
        gbc.gridx = 1;
        settingsPanel.add(bevelHeightSpinner, gbc);

        // NOW PUT IT ALL TOGETHER:
        add(statusLabel, BorderLayout.NORTH);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                   inputPanel, settingsPanel);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(400);
        add(splitPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        pack();
    }

    void updateSettingsPanel() {
        fontNameLabel.setText(renderFont.getName());
        fontSizeSpinner.setValue(renderFont.getSize());
        switch(textAlignment) {
            case LEFT: alignmentLeft.setSelected(true); break;
            case CENTER: alignmentCenter.setSelected(true); break;
            case RIGHT: alignmentRight.setSelected(true); break;
            default: throw new IllegalStateException("Unknown text alignment");
        }
        baseHeightSpinner.setValue(baseHeight);
        baseMarginSpinner.setValue(baseMargin);
        letterHeightSpinner.setValue(letterHeight);
        bevelHeightSpinner.setValue(bevelHeight);
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();

        // FILE MENU
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

        // Edit Menu
        JMenu editMenu = new JMenu("Edit");
        bar.add(editMenu);

        // Help Menu
        JMenu helpMenu = new JMenu("Help");
        JMenuItem helpAbout = new JMenuItem("About...");
        helpAbout.addActionListener(e -> JOptionPane.showMessageDialog(this,
                """
                        <html>JMenuItem generates 3D print files for signs, stamps, etc.<br/>
                        Built by Rejminet Group https://rejmi.net<br/>
                        Let us build <em>your</em> Mobile and/or Desktop app!"""));
        helpMenu.add(helpAbout);
        bar.add(helpMenu);
        return bar;
    }

    void changeFont() {
        FontChooser chooser = new FontChooser(this);
        chooser.setVisible(true); // Blocking
        var chosenFont = chooser.getSelectedFont();
        if (chosenFont != null) {
            renderFont = chosenFont;
            previewFont = renderFont.deriveFont((float)PREVIEW_FONT_SIZE);
            textArea.setFont(previewFont);
            fontNameLabel.setText(renderFont.getFontName());
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
                textAlignment = sign.alignment();
                setBaseHeight(sign.baseHeight());
                setBaseMargin(sign.baseMargin());
                setLetterHeight(sign.letterHeight());
                setBevelHeight(sign.bevelHeight());
                updateSettingsPanel();
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
                        new Sign(textArea.getText(), renderFont,
                            textAlignment, baseHeight, baseMargin, letterHeight, bevelHeight
                                ).toJSON());
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
                signFilePath = fileChooser.getSelectedFile().getAbsolutePath();
                Files.writeString(Path.of(signFilePath),
                        new Sign(textArea.getText(), renderFont,
                                textAlignment, baseHeight, baseMargin, letterHeight, bevelHeight).toJSON());
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

    ///  NOT-SO-SIMPLE ACCESSORS

    JTextArea textArea() { return textArea; }

    void setRenderer(TextToFile renderer) {
        this.renderer = renderer;
        prefs.put(PREF_RENDERER, String.valueOf(renderer.getClass().getSimpleName().charAt(0)));
    }

    // Overrides setFont() in AWT but we never call it on the main class so OK
    public void setFont(Font font) {
        // this.font = font;
        prefs.put(PREF_FONT_NAME, font.getName());
    }

    void setBaseHeight(double baseHeight) {
        this.baseHeight = baseHeight;
        prefs.putDouble(PREF_BASE_HEIGHT, baseHeight);
    }

    void setBaseMargin(double baseMargin) {
        this.baseMargin = baseMargin;
        prefs.putDouble(PREF_BASE_MARGIN, baseMargin);
    }

    void setLetterHeight(double letterHeight) {
        this.letterHeight = letterHeight;
        prefs.putDouble(PREF_LETTER_HEIGHT, letterHeight);
    }

    void setBevelHeight(double bevelHeight) {
        this.bevelHeight = bevelHeight;
        prefs.putDouble(PREF_BEVEL_HEIGHT, bevelHeight);
    }

    public void setAlignment(TextAlign textAlignment) {
        this.textAlignment = textAlignment;
    }
}
