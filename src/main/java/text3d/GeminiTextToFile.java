package text3d;

import eu.mihosoft.jcsg.CSG;
import eu.mihosoft.jcsg.Extrude;
import eu.mihosoft.jcsg.Cube;
import eu.mihosoft.vvecmath.Vector3d;
import eu.mihosoft.vvecmath.Transform;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.algorithm.Orientation;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.PathIterator;
import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.zip.*;

// Static imports for BASE_HEIGHT, LETTER_HEIGHT, BEVEL_DEPTH, BASE_MARGIN
import static text3d.SignGenerator.*;

public class GeminiTextToFile implements TextToFile {

    @Override
    public void generateFile(String text, Font font, File file, OutputFormat format, TextAlign align) throws IOException {
        // 1. Generate 2D Polygons via JTS
        List<org.locationtech.jts.geom.Polygon> fullLetterPolys = multilineTextToJTS(text, font, align);
        if (fullLetterPolys.isEmpty()) return;

        // Inset for the colored face and subtraction for the border rim
        List<org.locationtech.jts.geom.Polygon> insetPolys = insetPolygons(fullLetterPolys, -BEVEL_HEIGHT);
        List<org.locationtech.jts.geom.Polygon> borderPolys = subtractPolygons(fullLetterPolys, insetPolys);

        // Calculate total bounds for the base plate
        Envelope env = new Envelope();
        fullLetterPolys.forEach(p -> env.expandToInclude(p.getEnvelopeInternal()));

        // 2. Build the Base Plate
        // JCSG Cube is centered at 0,0,0. We move Z up by half its height so bottom is at Z=0.
        double baseW = env.getWidth() + (BASE_MARGIN * 2);
        double baseH = env.getHeight() + (BASE_MARGIN * 2);
        CSG basePlate = new Cube(baseW, baseH, BASE_HEIGHT).toCSG();

        double cx = env.getMinX() + env.getWidth() / 2.0;
        double cy = env.getMinY() + env.getHeight() / 2.0;
        basePlate = basePlate.transformed(Transform.unity().translate(cx, cy, BASE_HEIGHT / 2.0));

        // 3. Build the Letter Components
        Transform textRise = Transform.unity().translateZ(BASE_HEIGHT);

        // Body: The bottom part of the letters (stalk)
        CSG letterBody = createExtrusion(fullLetterPolys, LETTER_HEIGHT - 1.0).transformed(textRise);

        // Top: The Rim (same color as body) and Inlay (different color)
        Transform topRise = textRise.translateZ(LETTER_HEIGHT - 1.0);
        CSG letterRim = createExtrusion(borderPolys, 1.0).transformed(topRise);
        CSG letterInlay = createExtrusion(insetPolys, 1.0).transformed(topRise);

        // 4. Export logic
        if (format == OutputFormat.STL) {
            // STL is a single mesh; union everything
            CSG combined = basePlate.union(letterBody).union(letterRim).union(letterInlay);
            Files.writeString(file.toPath(), combined.toStlString());
        } else {
            // 3MF supports multi-material; group by intended color
            Map<String, CSG> parts = new LinkedHashMap<>();
            parts.put("Main_Structure", basePlate.union(letterBody).union(letterRim));
            parts.put("Text_Inlay", letterInlay);
            export3MF(file, parts);
        }
    }

