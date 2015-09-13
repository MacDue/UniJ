package unij;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.websocket.*;
import java.io.IOException;

public class UniJEndpoint extends Endpoint implements MessageHandler.Whole<String> {

	static private int clientCounter = 0;

	private final JsonFactory factory;

	private RemoteEndpoint.Async client;

	private String name;

	/**
	 * Will be instanciated every time a client connects
	 */
	public UniJEndpoint() {
		this.factory = new JsonFactory();
		this.factory.setCodec(new ObjectMapper());
	}

	/**
	 * Signal that the Browser connected
	 * @param session       Websocket session with one client
	 * @param config        Configuration of the client endpoint
	 */

	@Override
	public void onOpen(Session session, EndpointConfig config) {
		UniJ.log("Client \"" + clientCounter + "\" just connected");
		session.setMaxIdleTimeout(Integer.MAX_VALUE);
		session.addMessageHandler(this);
		this.client = session.getAsyncRemote();

		this.name = String.valueOf(clientCounter++);

		UniJ.addClient(name, this);
		UniJ.execute(name, "setClientName", name);
		UniJ.execute(name, "clientIsReadyNow");
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

			UniJProcedure procedure = UniJ.getProcedure(procedureName);

			if (procedure != null) {

				Object[] parameters = new Object[procedure.paramTypes.length];

				parser.nextToken(); // [

				for (int i = 0; i < parameters.length; i++) {
					parser.nextValue();
					parameters[i] = parser.readValueAs(procedure.paramTypes[i]);
				}

				// Not checking for nulls in parameters

				if (procedure.willReturnSomething) {
					UniJ.execute(this.name, procedureName, procedure.execute(parameters));
				} else {
					procedure.execute(parameters);
				}

			} else {
				UniJ.logToClient(this.name, "Remote procedure with name \"" + procedureName
						+ "\" does not exist");
			}

			// No need to parse ]}
			parser.close();

		} catch (JsonMappingException e) {
			String procedureName = message.substring(1, message.indexOf(":"));
			String procedureParam = message.substring(message.indexOf(":") + 2, message.length() - 2);
			UniJ.log("While local procedure with name " + procedureName + " exists, remote execution"
					+ " failed because of invalid parameters: " + procedureParam);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * WebSocket connection has been closed
	 *
	 * @param session
	 * @param reason
	 */

	@Override
	public void onClose(Session session, CloseReason reason) {
		UniJ.log("Client disconnected because of " + reason);
		UniJ.removeClient(this.name);
		super.onClose(session, reason);
	}

	/**
	 * Error happened and connection has been closed (probably in onMessage())
	 *
	 * @param session
	 * @param cause
	 */

	@Override
	public void onError(Session session, Throwable cause) {
		UniJ.log("Error: " + cause + " happened with client \"" + this.name + "\"");
		UniJ.removeClient(this.name);
		super.onError(session, cause);
	}

	public void setName(String newName) {
		this.name = newName;
	}

	public void sendRaw(String message) {
		client.sendText(message);
	}
}
