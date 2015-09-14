"use strict";

// Make sure to install "ws" with for example:"npm install --save ws"

var UniJ = require("./unij-client-nodejs-0.1.0.js")("ws://localhost:7777");

UniJ.addProcedure("showThemApples", function(msg) { console.log(msg);});

UniJ.onReady(function() {

    UniJ.setClientName("because I can");
    UniJ.execute("printToConsole", "Hello there!");

    // After connection, otherwise server will overwrite your name
    UniJ.execute("destroyAndShow", "This was a sentence once");
});
