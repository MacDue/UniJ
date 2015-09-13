package example;

import com.fasterxml.jackson.annotation.JsonProperty;
import unij.Procedure;
import unij.UniJ;

import java.util.concurrent.TimeUnit;

public class LittleExample {
    public static void main (String[] args) throws Exception {

	    // If a keystore is found then UniJ will automatically use SSL
	    // Ideally you should do this a bit more securely ;)
	    System.setProperty("unij.keystore.path", "src/main/resources/example/server.keystore");
	    System.setProperty("unij.keystore.password", "mykeystorepass");

	    // Path is relative from project directory
	    UniJ.hostFilesIn("src/main/resources/example");

	    // Adds printToConsole (static methods don't need an instance)
	    UniJ.addProcedure(LittleExample.class);
	    // Adds destroyAndShow and invokes on new StringDestroyer()
	    UniJ.addProcedure(new StringDestroyer());

	    UniJ.start();

	    // You should type in "localhost:7777" into your browser now
	    UniJ.waitForFirstClient();

	    // Will execute "showThemApples" on all connected clients
	    UniJ.executeAll("showThemApples", new RandomObject("apples"));

	    // There is no such procedure -> will throw warning
	    UniJ.executeAll("iDontExist", 404);

	    // Wait for client to rename itself and address client directly
	    TimeUnit.SECONDS.sleep(2);
	    UniJ.execute("because I can", "showThemApples", "them apples");

    }

	@Procedure("printToConsole")
	public static void printToConsole(String msg) {
		System.out.println(msg);
	}

	static class StringDestroyer {

		public StringDestroyer() {
		}

		@Procedure("destroyAndShow")
		public void destroyAndShow(String string) {

			char[] splitString = string.toCharArray();

			for (int i = 0; i < splitString.length; i++) {
				splitString[i] += (int) Math.floor(Math.random() * 20) - 10;
			}

			System.out.println(new String(splitString));
		}
	}

	static class RandomObject {
		// Will be seriaized when sent to clients
		@JsonProperty("name")
		String name;

		// Won't be serialized when sent to clients
		double value;

		public RandomObject(String name) {
			this.name = name;
			this.value = Math.random() * 256;
		}
	}
}
