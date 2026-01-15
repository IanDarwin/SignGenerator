package text3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;

/** Standalone tool to try out dynamic loading on various OSes. */
public class FreeLoader {

	final static String osName = System.getProperty("os.name");

	static OsInfo[] oses = {
		new OsInfo("OpenBSD",
				"libfreetype.so",
				List.of("/usr/X11R6/lib/X11/fonts", "/usr/local/share/fonts")),
		// This should be named "macOS" or "Darwin" but Java still uses this name,
		new OsInfo("Mac Os X",
				"libfreetype.dylib",
				List.of("/System/Library/Fonts")),
	};

	public static Optional<OsInfo> getOsInfo() {
		for (OsInfo os : oses) {
			if (os.name().equals(osName)) {
				return Optional.of(os);
			}
		}
		return Optional.empty();
	}

	public static Optional<String> loadFreetypeLibrary() {
		var ret = getOsInfo();
		if (ret.isPresent()) {
			var os = ret.get();
			if (os.name().equals(osName)) {
				System.out.println(os);
				final SymbolLookup LNK = SymbolLookup.libraryLookup(os.libraryName(), Arena.global());
				return Optional.of("Success: LNK = " + LNK);
			}
		}
		return Optional.of("OsInfo not matched");
	}

	final static List<Path> fontPaths = new ArrayList<>();
	public static Path getFontFile(String fontName) throws IOException {
		System.out.printf("getFontFile(%s)\n", fontName);
		var lcFontName = fontName.toLowerCase();
		if (getOsInfo().isEmpty()) {
			throw new IllegalStateException("Don't know font list for " + osName);
		}
		fontPaths.clear();
		for (String s : getOsInfo().get().fontDirs()) {
			Path path = Path.of(s);
			System.out.println("Looking in " + path);
			Files.walk(path, Integer.MAX_VALUE)
					.filter(p->p.getFileName().toString().endsWith(".ttf"))
					.filter(p -> p.getFileName().toString().toLowerCase().endsWith(lcFontName + ".ttf"))
					.forEach(fontPaths::add);
		}
		if (fontPaths.isEmpty()) {
			throw new IllegalStateException("No fontname found for " + fontName);
		}
		if (fontPaths.size() == 1) {
			return fontPaths.getFirst();
		}
		throw new IllegalStateException("Multiple fontnames found for " + fontName + "\n" + fontPaths);
	}

	static void main() throws IOException {
		var lib = loadFreetypeLibrary();
		lib.ifPresent(System.out::println);
		System.out.println("Found: " + getFontFile("DejaVuSans"));
	}
}
