package tomcat.minitomcat.server;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;


public class NioServer {
  private static final Logger logger = Logger.getLogger(NioServer.class.getName());
  private static final int PORT = 8090;
  private static final int MAX_WORKERS = 10;

  public static void main(String[] args) throws IOException {
    logger.info("Starting NIO server...");

    ExecutorService workerPool = Executors.newFixedThreadPool(MAX_WORKERS);

    NioPoller poller = new NioPoller(workerPool);
    new Thread(poller, "NioPoller").start();
    new Thread(new NioAcceptor(PORT, poller), "NioAcceptor").start();

    // This is a placeholder for the actual server logic
    logger.info("NIO server started on port " + PORT);
  }
}
