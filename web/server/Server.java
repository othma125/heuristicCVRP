// Author: Othmane

package web.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.Executors;

/**
 * Minimal landing-page backend built on the JDK's {@link HttpServer} (no
 * dependencies). This class owns only the bootstrap and the route table; the
 * work lives in {@link Http} (transport), {@link Instances} (dataset), and
 * {@link Solver} (solving endpoints).
 *
 * @author Othmane EL YAAKOUBI
 */
public class Server {

    /**
     * Starts the HTTP server on the port supplied as the first argument, or on the
     * port read from {@code .env} (default 8081), and registers all API contexts.
     *
     * @param args optional port number
     * @throws IOException if the server socket cannot be created
     */
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : envPort();
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (BindException e) {
            System.err.println("Port " + port + " is already in use. Stop the running server"
                    + " (bash kill-server.sh) or pick another port: java -cp out web.server.Server 9090");
            System.exit(1);
            return;
        }
        server.setExecutor(Executors.newCachedThreadPool());

        Solver solver = new Solver();

        server.createContext("/", Server::serveIndex);
        server.createContext("/app.js", ex -> Http.serveFile(ex, new File("Web/app.js"), "text/javascript; charset=utf-8"));
        server.createContext("/styles.css", ex -> Http.serveFile(ex, new File("Web/styles.css"), "text/css; charset=utf-8"));
        server.createContext("/assets/profile.jpg", ex -> Http.serveFile(ex, new File("profile.jpg"), "image/jpeg"));
        server.createContext("/api/folders", ex -> Http.json(ex, Instances.folders()));
        server.createContext("/api/instances", ex -> Http.json(ex, Instances.in(Http.query(ex).get("folder"))));
        server.createContext("/api/vrp", ex -> Http.serveFile(ex, Instances.vrpFile(Http.query(ex)), "text/plain; charset=utf-8"));
        server.createContext("/api/solve", solver::solve);
        server.createContext("/api/stop", solver::stop);

        server.start();
        System.out.println("Landing page ready -> http://localhost:" + port);
    }

    /** Reads PORT from the .env file at the project root; defaults to 8081. */
    private static int envPort() {
        File env = new File(".env");
        if (env.exists()) {
            try {
                for (String line : Files.readAllLines(env.toPath())) {
                    String t = line.trim();
                    if (t.startsWith("PORT=")) return Integer.parseInt(t.substring(5).trim());
                }
            } catch (IOException | NumberFormatException ignored) {
                // Fall back to the default port.
            }
        }
        return 8081;
    }

    /**
     * Serves the landing page ({@code Web/index.html}) for the root path only.
     *
     * @param ex the HTTP exchange
     * @throws IOException when writing the response fails
     */
    private static void serveIndex(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) {
            Http.notFound(ex);
            return;
        }
        Http.serveFile(ex, new File("Web/index.html"), "text/html; charset=utf-8");
    }
}
