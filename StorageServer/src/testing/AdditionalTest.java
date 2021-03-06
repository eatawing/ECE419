package testing;

import java.io.IOException;

import org.junit.Test;

import client.KVStore;
import app_kvClient.KVClient;
import app_kvServer.KVServer;
import common.*;
import common.HashRing.*;
import common.messages.*;
import client.Client;

import java.util.*;
import junit.framework.TestCase;

public class AdditionalTest extends TestCase {

	private KVStore kvClient;
	private Exception ex;
	private KVMessage response;
	private List<KVServer> servers;

	public void setUp() {
		servers = AllTests.createAndStartServers(1, 61000);
		response = null;
		ex = null;
		kvClient = new KVStore("localhost", 61000);
		try {
			kvClient.connect();
		} catch (Exception e) {
		}
	}

	public void tearDown() {
		kvClient.disconnect();
		AllTests.closeServers(servers);
		AllTests.deleteLocalStorageFiles();	
		try {
			Thread.sleep(1000); //need to delay a bit between tests because it takes some time for servers to release ports
		} catch (Exception e) {}
	}

	// Tests connecting using the command line handler
	public void testHandleConnect() {
		KVClient app = new KVClient();
		try {
			app.handleCommand("connect localhost 51234");
		} catch (Exception e) {
			ex = e;
		}

		assertNull(ex);
	}
	
	public void testQuoteInValue(){	
		try{	
			//write value with quotes
			response = kvClient.put("key", "\"a\"\"bc\"");
			//could be update or success depending on whether the test has been run before
			assertTrue(response.getStatus().equals("PUT_UPDATE")|| response.getStatus().equals("PUT_SUCCESS")); 
			
			//read value back
			response = kvClient.get("key");
			assertEquals(response.getStatus(),"GET_SUCCESS"); 
			assertEquals(response.getValue(), "\"a\"\"bc\"");
		}
		catch (Exception e) {
			e.printStackTrace();
			ex = e;
		}
		assertNull(ex);
	}
	
	public void testDeleteExists() {		
		try {
			//put the key-value
			response = kvClient.put("key",  "value");
			assertTrue(response.getStatus().equals("PUT_UPDATE")|| response.getStatus().equals("PUT_SUCCESS")); 
			
			//try to delete it
			response = kvClient.put("key", "null");
			assertEquals(response.getStatus(),"DELETE_SUCCESS");
			
			//make sure it's actually gone
			response = kvClient.get("key");
			assertEquals(response.getStatus(),"GET_ERROR");
		}
		catch (Exception e) {
			ex = e;
		}
		assertNull(ex);
	}
	
	public void testDeleteDoesNotExist() {	
		try{	
			//delete key which does not exist. Should return DELETE_SUCCESS status. 
			response = kvClient.put("123456789", "null");
			assertEquals(response.getStatus(),"DELETE_SUCCESS");
			//make sure key does not exist
			response = kvClient.get("123456789");
			assertEquals(response.getStatus(),"GET_ERROR");
		}
		catch (Exception e){
			ex = e;
		}
		assertNull(ex);
	}
	
	public void testMultipleClientsAgree() {
		KVStore client2 = new KVStore("localhost",61000);
		try{
			client2.connect();
			//client 1 does a put
			response = kvClient.put("key1", "abc");
			assertTrue(response.getStatus().equals("PUT_UPDATE")|| response.getStatus().equals("PUT_SUCCESS")); 
			
			//client 2 does a get for the same key. Should get the value just written.
			response = client2.get("key1");
		}
		catch (Exception e){
			e.printStackTrace();
			ex = e;
		}
		assertNull(ex);
		assertEquals(response.getStatus(), "GET_SUCCESS");
		assertEquals(response.getValue(), "abc");
	}
	
