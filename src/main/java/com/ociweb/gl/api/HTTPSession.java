package com.ociweb.gl.api;

import java.util.concurrent.atomic.AtomicInteger;

public class HTTPSession {

	public static AtomicInteger sessionCounter = new AtomicInteger(0);
	
	public final String host;
	public final byte[] hostBytes;
	public final int port;
	public final int sessionId;
	public final int uniqueId;
		
	//cache
	private long connectionId=-1;
	
	public String toString() {
		return host+":"+port;
	}
	
	public HTTPSession(String host, int port, int sessionId) {
		this.uniqueId = sessionCounter.incrementAndGet();
		this.host = host;
		this.hostBytes = host.getBytes();
		this.port = port;
		this.sessionId = sessionId;
	}
	
	public HTTPSession(String host, int port) {
		this.uniqueId = sessionCounter.incrementAndGet();
		this.host = host;
		this.hostBytes = host.getBytes();
		this.port = port;
		this.sessionId = 0;
	}
	
	void setConnectionId(long id) {
		connectionId = id;		
	}
	
	long getConnectionId() {
		return connectionId;
	}

	public static int getSessionCount() {
		return sessionCounter.get();
	}
	
}
