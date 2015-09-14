package unij;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JSLibraryGenerator {
	public static void main(String[] args) throws Exception {

		String version = args[0];

		String clientPath = System.getProperty("user.dir") + File.separator + "src"
				+ File.separator + "main" + File.separator + "js" + File.separator + "unij"
				+ File.separator;

		String buildPath = System.getProperty("user.dir") + File.separator + "build" + File.separator
				+ "libs" + File.separator;

		String examplePath = System.getProperty("user.dir") + File.separator + "src" + File.separator
				+ "main" + File.separator + "resources" + File.separator + "example" + File.separator;

		// Read base client code
		String sourceCode = new String(Files.readAllBytes(Paths.get(clientPath + "unij-client.js")));

		String nodeJSVersion = createNodeJSVersion(sourceCode);
		String browserVersion = createBrowserVersion(sourceCode);

		// Generate production versions
		Files.write(Paths.get(buildPath + "unij-client-nodejs-" + version + ".js"),
				nodeJSVersion.getBytes());
		Files.write(Paths.get(buildPath + "unij-client-browser-" + version + ".js"),
				browserVersion.getBytes());

		// Delete all libraries in example
		Files.list(Paths.get(examplePath))
				.map(Path::toFile)
				.filter(file -> file.getName().contains("unij-client"))
				.forEach(File::delete);

		// Generate for example
		Files.write(Paths.get(examplePath + "unij-client-browser-" + version + ".js"),
				browserVersion.getBytes());
		Files.write(Paths.get(examplePath + "unij-client-nodejs-" + version + ".js"),
				nodeJSVersion.getBytes());
	}

	private static String createNodeJSVersion(String sourceCode) {
		return "\"use strict\";\nvar WebSocket = require(\"ws\");\n" + sourceCode
				+ "\nmodule.exports = function(address) { return new UniJClient(address); }";
	}

	private static String createBrowserVersion(String sourceCode) {
		return "\"use strict\";\n" + sourceCode + "\nvar UniJ = new UniJClient();";
	}
}
