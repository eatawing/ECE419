package client;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;

import common.messages.KVMessage;
import common.messages.MessageType;
import common.HashRing;
import common.HashRing.Server;

import client.Client;
import client.KVCommInterface.SocketStatus;

public class KVStore implements KVCommInterface {
	private String address;
	private int port;
	private Client client = null;
	private HashRing metadata;
	private Logger logger = Logger.getRootLogger();
	private boolean connected = false;
	
	/**
	 * Initialize KVStore with address and port of KVServer
	 * @param address the address of the KVServer
	 * @param port the port of the KVServer
	 */
	public KVStore(String address, int port) {
		this.address = address;
		this.port = port;
		this.metadata = new HashRing();
		//add the given server to the metadata. This will be KVStore's first try when doing a request.
		this.metadata.addServer(new Server(address,port));
	}
	
	@Override
	public boolean connect() 
		throws UnknownHostException, IOException, ConnectException{
		
		int triesRemaining = 5;
		while (triesRemaining-- > 0){
			//try connecting to this server 
			try {
				logger.info("Client trying to connect to " + String.valueOf(port));
				client = new Client(address, port);
				client.addListener(this);
				//wait for "connection successful" response
				KVMessage response = client.getResponse();
				if (response.getStatus().equals("CONNECT_SUCCESS")){
					logger.debug("Client: Connection successful to server "+String.valueOf(port));
					connected = true;
					return true;
				}
				else{
					if (triesRemaining > 0){
						logger.debug("Client: Unable to connect to server "+String.valueOf(port)+". Waiting 1 second and trying again.");
						try {
							TimeUnit.SECONDS.sleep(1); 		
						} catch (InterruptedException e){}
					}
				}
			}
			catch (Exception e){
				logger.debug(e.getMessage());			
			}
		}
		return false;
	}
	
	/**
	 * function added for convenience (used in testing)
	 * Connect to the server with the given address
	 */
	public boolean connect(String address, int port) 
		throws UnknownHostException, IOException, ConnectException{
		disconnect();
		this.address = address;
		this.port = port;
		return connect();
	}
	
	public boolean isConnected() {
		return connected;
	}

	@Override
	public void disconnect() {
		if(client != null) {
			client.closeConnection();
			client = null;
		}
	}
	

	@Override
	public KVMessage put(String key, String value) throws Exception {		
		MessageType request = new MessageType("put","PUT",key,value);
		if (request.error != null){
			throw new Exception(request.error);
		}		
		
		if(!connectToResponsible(key)) {
			return new MessageType("put", "put_responsible_NOT_PROCESSED", "", "");
		}
		
		KVMessage output = sendRequest(request);

		return output;
	}
	
	

	@Override
	public KVMessage get(String key) throws Exception {
		MessageType request = new MessageType("get","GET",key,"");
		if (request.error != null){
			throw new Exception(request.error);
		}
		if(!connectToResponsibleGet(key)) {
			return new MessageType("get", "connectToResponsibleGet_NOT_PROCESSED", "", "");
		}
		return sendRequest(request);
	}
	
	/**
	 * Check which server is responsible for the given key from the cached metadata
	 * and try connecting to it. If unable to connect, try to connect to any server.
	 * 
	 * If we are already connected to the responsible server, simply return true
	 */
	private boolean connectToResponsible(String key) {
		Server responsible = this.metadata.getResponsible(key);
		if (responsible == null) {
			return false;
		}
		
		// Check if we are ALREADY connected to the right server, then we can return true and do nothing
		if(this.address.equals(responsible.ipAddress) && this.port == responsible.port) {
			return true;
		}
		
		logger.debug("Trying to connect to responsible server "+responsible.toString());
		disconnect();
		
		try {
			this.address = responsible.ipAddress;
			this.port = responsible.port;
			boolean ok = connect();
			if (!ok){
				return connectToAnyServer();
			}
		} catch (Exception e) {
			logger.debug("Unable to connect to responsible server "+responsible.toString());
			return connectToAnyServer();
		}
		
		return true;
	}
	
	/**
	 * Same as previous function, except this is for GET
	 * here we can check if the currently connected server can operate get on the data
	 */
	private boolean connectToResponsibleGet(String key) {
		Server responsible = this.metadata.getResponsible(key);
		if (responsible == null) {
			return false;
		}
		
		// Check if we can use GET on the current server
		if(this.metadata.canGet(address, port, key)) {
			//logger.debug(this.metadata.toString());
			if(!this.address.equals(responsible.ipAddress) || this.port != responsible.port) {
				logger.debug("Not primarily responsible, but can handle get.");
			}
			return true;
		}
		
		logger.debug("Trying to connect to responsible server "+responsible.toString());
		disconnect();
		
		try {
			this.address = responsible.ipAddress;
			this.port = responsible.port;
			boolean ok = connect();
			if (!ok){
				return connectToAnyServer();
			}
		} catch (Exception e) {
			logger.debug("Unable to connect to responsible server "+responsible.toString());
			return connectToAnyServer();
		}
		
		return true;
	}
	
