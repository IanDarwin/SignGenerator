package text3d;

import java.util.Optional;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;

/** Standalone tool to try out dynamic loading on various OSes. */
public class FreeLoader {

	final static String osName = System.getProperty("os.name");

	record OS(String name, String libraryPath, String libraryName) {}

	static OS[] oses = {
		new OS("OpenBSD", "/usr/X11R6/lib", "libfreetype.so"),
		// This should be "macOS" or "Darwin" but Java still uses this name,
		// but we don't have to set the library path
		new OS("Mac OS X", null, "libfreetype.dylib"),
	};

	public static Optional<OS> getOsInfo() {
		System.out.println("osName = " + osName);
		for (OS os : oses) {
			if (os.name().equals(osName)) {
				return Optional.of(os);
			}
		}
		return Optional.empty();
	}

	public static Optional<String> getLoadLibrary() {
		var ret = getOsInfo();
		if (ret.isPresent()) {
			var os = ret.get();
			if (os.name().equals(osName)) {
				System.out.println(os);
				final SymbolLookup LNK = SymbolLookup.libraryLookup(os.libraryName(), Arena.global());
				return Optional.of("Success: LNK = " + LNK);
			}
		}
		return Optional.of("OS not matched");
	}

	public static Optional<String> getFontFile() {
		System.out.println("XXX writeme getFontFile()");
		return Optional.empty();
	}

	public static void main(String[] args) {
		var lib = getLoadLibrary();
		lib.ifPresent(System.out::println);
		var fontFile = getFontFile();
		fontFile.ifPresent(System.out::println);
	}

}
