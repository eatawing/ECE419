/***
Implementation of KVMessage interface which is used to store messages between
the client and server. 
How to use this class:
	- Create a message using MessageType(header,status,key,value)
	- Make sure that error = null
	- convert to byte array using getBytes()
	Converting from a byte array:
	- MessageType(bytes)
	- Make sure that error = null
	- Use getHeader(), getStatus, getKey(), etc.
***/
package common.messages;

import java.io.Serializable;
import java.util.*;


public class MessageType implements KVMessage {
	public String error;
	private byte[] msgBytes;
	private static final char LINE_FEED = 0x0A;
	private static final char RETURN = 0x0D;
	private String key;
	private String value;
	private String header;
	private String status;
	
	/**
	 * Replace all single quotes in the string with double quotes
	 */
	public static String doubleQuotes(String s)
	{
		for (int i=0; i<s.length(); i++){
			if (s.charAt(i) == '"'){
				s = s.substring(0,i) + "\"\"" + s.substring(i+1,s.length());
				i++;
			}
		}
		return s;
	}
	
	/**
	 * Replace all double quotes in the string with single quotes
	 */
	public static String singleQuotes(String s)
	{
		for (int i=0; i<s.length()-1; i++){
			if (s.charAt(i) == '"' && s.charAt(i+1) == '"'){
				s = s.substring(0,i) + s.substring(i+1,s.length());
			}
		}
		return s;
	}
	
	/**
	 * Construct a MessageType with the 4 required fields. All fields should be 
	 * given with single quotes. 
	 * DO NOT USE EMPTY STRINGS! Use " " instead.
	 */
	public MessageType(String header, String status, String key, String value)
	{
		this.header = header;
		this.status = status;
		this.key = key;
		this.value = value;
		this.error = validityCheck();
	}

	/***
	Construct MessageType from a byte array (ASCII-coded).
	***/
	public MessageType(byte[] bytes) {
		this.msgBytes = bytes;
		String str = new String(this.msgBytes);
		this.header = " ";
		this.status = " ";
		this.key = " ";
		this.value = " ";
		parse(str.trim());
	}	

	/**
	 * @return the key that is associated with this message, 
	 * 		null if not key is associated.
	 */
	@Override
	public String getKey() {
		return this.key;
	}

	/**
	 * @return the value that is associated with this message, 
	 * 		null if not value is associated.
	 */
	@Override
	public String getValue() {
		return this.value;
	}

	/**
	 * @return a status string that is used to identify request types, 
	 * response types and error types associated to the message.
	 */
	@Override
	public String getStatus() {
		return this.status;
	}
	
	@Override
	public void setStatus(String status) {
		this.status = status;
	}
		
	/**
	 * 
	 * @return a header string that is used to identify the message type
	 */
	@Override
	public String getHeader() {
		return this.header;
	}
	
	/**
	 * Returns the content of this message as a String. All fields (header, status,
	 * key, value) are surrounded by quotes. Any quotes in these values are replaced
	 * with double quotes.
	 */
	public String getMsg() {
		return 	"\""+doubleQuotes(header)+"\" " +
				"\""+doubleQuotes(status)+"\" " + 
				"\""+doubleQuotes(key)+"\" " + 
				"\""+doubleQuotes(value)+"\"";
	}

	/***
	Returns an array of bytes that represent the ASCII coded message content.
	Byte array is terminated by a '\n' character.
	***/
	public byte[] getMsgBytes() {
		this.msgBytes = toByteArray(this.getMsg());
		return this.msgBytes;
	}
	
	/**
	 * Returns the given string as an ASCII-coded byte array
	 */
	private byte[] toByteArray(String s){
		byte[] bytes = s.getBytes();
		byte[] ctrBytes = new byte[]{LINE_FEED};
		byte[] tmp = new byte[bytes.length + ctrBytes.length];
		
		System.arraycopy(bytes, 0, tmp, 0, bytes.length);
		System.arraycopy(ctrBytes, 0, tmp, bytes.length, ctrBytes.length);
		
		return tmp;		
	}
	
	/**
	 * Parse the given string into the header, status, key, and value fields.
	 * The given string MUST have the following format:
	 * "header" "status" "key" "value"
	 * where each of those fields can have quotes, but doubled. 
	 * Fields cannot be empty - for empty fields use a single space (" ").
	 */
	private void parse(String msg){	
		msg = msg.trim();
		//String[] tokens = msg.split("\"[^\"]");
		List<String> tokens = new ArrayList<String>();
		int start = -1;
		for (int i=0; i<msg.length(); i++){
			if (msg.charAt(i) == '"' && (i+1 == msg.length() || msg.charAt(i+1) != '"')){
				if (start == -1){
					start = i;
				}
				else{
					tokens.add(msg.substring(start+1,i));
					start = -1;
				}
			}
		}
		
		if (tokens.size() != 4){
			this.error = "Invalid message format";
			return;
		}
		this.header = singleQuotes(tokens.get(0));
		this.status = singleQuotes(tokens.get(1));
		this.key = singleQuotes(tokens.get(2));
		this.value = singleQuotes(tokens.get(3));
		
		this.error = validityCheck();
	}

	public String validityCheck()
	{
		switch (header) {
		case "connect": 
			if (key.trim().equals("") || value.trim().equals("")){
				return "Incorrect number of tokens for message "+header;
			}
			break;
		case "disconnect":
			break;
		case "put":
			if (key.trim().equals("") || value.trim().equals("")){
				return "Incorrect number of tokens for message "+header;
			}
			break;
		case "get":
			if (key.trim().equals("")){
				return "Incorrect number of tokens for message "+header;
			}
			break;
		case "logLevel":
			if (key.trim().equals("")){
				return "Incorrect number of tokens for message "+header;
			}
			break;
		case "help":
			break;
		case "quit":
			break;
		default:
			return "Unknown command";
		}
		
		return null;
	}
}