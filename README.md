# UniJ - Java to JavaScript Bridge

Connect your Java applications with the Browser, Node.js or any other platform that supports WebSockets.

Why would you do that? Well you could write a nice UI in HTML/CSS for your Java app, create a web application or just use Java and JavaScript in the same project.

***Be aware:*** *UniJ is still in an early stage of development.*

## How to use UniJ?

The connection is based on the concept of **procedures**. A procedure is a method which is available for others to execute. This means the browser can execute a procedure on the server and the other way around. Any party can do this independently.

Here a simple example where the browser creates a procedure which the Java application then executes.

```javascript
~ imaBrowserScript.js

  // Make drawing trees available for others to call
  UniJ.addProcedure("drawTree", (tree) => draw(tree));

~ SeriousBusinessLogic.java

  // Execute a procedure called "drawTree" in the browser
  UniJ.execute("browser", "drawTree", new Tree());
```

That's it!

And yes, you can send any objects back and forth and don't have to worry about casting.

## Get it!

Java Server

- Standalone .jar
- Maven repository (soon!)

JavaScript client

- Get it here

## FAQ

**Is it possible to connect to other languages as well?**

Yes, UniJ uses WebSockets and can therefore connect with any platform that supports it. However so far there only exists a JavaScript client.

**How is UniJ different from a REST API?**

UniJ uses WebSockets which allow for bi-directional communication, where as with regular HTTP the client always initiates communication. WebSockets allow the server to easily emit changes to the clients without the need of client side polling. Also there is a lot less overhead when using the connection for many small messages because the connection stays open.

And lastly you don't have to worry about casting and parsing of messages and their content. UniJ does it automatically.

**How fast is UniJ?**

Essentially as fast as WebSocket allows. UniJ does only minimal processing, namely casting, serialization and routing.

The Round-Trip-Latency of a message is ~0.18ms which will allow for more than 5k distinct messages to be send to the client and back per second (of course the values depend on the network). But there is also the possibility to add a custom WebSocket if raw byte streaming is desired.

## Requirements

- Java 8
- Platform with WebSocket support (i.e. Browser, Node.js)

