package text3d;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.swing.filechooser.FileFilter;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.darwinsys.swingui.FontChooser;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.triangulate.polygon.ConstrainedDelaunayTriangulator;
import org.locationtech.jts.geom.Geometry;

/**
 * 3D Sign Generator - Creates STL files with colored regions for 3D printing
 * @author Original by Claude.io, guided by Ian Darwin
 */

public class SignGenerator extends JFrame {
    private JTextArea textArea;
    private final JButton generateSTLButton, generate3MFButton;
    private final JLabel statusLabel;
    private Font previewFont, renderFont;
    private final JPanel infoPanel;

    // Dimensions in mm
    private static final double BASE_HEIGHT = 2.0;
    private static final double LETTER_HEIGHT = 5.0;
    private static final double BEVEL_DEPTH = 0.5;
    private static final double BASE_MARGIN = 5.0;
    private static final double SCALE_FACTOR = 0.5;

    // Font settings
    private static final String DEFAULT_FONT_NAME = "Arial";
    private static final int DEFAULT_FONT_STYLE = Font.BOLD;
    private static final int RENDER_FONT_DEFAULT_SIZE = 36;
    private static final int PREVIEW_FONT_SIZE = 14;

    public static final String STARTER_TEXT = "HELLO\nWORLD";

    public record Sign(String text, Font font){
        String toJSON() {
            return String.format("""
                    {
                    "text": "%s",
                    "font": {
                        "name": "%s",
                        "size": %d,
                        "style": %d
                        }
                    }""",
                    text.replaceAll("\n", "\\\\n"), font.getName(), font.getSize(), font.getStyle());
        }
    }

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
        generateSTLButton.addActionListener(e -> generateSTL());
        buttonPanel.add(generateSTLButton);

