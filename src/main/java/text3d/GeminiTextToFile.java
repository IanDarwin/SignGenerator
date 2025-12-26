package text3d;

import eu.mihosoft.jcsg.CSG;
import eu.mihosoft.jcsg.Extrude;
import eu.mihosoft.jcsg.Polygon;
import eu.mihosoft.vvecmath.Vector3d;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.buffer.BufferParameters;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.PathIterator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;

public class GeminiTextToFile implements TextToFile {

    private static final double TOTAL_DEPTH = 5.0;
    private static final double FRONT_LAYER_Z = 1.0;
    private static final double BORDER_SIZE = 0.6;

    @Override
    public void generateFile(String text, Font font, File file, OutputFormat format) throws IOException {
        // 1. Convert Text to JTS Polygons
        List<org.locationtech.jts.geom.Polygon> basePolygons = textToJTS(text, font);

        // 2. Create Geometry Components
        // Base Body (The back and main sides)
        CSG mainBody = createExtrusion(basePolygons, TOTAL_DEPTH - FRONT_LAYER_Z);

        // Inset Polygons for the front face
        List<org.locationtech.jts.geom.Polygon> insetPolys = insetPolygons(basePolygons, -BORDER_SIZE);

        // Front Face (The colored "infill")
        CSG frontFace = createExtrusion(insetPolys, FRONT_LAYER_Z)
                .transformed(eu.mihosoft.vvecmath.Transform.unity().translateZ(TOTAL_DEPTH - FRONT_LAYER_Z));

        // Border (The outer rim on the front)
        CSG border = createExtrusion(basePolygons, FRONT_LAYER_Z)
                .difference(createExtrusion(insetPolys, FRONT_LAYER_Z))
                .transformed(eu.mihosoft.vvecmath.Transform.unity().translateZ(TOTAL_DEPTH - FRONT_LAYER_Z));

        // 3. Export
        if (format == OutputFormat.STL) {
            // STL: Union all for a single printable object
            String stl = mainBody.union(border).union(frontFace).toStlString();
            Files.writeString(file.toPath(), stl);
        } else {
            // 3MF: Keep as separate parts for multi-color support
            Map<String, CSG> parts = new HashMap<>();
            parts.put("Body", mainBody);
            parts.put("Border", border);
            parts.put("FrontFace", frontFace);
            threeMFExport(file, parts);
        }
    }

