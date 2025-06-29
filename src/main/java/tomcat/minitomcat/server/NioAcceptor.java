package tomcat.minitomcat.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;


public class NioAcceptor implements Runnable {

  private static final Logger logger = Logger.getLogger(NioAcceptor.class.getName());
  private final int port;
  private final NioPoller poller;
  private volatile boolean stopped = false;

  public NioAcceptor(int port, NioPoller poller) {
    this.port = port;
    this.poller = poller;
  }

  @Override
  public void run() {
    logger.info("NioAcceptor is running...");
    try {
      try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {

        serverSocketChannel.bind(new InetSocketAddress(port));
        serverSocketChannel.configureBlocking(false);
        logger.log(Level.INFO, "NioAcceptor started on port {0}", port);

        while (!isStopped()) {
          SocketChannel clientChannel = serverSocketChannel.accept();
          if (clientChannel == null) {
            continue; // No connection accepted, continue polling
          }
          logger.info("Accepted connection from " + clientChannel.getRemoteAddress());
          clientChannel.configureBlocking(false);
          poller.registerChannel(clientChannel);
        }
      }
    } catch (IOException e) {
      logger.warning("Error in NioAcceptor: " + e.getMessage());
    }
  }

  private boolean isStopped() {
    return stopped;
  }
}