        generate3MFButton = new JButton("Generate 3MF File");
        generate3MFButton.addActionListener(e -> generate3MF());
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
        System.out.println("openFile: Not written yet");
    }

    private void saveExisting() {
        System.out.println("saveExisting: Not written yet");
    }

    private void saveFileAs() {
        System.out.println("saveFileAs");
        JFileChooser fileChooser = new JFileChooser("Save Sign As");
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                final String name = fileChooser.getSelectedFile().getAbsolutePath();
                System.out.println("name = " + name);
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
        infoPanel.add(new JLabel("Bevel depth: " + BEVEL_DEPTH + " mm"));
        pack();
    }

    private void generateSTL() {
        String text = textArea.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter some text", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save STL File");
        fileChooser.setSelectedFile(new File("sign.stl"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".stl")) {
                file = new File(file.getAbsolutePath() + ".stl");
            }
            final File ffile = file;

            generateSTLButton.setEnabled(false);
            statusLabel.setText("Generating STL...");

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    generateSTLFile(text, renderFont, ffile);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        statusLabel.setText("STL file generated successfully: " + ffile.getName());
                        JOptionPane.showMessageDialog(SignGenerator.this,
                            "STL file created successfully!\n\nFor multi-color printing:\n" +
                            "1. Base: Z = 0 to " + BASE_HEIGHT + " mm\n" +
                            "2. Letter body: Z = " + BASE_HEIGHT + " to " + (BASE_HEIGHT + LETTER_HEIGHT - BEVEL_DEPTH) + " mm\n" +
                            "3. Letter front (beveled): Z = " + (BASE_HEIGHT + LETTER_HEIGHT - BEVEL_DEPTH) + " to " + (BASE_HEIGHT + LETTER_HEIGHT) + " mm",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        statusLabel.setText("Error: " + ex.getMessage());
                        JOptionPane.showMessageDialog(SignGenerator.this,
                            "Error generating STL: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                    } finally {
                        generateSTLButton.setEnabled(true);
                    }
                }
            };
            worker.execute();
        }
    }

    private void generateSTLFile(String text, Font font, File file) throws IOException {

        List<Shape> letterShapes = new ArrayList<>();
        String[] lines = text.split("\n");
        double currentY = 0;

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            Shape lineShape = createTextShape(line, font, 0, currentY);
            if (lineShape != null) {
                letterShapes.add(lineShape);
                Rectangle2D bounds = lineShape.getBounds2D();
                currentY += bounds.getHeight() + 10;
            }
        }

        if (letterShapes.isEmpty()) {
            throw new IOException("No valid text to generate");
        }

        Rectangle2D overallBounds = letterShapes.getFirst().getBounds2D();
        for (int i = 1; i < letterShapes.size(); i++) {
            overallBounds = overallBounds.createUnion(letterShapes.get(i).getBounds2D());
        }

        Rectangle2D baseBounds = new Rectangle2D.Double(
            overallBounds.getX() - BASE_MARGIN,
            overallBounds.getY() - BASE_MARGIN,
            overallBounds.getWidth() + 2 * BASE_MARGIN,
            overallBounds.getHeight() + 2 * BASE_MARGIN
        );

        List<Triangle> triangles = new ArrayList<>();

        addBase(triangles, baseBounds);

        for (Shape shape : letterShapes) {
            addLetterGeometry(triangles, shape);
        }

        writeSTL(triangles, file);
    }

    private Shape createTextShape(String text, Font font, double x, double y) {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        FontRenderContext frc = g2d.getFontRenderContext();
        GlyphVector gv = font.createGlyphVector(frc, text);
        Shape shape = gv.getOutline((float)x, (float)y);
        g2d.dispose();
        return shape;
    }

    private void addBase(List<Triangle> triangles, Rectangle2D bounds) {
        double x1 = bounds.getX() * SCALE_FACTOR;
        double y1 = -bounds.getY() * SCALE_FACTOR;
        double x2 = (bounds.getX() + bounds.getWidth()) * SCALE_FACTOR;
        double y2 = -(bounds.getY() + bounds.getHeight()) * SCALE_FACTOR;
        double z0 = 0;
        double z1 = BASE_HEIGHT;

        addQuad(triangles,
            new Point3D(x1, y1, z0), new Point3D(x2, y1, z0),
            new Point3D(x2, y2, z0), new Point3D(x1, y2, z0),
            new Point3D(0, 0, -1));

        addQuad(triangles,
            new Point3D(x1, y1, z1), new Point3D(x1, y2, z1),
            new Point3D(x2, y2, z1), new Point3D(x2, y1, z1),
            new Point3D(0, 0, 1));

        addQuad(triangles, new Point3D(x1, y1, z0), new Point3D(x1, y1, z1),
            new Point3D(x2, y1, z1), new Point3D(x2, y1, z0), new Point3D(0, -1, 0));
        addQuad(triangles, new Point3D(x2, y1, z0), new Point3D(x2, y1, z1),
            new Point3D(x2, y2, z1), new Point3D(x2, y2, z0), new Point3D(1, 0, 0));
        addQuad(triangles, new Point3D(x2, y2, z0), new Point3D(x2, y2, z1),
            new Point3D(x1, y2, z1), new Point3D(x1, y2, z0), new Point3D(0, 1, 0));
        addQuad(triangles, new Point3D(x1, y2, z0), new Point3D(x1, y2, z1),
            new Point3D(x1, y1, z1), new Point3D(x1, y1, z0), new Point3D(-1, 0, 0));
    }

    private void addLetterGeometry(List<Triangle> triangles, Shape shape) {
        PathIterator pi = shape.getPathIterator(null, 0.5);
        List<List<Point2D>> allContours = new ArrayList<>();
        List<Point2D> currentContour = new ArrayList<>();
        double[] coords = new double[6];

        while (!pi.isDone()) {
            int type = pi.currentSegment(coords);

            if (type == PathIterator.SEG_MOVETO) {
                if (currentContour.size() > 2) {
                    allContours.add(new ArrayList<>(currentContour));
                }
                currentContour.clear();
                currentContour.add(new Point2D.Double(coords[0], coords[1]));
            } else if (type == PathIterator.SEG_LINETO) {
                currentContour.add(new Point2D.Double(coords[0], coords[1]));
            } else if (type == PathIterator.SEG_CLOSE) {
                if (currentContour.size() > 2) {
                    allContours.add(new ArrayList<>(currentContour));
                }
                currentContour.clear();
            }
            pi.next();
        }

        if (currentContour.size() > 2) {
            allContours.add(new ArrayList<>(currentContour));
        }

        // Separate outer contours and holes
        List<List<Point2D>> outerContours = new ArrayList<>();
        List<List<Point2D>> holeContours = new ArrayList<>();

        for (List<Point2D> contour : allContours) {
            if (contour.size() > 2) {
                if (isClockwise(contour)) {
                    outerContours.add(contour);
                } else {
                    holeContours.add(contour);
                }
            }
        }

        // Process each outer contour with its associated holes
        // For simplicity, assume all holes belong to the first outer contour
        // A more robust solution would determine which holes belong to which outer
        if (!outerContours.isEmpty()) {
            addLetterWithProperTriangulation(triangles, outerContours.getFirst(), holeContours);
        }
    }

    private void addLetterWithProperTriangulation(List<Triangle> triangles, List<Point2D> outer, List<List<Point2D>> holes) {
        double zBase = BASE_HEIGHT;
        double zTop = BASE_HEIGHT + LETTER_HEIGHT - BEVEL_DEPTH;
        double zBevel = BASE_HEIGHT + LETTER_HEIGHT;
        double bevelInset = BEVEL_DEPTH * 0.7;

        // Add side walls for outer contour
        for (int i = 0; i < outer.size(); i++) {
            Point2D p1 = outer.get(i);
            Point2D p2 = outer.get((i + 1) % outer.size());

            double x1 = p1.getX() * SCALE_FACTOR;
            double y1 = -p1.getY() * SCALE_FACTOR;
            double x2 = p2.getX() * SCALE_FACTOR;
            double y2 = -p2.getY() * SCALE_FACTOR;

            addQuad(triangles,
                new Point3D(x1, y1, zBase), new Point3D(x2, y2, zBase),
                new Point3D(x2, y2, zTop), new Point3D(x1, y1, zTop),
                null);
        }

        // Add side walls for holes
        for (List<Point2D> hole : holes) {
            for (int i = 0; i < hole.size(); i++) {
                Point2D p1 = hole.get(i);
                Point2D p2 = hole.get((i + 1) % hole.size());

                double x1 = p1.getX() * SCALE_FACTOR;
                double y1 = -p1.getY() * SCALE_FACTOR;
                double x2 = p2.getX() * SCALE_FACTOR;
                double y2 = -p2.getY() * SCALE_FACTOR;

                // Inward-facing walls
                addQuad(triangles,
                    new Point3D(x1, y1, zBase), new Point3D(x1, y1, zTop),
                    new Point3D(x2, y2, zTop), new Point3D(x2, y2, zBase),
                    null);
            }
        }

        // Triangulate bottom face (ring if holes exist)
        List<Point2D[]> bottomTris = triangulatePolygonWithHoles(outer, holes);
        for (Point2D[] tri : bottomTris) {
            double x1 = tri[0].getX() * SCALE_FACTOR;
            double y1 = -tri[0].getY() * SCALE_FACTOR;
            double x2 = tri[1].getX() * SCALE_FACTOR;
            double y2 = -tri[1].getY() * SCALE_FACTOR;
            double x3 = tri[2].getX() * SCALE_FACTOR;
            double y3 = -tri[2].getY() * SCALE_FACTOR;

            triangles.add(new Triangle(
                new Point3D(x1, y1, zBase),
                new Point3D(x2, y2, zBase),
                new Point3D(x3, y3, zBase),
                new Point3D(0, 0, -1)));
        }

        // Triangulate top face with beveling
        // For now, use simple approach for outer edge
        Point2D center = calculateCentroid(outer);
        double cx = center.getX() * SCALE_FACTOR;
        double cy = -center.getY() * SCALE_FACTOR;

        for (int i = 0; i < outer.size(); i++) {
            Point2D p1 = outer.get(i);
            Point2D p2 = outer.get((i + 1) % outer.size());

            double x1 = p1.getX() * SCALE_FACTOR;
            double y1 = -p1.getY() * SCALE_FACTOR;
            double x2 = p2.getX() * SCALE_FACTOR;
            double y2 = -p2.getY() * SCALE_FACTOR;

            double dx1 = cx - x1, dy1 = cy - y1;
            double len1 = Math.sqrt(dx1*dx1 + dy1*dy1);
            if (len1 > 0.001) {
                double bx1 = x1 + (dx1/len1) * bevelInset;
                double by1 = y1 + (dy1/len1) * bevelInset;

                double dx2 = cx - x2, dy2 = cy - y2;
                double len2 = Math.sqrt(dx2*dx2 + dy2*dy2);
                double bx2 = x2 + (dx2/len2) * bevelInset;
                double by2 = y2 + (dy2/len2) * bevelInset;

                addQuad(triangles,
                    new Point3D(x1, y1, zTop), new Point3D(x2, y2, zTop),
                    new Point3D(bx2, by2, zBevel), new Point3D(bx1, by1, zBevel),
                    null);

                triangles.add(new Triangle(
                    new Point3D(bx1, by1, zBevel),
                    new Point3D(bx2, by2, zBevel),
                    new Point3D(cx, cy, zBevel),
                    new Point3D(0, 0, 1)));
            }
        }
    }

    /**
     * Triangulate a polygon with holes using JTS ConstrainedDelaunayTriangulator.
     *
     * @return List of triangles as Point2D triplets
     */
    private List<Point2D[]> triangulatePolygonWithHoles(List<Point2D> outer, List<List<Point2D>> holes) {
        List<Point2D[]> result = new ArrayList<>();

        try {
            GeometryFactory gf = new GeometryFactory();

            // Remove duplicate/near-duplicate points from outer contour
            List<Point2D> cleanOuter = removeDuplicates(outer, 0.01);

            // Convert outer contour to JTS Coordinate array
            Coordinate[] outerCoords = new Coordinate[cleanOuter.size() + 1];
            for (int i = 0; i < cleanOuter.size(); i++) {
                Point2D p = cleanOuter.get(i);
                outerCoords[i] = new Coordinate(p.getX(), p.getY());
            }
            // Close the ring
            outerCoords[cleanOuter.size()] = new Coordinate(cleanOuter.getFirst().getX(), cleanOuter.getFirst().getY());

            LinearRing shell = gf.createLinearRing(outerCoords);

            // Convert holes to JTS LinearRing array
            LinearRing[] holeRings = new LinearRing[holes.size()];
            for (int h = 0; h < holes.size(); h++) {
                List<Point2D> hole = holes.get(h);
                List<Point2D> cleanHole = removeDuplicates(hole, 0.01);

                Coordinate[] holeCoords = new Coordinate[cleanHole.size() + 1];
                for (int i = 0; i < cleanHole.size(); i++) {
                    Point2D p = cleanHole.get(i);
                    holeCoords[i] = new Coordinate(p.getX(), p.getY());
                }
                // Close the ring
                holeCoords[cleanHole.size()] = new Coordinate(cleanHole.getFirst().getX(), cleanHole.getFirst().getY());
                holeRings[h] = gf.createLinearRing(holeCoords);
            }

            // Create JTS Polygon with holes
            Polygon polygon = gf.createPolygon(shell, holeRings);

            // Triangulate using ConstrainedDelaunayTriangulator
            ConstrainedDelaunayTriangulator triangulator = new ConstrainedDelaunayTriangulator(polygon);
            Geometry triangulated = triangulator.getResult();

            // Extract triangles from result
            for (int i = 0; i < triangulated.getNumGeometries(); i++) {
                Geometry geom = triangulated.getGeometryN(i);
                Coordinate[] coords = geom.getCoordinates();

                if (coords.length >= 3) {
                    Point2D[] tri = new Point2D[3];
                    tri[0] = new Point2D.Double(coords[0].x, coords[0].y);
                    tri[1] = new Point2D.Double(coords[1].x, coords[1].y);
                    tri[2] = new Point2D.Double(coords[2].x, coords[2].y);
                    result.add(tri);
                }
            }
        } catch (Exception e) {
            // Uncomment below for full stack trace during debugging:
            // System.err.println("JTS triangulation failed, using fallback: " + e.getClass().getSimpleName());
            // e.printStackTrace();

            // Fallback to simple fan triangulation if JTS fails
            Point2D center = calculateCentroid(outer);
            for (int i = 0; i < outer.size(); i++) {
                Point2D[] tri = new Point2D[3];
                tri[0] = outer.get(i);
                tri[1] = outer.get((i + 1) % outer.size());
                tri[2] = center;
                result.add(tri);
            }
        }

        return result;
    }

    /**
     * Remove duplicate or near-duplicate consecutive points from a contour
     */
    private List<Point2D> removeDuplicates(List<Point2D> points, double tolerance) {
        List<Point2D> result = new ArrayList<>();
        if (points.isEmpty()) return result;

        result.add(points.getFirst());
        for (int i = 1; i < points.size(); i++) {
            Point2D prev = result.getLast();
            Point2D curr = points.get(i);
            double dist = prev.distance(curr);
            if (dist > tolerance) {
                result.add(curr);
            }
        }

        // Check if first and last are too close
        if (result.size() > 1) {
            double dist = result.getFirst().distance(result.getLast());
            if (dist <= tolerance) {
                result.removeLast();
            }
        }

        return result;
    }

    private boolean isClockwise(List<Point2D> points) {
        double sum = 0;
        for (int i = 0; i < points.size(); i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % points.size());
            sum += (p2.getX() - p1.getX()) * (p2.getY() + p1.getY());
        }
        return sum > 0;
    }

    private void addSolidContour(List<Triangle> triangles, List<Point2D> points, boolean isHole) {
        double zBase = BASE_HEIGHT;
        double zTop = BASE_HEIGHT + LETTER_HEIGHT - BEVEL_DEPTH;
        double zBevel = BASE_HEIGHT + LETTER_HEIGHT;
        double bevelInset = BEVEL_DEPTH * 0.7;

        Point2D center = calculateCentroid(points);
        double cx = center.getX() * SCALE_FACTOR;
        double cy = -center.getY() * SCALE_FACTOR;

        // For now: generate all contours as solid geometry
        // Holes will need manual post-processing in slicer or proper triangulation library
        // TODO: Implement proper constrained Delaunay triangulation for rings

        // Generate side walls
        for (int i = 0; i < points.size(); i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % points.size());

            double x1 = p1.getX() * SCALE_FACTOR;
            double y1 = -p1.getY() * SCALE_FACTOR;
            double x2 = p2.getX() * SCALE_FACTOR;
            double y2 = -p2.getY() * SCALE_FACTOR;

            if (isHole) {
                // Hole: inward-facing walls
                addQuad(triangles,
                    new Point3D(x1, y1, zBase), new Point3D(x1, y1, zTop),
                    new Point3D(x2, y2, zTop), new Point3D(x2, y2, zBase),
                    null);
            } else {
                // Outer: outward-facing walls
                addQuad(triangles,
                    new Point3D(x1, y1, zBase), new Point3D(x2, y2, zBase),
                    new Point3D(x2, y2, zTop), new Point3D(x1, y1, zTop),
                    null);
            }
        }

        // Don't generate any caps for holes - leave open
        if (isHole) {
            return;
        }

        // Only outer contours get bottom and beveled top
        // Generate bottom face
        for (int i = 0; i < points.size(); i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % points.size());

            double x1 = p1.getX() * SCALE_FACTOR;
            double y1 = -p1.getY() * SCALE_FACTOR;
            double x2 = p2.getX() * SCALE_FACTOR;
            double y2 = -p2.getY() * SCALE_FACTOR;

            triangles.add(new Triangle(
                new Point3D(x1, y1, zBase),
                new Point3D(cx, cy, zBase),
                new Point3D(x2, y2, zBase),
                new Point3D(0, 0, -1)));
        }

        // Generate beveled top
        for (int i = 0; i < points.size(); i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % points.size());

            double x1 = p1.getX() * SCALE_FACTOR;
            double y1 = -p1.getY() * SCALE_FACTOR;
            double x2 = p2.getX() * SCALE_FACTOR;
            double y2 = -p2.getY() * SCALE_FACTOR;

            double dx1 = cx - x1, dy1 = cy - y1;
            double len1 = Math.sqrt(dx1*dx1 + dy1*dy1);
            if (len1 > 0.001) {
                double bx1 = x1 + (dx1/len1) * bevelInset;
                double by1 = y1 + (dy1/len1) * bevelInset;

                double dx2 = cx - x2, dy2 = cy - y2;
                double len2 = Math.sqrt(dx2*dx2 + dy2*dy2);
                double bx2 = x2 + (dx2/len2) * bevelInset;
                double by2 = y2 + (dy2/len2) * bevelInset;

                addQuad(triangles,
                    new Point3D(x1, y1, zTop), new Point3D(x2, y2, zTop),
                    new Point3D(bx2, by2, zBevel), new Point3D(bx1, by1, zBevel),
                    null);

                triangles.add(new Triangle(
                    new Point3D(bx1, by1, zBevel),
                    new Point3D(bx2, by2, zBevel),
                    new Point3D(cx, cy, zBevel),
                    new Point3D(0, 0, 1)));
            }
        }
    }

    private Point2D calculateCentroid(List<Point2D> points) {
        double sumX = 0, sumY = 0;
        for (Point2D p : points) {
            sumX += p.getX();
            sumY += p.getY();
        }
        return new Point2D.Double(sumX / points.size(), sumY / points.size());
    }

    private void addQuad(List<Triangle> triangles, Point3D p1, Point3D p2, Point3D p3, Point3D p4, Point3D normal) {
        if (normal == null) {
            normal = calculateNormal(p1, p2, p3);
        }
        triangles.add(new Triangle(p1, p2, p3, normal));
        triangles.add(new Triangle(p1, p3, p4, normal));
    }

    private Point3D calculateNormal(Point3D p1, Point3D p2, Point3D p3) {
        double ux = p2.x - p1.x, uy = p2.y - p1.y, uz = p2.z - p1.z;
        double vx = p3.x - p1.x, vy = p3.y - p1.y, vz = p3.z - p1.z;
        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;
        double len = Math.sqrt(nx*nx + ny*ny + nz*nz);
        return new Point3D(nx/len, ny/len, nz/len);
    }

    private void generate3MF() {
        String text = textArea.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter some text", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save 3MF File");
        fileChooser.setSelectedFile(new File("sign.3mf"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".3mf")) {
                file = new File(file.getAbsolutePath() + ".3mf");
            }
            final File ffile = file;

            generate3MFButton.setEnabled(false);
            statusLabel.setText("Generating 3MF...");

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    generate3MFFile(text, renderFont, ffile);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        statusLabel.setText("3MF file generated successfully: " + ffile.getName());
                        JOptionPane.showMessageDialog(SignGenerator.this,
                            "3MF file created successfully!\n\nFor multi-color printing:\n" +
                            "1. Base: Z = 0 to " + BASE_HEIGHT + " mm\n" +
                            "2. Letter body: Z = " + BASE_HEIGHT + " to " + (BASE_HEIGHT + LETTER_HEIGHT - BEVEL_DEPTH) + " mm\n" +
                            "3. Letter front (beveled): Z = " + (BASE_HEIGHT + LETTER_HEIGHT - BEVEL_DEPTH) + " to " + (BASE_HEIGHT + LETTER_HEIGHT) + " mm",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        statusLabel.setText("Error: " + ex.getMessage());
                        JOptionPane.showMessageDialog(SignGenerator.this,
                            "Error generating 3MF: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                    } finally {
                        generate3MFButton.setEnabled(true);
                    }
                }
            };
            worker.execute();
        }
    }

    private void generate3MFFile(String text, Font font, File file) throws IOException {
        // Generate the triangles just like for STL
        List<Shape> letterShapes = new ArrayList<>();
        String[] lines = text.split("\n");
        double currentY = 0;

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            Shape lineShape = createTextShape(line, font, 0, currentY);
            if (lineShape != null) {
                letterShapes.add(lineShape);
                Rectangle2D bounds = lineShape.getBounds2D();
                currentY += bounds.getHeight() + 10;
            }
        }

        if (letterShapes.isEmpty()) {
            throw new IOException("No valid text to generate");
        }

        Rectangle2D overallBounds = letterShapes.getFirst().getBounds2D();
        for (int i = 1; i < letterShapes.size(); i++) {
            overallBounds = overallBounds.createUnion(letterShapes.get(i).getBounds2D());
        }

        Rectangle2D baseBounds = new Rectangle2D.Double(
            overallBounds.getX() - BASE_MARGIN,
            overallBounds.getY() - BASE_MARGIN,
            overallBounds.getWidth() + 2 * BASE_MARGIN,
            overallBounds.getHeight() + 2 * BASE_MARGIN
        );

        List<Triangle> triangles = new ArrayList<>();
        addBase(triangles, baseBounds);
        for (Shape shape : letterShapes) {
            addLetterGeometry(triangles, shape);
        }

        // Write 3MF file (it's a ZIP containing XML)
        write3MF(triangles, file);
    }

    private void write3MF(List<Triangle> triangles, File file) throws IOException {
        // 3MF is a ZIP file with specific structure
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new FileOutputStream(file))) {

            // Add [Content_Types].xml
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                "  <Default Extension=\"model\" ContentType=\"application/vnd.ms-package.3dmanufacturing-3dmodel+xml\"/>\n" +
                "</Types>\n").getBytes());
            zos.closeEntry();

            // Add _rels/.rels
            zos.putNextEntry(new java.util.zip.ZipEntry("_rels/.rels"));
            zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Target=\"/3D/3dmodel.model\" Id=\"rel0\" Type=\"http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel\"/>\n" +
                "</Relationships>\n").getBytes());
            zos.closeEntry();

            // Add 3D/3dmodel.model (the main model file with color info)
            zos.putNextEntry(new java.util.zip.ZipEntry("3D/3dmodel.model"));
            write3DModel(zos, triangles);
            zos.closeEntry();
        }
    }

    private void write3DModel(java.util.zip.ZipOutputStream zos, List<Triangle> triangles) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<model unit=\"millimeter\" xml:lang=\"en-US\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n");
        xml.append("  <resources>\n");

        // Define materials/colors
        xml.append("    <basematerials id=\"1\">\n");
        xml.append("      <base name=\"Base\" displaycolor=\"#FF8B4513\"/>\n");  // Brown for base
        xml.append("      <base name=\"Body\" displaycolor=\"#FF4169E1\"/>\n");   // Blue for letter body
        xml.append("      <base name=\"Front\" displaycolor=\"#FFFFD700\"/>\n");  // Gold for letter front
        xml.append("    </basematerials>\n");

        // Build vertex and triangle lists
        List<Point3D> vertices = new ArrayList<>();
        List<Integer> triangleIndices = new ArrayList<>();
        List<Integer> triangleMaterials = new ArrayList<>();

        for (Triangle tri : triangles) {
            int idx1 = addVertex(vertices, tri.p1);
            int idx2 = addVertex(vertices, tri.p2);
            int idx3 = addVertex(vertices, tri.p3);

            triangleIndices.add(idx1);
            triangleIndices.add(idx2);
            triangleIndices.add(idx3);

            // Determine material based on Z height
            double avgZ = (tri.p1.z + tri.p2.z + tri.p3.z) / 3.0;
            int material;
            if (avgZ < BASE_HEIGHT + 0.1) {
                material = 0; // Base
            } else if (avgZ < BASE_HEIGHT + LETTER_HEIGHT - BEVEL_DEPTH + 0.1) {
                material = 1; // Body
            } else {
                material = 2; // Front
            }
            triangleMaterials.add(material);
        }

        // Write mesh object
        xml.append("    <object id=\"2\" type=\"model\">\n");
        xml.append("      <mesh>\n");
        xml.append("        <vertices>\n");
        for (Point3D v : vertices) {
            xml.append(String.format("          <vertex x=\"%.6f\" y=\"%.6f\" z=\"%.6f\"/>\n",
                v.x, v.y, v.z));
        }
        xml.append("        </vertices>\n");
        xml.append("        <triangles>\n");
        for (int i = 0; i < triangleIndices.size(); i += 3) {
            // All three vertices of the triangle use the same material
            int mat = triangleMaterials.get(i / 3);
            xml.append(String.format("          <triangle v1=\"%d\" v2=\"%d\" v3=\"%d\" pid=\"1\" p1=\"%d\" p2=\"%d\" p3=\"%d\"/>\n",
                triangleIndices.get(i),
                triangleIndices.get(i + 1),
                triangleIndices.get(i + 2),
                mat, mat, mat));  // All vertices use same material
        }
        xml.append("        </triangles>\n");
        xml.append("      </mesh>\n");
        xml.append("    </object>\n");
        xml.append("  </resources>\n");
        xml.append("  <build>\n");
        xml.append("    <item objectid=\"2\"/>\n");
        xml.append("  </build>\n");
        xml.append("</model>\n");

        zos.write(xml.toString().getBytes());
    }

    private int addVertex(List<Point3D> vertices, Point3D p) {
        // Check if vertex already exists (for efficiency)
        for (int i = 0; i < vertices.size(); i++) {
            Point3D existing = vertices.get(i);
            if (Math.abs(existing.x - p.x) < 0.0001 &&
                Math.abs(existing.y - p.y) < 0.0001 &&
                Math.abs(existing.z - p.z) < 0.0001) {
                return i;
            }
        }
        vertices.add(p);
        return vertices.size() - 1;
    }

    private void writeSTL(List<Triangle> triangles, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("solid sign\n");

            for (Triangle tri : triangles) {
                writer.write(String.format("  facet normal %.6f %.6f %.6f\n",
                    tri.normal.x, tri.normal.y, tri.normal.z));
                writer.write("    outer loop\n");
                writer.write(String.format("      vertex %.6f %.6f %.6f\n",
                    tri.p1.x, tri.p1.y, tri.p1.z));
                writer.write(String.format("      vertex %.6f %.6f %.6f\n",
                    tri.p2.x, tri.p2.y, tri.p2.z));
                writer.write(String.format("      vertex %.6f %.6f %.6f\n",
                    tri.p3.x, tri.p3.y, tri.p3.z));
                writer.write("    endloop\n");
                writer.write("  endfacet\n");
            }

            writer.write("endsolid sign\n");
        }
    }

    private static class Point3D {
        double x, y, z;
        Point3D(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z;
        }
    }

    private static class Triangle {
        Point3D p1, p2, p3, normal;
        Triangle(Point3D p1, Point3D p2, Point3D p3, Point3D normal) {
            this.p1 = p1; this.p2 = p2; this.p3 = p3; this.normal = normal;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SignGenerator generator = new SignGenerator();
            generator.setVisible(true);
        });
    }
}
