package tomcat.minitomcat.server;

import java.nio.channels.SocketChannel;


public class NioConnection {
  private final SocketChannel clientChannel;

  public NioConnection(SocketChannel clientChannel) {
    this.clientChannel = clientChannel;
  }

  public SocketChannel getClientChannel() {
    return clientChannel;
  }
}