	@Test
	public void testPersistence() {		
		try {
			kvClient.connect();
			
			//first delete the key to make sure this test always starts from the same state
			response = kvClient.put("key", "null");
			assertEquals("DELETE_SUCCESS", response.getStatus());
			//now write it
			response = kvClient.put("key", "1010");
			assertTrue(response.getStatus().equals("PUT_UPDATE")|| response.getStatus().equals("PUT_SUCCESS")); 
			
			//disconnect and kill the server
			kvClient.disconnect();
			AllTests.closeServers(servers);
			
			//start up a new server and reconnect
			servers = AllTests.createAndStartServers(1,61000); //use different port this time, because we can
			kvClient.connect("localhost",61000);
			
			//get the value. Should be the same as put.
			response = kvClient.get("key");
			assertEquals("GET_SUCCESS", response.getStatus());
			assertEquals("1010", response.getValue());
		}
		catch (Exception e){
			e.printStackTrace();
			ex = e;
		}
		assertNull(ex);
	}

	///////////////////////////////////////////////////////////////////////////////////////////
	// Tests added specifically for milestone 3 (more in IntegrationTest.java)
	///////////////////////////////////////////////////////////////////////////////////////////
	
	public void testExactlyOneServerResponsibleForPut() {
		AllTests.closeServers(servers);
		servers = AllTests.createAndStartServers(8);
		int responsibleCnt = 0;
		
		for (int i=0; i<8; i++) {
			KVMessage response = servers.get(i).handlePut(new MessageType("put","","1","1"));
			assertTrue("PUT_UPDATE PUT_SUCCESS SERVER_NOT_RESPONSIBLE".contains(response.getStatus()));
			if (!response.getStatus().equals("SERVER_NOT_RESPONSIBLE")) {
				responsibleCnt++;
			}
		}
		assertEquals(1,responsibleCnt);
	}
	
	//tests that when we put to the primary sever the data gets replicated to the next two
	//and we can then do a get from them
	public void testPutReplicate() {
		AllTests.closeServers(servers);
		servers = AllTests.createAndStartServers(5, 50000);
		String x = "1";
		
		KVMessage response = servers.get(3).handlePut(new MessageType("put","",x,x));
		System.out.println(response.getMsg());
		assertTrue("PUT_UPDATE PUT_SUCCESS".contains(response.getStatus()));
		
		//make sure the primary and 2 replicas can do a get on the key
		response = servers.get(3).handleGet(new MessageType("get","",x,""));
		assertEquals("GET_SUCCESS",response.getStatus());
		assertEquals(x,response.getValue());
		response = servers.get(1).handleGet(new MessageType("get","",x,""));
		assertEquals("GET_SUCCESS",response.getStatus());
		assertEquals(x,response.getValue());
		response = servers.get(4).handleGet(new MessageType("get","",x,""));
		assertEquals("GET_SUCCESS",response.getStatus());
		assertEquals(x,response.getValue());
	}
	
	//test putting and getting with less than 3 servers such that one of the replicas is itself
	//make sure nothing bad happens and we can still read all the data
	public void testLessThan3Servers() {
		String x = "100";
		//first with 1 server
		KVMessage response = servers.get(0).handlePut(new MessageType("put","",x,x));
		assertTrue("PUT_UPDATE PUT_SUCCESS".contains(response.getStatus()));
		response = servers.get(0).handleGet(new MessageType("get","",x,""));
		assertEquals("GET_SUCCESS",response.getStatus());
		assertEquals(x,response.getValue());
		
		//test 2 servers
		AllTests.closeServers(servers);
		servers = AllTests.createAndStartServers(2, 54930);
		response = servers.get(0).handleGet(new MessageType("get","",x,""));
		assertEquals("GET_SUCCESS",response.getStatus());
		assertEquals("100",response.getValue());
		
		x = "101";
		//put with 2 servers
		response = servers.get(1).handlePut(new MessageType("put","",x,x));
		assertTrue("PUT_UPDATE PUT_SUCCESS".contains(response.getStatus()));
		response = servers.get(0).handleGet(new MessageType("get","",x,"")); //try to get from the other one
		assertEquals("GET_SUCCESS",response.getStatus());
		assertEquals(x,response.getValue());
	}
}
