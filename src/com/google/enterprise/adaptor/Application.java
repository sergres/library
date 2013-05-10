// Copyright 2013 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.adaptor;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Provides framework for adaptors to act as a stand-alone application. This
 * entails creating well-configured HttpServer instances, argument parsing,
 * shutdown handling, and more.
 */
public final class Application {
  private static final String SLEEP_PATH = "/sleep";

  private static final Logger log
      = Logger.getLogger(Application.class.getName());

  private final Config config;
  private final GsaCommunicationHandler gsa;
  private Thread shutdownHook;
  private HttpServer primaryServer;
  private HttpServer dashboardServer;

  public Application(Adaptor adaptor, Config config) {
    this.config = config;

    gsa = new GsaCommunicationHandler(adaptor, config);
  }

  /**
   * Start necessary services for receiving requests and managing background
   * tasks. Non-daemon threads are created, so call {@link #stop} for graceful
   * manual shutdown. A shutdown hook is automatically installed that calls
   * {@code stop()}.
   */
  public void start() throws IOException, InterruptedException {
    synchronized (this) {
      daemonInit();

      // The shutdown hook is purposefully not part of the deamon methods,
      // because it should only be done when running from the command line.
      shutdownHook = new Thread(new ShutdownHook(), "gsacomm-shutdown");
      Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    daemonStart();
  }

  /**
   * Reserves resources for later use. This may be run with different
   * permissions (like as root), to reserve ports or other things that need
   * elevated privileges.
   */
  synchronized void daemonInit() throws IOException {
    if (primaryServer != null) {
      throw new IllegalStateException("Already started");
    }
    primaryServer = createHttpServer(config);
    dashboardServer = createDashboardHttpServer(config);
    // Because once stopped the server can't be reused, we can't reuse its
    // bind()ed socket if we stop it. So although ideally we would start/stop in
    // daemonStart/daemonStop, we instead must do it in
    // daemonInit/daemonDestroy.
    primaryServer.start();
    dashboardServer.start();
  }

  /**
   * Really start. This must be called after a successful {@link #daemonInit}.
   */
  void daemonStart() throws IOException, InterruptedException {
    gsa.start(primaryServer, dashboardServer);

    if (config.isAdaptorPushDocIdsOnStartup()) {
      log.info("Pushing once at program start");
      gsa.checkAndScheduleImmediatePushOfDocIds();
    }
  }

  /**
   * Stop processing incoming requests and background tasks, allowing graceful
   * shutdown.
   */
  public synchronized void stop(long time, TimeUnit unit) {
    daemonStop(time, unit);
    daemonDestroy(time, unit);
    if (shutdownHook != null) {
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
      } catch (IllegalStateException ex) {
        // Already executing hook.
      }
      shutdownHook = null;
    }
  }

  /**
   * Stop all the services we provide. This is the opposite of {@link
   * #daemonStart}.
   */
  synchronized void daemonStop(long time, TimeUnit unit) {
    if (primaryServer == null) {
      throw new IllegalStateException("Already stopped");
    }
    gsa.stop(time, unit);
  }

  /**
   * Release reserved resources. This is the opposite of {@link
   * #daemonInit}.
   */
  synchronized void daemonDestroy(long time, TimeUnit unit) {
    httpServerShutdown(primaryServer, time, unit);
    log.finer("Completed primary server stop");
    primaryServer = null;

    httpServerShutdown(dashboardServer, time, unit);
    log.finer("Completed dashboard stop");
    dashboardServer = null;
  }

  private static void httpServerShutdown(HttpServer server, long time,
      TimeUnit unit) {
    // Workaround Java Bug 7105369.
    SleepHandler sleepHandler = new SleepHandler(100 /* millis */);
    server.createContext(SLEEP_PATH, sleepHandler);
    issueSleepGetRequest(server.getAddress(), server instanceof HttpsServer);

    server.stop((int) unit.toSeconds(time));
    ((ExecutorService) server.getExecutor()).shutdownNow();
  }

