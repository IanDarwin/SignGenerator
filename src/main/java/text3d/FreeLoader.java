package text3d;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;

/** Standalone tool to try out dynamic loading on various OSes. */
public class FreeLoader {

	record OS(String name, String libraryPath, String libraryName) {}

	static OS[] oses = {
		new OS("OpenBSD", "/usr/X11R6/lib", "libfreetype.so"),
		// This should be "macOS" or "Darwin" but Java still uses this name,
		// but we don't have to set the library path
		new OS("Mac OS X", null, "libfreetype.dylib"),
	};

	public static void main(String[] args) {
		var osName = System.getProperty("os.name");
		System.out.println("osName = " + osName);
		for (OS os : oses) {
			if (os.name().equals(osName)) {
				System.out.println(os);
				final SymbolLookup LNK = SymbolLookup.libraryLookup(os.libraryName(), Arena.global());
				System.out.println("LNK = " + LNK);
				return;
			}
		}
		System.out.println("OS not matched");
	}
}
