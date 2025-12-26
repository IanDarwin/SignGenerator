package text3d;

public enum OutputFormat {
    STL(".stl"),
    THREEMF(".3mf");

    private final String ext;

    OutputFormat(String ext) {
        this.ext = ext;
    }

    String ext() {
        return ext;
    }
}
