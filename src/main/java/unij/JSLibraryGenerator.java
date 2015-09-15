package unij;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JSLibraryGenerator {
	public static void main(String[] args) throws Exception {

		String version = args[0];
		
		final String I = File.separator;
		final String userDir = System.getProperty("user.dir");
		
		final String buildDir = userDir + I + "build" + I + "libs";
		final String clientDir = userDir + I + "src" + I + "main" + I + "js" + I + "unij";
		final String exampleDir = userDir + I + "src" + I + "main" + I + "resources" + I + "example";

		// Read base client code
		String sourceCode = new String(Files.readAllBytes(Paths.get(clientDir + I + "unij-client.js")));

		String nodeJSVersion = createNodeJSVersion(sourceCode);
		String browserVersion = createBrowserVersion(sourceCode);

		// Generate production versions
		Files.write(Paths.get(buildDir + I + "unij-client-nodejs-" + version + ".js"),
				nodeJSVersion.getBytes());
		Files.write(Paths.get(buildDir + I + "unij-client-browser-" + version + ".js"),
				browserVersion.getBytes());

		// Delete all libraries in example
		Files.list(Paths.get(exampleDir + I))
				.map(Path::toFile)
				.filter(file -> file.getName().contains("unij-client"))
				.forEach(File::delete);

		// Generate for example
		Files.write(Paths.get(exampleDir + I + "unij-client-browser-" + version + ".js"),
				browserVersion.getBytes());
		Files.write(Paths.get(exampleDir + I + "unij-client-nodejs-" + version + ".js"),
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
