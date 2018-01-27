package unij;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import javax.websocket.*;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UniJEndpoint extends Endpoint implements MessageHandler.Whole<String> {

	private static final HashMap<String, UniJProcedure> localProcedures = new HashMap<>();
	
	private static final ConcurrentHashMap<String, UniJEndpoint> clientPool = new ConcurrentHashMap<>();
	private static int maxClients = Integer.MAX_VALUE;

	private static final ObjectWriter writer = new ObjectMapper().writer();
	private final JsonFactory factory = new JsonFactory(new ObjectMapper());

	private String clientName;
	private RemoteEndpoint.Async remoteEndpoint;

	/**
	 * Signal that the Browser connected
	 * @param session       Websocket session of current client
	 * @param config        Configuration of the client endpoint
	 */

	@Override
	public void onOpen(Session session, EndpointConfig config) {
		// ID = "websocket-X" -> "X"
    String sessionId = session.getId();
		this.clientName = sessionId.substring(0, Math.min(sessionId.length(), 10));
		this.remoteEndpoint = session.getAsyncRemote();
		
		if (clientPool.size() < maxClients) {

			session.setMaxIdleTimeout(Integer.MAX_VALUE);
			session.addMessageHandler(this);

			clientPool.put(this.clientName, this);

			execute(clientName, "setClientName", clientName);
			execute(clientName, "clientIsReadyNow");

			UniJ.log("Client \"" + clientName + "\" just connected");

		} else {
			try {
				session.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
	/**
	 * Receive messages from websocket clients
	 * @param message       Expected pattern: {"procedure":[param1,param2]}
	 */

	@Override
	public void onMessage(String message) {

		try {
			JsonParser parser = factory.createParser(message);
			parser.nextToken(); // {
			String procedureName = parser.nextFieldName();

			UniJProcedure procedure = localProcedures.get(procedureName);

			if (procedure != null) {

				Object[] parameters = new Object[procedure.paramTypes.length];

				parser.nextToken(); // [

				for (int i = 0; i < parameters.length; i++) {
					parser.nextValue();
					parameters[i] = parser.readValueAs(procedure.paramTypes[i]);
				}

				// Not checking for nulls in parameters

				if (procedure.willReturnSomething) {
					UniJ.execute(this.clientName, procedureName, procedure.execute(parameters));
				} else {
					procedure.execute(parameters);
				}

			} else {
				UniJ.logToClient(this.clientName, "Remote procedure with clientName \"" + procedureName
						+ "\" does not exist");
			}

			// No need to parse ]}
			parser.close();

		} catch (JsonMappingException e) {
			String procedureName = message.substring(1, message.indexOf(":"));
			String procedureParam = message.substring(message.indexOf(":") + 2, message.length() - 2);
			UniJ.log("While local procedure with clientName " + procedureName + " exists, remote execution"
					+ " failed because of invalid parameters: " + procedureParam);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * WebSocket connection has been closed
	 *
	 * @param session       WebSocket session of current client
	 * @param reason        Reason for closing
	 */

	@Override
	public void onClose(Session session, CloseReason reason) {
		UniJ.log("Client disconnected because of " + reason);
		clientPool.remove(this.clientName);
		super.onClose(session, reason);
	}

	/**
	 * Error happened and connection has been closed
	 *
	 * @param session       WebSocket session of current client
	 * @param cause         Cause of the error
	 */

	@Override
	public void onError(Session session, Throwable cause) {
		UniJ.log("Error: " + cause + " happened with client \"" + this.clientName + "\"");
		clientPool.remove(clientName);
		super.onError(session, cause);
	}

	/**
	 * Send a raw text message to the remote endpoint aka. client
	 * @param message       String to send
	 */

	public void sendText(String message) {
		remoteEndpoint.sendText(message);
	}
	
	


	/**
	 * Actively remove a client from the pool
	 * @param clientName        Name of the client
	 */
	
	protected static void removeClient(String clientName) {
		UniJEndpoint clientToRemove = clientPool.remove(clientName);
		if (clientToRemove == null) {
			UniJ.log("There is no client \"" + clientName + "\" which could be removed");
		}
	}

	/**
	 * Set the maximum number of clients that are allowed on the UniJ standard WebSocket
	 * @param numberOfClientsAllowed        Number of allowed clients
	 */
	
	protected static void setMaxClients(int numberOfClientsAllowed) {
		maxClients = numberOfClientsAllowed;
	}

	/**
	 * Set the name of a connected client
	 * @param oldName       Current name of the client
	 * @param newName       Desired new name for the client
	 */

	protected static void setClientName(String oldName, String newName) {

		if (!clientPool.containsKey(oldName)) {
			UniJ.log("Client \"" + oldName + "\" failed changing its name because it is unkown");
			UniJ.logToClient(oldName, "Could not change your name to \"" + newName
					+ "\", because I don't know you...");

		} else if (clientPool.containsKey(newName)) {
			UniJ.log("Client \"" + oldName + "\" failed changing its name to \"" + newName
					+ "\", because someone else already has it");
			UniJ.logToClient(oldName, "Could not change your name to \"" + newName
					+ "\", because someone else already has it");
		} else {
			UniJ.log("Client \"" + oldName + "\" changed its name to \"" + newName + "\"");
			UniJEndpoint client = clientPool.remove(oldName);
			client.clientName = newName;
			clientPool.put(newName, client);

			// Tell client its new name
			UniJ.execute(newName, "setClientName", newName);
			UniJ.logToClient(newName, "You changed your name to \"" + newName + "\"");
		}
	}


	protected static void addProcedure(String procedureName, Object executor, Method procedure) {
		if (localProcedures.containsKey(procedureName)) {
			UniJ.log("Procedure \"" + procedureName + "\" was overwritten");
		}
		localProcedures.put(procedureName, new UniJProcedure(executor, procedure));
	}
	
	protected static void addProcedure(Object procedureExecutor) throws IllegalArgumentException {

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
				String clientName = method.getAnnotation(Procedure.class).value();
				addProcedure(clientName, procedureExecutor, method);
			}
		}

		if (hadNoProcedures) {
			throw new IllegalArgumentException("UniJ: Class \"" + procedureClass.getSimpleName() + 
					"\" was passed to UniJ.addProcedure() but had no @Procedure annotations");
		}
	}
	
	protected static void removeProcedure(String clientName) {
		UniJProcedure procedure = localProcedures.remove(clientName);
		if (procedure == null) {
			UniJ.log("Tried to remove procedure \"" + clientName + "\", but it didn't exist");
		}
	}
	
	protected static void execute(String clientName, String procedureName, Object... parameters) {
		UniJEndpoint client = clientPool.get(clientName);
		
		if (client == null) {
			UniJ.log("Could not execute remote procedure \"" + procedureName + "\" from client \"" +
					clientName + "\", because the client doesn't exist");
		} else {
			try {
				client.sendText(buildMessage(procedureName, parameters));
			} catch (JsonProcessingException e) {
				UniJ.log("Could not execute remote procedure \"" + procedureName + "\" from client \"" +
						clientName + "\", because the parameters are invalid");
			}
		}
	}

	protected static void executeAll(String procedureName, Object... parameters) {

		if (clientPool.isEmpty()) {
			UniJ.log("Could not execute remote procedure \"" + procedureName + "\" because no clients" +
					" are connected");
		} else {
			try {
				String message = buildMessage(procedureName, parameters);
				// TODO: Check the parallelismThreshold for performance
				clientPool.forEachValue(Long.MAX_VALUE, (client) -> client.sendText(message));
				
			} catch (JsonProcessingException e) {
				UniJ.log("Could not execute remote procedure \"" + procedureName + "\" because the" +
						" parameters are invalid");
			}
		}
	}
	
	private static String buildMessage(String procedureName, Object... parameters) throws JsonProcessingException{
		return "{\"pro\":\"" + procedureName +
				"\",\"par\":" + writer.writeValueAsString(parameters) + "}";
	}




	protected static Set<String> getClientNames() {
		Set<String> names = new HashSet<>();
		clientPool.forEachKey(Long.MAX_VALUE, names::add);
		return names;
	}
	
	protected static int getNumberOfConnectedClients() {
		return clientPool.size();
	}
}
