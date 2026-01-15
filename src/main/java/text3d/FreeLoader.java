package text3d;

/** Standalone tool to try out dynamic loading on various OSes. */
public class FreeLoader {

	record OS(String name, String libraryPath, String libraryName) {}

	static OS[] oses = {
		new OS("OpenBSD", "/usr/X11R6/lib", "libfreetype"),
		new OS("macOS", "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/lib", "libfreetype.dylib"),
	};

	public static void main(String[] args) {
		var osName = System.getProperty("os.name");
		System.out.println("osName = " + osName);
		for (OS os : oses) {
			if (os.name().equals(osName)) {
				System.out.println(os);
				return;
			}
		}
		System.out.println("OS not matched");
	}
}