    private List<org.locationtech.jts.geom.Polygon> multilineTextToJTS(String text, Font font, TextAlign align) {
        String[] lines = text.split("\n");
        FontRenderContext frc = new FontRenderContext(null, true, true);
        double lineSpacing = font.getSize() * 1.2;

        double maxWidth = 0;
        double[] lineWidths = new double[lines.length];

        // Pass 1: Measure widths
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) continue;
            GlyphVector gv = font.createGlyphVector(frc, lines[i]);
            lineWidths[i] = gv.getVisualBounds().getWidth();
            maxWidth = Math.max(maxWidth, lineWidths[i]);
        }

        // Pass 2: Create Polygons
        List<org.locationtech.jts.geom.Polygon> allPolys = new ArrayList<>();
        double yOffset = 0;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                double xOffset = switch (align) {
                    case LEFT -> 0;
                    case CENTER -> (maxWidth - lineWidths[i]) / 2.0;
                    case RIGHT -> maxWidth - lineWidths[i];
                };
                GlyphVector gv = font.createGlyphVector(frc, lines[i]);
                allPolys.addAll(shapeToJTS(gv.getOutline((float) xOffset, (float) yOffset)));
            }
            yOffset += lineSpacing;
        }
        return allPolys;
    }

    private List<org.locationtech.jts.geom.Polygon> shapeToJTS(Shape shape) {
        PathIterator iter = shape.getPathIterator(null, 0.01);
        GeometryFactory fact = new GeometryFactory();
        List<Geometry> paths = new ArrayList<>();
        List<Coordinate> coords = new ArrayList<>();

        while (!iter.isDone()) {
            double[] c = new double[6];
            int type = iter.currentSegment(c);
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
                coords.add(new Coordinate(c[0], -c[1]));
            } else if (type == PathIterator.SEG_CLOSE) {
                if (coords.size() > 2) {
                    coords.add(new Coordinate(coords.getFirst()));
                    paths.add(fact.createPolygon(coords.toArray(new Coordinate[0])));
                }
                coords.clear();
            }
            iter.next();
        }

        // Solve nesting: If one path is inside another, subtract it to create a hole
        Geometry combined = fact.createPolygon();
        for (Geometry p : paths) {
            if (combined.contains(p)) combined = combined.difference(p);
            else if (p.contains(combined)) combined = p.difference(combined);
            else combined = combined.union(p);
        }

        List<org.locationtech.jts.geom.Polygon> result = new ArrayList<>();
        for (int i = 0; i < combined.getNumGeometries(); i++) {
            if (combined.getGeometryN(i) instanceof org.locationtech.jts.geom.Polygon p) result.add(p);
        }
        return result;
    }

    private CSG createExtrusion(List<org.locationtech.jts.geom.Polygon> jtsPolys, double depth) {
        CSG result = null;
        Vector3d dir = Vector3d.xyz(0, 0, depth);

        for (org.locationtech.jts.geom.Polygon jp : jtsPolys) {
            // Shell must be CCW for most renderers
            LinearRing shell = jp.getExteriorRing();
            if (Orientation.isCCW(shell.getCoordinates())) shell = shell.reverse();
            CSG charCSG = Extrude.points(dir, ringToVec(shell));

            // Holes must be CW
            for (int i = 0; i < jp.getNumInteriorRing(); i++) {
                LinearRing hole = jp.getInteriorRingN(i);
                if (!Orientation.isCCW(hole.getCoordinates())) hole = hole.reverse();
                charCSG = charCSG.difference(Extrude.points(dir, ringToVec(hole)));
            }
            result = (result == null) ? charCSG : result.union(charCSG);
        }
        return result != null ? result : new Cube(0.01).toCSG();
    }

    private List<Vector3d> ringToVec(LineString ring) {
        List<Vector3d> pts = new ArrayList<>();
        Coordinate[] coords = ring.getCoordinates();
        // JCSG Extrude.points expects an open path (it closes it internally)
        for (int i = 0; i < coords.length - 1; i++) {
            pts.add(Vector3d.xyz(coords[i].x, coords[i].y, 0));
        }
        return pts;
    }

    private List<org.locationtech.jts.geom.Polygon> insetPolygons(List<org.locationtech.jts.geom.Polygon> inputs, double dist) {
        List<org.locationtech.jts.geom.Polygon> res = new ArrayList<>();
        for (var p : inputs) {
            Geometry g = p.buffer(dist, 8, BufferParameters.CAP_ROUND);
            if (g instanceof org.locationtech.jts.geom.Polygon poly) res.add(poly);
            else if (g instanceof MultiPolygon mp) {
                for (int i = 0; i < mp.getNumGeometries(); i++) {
                    if (mp.getGeometryN(i) instanceof org.locationtech.jts.geom.Polygon sp) res.add(sp);
                }
            }
        }
        return res;
    }

    private List<org.locationtech.jts.geom.Polygon> subtractPolygons(List<org.locationtech.jts.geom.Polygon> main, List<org.locationtech.jts.geom.Polygon> minus) {
        GeometryFactory fact = new GeometryFactory();
        Geometry m = fact.buildGeometry(main);
        Geometry s = fact.buildGeometry(minus);
        Geometry res = m.difference(s);
        List<org.locationtech.jts.geom.Polygon> out = new ArrayList<>();
        for (int i = 0; i < res.getNumGeometries(); i++) {
            if (res.getGeometryN(i) instanceof org.locationtech.jts.geom.Polygon p) out.add(p);
        }
        return out;
    }

    // --- 3MF Packager ---

    private void export3MF(File file, Map<String, CSG> parts) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            addZipEntry(zos, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"model\" ContentType=\"application/vnd.ms-package.3dmanufacturing-3dmodel+xml\"/></Types>");
            addZipEntry(zos, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Target=\"/3D/3dmodel.model\" Id=\"rel0\" Type=\"http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel\"/></Relationships>");
            zos.putNextEntry(new ZipEntry("3D/3dmodel.model"));
            zos.write(buildModelXml(parts).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private String buildModelXml(Map<String, CSG> parts) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><model unit=\"millimeter\" xml:lang=\"en-US\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\"><resources>");
        int id = 1;
        for (var entry : parts.entrySet()) {
            sb.append("<object id=\"").append(id).append("\" name=\"").append(entry.getKey()).append("\" type=\"model\"><mesh><vertices>");
            List<eu.mihosoft.jcsg.Polygon> polygons = entry.getValue().getPolygons();
            for (var p : polygons) {
                for (var v : p.vertices) sb.append(String.format("<vertex x=\"%.4f\" y=\"%.4f\" z=\"%.4f\" />", v.pos.getX(), v.pos.getY(), v.pos.getZ()));
            }
            sb.append("</vertices><triangles>");
            int vOffset = 0;
            for (var p : polygons) {
                for (int i = 1; i < p.vertices.size() - 1; i++) {
                    sb.append(String.format("<triangle v1=\"%d\" v2=\"%d\" v3=\"%d\" />", vOffset, vOffset + i, vOffset + i + 1));
                }
                vOffset += p.vertices.size();
            }
            sb.append("</triangles></mesh></object>");
            id++;
        }
        sb.append("</resources><build>");
        for (int i = 1; i < id; i++) sb.append("<item objectid=\"").append(i).append("\" />");
        return sb.append("</build></model>").toString();
    }

    private void addZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}