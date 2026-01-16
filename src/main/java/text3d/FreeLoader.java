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

	private final static String osName = System.getProperty("os.name");

	private final static OsInfo[] oses = {
		new OsInfo("OpenBSD",
				"libfreetype.so",
				List.of("/usr/X11R6/lib/X11/fonts", "/usr/local/share/fonts")),
		// This should be named "macOS" or "Darwin" but Java still uses this name,
		new OsInfo("Mac Os X",
				"libfreetype.dylib",
				List.of("/System/Library/Fonts")),
		new OsInfo("Weindows",
				"libfreetype.dll",
				List.of("C:/Windows/Fonts")),
	};

	/**
	 * Find the OsInfo structure for the OS we are running on
	 * @return An Optional, containing the correct OsInfo if found, else empty
	 */
	public static Optional<OsInfo> getOsInfo() {
		for (OsInfo os : oses) {
			if (os.name().equals(osName)) {
				return Optional.of(os);
			}
		}
		return Optional.empty();
	}

	/**
	 * Does what it says on the tin: loads the Freetype Library
	 * @return The SymbolLookup to access FreeType via FFI
	 */
	public static SymbolLookup loadFreetypeLibrary() {
		var ret = getOsInfo();
		if (ret.isPresent()) {
			var os = ret.get();
			if (os.name().equals(osName)) {
				System.out.println(os);
				final SymbolLookup LNK = SymbolLookup.libraryLookup(os.libraryName(), Arena.global());
				System.out.println("Success: LNK = " + LNK);
				return LNK;
			}
		}
		throw new IllegalStateException("OsInfo not matched");
	}

	private final static List<Path> fontPaths = new ArrayList<>();

	/**
	 * Get the font file corresponding to the given face name
	 * @param fontName The name we are looking for
	 * @return The Path to the file, if we find it
	 * @throws IOException If Files.walk() finds an error.
	 * @throws IllegalStateException If we fail to find a single exact match
	 */
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
					.filter(p -> p.getFileName()
							.toString()
							.toLowerCase()
							.replace(' ', '-')
							.endsWith(lcFontName + ".ttf"))
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
		System.out.println(lib);
		System.out.println("Found: " + getFontFile("DejaVuSans"));
	}
}
