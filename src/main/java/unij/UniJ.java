package unij;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.websocket.Endpoint;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class UniJ {

	// Use default Jetty Logger
	private static final Logger log = Log.getLog();

	private static UniJServer server;
	private final static HashMap<String, String> serverSettings = new HashMap<>();

	private final static Map<String, Class<? extends Endpoint>> webSockets = new HashMap<>();

	// Default values for UniJ
	static {
		serverSettings.put("port", "" + 7777);
		serverSettings.put("securePort", "" + 7778);
		serverSettings.put("uniJWebSocketPath", "/unij");
		addCustomWebSocket("/unij", UniJEndpoint.class);

		// Add standard procedures for UniJ specific communication
		addProcedure(UniJ.class);
	}


	/*** UniJ Standard Procedures ****************************************************************/


	@Procedure("unijLog")
	protected static void log(String message) {
		log.warn("UniJ: " + message);
	}

	@Procedure("setClientName")
	protected static void setClientName(String oldName, String newName) {
		UniJEndpoint.setClientName(oldName, newName);
	}


	/*** UniJ User Methods ***********************************************************************/

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
		execute(clientName, "unijLog", "UniJ Server: " + message);
	}

	/**
	 * Add a procedure that UniJ clients can execute
	 * @param procedureName     Name of the procedure
	 * @param executor          Name of the executing instance
	 * @param procedure         Actual method to be executed
	 */

	public static void addProcedure(String procedureName, Object executor, Method procedure) {
		UniJEndpoint.addProcedure(procedureName, executor, procedure);
	}

	/**
	 * Add a procedure by passing the class containing the procedures annotated with @Procedure.
	 * If passed object is an instance then it will be used to execute the procedure.
	 * @param procedureExecutor         Contains the procedures
	 * @throws IllegalArgumentException
	 */

	public static void addProcedure(Object procedureExecutor) throws IllegalArgumentException {
		UniJEndpoint.addProcedure(procedureExecutor);
	}

	/**
	 * Remove a local procedure with the given name
	 * @param name      Name of the procedure to remove
	 */

	public static void removeProcedure(String name) {
		UniJEndpoint.removeProcedure(name);
	}

	/**
	 * Execute a remote procedure from a specific client
	 * @param clientName            Name of the client
	 * @param remoteProcedureName   Name of the client's procedure
	 * @param parameters            Parameters for the client's procedure
	 */

	public static void execute(String clientName, String remoteProcedureName, Object... parameters) {
		UniJEndpoint.execute(clientName, remoteProcedureName, parameters);
	}

	/**
	 * Execute a remote procedure on all connected clients
	 * @param remoteProcedureName   Name of the procedure
	 * @param parameters            Parameters for the procedure
	 */

	public static void executeAll(String remoteProcedureName, Object... parameters) {
		UniJEndpoint.executeAll(remoteProcedureName, parameters);
	}

	/**
	 * Add a custom WebSocket to the UniJ server
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
		return UniJEndpoint.getNumberOfConnectedClients();
	}

	/**
	 * Get all the client names in a Set
	 * @return      Set of client names
	 */

	public static Set<String> getAllConnectedClientNames() {
		return UniJEndpoint.getClientNames();
	}

	/**
	 * Get the relative path of the standard UniJ WebSocket
	 * @return      Relative WebSocket path
	 */

	public static String getUniJWebSocketPath() {
		return serverSettings.get("uniJWebSocketPath");
	}

	/**
	 * Stop the UniJ server
	 * @throws Exception
	 */

	public static void stop() throws Exception {
		server.stop();
	}

	/**
	 * Block current thread until the first client connects
	 */

	public static void waitForFirstClient() {
		while (UniJEndpoint.getNumberOfConnectedClients() < 1)  {
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) {
				log("Error happened while waiting for first client");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Set the number of clients that are allowed to connect concurrently
	 * @param clientsAllowed    Number of clients
	 */

	public static void setMaxConnectedClients(int clientsAllowed) {
		UniJEndpoint.setMaxClients(clientsAllowed);
	}

	/**
	 * Set the host name of the UniJ server
	 * @param hostName      Hostname .i.e "127.0.0.1"
	 */

	public static void setHostName(String hostName) {
		serverSettings.put("hostName", hostName);
	}

}
