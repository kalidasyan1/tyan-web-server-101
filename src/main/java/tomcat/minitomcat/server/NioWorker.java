package tomcat.minitomcat.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;


public class NioWorker implements Runnable {
  private static final Logger logger = Logger.getLogger(NioWorker.class.getName());
  private final SelectionKey key;

  public NioWorker(SelectionKey key) {
    this.key = key;
  }

  @Override
  public void run() {
    try {
      NioConnection nioConnection = (NioConnection) key.attachment();
      SocketChannel clientChannel = nioConnection.getClientChannel();
      ByteBuffer readBuffer = ByteBuffer.allocate(1024);
      int bytesRead = clientChannel.read(readBuffer);

      if (bytesRead == -1) {
        onClientDisconnected(clientChannel);
        return;
      } else if (bytesRead == 0) {
        return; // No data to read, return
      }

      readBuffer.flip();
      String request = new String(readBuffer.array(), 0, bytesRead);
      logger.info("Received request: " + request);
      String httpResponse = "HTTP/1.1 200 OK\r\nContent-Length: 13\r\n\r\nHello, World!";
      ByteBuffer writeBuffer = ByteBuffer.wrap(httpResponse.getBytes());
      while (writeBuffer.hasRemaining()) {
        clientChannel.write(writeBuffer);
      }
      logger.info("Sent response to client: " + clientChannel.getRemoteAddress());

      onClientDisconnected(clientChannel);
    } catch (IOException e) {
      logger.warning("Error in NioWorker: " + e.getMessage());
      onClientDisconnected((SocketChannel) key.channel());
    }
  }

  /**
   * Handle client disconnection.
   */
  protected void onClientDisconnected(SocketChannel clientChannel) {
    try {
      logger.info("Client disconnected: " + clientChannel.getRemoteAddress());
      clientChannel.close();
    } catch (IOException e) {
      logger.warning("Error closing client channel: " + e.getMessage());
    }
  }
}
