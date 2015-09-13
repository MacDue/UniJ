package unij;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

import javax.servlet.ServletException;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerEndpointConfig;
import java.util.Map;

class UniJServer implements Runnable {

	private final Server server;
	private final int PORT;
	private final String STATIC_FILE_LOCATION;
	private final Map<String, Class<? extends Endpoint>> webSockets;
	private final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
	private final String hostName;

	/**
	 * Constructor for UniJ Server
	 * @param serverSettings        All the settings for the server
	 * @param webSockets            All the websockets which should be added
	 */

	protected UniJServer(Map<String, String> serverSettings, Map<String, Class<? extends Endpoint>> webSockets) {
		this.PORT = Integer.parseInt(serverSettings.get("port"));
		this.STATIC_FILE_LOCATION = serverSettings.get("staticFileLocation");
		this.server = new Server();
		this.webSockets = webSockets;
		this.hostName = serverSettings.get("hostName");

		int securePort = Integer.parseInt(serverSettings.get("securePort"));
		boolean sslEnabled = System.getProperty("unij.keystore.path") != null;

		context.setContextPath("/");
		ContextHandlerCollection contextHandlers =
				sslEnabled ? createSSLHandlers(securePort) : createBasicHandlers();

		HandlerList handlerList = new HandlerList();
		handlerList.addHandler(contextHandlers);
		handlerList.addHandler(new DefaultHandler());

		server.setHandler(handlerList);

		if (serverSettings.containsKey("staticFileLocation")) {
			setupStaticFileService();
		}

		setupWebsockets();

		// DOES NOT WORK..
		//// Enable CORS - cross origin resource sharing (for http and https)
		//FilterHolder cors = new FilterHolder();
		//cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
		//cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "*");
		////cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET, POST");
		//cors.setFilter(new CrossOriginFilter());
		//context.addFilter(cors, "/*", EnumSet.of(DispatcherType.REQUEST));
	}

	/**
	 * Start UniJ server. Well... actually Jetty server
	 */

	public void run() {
		try {
			server.start();
			server.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Setup basic unencrypted server
	 * @return      The regular context
	 */

	private ContextHandlerCollection createBasicHandlers() {

		HttpConfiguration httpConf = new HttpConfiguration();

		ServerConnector httpConnector = new ServerConnector(server,
				new HttpConnectionFactory(httpConf));
		httpConnector.setName("unsecured");
		if (hostName != null) {
			httpConnector.setHost(hostName);
		}
		httpConnector.setPort(PORT);

		ContextHandlerCollection contextHandlers = new ContextHandlerCollection();
		server.setConnectors(new Connector[]{httpConnector});
		contextHandlers.setHandlers(new Handler[]{ context});

		return contextHandlers;
	}


	/**
	 * Setup SSL for UniJ server. Unsecure HTTP request will be redirected to the HTTPS connector
	 * @return      The redirect handler and regular context
	 */

	private ContextHandlerCollection createSSLHandlers(int securePort) {

		/**** HTTP Configuration ****/

		HttpConfiguration httpConf = new HttpConfiguration();
		httpConf.setSecurePort(securePort);
		httpConf.setSecureScheme("https");

		ServerConnector httpConnector = new ServerConnector(server,
				new HttpConnectionFactory(httpConf));
		httpConnector.setName("unsecured");
		if (hostName != null) {
			httpConnector.setHost(hostName);
		}
		httpConnector.setPort(PORT);

		/**** HTTPS Configuration ****/

		SslContextFactory sslContextFactory = new SslContextFactory();

		sslContextFactory.setKeyStorePath(System.getProperty("unij.keystore.path"));
		sslContextFactory.setKeyStorePassword(System.getProperty("unij.keystore.password"));
		sslContextFactory.setKeyManagerPassword(System.getProperty("unij.keymanager.password"));

		sslContextFactory.addExcludeCipherSuites("SSLv3");
		sslContextFactory.addExcludeCipherSuites(".*_GCM_.*");

		HttpConfiguration httpsConf = new HttpConfiguration(httpConf);
		httpsConf.addCustomizer(new SecureRequestCustomizer());

		ServerConnector httpsConnector = new ServerConnector(server,
				new SslConnectionFactory(sslContextFactory, "http/1.1"),
				new HttpConnectionFactory(httpsConf));
		httpsConnector.setName("secured");
		if (hostName != null) {
			httpsConnector.setHost(hostName);
		}
		httpsConnector.setPort(securePort);

		// Add connectors
		server.setConnectors(new Connector[]{httpConnector, httpsConnector});

		// Wire up contexts for secure handling to named connector
		String[] secureHosts = new String[] {"@secured"};
		context.setVirtualHosts(secureHosts);

		// Wire up context for unsecure handling to only "unsecured" connector
		ContextHandler redirectHandler = new ContextHandler();
		redirectHandler.setContextPath("/");
		redirectHandler.setHandler(new SecuredRedirectHandler());
		redirectHandler.setVirtualHosts(new String[]{"@unsecured"});

		// Add all context handlers
		ContextHandlerCollection contextHandlers = new ContextHandlerCollection();
		contextHandlers.setHandlers(new Handler[]{ redirectHandler, context});

		return contextHandlers;
	}

	/**
	 * Setup static file serving
	 */

	private void setupStaticFileService() {
		ServletHolder defHolder = new ServletHolder("default", new DefaultServlet());
		// Allows for dynamic reload of static files
		defHolder.setInitParameter("useFileMappedBuffer", "false");
		defHolder.setInitParameter("dirAllowed", "false");
		// Set location of static files
		defHolder.setInitParameter("resourceBase", STATIC_FILE_LOCATION);
		context.addServlet(defHolder, "/");
	}

	/**
	 * Add all user websockets and the standard UniJ websocket
	 */

	private void setupWebsockets() {

		try {
			// Add standard UniJ WebSocket
			webSockets.put(UniJ.getUniJWebSocketPath(), UniJEndpoint.class);

			// Add javax.websocket support
			ServerContainer container = WebSocketServerContainerInitializer.configureContext(context);

			webSockets.forEach((address, webSocket) -> {
				try {
					container.addEndpoint(
							ServerEndpointConfig.Builder.create(webSocket, address).build());
				} catch (DeploymentException e) {
					e.printStackTrace();
				}
			});

		} catch (ServletException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Stop UniJ server and disconnect all clients
	 *
	 * @throws Exception
	 */

	protected void stop() throws Exception {
		server.stop();
	}
}
