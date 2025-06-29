package tomcat.minitomcat.server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;


public class NioPoller implements Runnable {

  private final Selector selector;
  private final ExecutorService workerPool;
  private static final Logger logger = Logger.getLogger(NioPoller.class.getName());

  public NioPoller(ExecutorService workerPool) throws IOException {
    this.selector = Selector.open();
    this.workerPool = workerPool;
  }

  public void registerChannel(SocketChannel clientChannel) throws IOException {
    clientChannel.register(selector, SelectionKey.OP_READ, new NioConnection(clientChannel));
    selector.wakeup();
    logger.info("Registered channel: " + clientChannel.getRemoteAddress());
  }

  @Override
  public void run() {
    logger.info("NioPoller is running...");
    try {
      while (true) {
        int readyChannels = selector.select(1000);
        if (readyChannels == 0) {
          continue; // No channels ready, continue polling
        }
        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while (iterator.hasNext()) {
          SelectionKey key = iterator.next();
          iterator.remove();

          if (key.isValid() && key.isReadable()) {
            workerPool.submit(new NioWorker(key));
          }
        }
      }
    } catch (IOException e) {
      logger.warning("Error in NioPoller: " + e.getMessage());
    }
  }
}