    private List<org.locationtech.jts.geom.Polygon> textToJTS(String text, Font font) {
        FontRenderContext frc = new FontRenderContext(null, true, true);
        GlyphVector gv = font.createGlyphVector(frc, text);
        Shape outline = gv.getOutline();
        PathIterator iter = outline.getPathIterator(null, 0.05);

        GeometryFactory fact = new GeometryFactory();
        List<org.locationtech.jts.geom.Polygon> polys = new ArrayList<>();
        List<Coordinate> coords = new ArrayList<>();

        while (!iter.isDone()) {
            double[] c = new double[6];
            int type = iter.currentSegment(c);
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
                coords.add(new Coordinate(c[0], -c[1]));
            } else if (type == PathIterator.SEG_CLOSE) {
                if (coords.size() > 2) {
                    coords.add(new Coordinate(coords.get(0))); // Close JTS ring
                    polys.add(fact.createPolygon(coords.toArray(new Coordinate[0])));
                }
                coords.clear();
            }
            iter.next();
        }
        return polys;
    }

    private List<org.locationtech.jts.geom.Polygon> insetPolygons(List<org.locationtech.jts.geom.Polygon> inputs, double distance) {
        List<org.locationtech.jts.geom.Polygon> result = new ArrayList<>();
        for (var p : inputs) {
            Geometry g = p.buffer(distance, 4, BufferParameters.CAP_SQUARE);
            if (g instanceof org.locationtech.jts.geom.Polygon poly) result.add(poly);
            else if (g instanceof MultiPolygon mp) {
                for (int i = 0; i < mp.getNumGeometries(); i++)
                    result.add((org.locationtech.jts.geom.Polygon) mp.getGeometryN(i));
            }
        }
        return result;
    }

    private CSG createExtrusion(List<org.locationtech.jts.geom.Polygon> jtsPolys, double depth) {
    CSG result = null;
    Vector3d direction = Vector3d.xyz(0, 0, depth);

    for (org.locationtech.jts.geom.Polygon jp : jtsPolys) {
        // 1. Extrude the Exterior Ring (the main shape)
        List<Vector3d> shellPoints = ringToVectorList(jp.getExteriorRing());
        CSG characterSlab = Extrude.points(direction, shellPoints);

        // 2. Extrude and Subtract each Interior Ring (the holes)
        for (int i = 0; i < jp.getNumInteriorRing(); i++) {
            List<Vector3d> holePoints = ringToVectorList(jp.getInteriorRingN(i));
            CSG holeSlab = Extrude.points(direction, holePoints);
            characterSlab = characterSlab.difference(holeSlab);
        }

        // 3. Union this character into the final result
        if (result == null) {
            result = characterSlab;
        } else {
            result = result.union(characterSlab);
        }
    }
    return result;
}

	// Helper to convert JTS LineString to a list of JCSG Vector3d
	private List<Vector3d> ringToVectorList(org.locationtech.jts.geom.LineString ring) {
		List<Vector3d> pts = new ArrayList<>();
		org.locationtech.jts.geom.Coordinate[] coords = ring.getCoordinates();

		// Extrude.points expects a path where the first and last points are NOT duplicated
		// JTS rings always duplicate the first point at the end, so we skip the last one.
		for (int i = 0; i < coords.length - 1; i++) {
			pts.add(Vector3d.xyz(coords[i].x, coords[i].y, 0));
		}
		return pts;
	}

    public static void threeMFExport(File file, Map<String, CSG> parts) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {

            // 1. OPC Content Types
            addEntry(zos, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"model\" ContentType=\"application/vnd.ms-package.3dmanufacturing-3dmodel+xml\"/></Types>");

            // 2. Relationships
            addEntry(zos, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Target=\"/3D/3dmodel.model\" Id=\"rel0\" Type=\"http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel\"/></Relationships>");

            // 3. Model Geometry
            zos.putNextEntry(new ZipEntry("3D/3dmodel.model"));
            String modelXml = buildModelXml(parts);
            zos.write(modelXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private static String buildModelXml(Map<String, CSG> parts) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><model unit=\"millimeter\" xml:lang=\"en-US\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">");
        sb.append("<resources>");

        int objectId = 1;
        for (var entry : parts.entrySet()) {
            sb.append("<object id=\"").append(objectId).append("\" name=\"").append(entry.getKey()).append("\" type=\"model\"><mesh><vertices>");

            List<Polygon> polygons = entry.getValue().getPolygons();
            // Vertices
            for (Polygon p : polygons) {
                for (var v : p.vertices) {
                    // Correct: v.pos.getX()
                    sb.append(String.format("<vertex x=\"%.4f\" y=\"%.4f\" z=\"%.4f\" />",
                        v.pos.getX(), v.pos.getY(), v.pos.getZ()));
                }
            }
            sb.append("</vertices><triangles>");

            // Triangles (Fan triangulation)
            int vOffset = 0;
            for (Polygon p : polygons) {
                for (int i = 1; i < p.vertices.size() - 1; i++) {
                    sb.append(String.format("<triangle v1=\"%d\" v2=\"%d\" v3=\"%d\" />",
                        vOffset, vOffset + i, vOffset + i + 1));
                }
                vOffset += p.vertices.size();
            }
            sb.append("</triangles></mesh></object>");
            objectId++;
        }

        sb.append("</resources><build>");
        for (int i = 1; i < objectId; i++) {
            sb.append("<item objectid=\"").append(i).append("\" />");
        }
        sb.append("</build></model>");
        return sb.toString();
    }

    private static void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