  /**
   * Issues a GET request to a SleepHandler. This is used to workaround Java
   * Bug 7105369.
   *
   * <p>The bug is an issue with HttpServer where stop() waits the full amount
   * of allotted time if the serve is idle. However, if a request is being
   * handled when stop() is called, then it will return as soon as all requests
   * are processed, or the allotted time is reached.
   *
   * <p>Thus, this workaround tries to force a request to be in-procees when
   * stop() is called, so that it can return sooner. We issue a request to a
   * SleepHandler that takes a fixed amount of time to process the request
   * before calling stop(). In the event everything goes as planned, the request
   * completes after stop() has been called and allows stop() to exit quickly.
   */
  private static void issueSleepGetRequest(InetSocketAddress address,
      boolean isHttps) {
    URL url;
    try {
      url = new URL(isHttps ? "https" : "http",
          address.getAddress().getHostAddress(), address.getPort(), SLEEP_PATH);
    } catch (MalformedURLException ex) {
      log.log(Level.WARNING,
          "Unexpected error. Shutting down will be slow.", ex);
      return;
    }

    final URLConnection conn;
    try {
      conn = url.openConnection();
      conn.connect();
    } catch (IOException ex) {
      log.log(Level.WARNING, "Error performing shutdown GET", ex);
      return;
    }
    try {
      // Provide some time for the connect() to be processed on the server.
      Thread.sleep(15);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          conn.getInputStream().close();
          log.finer("Closed shutdown GET");
        } catch (IOException ex) {
          log.log(Level.WARNING, "Error closing stream of shutdown GET", ex);
        }
      }
    }).start();
  }

  private static HttpServer createHttpServer(Config config) throws IOException {
    HttpServer server;
    if (!config.isServerSecure()) {
      server = HttpServer.create();
    } else {
      server = HttpsServer.create();
      try {
        HttpsConfigurator httpsConf
            = new HttpsConfigurator(SSLContext.getDefault()) {
              public void configure(HttpsParameters params) {
                SSLParameters sslParams
                    = getSSLContext().getDefaultSSLParameters();
                // Allow verifying the GSA and other trusted computers.
                sslParams.setWantClientAuth(true);
                params.setSSLParameters(sslParams);
              }
            };
        ((HttpsServer) server).setHttpsConfigurator(httpsConf);
      } catch (java.security.NoSuchAlgorithmException ex) {
        throw new RuntimeException(ex);
      }
    }

    int maxThreads = config.getServerMaxWorkerThreads();
    int queueCapacity = config.getServerQueueCapacity();
    BlockingQueue<Runnable> blockingQueue
        = new ArrayBlockingQueue<Runnable>(queueCapacity);
    // The Executor can't reject jobs directly, because HttpServer does not
    // appear to handle that case.
    RejectedExecutionHandler policy
        = new SuggestHandlerAbortPolicy(HttpExchanges.abortImmediately);
    Executor executor = new ThreadPoolExecutor(maxThreads, maxThreads,
        1, TimeUnit.MINUTES, blockingQueue, policy);
    server.setExecutor(executor);

    server.bind(new InetSocketAddress(config.getServerPort()), 0);
    log.info("GSA host name: " + config.getGsaHostname());
    log.info("server is listening on port #" + server.getAddress().getPort());
    return server;
  }

  private static HttpServer createDashboardHttpServer(Config config)
      throws IOException {
    boolean secure = config.isServerSecure();
    HttpServer server;
    if (!secure) {
      server = HttpServer.create();
    } else {
      server = HttpsServer.create();
      SSLContext defaultSslContext;
      try {
        defaultSslContext = SSLContext.getDefault();
      } catch (NoSuchAlgorithmException ex) {
        throw new RuntimeException(ex);
      }
      HttpsConfigurator httpsConf = new HttpsConfigurator(defaultSslContext);
      ((HttpsServer) server).setHttpsConfigurator(httpsConf);
    }
    // The Dashboard is on a separate port to prevent malicious HTML documents
    // in the user's repository from performing admin actions with
    // XMLHttpRequest or the like, as the HTML page will then be blocked by
    // same-origin policies.
    server.bind(new InetSocketAddress(config.getServerDashboardPort()), 0);

    // Use separate Executor for Dashboard to allow the administrator to
    // investigate why things are going wrong without waiting on the normal work
    // queue.
    int maxThreads = 4;
    Executor executor = new ThreadPoolExecutor(maxThreads, maxThreads,
        10, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>());
    server.setExecutor(executor);

    log.info("dashboard is listening on port #"
        + server.getAddress().getPort());

    return server;
  }

  /** Returns the {@link GsaCommunicationHandler} used by this instance. */
  public GsaCommunicationHandler getGsaCommunicationHandler() {
    return gsa;
  }

  /** Returns the {@link Config} used by this instance. */
  public Config getConfig() {
    return config;
  }

  /**
   * Main for adaptors to utilize when wanting to act as an application. This
   * method primarily parses arguments and creates an application instance
   * before calling it's {@link #start}.
   *
   * @return the application instance in use
   */
  public static Application main(Adaptor adaptor, String[] args) {
    Application app = daemonMain(adaptor, args);

    // Setup providing content.
    try {
      app.start();
      log.info("doc content serving started");
    } catch (InterruptedException e) {
      throw new RuntimeException("could not start serving", e);
    } catch (IOException e) {
      throw new RuntimeException("could not start serving", e);
    }

    return app;
  }

  /**
   * Performs basic bootstrapping like normal {@link #main}, but does not start
   * the application.
   */
  static Application daemonMain(Adaptor adaptor, String[] args) {
    Config config = new Config();
    adaptor.initConfig(config);
    config.autoConfig(args);

    if (config.useAdaptorAutoUnzip()) {
      adaptor = new AutoUnzipAdaptor(adaptor);
    }
    return new Application(adaptor, config);
  }

  private class ShutdownHook implements Runnable {
    @Override
    public void run() {
      stop(3, TimeUnit.SECONDS);
    }
  }

  /**
   * Executes Runnable in current thread, but only after setting a thread-local
   * object. The code that will be run, is expected to take notice of the set
   * variable and abort immediately. This is a hack.
   */
  private static class SuggestHandlerAbortPolicy
      implements RejectedExecutionHandler {
    private final ThreadLocal<Object> abortImmediately;
    private final Object signal = new Object();

    public SuggestHandlerAbortPolicy(ThreadLocal<Object> abortImmediately) {
      this.abortImmediately = abortImmediately;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
      abortImmediately.set(signal);
      try {
        r.run();
      } finally {
        abortImmediately.set(null);
      }
    }
  }
}
