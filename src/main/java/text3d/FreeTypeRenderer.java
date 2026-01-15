package text3d;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

public class FreeTypeRenderer implements TextToFile {

    private static final SymbolLookup LNK = SymbolLookup.libraryLookup("libfreetype.so", Arena.global());
    private static final Linker LINKER = Linker.nativeLinker();

    // --- Native Method Handles ---
    private static final MethodHandle FT_Init_FreeType = LINKER.downcallHandle(LNK.find("FT_Init_FreeType").get(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle FT_New_Face = LINKER.downcallHandle(LNK.find("FT_New_Face").get(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle FT_Set_Pixel_Sizes = LINKER.downcallHandle(LNK.find("FT_Set_Pixel_Sizes").get(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle FT_Load_Char = LINKER.downcallHandle(LNK.find("FT_Load_Char").get(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle FT_Outline_Decompose = LINKER.downcallHandle(LNK.find("FT_Outline_Decompose").get(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final float TOTAL_HEIGHT = 2.0f;
    private static final float BEVEL_VAL = 0.1f;
    private static final float SHOULDER_Z = TOTAL_HEIGHT - BEVEL_VAL;

    @Override
    public void generateFile(String text, Font font, File file, OutputFormat format, TextAlign align) throws IOException {
        try (Arena arena = Arena.ofConfined(); FileWriter writer = new FileWriter(file)) {
            writer.write("solid TextSign\n");

            // 1. Setup FreeType
            MemorySegment libPtr = arena.allocate(ValueLayout.ADDRESS);
            FT_Init_FreeType.invokeExact(libPtr);
            MemorySegment library = libPtr.get(ValueLayout.ADDRESS, 0);

            MemorySegment facePtr = arena.allocate(ValueLayout.ADDRESS);
            // In production, map 'font' to a real .ttf path
            String fontPath = "/usr/X11R6/lib/X11/fonts/TTF/DejaVuSans.ttf";
            FT_New_Face.invokeExact(library, arena.allocateFrom(fontPath), 0L, facePtr);
            MemorySegment face = facePtr.get(ValueLayout.ADDRESS, 0);
            FT_Set_Pixel_Sizes.invokeExact(face, 0, 48);

            // 2. Extract Vectors & Generate Mesh
            List<Contour> allGlyphContours = fetchGlyphVectors(text, face, arena);

            for (Contour contour : allGlyphContours) {
                boolean isHole = contour.isClockwise();
                float currentInset = isHole ? -BEVEL_VAL : BEVEL_VAL;

                // Walls: Base -> Shoulder
                writeWall(writer, contour.points, contour.points, 0.0f, SHOULDER_Z);

                // Walls: Shoulder -> Crown (Bevel)
                List<Vector2> insetPoints = calculateInset(contour.points, currentInset);
                writeWall(writer, contour.points, insetPoints, SHOULDER_Z, TOTAL_HEIGHT);

                // Top Cap
                tessellateTop(writer, insetPoints, TOTAL_HEIGHT);
            }

            // 3. Base Plate
            writeBasePlate(writer, allGlyphContours);

            writer.write("endsolid TextSign\n");
        } catch (Throwable t) {
            throw new IOException("Native FreeType error", t);
        }
    }

    private List<Contour> fetchGlyphVectors(String text, MemorySegment face, Arena arena) throws Throwable {
        List<Contour> all = new ArrayList<>();
        List<Vector2> current = new ArrayList<>();

        var moveToFunc = MethodHandles.lookup().findStatic(FreeTypeRenderer.class, "handleMoveTo",
            MethodType.methodType(int.class, List.class, List.class, MemorySegment.class, MemorySegment.class));
        var lineToFunc = MethodHandles.lookup().findStatic(FreeTypeRenderer.class, "handleLineTo",
            MethodType.methodType(int.class, List.class, MemorySegment.class, MemorySegment.class));

        MemorySegment funcs = arena.allocate(MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("move_to"), ValueLayout.ADDRESS.withName("line_to"),
            ValueLayout.ADDRESS.withName("conic_to"), ValueLayout.ADDRESS.withName("cubic_to"),
            ValueLayout.JAVA_INT.withName("shift"), ValueLayout.JAVA_LONG.withName("delta")
        ));
        funcs.set(ValueLayout.ADDRESS, 0, LINKER.upcallStub(moveToFunc.bindTo(all).bindTo(current), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS), arena));
        funcs.set(ValueLayout.ADDRESS, 8, LINKER.upcallStub(lineToFunc.bindTo(current), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS), arena));
        funcs.set(ValueLayout.ADDRESS, 16, funcs.get(ValueLayout.ADDRESS, 8)); // conic as lines
        funcs.set(ValueLayout.ADDRESS, 24, funcs.get(ValueLayout.ADDRESS, 8)); // cubic as lines

        for (char c : text.toCharArray()) {
            FT_Load_Char.invokeExact(face, (long) c, 1 << 0);
            // Get the pointer to the GlyphSlot (offset 152 in FT_FaceRec)
            MemorySegment glyphSlotPtr = face.get(ValueLayout.ADDRESS, 152);
            // The outline is a struct starting at offset 16 within the GlyphSlot
            // We use asSlice(offset, size) to point to it.
            // FT_Outline is roughly 40 bytes, but we only need the pointer for the Decompose call.
            MemorySegment outline = glyphSlotPtr.reinterpret(512).asSlice(16);
            FT_Outline_Decompose.invokeExact(outline, funcs, MemorySegment.NULL);
        }
        if (!current.isEmpty()) all.add(new Contour(new ArrayList<>(current)));
        return all;
    }

    private static int handleMoveTo(List<Contour> all, List<Vector2> current, MemorySegment to, MemorySegment user) {
        if (!current.isEmpty()) all.add(new Contour(new ArrayList<>(current)));
        current.clear();
        current.add(readVector(to));
        return 0;
    }

    private static int handleLineTo(List<Vector2> current, MemorySegment to, MemorySegment user) {
        current.add(readVector(to));
        return 0;
    }

    private static Vector2 readVector(MemorySegment ptr) {
        return new Vector2(ptr.get(ValueLayout.JAVA_LONG, 0) / 64.0f, -ptr.get(ValueLayout.JAVA_LONG, 8) / 64.0f);
    }

    private void writeBasePlate(FileWriter writer, List<Contour> contours) throws IOException {
        float minX = 0, minY = 0, maxX = 0, maxY = 0;
        for(var c : contours) for(var p : c.points) {
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
        }
        // Simplified: render one box covering the total area + margin
        writeFacet(writer, new Vector2(minX-2, minY-2), new Vector2(maxX+2, minY-2), new Vector2(minX-2, maxY+2), -2, -2, -2);
    }

    private void tessellateTop(FileWriter w, List<Vector2> pts, float z) throws IOException {
        for (int i = 1; i < pts.size() - 1; i++) writeFacet(w, pts.get(0), pts.get(i), pts.get(i+1), z, z, z);
    }

    private void writeWall(FileWriter w, List<Vector2> b, List<Vector2> t, float z1, float z2) throws IOException {
        for (int i = 0; i < b.size(); i++) {
            int n = (i + 1) % b.size();
            writeFacet(w, b.get(i), b.get(n), t.get(i), z1, z1, z2);
            writeFacet(w, b.get(n), t.get(n), t.get(i), z1, z2, z2);
        }
    }

    private void writeFacet(FileWriter w, Vector2 v1, Vector2 v2, Vector2 v3, float z1, float z2, float z3) throws IOException {
        w.write("  facet normal 0 0 0\n    outer loop\n");
        w.write(String.format("      vertex %.4f %.4f %.4f\n", v1.x, v1.y, z1));
        w.write(String.format("      vertex %.4f %.4f %.4f\n", v2.x, v2.y, z2));
        w.write(String.format("      vertex %.4f %.4f %.4f\n", v3.x, v3.y, z3));
        w.write("    endloop\n  endfacet\n");
    }

    private List<Vector2> calculateInset(List<Vector2> pts, float delta) {
        List<Vector2> inset = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            Vector2 p1 = pts.get((i + pts.size() - 1) % pts.size());
            Vector2 p2 = pts.get(i);
            Vector2 p3 = pts.get((i + 1) % pts.size());
            Vector2 n1 = p2.sub(p1).perp().normalize();
            Vector2 n2 = p3.sub(p2).perp().normalize();
            Vector2 bisector = n1.add(n2).normalize();
            float miterDist = delta / (float) Math.sqrt((1 + n1.dot(n2)) / 2);
            inset.add(p2.add(bisector.mul(miterDist)));
        }
        return inset;
    }

    record Vector2(float x, float y) {
        Vector2 add(Vector2 o) { return new Vector2(x + o.x, y + o.y); }
        Vector2 sub(Vector2 o) { return new Vector2(x - o.x, y - o.y); }
        Vector2 mul(float s) { return new Vector2(x * s, y * s); }
        Vector2 perp() { return new Vector2(-y, x); }
        float dot(Vector2 o) { return x * o.x + y * o.y; }
        Vector2 normalize() { float m = (float)Math.sqrt(x*x+y*y); return new Vector2(x/m, y/m); }
    }

    record Contour(List<Vector2> points) {
        boolean isClockwise() {
            float sum = 0;
            for (int i = 0; i < points.size(); i++) {
                Vector2 p1 = points.get(i), p2 = points.get((i + 1) % points.size());
                sum += (p2.x - p1.x) * (p2.y + p1.y);
            }
            return sum > 0;
        }
    }
}