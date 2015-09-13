function UniJClient(webSocketAddress) {

    var userStartFunction;

    /**
     * Set the method which will be executed immediately after the client connects to the server
     * @param toBeExecuted
     */

    this.onReady = function (toBeExecuted) {
        userStartFunction = toBeExecuted;
    };

    // Initialized by UniJ server
    var clientName = "no-name-yet";

    if (webSocketAddress === undefined) {
        var protocol = (window.location.protocol === "https:") ? "wss://" : "ws://";
        webSocketAddress = protocol + window.location.host + "/unij";
    }

    var webSocket = new WebSocket(webSocketAddress);

    webSocket.onopen = function (event) {
        setupDefaultProcedures();
    };

    webSocket.onmessage = function (event) {

        var message = JSON.parse(event.data);
        var procedure = localProcedures[message.pro];

        // Check if there are callbacks for this procedure
        if (procedure === undefined) {
            logToServer("I don't know procedure with name \"" + message.pro + "\"");
        } else {
            // check if procedure is disposable
            if (procedure.isDisposable) {
                procedure.method.apply(this, message.par);
                delete localProcedures[message.pro];
                // not disposable
            } else {
                procedure.method.apply(this, message.par);
            }
        }
    };

    webSocket.onclose = function (event) {
        var reason = (event.reason === "") ? "unexpected closing" : event.reason;
        log("Lost connection from server because of " + reason);
    };


    var localProcedures = {};

    var Procedure = function (method, isDisposable) {
        this.method = method;
        this.isDisposable = isDisposable;
    };

    /**
     * Add a procedure which will be available to the UniJ server for execution
     * @param name              Name of the procedure
     * @param procedure         Function to be exposed to the UniJ Server
     * @param isDisposable      If procedure will be deleted after first execution
     */

    this.addProcedure = function (name, procedure, isDisposable) {
        if (localProcedures[name] !== undefined) {
            log("Local procedure with name \"" + name + "\" has been overwritten");
        }
        if (isDisposable === undefined) {
            isDisposable = false;
        }
        localProcedures[name] = new Procedure(procedure, isDisposable);
    };

    /**
     * Remove a procedure if it exists
     * @param name
     */

    this.removeProcedure = function (name) {
        if (localProcedures[name] === undefined) {
            log("Removing local procedure with name \"" + name + "\" is unnecessary because it doesn't exit");
        } else {
            delete localProcedures[name];
        }
    };

    /**
     * Execute a procedure of the UniJ server remotely
     *
     * @param remoteProcedureName
     * @param parameters
     */

    this.execute = function (remoteProcedureName, parameters) {
        // Minified JSON to reduce parsing steps
        webSocket.send("{\"" + remoteProcedureName + "\":" +
            JSON.stringify(Array.prototype.slice.call(arguments, 1)) + "}");
    };

    /**
     * Sends a proposal to change this clients name. Server then changes it or not
     * @param newName
     */

    this.setClientName = function (newName) {
        this.execute("setClientName", clientName, newName);
    };

    /**
     * Get the name of this UniJ client
     * @returns {string}
     */

    this.getClientName = function () {
        return clientName;
    };

    function setupDefaultProcedures() {

        // Server can set the clients name
        localProcedures.setClientName = new Procedure(function (name) {
            clientName = name;
        }, false);

        // Will start onReady() block
        localProcedures.clientIsReadyNow = new Procedure(function() {
            log("Connected to server!");
            userStartFunction();
        }, true);

        // So that the server can display logs on the client side
        localProcedures.unijLog = new Procedure(function (log) {
            console.log(log);
        }, false);
    }

    /**
     * Send logs to the UniJ server
     * @param message   Contains the log
     */

    function logToServer(message) {
        UniJ.execute("unijLog", buildLog(message));
    }

    /**
     * Construct a client log message
     * @param message       Contains the log
     * @returns {string}
     */

    function buildLog(message) {
        return "UniJ Client \"" + clientName + "\" | " + message;
    }

    /**
     * Regular console.log() but formatted
     * @param message
     */

    function log(message) {
        console.log(buildLog(message));
    }
}

var UniJ = new UniJClient();