	/**
	 * Try to send the request to the server. 
	 * If the server replies with SERVER_WRITE_LOCK or SERVER_STOPPED, wait and then try again. 
	 * If the server replies with SERVER_NOT_RESPONSIBLE, update the metadata, determine what server
	 * should be responsible, connect to it, and try again. 
	 */
	private KVMessage sendRequest(KVMessage request) {
		KVMessage response = new MessageType(request.getHeader(), "sendRequest_NOT_PROCESSED", "", "");
		int attemptCount = 20; //maximum number of times to retry if we get a SERVER_WRITE_LOCK response
		
		do {
			logger.info("KVStore: sending request "+request.getMsg());
			try {
				if (client == null) {
					connectToAnyServer();
				}
				client.sendMessage(request);
			}
			catch (IOException e) {
				boolean success = connectToAnyServer();
				if (!success) {
					return new MessageType(request.getHeader(), "connectToAnyServer_NOT_PROCESSED", "", "");
				}
				connected = true;
			}
			
			//Wait for client thread to receive message from server (Client.java function)
			//TODO: timeout if no response is received
			response = client.getResponse();
			logger.info("KVStore: received response  "+response.getMsg());
			
			if (response.getStatus().equals("SERVER_STOPPED")){
				//The entire system is disabled for an indefinite amount of time, so there's 
				//no point waiting and trying again. Give the user the stopped message.
				return new MessageType(request.getHeader(), "SERVER_STOPPED", request.getKey(), request.getValue());
			} else if (response.getStatus().equals("SERVER_WRITE_LOCK")){
				// If write locked then a new server is being added and data is being transferred
				// We block until server is ready to receive

				logger.info("Server is temporarily locked for writing. Waiting and retrying");
				try {
					Thread.sleep(500);
				} catch (InterruptedException e){}
				
			} else if (response.getStatus().equals("SERVER_NOT_RESPONSIBLE")) {
				// get update metadata and determine responsible server
				String mdata = response.getValue(); 
				this.metadata = new HashRing(mdata);
				HashRing.Server responsibleServer = metadata.getResponsible(request.getKey());

				logger.info("Received SERVER_NOT_RESPONSIBLE. Connecting to server "+responsibleServer.toString());
				//System.out.println("Received SERVER_NOT_RESPONSIBLE. Connecting to server "+responsibleServer.toString());

				//disconnect from the current server and try to connect to the new one
				disconnect();
				connected = false;
				this.address = responsibleServer.ipAddress;
				this.port = responsibleServer.port;
				try {
					// We try to connect 5 times, making sure that we get a connection success message and not just random junk
					//int retry = 5;
					if(!connect()) {
						return new MessageType(request.getHeader(), "responsible_NOT_PROCESSED", "", "");
						//logger.info("Connection failed!");
						//retry -= 1;
						//try {
						//	Thread.sleep(500);
						//} catch (InterruptedException ie){}
					}
					connected = true;
				} 
				catch(Exception e) {
					//try to connect to any other server in the metadata
					boolean success = connectToAnyServer();
					if (!success) {
						return new MessageType(request.getHeader(), "connectToAnyServer_NOT_PROCESSED", "", "");
					}
					connected = true;
				}
			} else if (response.getStatus().equals("TIME_OUT")) {
				// Client.java sent back TIME_OUT, the read socket has timed out. Let's connect to any other server and
				// see what's going on. The heart beat from ECS should eventually sort things out.
				boolean success = connectToAnyServer();
				if (!success) {
					return new MessageType(request.getHeader(), "connectToAnyServer_NOT_PROCESSED", "", "");
				}
				connected = true;
			} else {
				break;
			}
			
		} while (attemptCount-- > 0);
		
		if (response.getStatus().equals("sendRequest_NOT_PROCESSED")){
			logger.info("KVStore: no response received");
		}				
		else if (response.getStatus().equals("SERVER_WRITE_LOCK")){
			logger.error("Timed out retrying on server with write lock!");
			// Returns a PUT_ERROR to the KVClient, since there is no other suitable status code
			return new MessageType(response.getHeader(), "PUT_ERROR", response.getKey(), response.getValue());
		}
		return response;
	}
	
	/**
	 * Run through all the known servers in the metadata, trying to connect to any of them.
	 * Returns true if successful.
	 */
	private boolean connectToAnyServer() {
		List<Server> allServers = this.metadata.getAllServers();
		logger.debug("Trying to connect to ANY server due to prior failure.");
		for (Server server : allServers) {
			logger.debug("Trying to connect to server "+server.toString());
			this.address = server.ipAddress;
			this.port = server.port;
			try {
				boolean success = connect();
				if (success) {
					return true;
				}
			} catch(Exception ex) {
				
			}
		}
		logger.error("KVStore: Unable to connect to any server in the system.");
		return false;
	}
}
