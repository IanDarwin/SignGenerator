package text3d;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.triangulate.polygon.ConstrainedDelaunayTriangulator;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static text3d.SignGenerator.*;


/** All the geometry calculations for the Sign Generator.
 * @author Claude.ai, prompted and iterated by Ian Darwin
 */
public class ClaudeTextToFile implements TextToFile {

    public void generateFile(String text, Font font, File file, OutputFormat format, TextAlign align) throws IOException {
        java.util.List<Shape> letterShapes = new ArrayList<>();
        String[] lines = text.split("\n");
        double currentY = 0;
        double maxWidth = 0;

        // First pass: create shapes and find max width
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            Shape lineShape = createTextShape(line, font, 0, currentY);
            if (lineShape != null) {
                letterShapes.add(lineShape);
                Rectangle2D bounds = lineShape.getBounds2D();
                maxWidth = Math.max(maxWidth, bounds.getWidth());
                currentY += bounds.getHeight() + 10;
            }
        }

        if (letterShapes.isEmpty()) {
            throw new IOException("No valid text to generate");
        }

        // Second pass: apply alignment if needed
        if (align != TextAlign.LEFT) {
            java.util.List<Shape> alignedShapes = new ArrayList<>();
            currentY = 0;

            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                Shape originalShape = letterShapes.get(alignedShapes.size());
                Rectangle2D bounds = originalShape.getBounds2D();
                double lineWidth = bounds.getWidth();

                double xOffset = switch (align) {
                    case CENTER -> (maxWidth - lineWidth) / 2;
                    case RIGHT -> maxWidth - lineWidth;
                    default -> 0;
                };

                Shape lineShape = createTextShape(line, font, xOffset, currentY);
                if (lineShape != null) {
                    alignedShapes.add(lineShape);
                    currentY += bounds.getHeight() + 10;
                }
            }
            letterShapes = alignedShapes;
        }

        Rectangle2D overallBounds = letterShapes.getFirst().getBounds2D();
        for (int i = 1; i < letterShapes.size(); i++) {
            overallBounds = overallBounds.createUnion(letterShapes.get(i).getBounds2D());
        }

        Rectangle2D baseBounds = new Rectangle2D.Double(
            overallBounds.getX() - DEFAULT_BASE_MARGIN,
            overallBounds.getY() - DEFAULT_BASE_MARGIN,
            overallBounds.getWidth() + 2 * DEFAULT_BASE_MARGIN,
            overallBounds.getHeight() + 2 * DEFAULT_BASE_MARGIN
        );

        java.util.List<Triangle> triangles = new ArrayList<>();

        addBase(triangles, baseBounds);

        for (Shape shape : letterShapes) {
            addLetterGeometry(triangles, shape);
        }

        switch (format) {
            case STL:
                writeSTL(triangles, file);
                break;
            case THREEMF:
                write3MF(triangles, file);
                break;
        }
    }

    private void write3MF(java.util.List<Triangle> triangles, File file) throws IOException {
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

            // Add 3D/3dmodel.model (the main model file)
            zos.putNextEntry(new java.util.zip.ZipEntry("3D/3dmodel.model"));
            write3DModelXML(zos, triangles);
            zos.closeEntry();
        }
    }

    private void write3DModelXML(java.util.zip.ZipOutputStream zos, java.util.List<Triangle> triangles) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<model unit=\"millimeter\" xml:lang=\"en-US\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n");
        xml.append("  <resources>\n");

        // Build vertex and triangle lists
        java.util.List<Point3D> vertices = new ArrayList<>();
        java.util.List<int[]> triangleIndices = new ArrayList<>();

        for (Triangle tri : triangles) {
            int idx1 = addVertex(vertices, tri.p1);
            int idx2 = addVertex(vertices, tri.p2);
            int idx3 = addVertex(vertices, tri.p3);

            triangleIndices.add(new int[]{idx1, idx2, idx3});
        }

        // Write mesh object (no materials/colors)
        xml.append("    <object id=\"2\" type=\"model\">\n");
        xml.append("      <mesh>\n");
        xml.append("        <vertices>\n");
        for (Point3D v : vertices) {
            xml.append(String.format("          <vertex x=\"%.6f\" y=\"%.6f\" z=\"%.6f\"/>\n",
                v.x, v.y, v.z));
        }
        xml.append("        </vertices>\n");
        xml.append("        <triangles>\n");
        for (int[] tri : triangleIndices) {
            xml.append(String.format("          <triangle v1=\"%d\" v2=\"%d\" v3=\"%d\"/>\n",
                tri[0], tri[1], tri[2]));
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

    private int addVertex(java.util.List<Point3D> vertices, Point3D p) {
        // Check if vertex already exists with very tight tolerance
        // This is critical for eliminating open edges
        final double EPSILON = 1e-9;
        for (int i = 0; i < vertices.size(); i++) {
            Point3D existing = vertices.get(i);
            if (Math.abs(existing.x - p.x) < EPSILON &&
                Math.abs(existing.y - p.y) < EPSILON &&
                Math.abs(existing.z - p.z) < EPSILON) {
                return i;
            }
        }
        vertices.add(p);
        return vertices.size() - 1;
    }


    private void writeSTL(List<Triangle> triangles, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("solid TextSign ; ClaudeRenderer\n");

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

            writer.write("endsolid TextSign\n");
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

    private Shape createTextShape(String text, Font font, double x, double y) {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        FontRenderContext frc = g2d.getFontRenderContext();
        GlyphVector gv = font.createGlyphVector(frc, text);
        Shape shape = gv.getOutline((float)x, (float)y);
        g2d.dispose();
        return shape;
    }

    private void addBase(java.util.List<Triangle> triangles, Rectangle2D bounds) {
        double x1 = bounds.getX() * SCALE_FACTOR;
        double y1 = -bounds.getY() * SCALE_FACTOR;
        double x2 = (bounds.getX() + bounds.getWidth()) * SCALE_FACTOR;
        double y2 = -(bounds.getY() + bounds.getHeight()) * SCALE_FACTOR;
        double z0 = 0;
        double z1 = DEFAULT_BASE_HEIGHT;

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

    private void addLetterGeometry(java.util.List<Triangle> triangles, Shape shape) {
        PathIterator pi = shape.getPathIterator(null, 0.5);
        java.util.List<java.util.List<Point2D>> allContours = new ArrayList<>();
        java.util.List<Point2D> currentContour = new ArrayList<>();
        double[] coords = new double[6];
        Point2D firstPoint = null;

        while (!pi.isDone()) {
            int type = pi.currentSegment(coords);

            if (type == PathIterator.SEG_MOVETO) {
                if (currentContour.size() > 2) {
                    allContours.add(new ArrayList<>(currentContour));
                }
                currentContour.clear();
                firstPoint = new Point2D.Double(coords[0], coords[1]);
                currentContour.add(firstPoint);
            } else if (type == PathIterator.SEG_LINETO) {
                Point2D newPoint = new Point2D.Double(coords[0], coords[1]);
                // Only add if it's not a duplicate of the last point
                if (currentContour.isEmpty() ||
                    currentContour.getLast().distance(newPoint) > 0.001) {
                    currentContour.add(newPoint);
                }
            } else if (type == PathIterator.SEG_CLOSE) {
                // Ensure the contour is actually closed
                if (!currentContour.isEmpty() && firstPoint != null) {
                    Point2D lastPoint = currentContour.getLast();
                    if (lastPoint.distance(firstPoint) > 0.001) {
                        currentContour.add(new Point2D.Double(firstPoint.getX(), firstPoint.getY()));
                    }
                }
                if (currentContour.size() > 2) {
                    allContours.add(new ArrayList<>(currentContour));
                }
                currentContour.clear();
                firstPoint = null;
            }
            pi.next();
        }

        // Handle unclosed contour at end
        if (currentContour.size() > 2) {
            if (firstPoint != null) {
                Point2D lastPoint = currentContour.getLast();
                if (lastPoint.distance(firstPoint) > 0.001) {
                    currentContour.add(new Point2D.Double(firstPoint.getX(), firstPoint.getY()));
                }
            }
            allContours.add(new ArrayList<>(currentContour));
        }

        // Separate outer contours and holes
        java.util.List<java.util.List<Point2D>> outerContours = new ArrayList<>();
        java.util.List<java.util.List<Point2D>> holeContours = new ArrayList<>();

        for (java.util.List<Point2D> contour : allContours) {
            if (contour.size() > 2) {
                if (isClockwise(contour)) {
                    outerContours.add(contour);
                } else {
                    holeContours.add(contour);
                }
            }
        }

        // Process EACH outer contour with its associated holes
        // Match holes to their containing outer contours
        for (java.util.List<Point2D> outer : outerContours) {
            java.util.List<java.util.List<Point2D>> matchingHoles = new ArrayList<>();

            for (java.util.List<Point2D> hole : holeContours) {
                // Simple containment test: check if first hole point is inside outer contour
                if (!hole.isEmpty() && pointInPolygon(hole.getFirst(), outer)) {
                    matchingHoles.add(hole);
                }
            }

            addLetterWithProperTriangulation(triangles, outer, matchingHoles);
        }
    }

    private boolean pointInPolygon(Point2D point, java.util.List<Point2D> polygon) {
        int intersections = 0;
        for (int i = 0; i < polygon.size(); i++) {
            Point2D p1 = polygon.get(i);
            Point2D p2 = polygon.get((i + 1) % polygon.size());

            if ((p1.getY() > point.getY()) != (p2.getY() > point.getY())) {
                double xIntersect = (p2.getX() - p1.getX()) * (point.getY() - p1.getY()) /
                                   (p2.getY() - p1.getY()) + p1.getX();
                if (point.getX() < xIntersect) {
                    intersections++;
                }
            }
        }
        return (intersections % 2) == 1;
    }

    private void addLetterWithProperTriangulation(java.util.List<Triangle> triangles, java.util.List<Point2D> outer, java.util.List<java.util.List<Point2D>> holes) {
        double zBase = DEFAULT_BASE_HEIGHT;
        double zTop = DEFAULT_BASE_HEIGHT + DEFAULT_LETTER_HEIGHT - DEFAULT_BEVEL_HEIGHT;
        double zBevel = DEFAULT_BASE_HEIGHT + DEFAULT_LETTER_HEIGHT;
        double bevelInset = DEFAULT_BEVEL_HEIGHT * 0.7;

        // Pre-calculate beveled vertices for outer contour
        Point2D center = calculateCentroid(outer);
        double cx = center.getX() * SCALE_FACTOR;
        double cy = -center.getY() * SCALE_FACTOR;

        java.util.List<Point3D> outerTopVertices = new ArrayList<>();
        java.util.List<Point3D> outerBevelVertices = new ArrayList<>();

        for (Point2D p : outer) {
            double x = p.getX() * SCALE_FACTOR;
            double y = -p.getY() * SCALE_FACTOR;

            outerTopVertices.add(new Point3D(x, y, zTop));

            // Calculate inset position for bevel
            double dx = cx - x;
            double dy = cy - y;
            double len = Math.sqrt(dx*dx + dy*dy);
            if (len > 0.001) {
                double bx = x + (dx/len) * bevelInset;
                double by = y + (dy/len) * bevelInset;
                outerBevelVertices.add(new Point3D(bx, by, zBevel));
            } else {
                // Degenerate case: point is at center
                outerBevelVertices.add(new Point3D(x, y, zBevel));
            }
        }

        // Add side walls for outer contour using pre-calculated vertices
        for (int i = 0; i < outer.size(); i++) {
            Point2D p1 = outer.get(i);
            Point2D p2 = outer.get((i + 1) % outer.size());

            double x1 = p1.getX() * SCALE_FACTOR;
            double y1 = -p1.getY() * SCALE_FACTOR;
            double x2 = p2.getX() * SCALE_FACTOR;
            double y2 = -p2.getY() * SCALE_FACTOR;

            Point3D bottomP1 = new Point3D(x1, y1, zBase);
            Point3D bottomP2 = new Point3D(x2, y2, zBase);
            Point3D topP1 = outerTopVertices.get(i);
            Point3D topP2 = outerTopVertices.get((i + 1) % outer.size());

            addQuad(triangles, bottomP1, bottomP2, topP2, topP1, null);
        }

        // Pre-calculate beveled vertices for holes
        java.util.List<java.util.List<Point3D>> holesTopVertices = new ArrayList<>();
        java.util.List<java.util.List<Point3D>> holesBevelVertices = new ArrayList<>();

        for (java.util.List<Point2D> hole : holes) {
            Point2D holeCenter = calculateCentroid(hole);
            double hcx = holeCenter.getX() * SCALE_FACTOR;
            double hcy = -holeCenter.getY() * SCALE_FACTOR;

            java.util.List<Point3D> holeTopVerts = new ArrayList<>();
            java.util.List<Point3D> holeBevelVerts = new ArrayList<>();

            for (Point2D p : hole) {
                double x = p.getX() * SCALE_FACTOR;
                double y = -p.getY() * SCALE_FACTOR;

                holeTopVerts.add(new Point3D(x, y, zTop));

                // For holes, inset is OUTWARD (away from hole center)
                double dx = x - hcx;
                double dy = y - hcy;
                double len = Math.sqrt(dx*dx + dy*dy);
                if (len > 0.001) {
                    double bx = x + (dx/len) * bevelInset;
                    double by = y + (dy/len) * bevelInset;
                    holeBevelVerts.add(new Point3D(bx, by, zBevel));
                } else {
                    holeBevelVerts.add(new Point3D(x, y, zBevel));
                }
            }

            holesTopVertices.add(holeTopVerts);
            holesBevelVertices.add(holeBevelVerts);
        }

        // Add side walls for holes using pre-calculated vertices
        for (int h = 0; h < holes.size(); h++) {
            java.util.List<Point2D> hole = holes.get(h);
            java.util.List<Point3D> holeTopVerts = holesTopVertices.get(h);

            for (int i = 0; i < hole.size(); i++) {
                Point2D p1 = hole.get(i);
                Point2D p2 = hole.get((i + 1) % hole.size());

                double x1 = p1.getX() * SCALE_FACTOR;
                double y1 = -p1.getY() * SCALE_FACTOR;
                double x2 = p2.getX() * SCALE_FACTOR;
                double y2 = -p2.getY() * SCALE_FACTOR;

                Point3D bottomP1 = new Point3D(x1, y1, zBase);
                Point3D bottomP2 = new Point3D(x2, y2, zBase);
                Point3D topP1 = holeTopVerts.get(i);
                Point3D topP2 = holeTopVerts.get((i + 1) % hole.size());

                // Inward-facing walls (reversed winding)
                addQuad(triangles, bottomP1, topP1, topP2, bottomP2, null);
            }
        }

        // Triangulate bottom face (ring if holes exist)
        java.util.List<Point2D[]> bottomTris = triangulatePolygonWithHoles(outer, holes);
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

        // Add beveled outer edge using pre-calculated vertices
        for (int i = 0; i < outer.size(); i++) {
            int nextI = (i + 1) % outer.size();
            Point3D topP1 = outerTopVertices.get(i);
            Point3D topP2 = outerTopVertices.get(nextI);
            Point3D bevelP1 = outerBevelVertices.get(i);
            Point3D bevelP2 = outerBevelVertices.get(nextI);

            addQuad(triangles, topP1, topP2, bevelP2, bevelP1, null);
        }

        // Add beveled hole edges using pre-calculated vertices
        for (int h = 0; h < holes.size(); h++) {
            java.util.List<Point3D> holeTopVerts = holesTopVertices.get(h);
            java.util.List<Point3D> holeBevelVerts = holesBevelVertices.get(h);

            for (int i = 0; i < holeTopVerts.size(); i++) {
                int nextI = (i + 1) % holeTopVerts.size();
                Point3D topP1 = holeTopVerts.get(i);
                Point3D topP2 = holeTopVerts.get(nextI);
                Point3D bevelP1 = holeBevelVerts.get(i);
                Point3D bevelP2 = holeBevelVerts.get(nextI);

                // Reversed winding for inward-facing bevel
                addQuad(triangles, topP1, bevelP1, bevelP2, topP2, null);
            }
        }

        // Triangulate the top beveled surface (including holes)
        java.util.List<Point2D> bevelOuter = new ArrayList<>();
        for (Point3D p : outerBevelVertices) {
            bevelOuter.add(new Point2D.Double(p.x / SCALE_FACTOR, -p.y / SCALE_FACTOR));
        }

        java.util.List<java.util.List<Point2D>> bevelHoles = new ArrayList<>();
        for (java.util.List<Point3D> holeBevelVerts : holesBevelVertices) {
            java.util.List<Point2D> bevelHole = new ArrayList<>();
            for (Point3D p : holeBevelVerts) {
                bevelHole.add(new Point2D.Double(p.x / SCALE_FACTOR, -p.y / SCALE_FACTOR));
            }
            bevelHoles.add(bevelHole);
        }

        java.util.List<Point2D[]> topTris = triangulatePolygonWithHoles(bevelOuter, bevelHoles);
        for (Point2D[] tri : topTris) {
            double x1 = tri[0].getX() * SCALE_FACTOR;
            double y1 = -tri[0].getY() * SCALE_FACTOR;
            double x2 = tri[1].getX() * SCALE_FACTOR;
            double y2 = -tri[1].getY() * SCALE_FACTOR;
            double x3 = tri[2].getX() * SCALE_FACTOR;
            double y3 = -tri[2].getY() * SCALE_FACTOR;

            triangles.add(new Triangle(
                new Point3D(x1, y1, zBevel),
                new Point3D(x2, y2, zBevel),
                new Point3D(x3, y3, zBevel),
                new Point3D(0, 0, 1)));
        }
    }

    /**
     * Triangulate a polygon with holes using JTS ConstrainedDelaunayTriangulator.
     *
     * @return List of triangles as Point2D triplets
     */
    private java.util.List<Point2D[]> triangulatePolygonWithHoles(java.util.List<Point2D> outer, java.util.List<java.util.List<Point2D>> holes) {
        java.util.List<Point2D[]> result = new ArrayList<>();

        try {
            GeometryFactory gf = new GeometryFactory();

            // Remove duplicate/near-duplicate points from outer contour
            java.util.List<Point2D> cleanOuter = removeDuplicates(outer, 0.01);

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
                java.util.List<Point2D> hole = holes.get(h);
                java.util.List<Point2D> cleanHole = removeDuplicates(hole, 0.01);

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
            org.locationtech.jts.geom.Geometry triangulated = triangulator.getResult();

            // Extract triangles from result
            for (int i = 0; i < triangulated.getNumGeometries(); i++) {
                org.locationtech.jts.geom.Geometry geom = triangulated.getGeometryN(i);
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
    private java.util.List<Point2D> removeDuplicates(java.util.List<Point2D> points, double tolerance) {
        java.util.List<Point2D> result = new ArrayList<>();
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

    private boolean isClockwise(java.util.List<Point2D> points) {
        double sum = 0;
        for (int i = 0; i < points.size(); i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % points.size());
            sum += (p2.getX() - p1.getX()) * (p2.getY() + p1.getY());
        }
        return sum > 0;
    }

    private Point2D calculateCentroid(java.util.List<Point2D> points) {
        double sumX = 0, sumY = 0;
        for (Point2D p : points) {
            sumX += p.getX();
            sumY += p.getY();
        }
        return new Point2D.Double(sumX / points.size(), sumY / points.size());
    }

    private void addQuad(java.util.List<Triangle> triangles, Point3D p1, Point3D p2, Point3D p3, Point3D p4, Point3D normal) {
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


}
