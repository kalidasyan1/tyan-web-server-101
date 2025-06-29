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
        logger.info("Client disconnected: " + clientChannel.getRemoteAddress());
        key.cancel();
        clientChannel.close();
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

      clientChannel.close();
      key.cancel();
      logger.info("Closed connection for client: " + clientChannel.getRemoteAddress());
    } catch (IOException e) {
      logger.warning("Error in NioWorker: " + e.getMessage());
      if (key.channel() != null) {
        try {
          key.channel().close();
        } catch (IOException closeException) {
          logger.warning("Failed to close channel: " + closeException.getMessage());
        }
      }
      key.cancel();
    }
  }
}
