package app_kvClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import logger.LogSetup;

import client.KVStore;
import common.messages.KVMessage;
import common.messages.MessageType;


public class KVClient {

	private static Logger logger = Logger.getRootLogger();
	private static final String PROMPT = "EchoClient> ";
	private BufferedReader stdin;
	private boolean stop = false;
	
	private String serverAddress;
	private int serverPort;
	private KVStore kvstore = null;
	
	public void run() {
		while(!stop) {
			stdin = new BufferedReader(new InputStreamReader(System.in));
			System.out.print(PROMPT);
			
			try {
				String cmdLine = stdin.readLine();
				this.handleCommand(cmdLine);
			} catch (IOException e) {
				stop = true;
				printError("CLI does not respond - Application terminated ");
			}
		}
	}

	private void handleCommand(String cmdLine) {
		//parse cmdLine by spaces	
		String[] tokens = cmdLine.split("\\s");
		String header=" ", status=" ", key=" ", value=" ";
		if (tokens.length >= 1){
			header = tokens[0].trim();
		}
		if (tokens.length >= 2){
			key = tokens[1].trim();
		}
		if (tokens.length >= 3){
			//put remaining tokens in value
			//Now it handles multiple spaces, replaced regex \s+ with \s, so "asdf  asdf" generates three tokens "asdf", "", "asdf"
			value = "";
			for (int i=2; i<tokens.length; i++){
				value += tokens[i];
				if (i < tokens.length-1) 
					value += " ";
			}
		}

		MessageType msg = new MessageType(header, status, key, value);
		
		if (msg.error != null){
			printError((msg.error));
		}
		else{
			switch (msg.getHeader()) {
			case "connect":
				try{
					serverAddress = msg.getKey();
					serverPort = Integer.parseInt(msg.getValue());
					kvstore = new KVStore(serverAddress, serverPort);
					kvstore.connect();
				} catch(NumberFormatException nfe) {
					printError("No valid address. Port must be a number!");
					logger.info("Unable to parse argument <port>", nfe);
				} catch (UnknownHostException e) {
					printError("Unknown Host!");
					logger.info("Unknown Host!", e);
				} catch (IOException e) {
					printError("Could not establish connection!");
					logger.warn("Could not establish connection!", e);
				}
				break;
			case "disconnect":
				if (kvstore != null){
					kvstore.disconnect();
					kvstore = null;
				}
				else{
					printError("Not connected!");
				}
				break;
			case "put":
				if (kvstore != null){
					try{
						kvstore.put(msg.getKey(), msg.getValue());
					}
					catch (Exception e){
						
					}
				}
				else{
					printError("Not connected!");
				}
				break;
			case "get":
				if (kvstore != null){
					try{
						kvstore.get(msg.getKey());
					}
					catch (Exception e){
						
					}
				}
				else{
					printError("Not connected!");
				}
				break;
			case "logLevel":
				String level = setLevel(msg.getKey());
				if(level.equals(LogSetup.UNKNOWN_LEVEL)) {
					printError("No valid log level!");
					printPossibleLogLevels();
				} else {
					System.out.println(PROMPT + 
							"Log level changed to level " + level);
				}
				break;
			case "help":
				printHelp();
				break;
			case "quit":
				stop = true;
				if (kvstore != null){
					kvstore.disconnect();
					kvstore = null;
				}
				System.out.println(PROMPT + "Application exit!");
				break;
			}
		}
	}

	
	private void printHelp() {
		StringBuilder sb = new StringBuilder();
		sb.append(PROMPT).append("ECHO CLIENT HELP (Usage):\n");
		sb.append(PROMPT);
		sb.append("::::::::::::::::::::::::::::::::");
		sb.append("::::::::::::::::::::::::::::::::\n");
		sb.append(PROMPT).append("connect <host> <port>");
		sb.append("\t establishes a connection to a server\n");
		sb.append(PROMPT).append("get <key>");
		sb.append("\t\t sends a get request for key to the server \n");
		sb.append(PROMPT).append("put <key> <value>");
		sb.append("\t\t sends a put request for (key,value) to the server \n");
		sb.append(PROMPT).append("disconnect");
		sb.append("\t\t\t disconnects from the server \n");
		
		sb.append(PROMPT).append("logLevel");
		sb.append("\t\t\t changes the logLevel \n");
		sb.append(PROMPT).append("\t\t\t\t ");
		sb.append("ALL | DEBUG | INFO | WARN | ERROR | FATAL | OFF \n");
		
		sb.append(PROMPT).append("quit ");
		sb.append("\t\t\t exits the program");
		System.out.println(sb.toString());
	}
	
	private void printPossibleLogLevels() {
		System.out.println(PROMPT 
				+ "Possible log levels are:");
		System.out.println(PROMPT 
				+ "ALL | DEBUG | INFO | WARN | ERROR | FATAL | OFF");
	}

	private String setLevel(String levelString) {
		
		if(levelString.equals(Level.ALL.toString())) {
			logger.setLevel(Level.ALL);
			return Level.ALL.toString();
		} else if(levelString.equals(Level.DEBUG.toString())) {
			logger.setLevel(Level.DEBUG);
			return Level.DEBUG.toString();
		} else if(levelString.equals(Level.INFO.toString())) {
			logger.setLevel(Level.INFO);
			return Level.INFO.toString();
		} else if(levelString.equals(Level.WARN.toString())) {
			logger.setLevel(Level.WARN);
			return Level.WARN.toString();
		} else if(levelString.equals(Level.ERROR.toString())) {
			logger.setLevel(Level.ERROR);
			return Level.ERROR.toString();
		} else if(levelString.equals(Level.FATAL.toString())) {
			logger.setLevel(Level.FATAL);
			return Level.FATAL.toString();
		} else if(levelString.equals(Level.OFF.toString())) {
			logger.setLevel(Level.OFF);
			return Level.OFF.toString();
		} else {
			return LogSetup.UNKNOWN_LEVEL;
		}
	}

	private void printError(String error){
		System.out.println(PROMPT + "Error! " +  error);
	}
	
    /**
     * Main entry point for the echo server application. 
     * @param args contains the port number at args[0].
     */
    public static void main(String[] args) {
    	try {
			new LogSetup("logs/client.log", Level.OFF);
			KVClient app = new KVClient();
			app.run();
		} catch (IOException e) {
			System.out.println("Error! Unable to initialize logger!");
			e.printStackTrace();
			System.exit(1);
		}
    }

}