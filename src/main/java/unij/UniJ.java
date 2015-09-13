package unij;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.websocket.Endpoint;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class UniJ {

	// Use default Jetty Logger
	private static final Logger log = Log.getLog();

	private static UniJServer server;
	private final static ConcurrentHashMap<String, UniJEndpoint> clients = new ConcurrentHashMap<>();
	private final static HashMap<String, UniJProcedure> localProcedures = new HashMap<>();

	private final static Map<String, Class<? extends Endpoint>> webSockets = new HashMap<>();

	private final static ObjectWriter jsonWriter = new ObjectMapper().writer();

	private final static HashMap<String, String> serverSettings = new HashMap<>();

	// Default values
	static {
		serverSettings.put("port", "" + 7777);
		serverSettings.put("securePort", "" + 7778);
		serverSettings.put("uniJWebSocketPath", "/unij");
		addCustomWebSocket("/unij", UniJEndpoint.class);

		// Add standard procedures for UniJ specific communication
		addProcedure(UniJ.class);
	}


	/********************************* UniJ Methods *********************************/


	@Procedure("unijLog")
	protected static void log(String message) {
		log.warn("UniJ: " + message);
	}

	@Procedure("setClientName")
	protected static void setClientName(String oldName, String newName) {

		if (!clients.containsKey(oldName)) {
			log("Client \"" + oldName + "\" failed changing its name because it is unknown");
			logToClient(oldName, "Could not change your name to \"" + newName
					+ "\", because I don't know you...");

		} else if (clients.containsKey(newName)) {
			log("Client \"" + oldName + "\" failed changing its name to \"" + newName
					+ "\", because someone else is already named like that");
			logToClient(oldName, "Could not change your name to \"" + newName
					+ "\", because someone else already is named like that");
		} else {
			log("Client \"" + oldName + "\" changed its name to \"" + newName + "\"");
			UniJEndpoint client = clients.remove(oldName);
			clients.put(newName, client);

			// Tell client its new name
			UniJ.execute(newName, "setClientName", newName);

			logToClient(newName, "You changed your name to \"" + newName + "\"");
		}
	}

	/**
	 * Add a client (equivalent to UniJEndpoint instance) to the pool of clients
	 * @param name      Name of the client
	 * @param client    Socket instance of the client
	 * @throws IllegalArgumentException
	 */

	protected static void addClient(String name, UniJEndpoint client) throws IllegalArgumentException {
		if (clients.containsKey(name)) {
			throw new IllegalArgumentException("UniJ: Could not add a client with name: " + name
					+ " because the name is taken");
		} else {
			clients.put(name, client);
		}
	}

	/**
	 * Remove a client (equivalent to UniJEndpoint instance) from the pool of clients
	 * @param name      Name of the client
	 */

	protected static void removeClient(String name) {
		if (clients.containsKey(name)) {
			clients.remove(name);
		} else {
			log("Tried to remove client with name \"" + name + "\" but couldn't find it");
		}
	}

	/**
	 * Get the local procedure with given name
	 * @param procedure
	 * @return
	 */

	protected static UniJProcedure getProcedure(String procedure) {
		return localProcedures.get(procedure);
	}



	/********************************* User Methods *********************************/

	/**
	 * Start UniJ Server
	 */

	public static void start() {


		server = new UniJServer(serverSettings, webSockets);

		// Use SSL if possible
		if (System.getProperty("unij.keystore.path") != null) {
			log("Found SSL keystore and will serve securely on port: "
					+ serverSettings.get("securePort") + " if keystore is valid");
		}

		Thread serverThread = new Thread(server);
		serverThread.start();
	}

	/**
	 * Send a message that will be displayed in the console logs of the client
	 * @param clientName        Name of the clienta
	 * @param message           Message that contains the log
	 */

	public static void logToClient(String clientName, String message) {
		execute(clientName, "unijLog", "UniJ Server : " + message);
	}

	/**
	 * Add a procedure which can be called by a remote client and executed by the passed executor
	 * @param procedureName  Name of the procedure
	 * @param executor       Either instance or class
	 * @param procedure      To be executed when a remote procedure call from client happens
	 */

	public static void addProcedure(String procedureName, Object executor, Method procedure) {
		if (localProcedures.containsKey(procedureName)) {
			log("unij.Procedure with name:\"" + procedureName + "\" was overwritten");
		}
		localProcedures.put(procedureName, new UniJProcedure(executor, procedure));
	}

	/**
	 * Add a procedure which can be called by a remote client and executed by the passed executor.
	 * Private methods can be executed as well.
	 * @param procedureExecutor    Object annotated with Subscribe
	 */

	public static void addProcedure(Object procedureExecutor) throws IllegalArgumentException {

		Class procedureClass = procedureExecutor.getClass();

		// For static methods
		if (procedureExecutor instanceof Class) {
			procedureClass = (Class) procedureExecutor;
			procedureExecutor = null;
		}

		boolean hadNoProcedures = true;

		for (Method method : procedureClass.getDeclaredMethods()) {
			if (method.isAnnotationPresent(Procedure.class)) {
				// Make private method accessible
				method.setAccessible(true);

				hadNoProcedures = false;
				String name = method.getAnnotation(Procedure.class).value();
				addProcedure(name, procedureExecutor, method);
			}
		}

		if (hadNoProcedures) {
			log(procedureClass.getSimpleName() + " was passed to" +
					" UniJ.addProcedure() but had no @unij.Procedure annotations");
		}
	}

	/**
	 * Remove a local procedure with the given name
	 * @param name
	 */

	public static void removeProcedure(String name) {
		UniJProcedure procedure = localProcedures.remove(name);
		if (procedure == null) {
			log("Tried to remove procedure with name \"" + name + "\" but it didn't exist");
		}
	}


	/**
	 * Execute a remote procedure from client with given name
	 * @param clientName            Name of the connected client
	 * @param remoteProcedureName   Name of the remote procedure to execute
	 * @param parameters            Parameters for the remote procedure
	 */

	public static void execute(String clientName, String remoteProcedureName, Object... parameters) {

		UniJEndpoint client = clients.get(clientName);

		if (client != null) {
			try {
				client.sendRaw("{\"pro\":\"" + remoteProcedureName +
						"\",\"par\":" + jsonWriter.writeValueAsString(parameters) + "}");
			} catch (JsonProcessingException e) {
				log("Could not parse parameters of remote procedure execution:\""
						+ remoteProcedureName + "\"");
			}
		} else {
			log("Client with name: \"" + clientName + "\" does not exist or has a different name");
		}
	}

	/**
	 * Execute a remote procedures of all connected clients
	 * @param remoteProcedureName   Name of the remote procedure to execute
	 * @param parameters            Parameters for the remote procedure
	 */

	public static void executeAll(String remoteProcedureName, Object... parameters) {
		if (clients.isEmpty()) {
			log("There are no clients connected");
		} else {
			try {
				String message = "{\"pro\":\"" + remoteProcedureName +
						"\",\"par\":" + jsonWriter.writeValueAsString(parameters) + "}";

				clients.forEach((name, socket) -> socket.sendRaw(message));

			} catch (JsonProcessingException e) {
				log("Could not parse parameters of remote procedure execution:\""
						+ remoteProcedureName + "\"");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Add a custom websocket to the UniJ server
	 * @param address       Relative websocket adress
	 * @param websocket     Websocket class to build
	 */

	public static void addCustomWebSocket(String address, Class<? extends Endpoint> websocket) throws IllegalArgumentException {

		if (webSockets.containsKey(address)) {
			throw new IllegalArgumentException("UniJ: This WebSocket address is already taken");

		} else {
			webSockets.put(address, websocket);
		}
	}

	/**
	 * Tell UniJ to host files
	 * @param location      Path relative from project directory
	 */

	public static void hostFilesIn(String location) {
		serverSettings.put("staticFileLocation", location);
	}

	/**
	 * Set the TCP port for HTTP connections
	 * @param port      TCP port number
	 */

	public static void setPort(int port) {
		serverSettings.put("port", "" + port);
	}

	/**
	 * Get the TCP port for HTTP connections
	 * @return      TCP port number
	 */

	public static int getPort() {
		return Integer.parseInt(serverSettings.get("port"));
	}

	/**
	 * Set the TCP port for HTTPS connections
	 * @param port      TCP port number
	 */

	public static void setSecurePort(int port) {
		serverSettings.put("securePort", "" + port);
	}

	/**
	 * Get the TCP port for HTTPS connections
	 * @return      TCP port number
	 */

	public static int getSecurePort() {
		return Integer.parseInt(serverSettings.get("securePort"));
	}

	/**
	 * Get the number of clients currently connected to the standard UniJ websocket
	 * @return      Number of clients
	 */

	public static int getNumberOfConnectedClients() {
		return clients.size();
	}

	/**
	 * Get the relative path of the standard UniJ websocket
	 * @return
	 */

	public static String getUniJWebSocketPath() {
		return serverSettings.get("uniJWebSocketPath");
	}

	public static void stop() throws Exception {
		server.stop();
	}

	/**
	 * Block current thread until the first client connects
	 */

	public static void waitForFirstClient() {
		while (clients.isEmpty())  {
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) {
				log("Error happened while waiting for first client");
				e.printStackTrace();
			}
		}
	}

	public static void setHostName(String hostName) {
		serverSettings.put("hostName", hostName);
	}


}
