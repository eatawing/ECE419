package app_kvServer;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

import logger.LogSetup;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import app_kvServer.ClientConnection;

/**
 * Represents a simple Echo Server implementation.
 */
public class KVServer extends Thread {

	private static Logger logger = Logger.getRootLogger();
	
	private int port;
	// Add private variables for storing the cache size and caching strategy inside the Server
	private int m_cacheSize;
	private String m_strategy;
    private ServerSocket serverSocket;
    private boolean running;
    
    /**
     * Constructs a (Echo-) Server object which listens to connection attempts 
     * at the given port.
     * 
     * @param port a port number which the Server is listening to in order to 
     * 		establish a socket connection to a client. The port number should 
     * 		reside in the range of dynamic ports, i.e 49152 - 65535.
     */
	/**
	 * Start KV Server at given port
	 * @param port given port for storage server to operate
	 * @param cacheSize specifies how many key-value pairs the server is allowed 
	 *           to keep in-memory
	 * @param strategy specifies the cache replacement strategy in case the cache 
	 *           is full and there is a GET- or PUT-request on a key that is 
	 *           currently not contained in the cache. Options are "FIFO", "LRU", 
	 *           and "LFU".
	 */
    public KVServer(int port, int cacheSize, String strategy) {
    	// Initialize the private variables of the server object
        this.port = port;
        this.m_cacheSize = cacheSize;
        this.m_strategy = strategy;
        this.start();
    }

    /**
     * Initializes and starts the server. 
     * Loops until the the server should be closed.
     */
    public void run() {
        
    	running = initializeServer();
        
        if(serverSocket != null) {
	        while(isRunning()){
	            try {
	                Socket client = serverSocket.accept();                
	                ClientConnection connection = 
	                		new ClientConnection(client);
	                new Thread(connection).start();
	                
	                logger.info("Connected to " 
	                		+ client.getInetAddress().getHostName() 
	                		+  " on port " + client.getPort());
	            } catch (IOException e) {
	            	logger.error("Error! " +
	            			"Unable to establish connection. \n", e);
	            }
	        }
        }
        logger.info("Server stopped.");
    }
    
    private boolean isRunning() {
        return this.running;
    }

    /**
     * Stops the server insofar that it won't listen at the given port any more.
     */
    public void stopServer(){
        running = false;
        try {
			serverSocket.close();
		} catch (IOException e) {
			logger.error("Error! " +
					"Unable to close socket on port: " + port, e);
		}
    }

    private boolean initializeServer() {
    	logger.info("Initialize server ...");
    	try {
            serverSocket = new ServerSocket(port);
            logger.info("Server listening on port: " 
            		+ serverSocket.getLocalPort());    
            return true;
        
        } catch (IOException e) {
        	logger.error("Error! Cannot open server socket:");
            if(e instanceof BindException){
            	logger.error("Port " + port + " is already bound!");
            }
            return false;
        }
    }
    
    /**
     * Main entry point for the echo server application. 
     * @param args contains the port number at args[0].
     */
    public static void main(String[] args) {
    	try {
			new LogSetup("logs/server.log", Level.ALL);
			// change the amount of commandline arguments to be parsed into 3
			// first one is port, second is cache size, third one is strategy
			if(args.length != 3) {
				System.out.println("Error! Invalid number of arguments!");
				System.out.println("Usage: Server <int port> <int cacheSize> <string strategy: FIFO, LRU or LFU>!");
			} else {
				int port = Integer.parseInt(args[0]);
				int cacheSize = Integer.parseInt(args[1]);
				String strategy = args[2];
				// Handle invalid input for server strategy argument
				if (!strategy.equals("FIFO") && !strategy.equals("FIFO") && !strategy.equals("FIFO")) {
					System.out.println("Error! strategy argument invalid!");
					System.out.println("Usage: Server <int port> <int cacheSize> <string strategy: FIFO, LRU or LFU>! Please try again");
					System.exit(0);
				}
				new KVServer(port, cacheSize, strategy).start();
			}
		} catch (IOException e) {
			System.out.println("Error! Unable to initialize logger!");
			e.printStackTrace();
			System.exit(1);
		} catch (NumberFormatException nfe) {
			System.out.println("Error! Invalid argument <port>! Not a number!");
			System.out.println("Usage: Server <port>!");
			System.exit(1);
		}
    }
}


