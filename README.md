# Netty Usage Examples

This directory contains various examples demonstrating basic Netty usage patterns, organized by type.

## Prerequisites

Make sure you have Java 17+ and Maven installed. The Netty dependency is already configured in the `pom.xml` file.

## Directory Structure

The examples are organized into subdirectories by functionality:
- `echo/` - Echo server and client examples
- `http/` - HTTP server examples  
- `time/` - Time server examples
- `chat/` - Chat server examples
- `websocket/` - WebSocket server examples
- `file/` - File server examples

## Examples Included

### 1. Echo Server & Client (`echo/`)
- **Files**: `EchoServer.java`, `EchoClient.java`, `EchoServerHandler.java`
- **Purpose**: Demonstrates basic server-client communication
- **Server**: Echoes back any message received from clients
- **Client**: Connects to server and sends messages
- **Run Server**: `java netty.usage.echo.EchoServer [port]` (default: 8080)
- **Run Client**: `java netty.usage.echo.EchoClient [host] [port]` (default: localhost 8080)

### 2. Simple HTTP Server (`http/`)
- **Files**: `SimpleHttpServer.java`
- **Purpose**: Basic HTTP server responding to all requests
- **Features**: Returns request information and "Hello World" message
- **Run**: `java netty.usage.http.SimpleHttpServer [port]` (default: 8081)
- **Test**: Open `http://localhost:8081` in your browser

### 3. Time Server (`time/`)
- **Files**: `TimeServer.java`
- **Purpose**: Sends current timestamp to clients upon connection
- **Run**: `java netty.usage.time.TimeServer [port]` (default: 8082)
- **Test**: `telnet localhost 8082`

### 4. Chat Server (`chat/`)
- **Files**: `ChatServer.java`
- **Purpose**: Multi-client chat server that broadcasts messages
- **Features**: 
  - Supports multiple concurrent clients
  - Broadcasts messages to all connected clients
  - Shows join/leave notifications
- **Run**: `java netty.usage.chat.ChatServer [port]` (default: 8083)
- **Test**: `telnet localhost 8083` (open multiple terminals)

### 5. WebSocket Server (`websocket/`)
- **Files**: `WebSocketServer.java`
- **Purpose**: WebSocket server for real-time communication
- **Features**: Handles WebSocket handshake and echoes messages
- **Run**: `java netty.usage.websocket.WebSocketServer [port]` (default: 8084)
- **Test**: Use a WebSocket client or browser developer tools

### 6. File Server (`file/`)
- **Files**: `FileServer.java`
- **Purpose**: Static file server serving files from current directory
- **Features**: 
  - Serves files with appropriate MIME types
  - Handles chunked file transfer for large files
  - Basic error handling for missing files
- **Run**: `java netty.usage.file.FileServer [port]` (default: 8085)
- **Test**: Open `http://localhost:8085` in your browser

## Building and Running

1. **Compile the project**:
   ```bash
   mvn compile
   ```

2. **Run any example**:
   ```bash
   mvn exec:java -Dexec.mainClass="netty.usage.echo.EchoServer"
   ```

   Or compile and run directly:
   ```bash
   mvn compile exec:java -Dexec.mainClass="netty.usage.echo.EchoServer"
   ```

## Key Netty Concepts Demonstrated

- **Bootstrap & ServerBootstrap**: Setting up clients and servers
- **EventLoopGroup**: Managing I/O threads (boss and worker groups)
- **ChannelInitializer**: Configuring the channel pipeline
- **ChannelHandler**: Processing inbound and outbound events
- **ByteBuf**: Netty's buffer implementation
- **Codecs**: HTTP, WebSocket, String, and frame-based protocols
- **Channel Groups**: Managing multiple connections
- **Chunked Transfer**: Efficient large file handling

## Testing the Examples

1. **Echo Server/Client**: 
   - Start EchoServer in one terminal
   - Run EchoClient in another terminal

2. **HTTP Server**: 
   - Start SimpleHttpServer
   - Open browser to `http://localhost:8081`

3. **Chat Server**: 
   - Start ChatServer
   - Connect multiple telnet clients: `telnet localhost 8083`
   - Type messages in any client to broadcast to all

4. **File Server**: 
   - Create some test files (HTML, CSS, images) in the project directory
   - Start FileServer
   - Access files via browser: `http://localhost:8085/filename`

## Notes

- All servers use different default ports to avoid conflicts
- Press Ctrl+C to stop any running server
- Check console output for connection status and errors
- Ensure firewall allows connections on the specified ports


## Key Learnings: Netty EventLoop vs Tomcat NIO Architecture

### EventLoop Architecture Deep Dive

**Netty's Single-Threaded-Per-Channel Model:**
- Each `EventLoop` runs on exactly one thread and handles multiple channels
- Channels are permanently bound to the same `EventLoop` for their lifetime (thread affinity)
- All I/O operations and business logic for a channel execute on the same thread
- `NioEventLoopGroup()` creates 2 × CPU cores EventLoops by default

**Key Architectural Benefits:**

1. **Lock-Free Channel Operations**: No synchronization needed for channel-specific data since only one thread accesses each channel
2. **Minimal Context Switching**: I/O detection, processing, and response writing happen on the same thread
3. **Better CPU Cache Utilization**: Thread-local data structures reduce cache misses and improve temporal locality
4. **Simplified Concurrency**: No coordination needed between I/O and business logic threads

### Tomcat NIO vs Netty Comparison

| Aspect | Tomcat NIO | Netty EventLoop |
|--------|------------|-----------------|
| **Thread Model** | Acceptor → Poller → Worker Thread Pool | Single EventLoop thread per channel group |
| **Channel Access** | Multiple worker threads can access same socket (HTTP keep-alive) | Single thread per channel (thread affinity) |
| **Synchronization** | Required for concurrent socket writes/reads | Not needed for channel operations |
| **Buffer Management** | Shared buffer pools with coordination overhead | Thread-local buffers per EventLoop |
| **Context Switching** | Poller → Worker handoff required | Everything on same thread |

### Critical Design Considerations

**Tomcat's Multi-Threading Challenges:**
- HTTP keep-alive connections can be processed by different worker threads
- Requires synchronization for concurrent socket access (read/write locks)
- Shared buffer pools need coordination between threads
- Cache misses occur when threads access shared data structures

**Netty's EventLoop Constraints:**
- **Never block the EventLoop thread** - offload blocking I/O to separate thread pools
- Business logic (UserController) should execute on dedicated thread pools
- Database calls, file I/O, or any blocking operations will freeze all channels on that EventLoop

**Best Practices for Netty:**
```java
// ❌ BAD - Blocks EventLoop thread
public void ch