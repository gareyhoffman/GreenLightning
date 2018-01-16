package com.ociweb.gl.api;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.PubSubMethodListenerBase;
import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.stage.PublishPrivateTopics;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.module.AbstractAppendablePayloadResponseStage;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.field.MessageConsumer;

/**
 * Represents a dedicated channel for communicating with a single device
 * or resource on an IoT system.
 */
public class MsgCommandChannel<B extends BuilderImpl> {

	private final static Logger logger = LoggerFactory.getLogger(MsgCommandChannel.class);
	
	private boolean isInit = false;
    private Pipe<TrafficOrderSchema> goPipe;
    
    ///////////////////////////All the known data pipes
    private Pipe<MessagePubSub> messagePubSub;
    private Pipe<ClientHTTPRequestSchema> httpRequest;
    private Pipe<ServerResponseSchema>[] netResponse;
    
    
    private Pipe<MessagePubSub>[] exclusivePubSub;

	private static final byte[] RETURN_NEWLINE = "\r\n".getBytes();
       
    private int lastResponseWriterFinished = 1;//starting in the "end" state
    
    private String[] exclusiveTopics =  new String[0];
    
    protected AtomicBoolean aBool = new AtomicBoolean(false);   

    protected static final long MS_TO_NS = 1_000_000;
         
    private Behavior listener;
    
    public static final int DYNAMIC_MESSAGING = 1<<0;
    public static final int STATE_MACHINE = DYNAMIC_MESSAGING;//state machine is based on DYNAMIC_MESSAGING;    
    
    //TODO: add GreenService class for getting API specific objects.
    
    public static final int NET_REQUESTER     = 1<<1;
    public static final int NET_RESPONDER     = 1<<2;
    public static final int USE_DELAY         = 1<<3;
    public static final int ALL = DYNAMIC_MESSAGING | NET_REQUESTER | NET_RESPONDER;
      
    protected final B builder;

	public int maxHTTPContentLength;
	
	protected Pipe<?>[] optionalOutputPipes;
	public int initFeatures; //this can be modified up to the moment that we build the pipes.
	
	private PublishPrivateTopics publishPrivateTopics;	

	protected PipeConfigManager pcm;
	private final int parallelInstanceId;
	
    public MsgCommandChannel(GraphManager gm, B hardware,
				  		    int parallelInstanceId,
				  		    PipeConfigManager pcm
				           ) {
    	this(gm,hardware,ALL, parallelInstanceId, pcm);
    }
    
    public MsgCommandChannel(GraphManager gm, B builder,
    					  int features,
    					  int parallelInstanceId,
    					  PipeConfigManager pcm
    		             ) {

       this.initFeatures = features;//this is held so we can check at every method call that its configured right
       this.builder = builder;
       this.pcm = pcm;
       this.parallelInstanceId = parallelInstanceId;
    }

    public boolean hasRoomFor(int messageCount) {
		return null==goPipe || Pipe.hasRoomForWrite(goPipe, 
    			FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(goPipe))*messageCount);
    }
    
    public boolean hasRoomForHTTP(int messageCount) {
		return Pipe.hasRoomForWrite(httpRequest, 
    			FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(httpRequest))*messageCount);
    }
    
    
    public void ensureDynamicMessaging() {
    	if (isInit) {
    		throw new UnsupportedOperationException("Too late, ensureDynamicMessaging method must be called in define behavior.");
    	}
    	this.initFeatures |= DYNAMIC_MESSAGING;
    }
    
    public void ensureDynamicMessaging(int queueLength, int maxMessageSize) {
    	if (isInit) {
    		throw new UnsupportedOperationException("Too late, ensureDynamicMessaging method must be called in define behavior.");
    	}
    	growCommandCountRoom(queueLength);
    	this.initFeatures |= DYNAMIC_MESSAGING;  
    	
    	pcm.ensureSize(MessagePubSub.class, queueLength, maxMessageSize);

		//also ensure consumers have pipes which can consume this.    		
		pcm.ensureSize(MessageSubscription.class, queueLength, maxMessageSize);
		
		//IngressMessages Confirm that MQTT ingress is big enough as well			
		pcm.ensureSize(IngressMessages.class, queueLength, maxMessageSize);
	
    }

	public static boolean isTooSmall(int queueLength, int maxMessageSize, PipeConfig<?> config) {
		return queueLength>config.minimumFragmentsOnPipe() || maxMessageSize>config.maxVarLenSize();
	}
    
    public void ensureHTTPClientRequesting() {
    	if (isInit) {
    		throw new UnsupportedOperationException("Too late, ensureHTTPClientRequesting method must be called in define behavior.");
    	}
    	this.initFeatures |= NET_REQUESTER;
    }
    
    public void ensureHTTPClientRequesting(int queueLength, int maxMessageSize) {
    	if (isInit) {
    		throw new UnsupportedOperationException("Too late, ensureHTTPClientRequesting method must be called in define behavior.");
    	}
    	growCommandCountRoom(queueLength);
    	this.initFeatures |= NET_REQUESTER;
    	
    	pcm.ensureSize(ClientHTTPRequestSchema.class, queueLength, maxMessageSize);

    }
   
    public void ensureHTTPServerResponse() {
    	if (isInit) {
    		throw new UnsupportedOperationException("Too late, ensureHTTPServerResponse method must be called in define behavior.");
    	}
    	this.initFeatures |= NET_RESPONDER;
    }
    
    public void ensureHTTPServerResponse(int queueLength, int maxMessageSize) {
    	if (isInit) {
    		throw new UnsupportedOperationException("Too late, ensureHTTPServerResponse method must be called in define behavior.");
    	}
    	growCommandCountRoom(queueLength);
    	this.initFeatures |= NET_RESPONDER;    	
    	
    	pcm.ensureSize(ServerResponseSchema.class, queueLength, maxMessageSize);

    }
    
    
    protected void growCommandCountRoom(int count) {
    	if (isInit) {
    		throw new UnsupportedOperationException("Too late, growCommandCountRoom method must be called in define behavior.");
    	}
    	
    	PipeConfig<TrafficOrderSchema> goConfig = this.pcm.getConfig(TrafficOrderSchema.class);
    	this.pcm.addConfig(count + goConfig.minimumFragmentsOnPipe(), 0, TrafficOrderSchema.class);

    }
    
    
	@SuppressWarnings("unchecked")
	private void buildAllPipes() {
		   
		   if (!isInit) {
			   
			   isInit = true;
			   this.messagePubSub = ((this.initFeatures & DYNAMIC_MESSAGING) == 0) ? null : newPubSubPipe(pcm.getConfig(MessagePubSub.class), builder);
			   this.httpRequest   = ((this.initFeatures & NET_REQUESTER) == 0)     ? null : newNetRequestPipe(pcm.getConfig(ClientHTTPRequestSchema.class), builder);
			   
			   
			   int featuresCount = Integer.bitCount(this.initFeatures);
			   if (featuresCount>1 || featuresCount==USE_DELAY) {
				   this.goPipe = newGoPipe(pcm.getConfig(TrafficOrderSchema.class));
			   } else {
				   assert(null==goPipe);
			   }
			   
			   
			   /////////////////////////
			   //build pipes for sending out the REST server responses
			   ////////////////////////       
			   Pipe<ServerResponseSchema>[] netResponse = null;
			   if ((this.initFeatures & NET_RESPONDER) != 0) {
				   //int parallelInstanceId = hardware.ac
				   if (-1 == parallelInstanceId) {
					   //we have only a single instance of this object so we must have 1 pipe for each parallel track
					   int p = builder.parallelismTracks();
					   netResponse = ( Pipe<ServerResponseSchema>[])new Pipe[p];
					   while (--p>=0) {
						   netResponse[p] = builder.newNetResponsePipe(pcm.getConfig(ServerResponseSchema.class), p);
					   }
				   } else {
					   //we have multiple instances of this object so each only has 1 pipe
					   netResponse = ( Pipe<ServerResponseSchema>[])new Pipe[1];
					   netResponse[0] = builder.newNetResponsePipe(pcm.getConfig(ServerResponseSchema.class), parallelInstanceId);
				   }
			   }
			   this.netResponse = netResponse;
			   ///////////////////////////
			   
			   
			   if (null != this.netResponse) {
				   
				   int x = this.netResponse.length;
	 
				   while(--x>=0) {
				   
			    	   if (!Pipe.isInit(netResponse[x])) {
			    		   //hack for now.
			    		   netResponse[x].initBuffers();
			    	   }
				   }
			   }
	
	
			   
			   ///////////////////
	
			   int e = this.exclusiveTopics.length;
			   this.exclusivePubSub = (Pipe<MessagePubSub>[])new Pipe[e];
			   while (--e>=0) {
				   exclusivePubSub[e] = newPubSubPipe(pcm.getConfig(MessagePubSub.class), builder);
			   }
			   ///////////////////       
	
			   ////////////////////////////////////////
			   int temp = 0;
			   if (null!=netResponse && netResponse.length>0) {
				   int x = netResponse.length;
				   int min = Integer.MAX_VALUE;
				   while (--x>=0) {
					   min = Math.min(min, netResponse[x].maxVarLen);
				   }
				   temp= min;
			   }
			   this.maxHTTPContentLength = temp;		
			   ///////////////////////////////////////
		   }
	}
    
	protected boolean goHasRoom() {
		return null==goPipe || PipeWriter.hasRoomForWrite(goPipe);
	}

    
    public Pipe<?>[] getOutputPipes() {
    	
    	//we wait till this last possible moment before building.
    	buildAllPipes();
    	 
    	 
    	int length = 0;
    	
    	if (null != messagePubSub) { //Index needed plust i2c index needed.
    		length++;
    	}
    	
    	if (null != httpRequest) {
    		length++;
    	}
    	
    	if (null != netResponse) {
    		length+=netResponse.length;
    	}
    	
    	boolean hasGoSpace = false;
    	if (length>0) {//last count for go pipe
    		length++;
    		hasGoSpace = true;
    	}
    	
    	length += exclusivePubSub.length;
    	
    	if (null!=optionalOutputPipes) {
    		length+=optionalOutputPipes.length;
    	}
    	
    	if (null!=publishPrivateTopics) {
    		length+=publishPrivateTopics.count();
    	}
  
    	int idx = 0;
    	Pipe<?>[] results = new Pipe[length];
    	
    	System.arraycopy(exclusivePubSub, 0, results, 0, exclusivePubSub.length);
    	idx+=exclusivePubSub.length;
    	
    	if (null != messagePubSub) {
    		results[idx++] = messagePubSub;
    	}
    	
    	if (null != httpRequest) {
    		results[idx++] = httpRequest;
    	}
    	
    	if (null != netResponse) {    		
    		System.arraycopy(netResponse, 0, results, idx, netResponse.length);
    		idx+=netResponse.length;
    	}
    	
    	if (null!=optionalOutputPipes) {
    		System.arraycopy(optionalOutputPipes, 0, results, idx, optionalOutputPipes.length);
    		idx+=optionalOutputPipes.length;
    	}
    	
    	if (null!=publishPrivateTopics) {
    		publishPrivateTopics.copyPipes(results, idx);
    		idx+=publishPrivateTopics.count();
    	}
    	
    	if (hasGoSpace) {//last pipe for go, may be null
    		results[idx++] = goPipe;
    	}
    	
    	return results;
    }
    
    
    private static <B extends BuilderImpl> Pipe<MessagePubSub> newPubSubPipe(PipeConfig<MessagePubSub> config, B builder) {
    	//TODO: need to create these pipes with constants for the topics we can avoid the copy...
    	//      this will add a new API where a constant can be used instead of a topic string...
    	//      in many cases this change will double the throughput or do even better.
    	
    	return new Pipe<MessagePubSub>(config) {
			@Override
			protected DataOutputBlobWriter<MessagePubSub> createNewBlobWriter() {
				return new PubSubWriter(this);
			}    		
    	};
    }
    
    private static <B extends BuilderImpl> Pipe<ClientHTTPRequestSchema> newNetRequestPipe(PipeConfig<ClientHTTPRequestSchema> config, B builder) {

    	return new Pipe<ClientHTTPRequestSchema>(config) {
			@Override
			protected DataOutputBlobWriter<ClientHTTPRequestSchema> createNewBlobWriter() {
				return new PayloadWriter<ClientHTTPRequestSchema>(this);
			}    		
    	};
    }


	private Pipe<TrafficOrderSchema> newGoPipe(PipeConfig<TrafficOrderSchema> goPipeConfig) {
		return new Pipe<TrafficOrderSchema>(goPipeConfig);
	}
        
    
    
    public static void setListener(MsgCommandChannel<?> c, Behavior listener) {
        if (null != c.listener && c.listener!=listener) {
            throw new UnsupportedOperationException("Bad Configuration, A CommandChannel can only be held and used by a single listener lambda/class");
        }
        c.listener = listener;        
    }


    protected boolean enterBlockOk() {
        return aBool.compareAndSet(false, true);
    }
    
    protected boolean exitBlockOk() {
        return aBool.compareAndSet(true, false);
    }

    public final boolean shutdown() {
        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
        try {
            if (goHasRoom()) {            	
            	if (null!=goPipe) {
            		PipeWriter.publishEOF(this.goPipe);            		
            	} else {
            		//must find one of these outputs to shutdown
            		if (!sentEOF(messagePubSub)) {
            			if (!sentEOF(httpRequest)) {
            				if (!sentEOF(netResponse)) {
            					secondShutdownMsg();
            				}            				
            			}
            		}
            	}
                return true;
            } else {
                return false;
            }
        } finally {
            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
        }
    }

    //can be overridden to add shutdown done by another service.
	protected void secondShutdownMsg() {
		//if pubsub or request or response is supported any can be used for shutdown
		logger.warn("Unable to shutdown, no supported services found...");
	}
    
    protected boolean sentEOF(Pipe<?> pipe) {
		if (null!=pipe) {
			PipeWriter.publishEOF(pipe);
			return true;		
		} else {
			return false;
		}
	}

    protected boolean sentEOF(Pipe<?>[] pipes) {
		if (null != pipes) {
			int i = pipes.length;
			while (--i>=0) {
				if (null!=pipes[i]) {
					PipeWriter.publishEOF(pipes[i]);
					return true;
				}
			}	
		}
		return false;
	}

    
	/**
     * Causes this channel to delay processing any actions until the specified
     * amount of time has elapsed.
     *
     * @param durationNanos Nanos to delay
     *
     * @return True if blocking was successful, and false otherwise.
     */
    public boolean block(long durationNanos) {
        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
        try {
            if (goHasRoom()) {
            	MsgCommandChannel.publishBlockChannel(durationNanos, this);
                return true;
            } else {
                return false;
            }
        } finally {
            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
        }
    }

    /**
     * Causes this channel to delay processing any actions
     * until the specified UNIX time is reached.
     *
     * @return True if blocking was successful, and false otherwise.
     */
    public boolean blockUntil(long msTime) {
        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
        try {
            if (goHasRoom()) {
            	MsgCommandChannel.publishBlockChannelUntil(msTime, this);
                return true;
            } else {
                return false;
            }
        } finally {
            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
        }
    }
 
    public boolean httpGet(HTTPSession session, CharSequence route) {
    	return httpGet(session,route,"");
    }
    
    //TODO: update the httpRequest to use the low level API.
    
	public boolean httpGet(HTTPSession session, CharSequence route, CharSequence headers) {
	
		int routeId = session.uniqueId;
		assert(builder.getHTTPClientConfig() != null);
		assert((this.initFeatures & NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";

		//////////////////////
		//get the cached connection ID so we need not deal with the host again
		/////////////////////
		if (session.getConnectionId()<0) {
			long id = builder.getClientCoordinator().lookup(
					    session.hostBytes, 0, session.hostBytes.length, Integer.MAX_VALUE,
					    session.port, session.sessionId);
		    if (id>=0) {
		    	session.setConnectionId(id);
		    }
		}

		if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe))) {

			if (session.getConnectionId()<0) {
		
				if (PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100)) {
					int pipeId = builder.lookupHTTPClientPipe(routeId);
					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_DESTINATION_11, pipeId);
					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_SESSION_10, session.sessionId);
					
		    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PORT_1, session.port);
		    		PipeWriter.writeBytes(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2, session.hostBytes);
		    		
		    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3, route);
					PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HEADERS_7, headers);
					PipeWriter.publishWrites(httpRequest);
					
					publishGo(1, builder.netIndex(), this);
					
					return true;
				}
			} else {
				if (PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200)) {
					int pipeId = builder.lookupHTTPClientPipe(routeId);
					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_DESTINATION_11, pipeId);
					PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_SESSION_10, session.sessionId);
					
		    		PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_PORT_1, session.port);
		    		PipeWriter.writeBytes(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_HOST_2, session.hostBytes);
		    		PipeWriter.writeLong(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_CONNECTIONID_20, session.getConnectionId());
		    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_PATH_3, route);
					PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_HEADERS_7, headers);
					PipeWriter.publishWrites(httpRequest);
					
					publishGo(1, builder.netIndex(), this);
					
					return true;
				}
				
			}
			
        }
        return false;
	}

	public boolean httpClose(HTTPSession session) {
		assert(builder.getHTTPClientConfig() != null);
		assert((this.initFeatures & NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";
		
		if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) 
			&& PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104)) {
                	    
			PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_SESSION_10, session.sessionId);
			PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_PORT_1, session.port);
			PipeWriter.writeBytes(httpRequest, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_HOST_2, session.hostBytes);
		
    		PipeWriter.publishWrites(httpRequest);
                		
    		publishGo(1, builder.netIndex(), this);
    		    	            
            return true;
        }        
        return false;
	}
	
	public boolean httpPost(HTTPSession session, CharSequence route, Writable payload) {
		return httpPost(session, route, "", payload);
	}
	
    
	public boolean httpPost(HTTPSession session, CharSequence route, CharSequence headers, Writable payload) {
		
		int routeId = session.uniqueId;
		assert((this.initFeatures & NET_REQUESTER)!=0) : "must turn on NET_REQUESTER to use this method";
		
		if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) 
			&& PipeWriter.tryWriteFragment(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101)) {

			int pipeId = builder.lookupHTTPClientPipe(routeId);
			
			PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_DESTINATION_11, pipeId);
			PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_SESSION_10, session.sessionId);
			PipeWriter.writeInt(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PORT_1, session.port);
			PipeWriter.writeBytes(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_HOST_2, session.hostBytes);
			PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PATH_3, route);
    		PipeWriter.writeUTF8(httpRequest, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_HEADERS_7, headers);
    				    
		    PayloadWriter<ClientHTTPRequestSchema> pw = (PayloadWriter<ClientHTTPRequestSchema>) Pipe.outputStream(httpRequest);

		    DataOutputBlobWriter.openField(pw);
		    payload.write(pw);
		    pw.closeHighLevelField(ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PAYLOAD_5);
		    
		    PipeWriter.publishWrites(httpRequest);
		    
		    publishGo(1, builder.netIndex(), this);
		    
		    return true;
		} 
		return false;
	}

    /**
     * Subscribes the listener associated with this command channel to
     * a topic.
     *
     * @param topic Topic to subscribe to.
     *
     * @return True if the topic was successfully subscribed to, and false
     *         otherwise.
     */
    public boolean subscribe(CharSequence topic) {
    			
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
    	
    	if (null==listener) {
    		throw new UnsupportedOperationException("Can not subscribe before startup. Call addSubscription when registering listener."); 
    	}
    	
        return subscribe(topic, (PubSubMethodListenerBase)listener);
    }

    /**
     * Subscribes a listener to a topic on this command channel.
     *
     * @param topic Topic to subscribe to.
     * @param listener Listener to subscribe.
     *
     * @return True if the topic was successfully subscribed to, and false
     *         otherwise.
     */
    public boolean subscribe(CharSequence topic, PubSubMethodListenerBase listener) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) 
        	&& PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100)) {
            
            PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode(listener));
            PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1, topic);
            
            PipeWriter.publishWrites(messagePubSub);
            
            builder.releasePubSubTraffic(1, this);
            
            return true;
        }        
        return false;
    }

    /**
     * Unsubscribes the listener associated with this command channel from
     * a topic.
     *
     * @param topic Topic to unsubscribe from.
     *
     * @return True if the topic was successfully unsubscribed from, and false otherwise.
     */
    public boolean unsubscribe(CharSequence topic) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

        return unsubscribe(topic, (PubSubMethodListenerBase)listener);
    }

    /**
     * Unsubscribes a listener from a topic.
     *
     * @param topic Topic to unsubscribe from.
     * @param listener Listener to unsubscribe.
     *
     * @return True if the topic was successfully unsubscribed from, and false otherwise.
     */
    public boolean unsubscribe(CharSequence topic, PubSubMethodListenerBase listener) {
		 assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) 
        	&& PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101)) {
            
            PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, System.identityHashCode(listener));
            PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_UNSUBSCRIBE_101_FIELD_TOPIC_1, topic);
            
            PipeWriter.publishWrites(messagePubSub);
            
            builder.releasePubSubTraffic(1, this);
            
            return true;
        }        
        return false;
    }

    /**
     * Changes the state of this command channel's state machine.
     *
     * @param state State to transition to.
     *
     * @return True if the state was successfully transitioned, and false otherwise.
     */
    public <E extends Enum<E>> boolean changeStateTo(E state) {
		 assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

    	 assert(builder.isValidState(state));
    	 if (!builder.isValidState(state)) {
    		 throw new UnsupportedOperationException("no match "+state.getClass());
    	 }
    	
    	 if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) 
    	     && PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_CHANGESTATE_70)) {

    		 PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_CHANGESTATE_70_FIELD_ORDINAL_7,  state.ordinal());
             PipeWriter.publishWrites(messagePubSub);
             
             builder.releasePubSubTraffic(1, this);
    		 return true;
    	 }

    	return false;
    	
    }

    public void presumePublishTopic(CharSequence topic, Writable writable) {
    	presumePublishTopic(topic, writable, WaitFor.All);
    }
    
    public void presumePublishTopic(CharSequence topic, Writable writable, WaitFor ap) {
    	assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
    	
    	if (publishTopic(topic, writable, ap)) {
			return;
		} else { 
			logger.warn("unable to publish on topic {} must wait.",topic);
			while (!publishTopic(topic, writable, ap)) {
				Thread.yield();
			}
		}
    }
    
    
    public boolean publishTopic(CharSequence topic, Writable writable) {
    	return publishTopic(topic, writable, WaitFor.All);
    }
    /**
     * Opens a topic on this channel for writing.
     *
     * @param topic Topic to open.
     *
     * @return {@link PayloadWriter} attached to the given topic.
     */
    public boolean publishTopic(CharSequence topic, Writable writable, WaitFor ap) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
        assert(writable != null);

        int token =  null==publishPrivateTopics ? -1 : publishPrivateTopics.getToken(topic);
		
		if (token>=0) {
			return publishOnPrivateTopic(token, writable);
		} else {
	        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && 
	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
	    		
	    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
	        	PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
	
	            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
	           
	        	DataOutputBlobWriter.openField(pw);
	        	writable.write(pw);
	            DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
	            
	            PipeWriter.publishWrites(messagePubSub);
	
	            publishGo(1,builder.pubSubIndex(), this);
	                        
	            
	            return true;
	            
	        } else {
	            return false;
	        }
		}
    }

    public boolean publishTopic(CharSequence topic) {
    	return publishTopic(topic, WaitFor.All);
    }
    
    public boolean publishTopic(CharSequence topic, WaitFor ap) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

		int token =  null==publishPrivateTopics ? -1 : publishPrivateTopics.getToken(topic);

		if (token>=0) {
			return publishOnPrivateTopic(token);
		} else {
			if (null==messagePubSub) {
				throw new RuntimeException("Enable DYNAMIC_MESSAGING for this CommandChannel before publishing.");
			}
			
	        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && 
	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
	    		
	    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
	        	PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
	        	
				PipeWriter.writeSpecialBytesPosAndLen(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3, -1, 0);
				PipeWriter.publishWrites(messagePubSub);
	
	            publishGo(1,builder.pubSubIndex(), this);
	                        
	            return true;
	            
	        } else {
	            return false;
	        }
		}
		
    }
  
    public boolean publishTopic(byte[] topic, Writable writable) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
        assert(writable != null);
 
        int token =  null==publishPrivateTopics ? -1 : publishPrivateTopics.getToken(topic, 0, topic.length);
		 
        if (token>=0) {
        	return publishOnPrivateTopic(token, writable);
        } else {
        	//should not be called when	DYNAMIC_MESSAGING is not on.
        	
	        //this is a public topic
        	if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && 
	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
	            
	        	PipeWriter.writeBytes(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
	        	
	            PubSubWriter writer = (PubSubWriter) Pipe.outputStream(messagePubSub);
	            
	            writer.openField(MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3,this);
	            writable.write(writer);
	            writer.publish();
	            publishGo(1,builder.pubSubIndex(), this);
	                        
	            return true;
	            
	        } else {
	            return false;
	        }
        }
    }

    public boolean publishTopic(byte[] topic) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
 
		int token =  null==publishPrivateTopics ? -1 : publishPrivateTopics.getToken(topic, 0, topic.length);

        if (token>=0) {
        	return publishOnPrivateTopic(token);
        } else {
        	//should not be called when	DYNAMIC_MESSAGING is not on.
        	
	        //this is a public topic
        	if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && 
	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
	            
	        	PipeWriter.writeBytes(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);         
	        		        	
				PipeWriter.writeSpecialBytesPosAndLen(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3, -1, 0);
				PipeWriter.publishWrites(messagePubSub);
	            	            
	            publishGo(1,builder.pubSubIndex(), this);
	                        
	            return true;
	            
	        } else {
	            return false;
	        }
        }
    }
    
	private boolean publishOnPrivateTopic(int token, Writable writable) {
		//this is a private topic            
		Pipe<MessagePrivate> output = publishPrivateTopics.getPipe(token);
		if (PipeWriter.tryWriteFragment(output, MessagePrivate.MSG_PUBLISH_1)) {
					
			DataOutputBlobWriter<MessagePrivate> writer = PipeWriter.outputStream(output);
			DataOutputBlobWriter.openField(writer);
			writable.write(writer);
			DataOutputBlobWriter.closeHighLevelField(writer, MessagePrivate.MSG_PUBLISH_1_FIELD_PAYLOAD_3);
			
			PipeWriter.publishWrites(output);

			return true;
		} else {
			return false;
		}
	}
    
	private boolean publishOnPrivateTopic(int token) {
		//this is a private topic            
		Pipe<MessagePrivate> output = publishPrivateTopics.getPipe(token);
		if (PipeWriter.tryWriteFragment(output, MessagePrivate.MSG_PUBLISH_1)) {
			
			PipeWriter.writeSpecialBytesPosAndLen(output, MessagePrivate.MSG_PUBLISH_1_FIELD_PAYLOAD_3, -1, 0);
			PipeWriter.publishWrites(output);

			return true;
		} else {
			return false;
		}
	}
	
    public void presumePublishTopic(TopicWritable topic, Writable writable) {
    	presumePublishTopic(topic,writable,WaitFor.All);
    }        
    public void presumePublishTopic(TopicWritable topic, Writable writable, WaitFor ap) {
    	assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

    	if (publishTopic(topic, writable, ap)) {
			return;
		} else { 
			logger.warn("unable to publish on topic {} must wait.",topic);
			while (!publishTopic(topic, writable, ap)) {
				Thread.yield();
			}
		}
    }
    
    public boolean publishTopic(TopicWritable topic, Writable writable) {
    	return publishTopic(topic, writable, WaitFor.All);    	
    }
    
    public boolean publishTopic(TopicWritable topic, Writable writable, WaitFor ap) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
        assert(writable != null);
        
		int token = tokenForPrivateTopic(topic);
		
		if (token>=0) {
			return publishOnPrivateTopic(token, writable);
		} else { 
	        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && 
	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
	        	
	    		
	    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
	
	            
	            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
	        	DataOutputBlobWriter.openField(pw);
	        	topic.write(pw);
	        	DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
	           
	        	DataOutputBlobWriter.openField(pw);
	        	writable.write(pw);
	            DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
	            
	            PipeWriter.publishWrites(messagePubSub);
	
	            publishGo(1,builder.pubSubIndex(), this);
	            
	                        
	            return true;
	            
	        } else {
	            return false;
	        }
		}
    }
    
    private final int maxDynamicTopicLength = 128;
    private Pipe<RawDataSchema> tempTopicPipe;
        
    public boolean publishTopic(TopicWritable topic) {
    	return publishTopic(topic, WaitFor.All);
    }
    
    public boolean publishTopic(TopicWritable topic, WaitFor ap) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
        
		int token = tokenForPrivateTopic(topic);
		
		if (token>=0) {
			return publishOnPrivateTopic(token);
		} else {
	        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) && 
	        	PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
	        	    		
	    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
	        	
	        	PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
	        	DataOutputBlobWriter.openField(pw);
	        	topic.write(pw);
	        	DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1);
	                   	
				PipeWriter.writeSpecialBytesPosAndLen(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3, -1, 0);
				PipeWriter.publishWrites(messagePubSub);
	
	            publishGo(1,builder.pubSubIndex(), this);
	                        
	            return true;
	            
	        } else {
	            return false;
	        }
		}
    }

	private int tokenForPrivateTopic(TopicWritable topic) {
		if (null==publishPrivateTopics) {
			return -1;
		}
		if (null==tempTopicPipe) {
			tempTopicPipe = RawDataSchema.instance.newPipe(2, maxDynamicTopicLength);
			tempTopicPipe.initBuffers();
		}

		int size = Pipe.addMsgIdx(tempTopicPipe, RawDataSchema.MSG_CHUNKEDSTREAM_1);
		DataOutputBlobWriter<RawDataSchema> output = Pipe.openOutputStream(tempTopicPipe);
		topic.write(output);
		DataOutputBlobWriter.closeLowLevelField(output);
		Pipe.confirmLowLevelWrite(tempTopicPipe, size);
		Pipe.publishWrites(tempTopicPipe);			
        
		Pipe.takeMsgIdx(tempTopicPipe);
		
		int token = publishPrivateTopics.getToken(tempTopicPipe);
				
		Pipe.confirmLowLevelRead(tempTopicPipe, size);
		Pipe.releaseReadLock(tempTopicPipe);
		return token;
	}
    

	public void presumePublishStructuredTopic(CharSequence topic, PubSubStructuredWritable writable) {
		assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";

		if (publishStructuredTopic(topic, writable)) {
			return;
		} else { 
			logger.warn("unable to publish on topic {} must wait.",topic);
			while (!publishStructuredTopic(topic, writable)) {
				Thread.yield();
			}
		}
    }
        
	//returns consumed boolean
	public boolean copyStructuredTopic(CharSequence topic, 
			ChannelReader reader, 
            MessageConsumer consumer) {
		return copyStructuredTopic(topic, reader, consumer, WaitFor.All);
	}
	
	//returns consumed boolean
    public boolean copyStructuredTopic(CharSequence topic, 
    								   ChannelReader inputReader, 
    		                           MessageConsumer consumer,
    		                           WaitFor ap) {
    	DataInputBlobReader<?> reader = (DataInputBlobReader<?>)inputReader;
    	
    	assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
    	
    	int pos = reader.absolutePosition();    	
    	if (consumer.process(reader)) {
    		
    		if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) 
        	    && PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)  ) {
    
	    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
	    		
	    		PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);            
	        	
	            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
	            DataOutputBlobWriter.openField(pw);
	            reader.absolutePosition(pos);//restore position as unread
	            //direct copy from one to the next
	            reader.readInto(pw, reader.available());
	
	            DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
	            PipeWriter.publishWrites(messagePubSub);
	 
	           	publishGo(1,builder.pubSubIndex(), this);  		
	    		
	    		return true;
    		} else {
    			reader.absolutePosition(pos);//restore position as unread
    			return false;//needed to consume but no place to go.
    		}
    	
    	} else {
    		return true;
    	}
    }
    
    public boolean publishStructuredTopic(CharSequence topic, PubSubStructuredWritable writable) {
    	return publishStructuredTopic(topic, writable, WaitFor.All);
    }
    
    public boolean publishStructuredTopic(CharSequence topic, PubSubStructuredWritable writable, WaitFor ap) {
 	    assert((0 != (initFeatures & DYNAMIC_MESSAGING))) : "CommandChannel must be created with DYNAMIC_MESSAGING flag";
     	assert(writable != null);
       
        assert(null != messagePubSub);
        if ((null==goPipe || PipeWriter.hasRoomForWrite(goPipe)) 
        	&& PipeWriter.tryWriteFragment(messagePubSub, MessagePubSub.MSG_PUBLISH_103)) {
    		
    		PipeWriter.writeInt(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_QOS_5, ap.policy());
        	PipeWriter.writeUTF8(messagePubSub, MessagePubSub.MSG_PUBLISH_103_FIELD_TOPIC_1, topic);            
                    	           
            PubSubWriter pw = (PubSubWriter) Pipe.outputStream(messagePubSub);
           
        	DataOutputBlobWriter.openField(pw);
        	writable.write(pw);
            DataOutputBlobWriter.closeHighLevelField(pw, MessagePubSub.MSG_PUBLISH_103_FIELD_PAYLOAD_3);
            
            PipeWriter.publishWrites(messagePubSub);

            publishGo(1,builder.pubSubIndex(), this);
           	
    
            return true;
            
        } else {
            return false;
        }
    }


    
	public boolean publishHTTPResponse(HTTPFieldReader<?> reqeustReader, int statusCode) {
		
		 assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";

		//logger.info("Building response for connection {} sequence {} ",w.getConnectionId(),w.getSequenceCode());
		
		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
				statusCode,false,null,Writable.NO_OP); //no type and no body so use null
	}

	public boolean publishHTTPResponse(HTTPFieldReader<?> reqeustReader, 
            							int statusCode,
									    HTTPContentType contentType,
									   	Writable writable) {
	
		assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";

		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
									statusCode, false, contentType, writable);
	}	

	public boolean publishHTTPResponse(HTTPFieldReader<?> reqeustReader, 
									   int statusCode, boolean hasContinuation,
									   HTTPContentType contentType,
									   Writable writable) {
		
		 assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";

		return publishHTTPResponse(reqeustReader.getConnectionId(), reqeustReader.getSequenceCode(),
				                statusCode, hasContinuation, contentType, writable);
	}	

	///////////////////////////////////
	//these fields are needed for holding the position data for the first block of two
	//this is required so we can go back to fill in length after the second block
	//length is known
	private long block1PositionOfLen;
	private int block1HeaderBlobPosition;
	//this is not thread safe but works because command channels are only used by same thread
	////////////////////////////////////
	
	public boolean publishHTTPResponse(long connectionId, long sequenceCode, 
			                           int statusCode, 
			                           boolean hasContinuation,
			                           HTTPContentType contentType,
			                           Writable writable) {
		
		assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";

		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);
	
		assert(1==lastResponseWriterFinished) : "Previous write was not ended can not start another.";
			
		Pipe<ServerResponseSchema> pipe = netResponse.length>1 ? netResponse[parallelIndex] : netResponse[0];
		
		//logger.trace("try new publishHTTPResponse");
		if (!Pipe.hasRoomForWrite(pipe)) {
			return false;
		}		

		///////////////////////////////////////
		//message 1 which contains the headers
		//////////////////////////////////////		
		holdEmptyBlock(connectionId, sequenceNo, pipe);
				
		//////////////////////////////////////////
		//begin message 2 which contains the body
		//////////////////////////////////////////

		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);

		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
		
		int context;
		if (hasContinuation) {
			context = 0;
			lastResponseWriterFinished = 0;
		} else {
			context = HTTPFieldReader.END_OF_RESPONSE;
			lastResponseWriterFinished = 1;	
		}

		//NB: context passed in here is looked at to know if this is END_RESPONSE and if so
		//then the length is added if not then the header will designate chunked.
		outputStream.openField(statusCode, context, contentType);
		writable.write(outputStream); 
		
		if (hasContinuation) {
			// for chunking we must end this block			
			outputStream.write(RETURN_NEWLINE);
		}
		
		outputStream.publishWithHeader(block1HeaderBlobPosition, block1PositionOfLen); //closeLowLevelField and publish 
	
		return true;

	}

	public boolean publishHTTPResponse(long connectionId, long sequenceCode, 
							           boolean hasContinuation,
							           CharSequence headers,
							           Writable writable) {

				assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";
				
				final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
				final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);
				
				assert(1==lastResponseWriterFinished) : "Previous write was not ended can not start another.";
				
				Pipe<ServerResponseSchema> pipe = netResponse.length>1 ? netResponse[parallelIndex] : netResponse[0];
				
				if (!Pipe.hasRoomForWrite(pipe)) {
					return false;
				}		
								
				///////////////////////////////////////
				//message 1 which contains the headers
				//////////////////////////////////////		
				holdEmptyBlock(connectionId, sequenceNo, pipe);
				
				//////////////////////////////////////////
				//begin message 2 which contains the body
				//////////////////////////////////////////
				
				Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
				Pipe.addLongValue(connectionId, pipe);
				Pipe.addIntValue(sequenceNo, pipe);	
				
				NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
				
				int context;
				if (hasContinuation) {
					context = 0;
					lastResponseWriterFinished = 0;
				} else {
					context = HTTPFieldReader.END_OF_RESPONSE;
					lastResponseWriterFinished = 1;	
				}	
				
				DataOutputBlobWriter.openField(outputStream);
				writable.write(outputStream); 
				
				if (hasContinuation) {
					// for chunking we must end this block			
					outputStream.write(RETURN_NEWLINE);
				}
				
				int len = NetResponseWriter.closeLowLevelField(outputStream); //end of writing the payload    	
				
				Pipe.addIntValue(context, outputStream.getPipe());  //real context    	
				Pipe.confirmLowLevelWrite(outputStream.getPipe());
				   	
				////////////////////Write the header

				DataOutputBlobWriter.openFieldAtPosition(outputStream, block1HeaderBlobPosition);
				
				//HACK TODO: must formalize response building..
				outputStream.write(HTTPRevisionDefaults.HTTP_1_1.getBytes());
				outputStream.append(" 200 OK\r\n");
				outputStream.append(headers);
				outputStream.append("Content-Length: "+len+"\r\n");
				outputStream.append("\r\n");
				
				//outputStream.debugAsUTF8();
				
				int propperLength = DataOutputBlobWriter.length(outputStream);
				Pipe.validateVarLength(outputStream.getPipe(), propperLength);
				Pipe.setIntValue(propperLength, outputStream.getPipe(), block1PositionOfLen); //go back and set the right length.
				outputStream.getPipe().closeBlobFieldWrite();
				
				//now publish both header and payload
				Pipe.publishWrites(outputStream.getPipe());
				
				return true;

	}
	
	private void holdEmptyBlock(long connectionId, final int sequenceNo, Pipe<ServerResponseSchema> pipe) {
	
			Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
			Pipe.addLongValue(connectionId, pipe);
			Pipe.addIntValue(sequenceNo, pipe);	
			
			NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);	
			block1HeaderBlobPosition = Pipe.getWorkingBlobHeadPosition(pipe);
	
			DataOutputBlobWriter.openFieldAtPosition(outputStream, block1HeaderBlobPosition); 	//no context, that will come in the second message 
	        
			//for the var field we store this as meta then length
			block1PositionOfLen = (1+Pipe.workingHeadPosition(pipe));
			
			DataOutputBlobWriter.closeLowLevelMaxVarLenField(outputStream);
			assert(pipe.maxVarLen == Pipe.slab(pipe)[((int)block1PositionOfLen) & Pipe.slabMask(pipe)]) : "expected max var field length";
			
			Pipe.addIntValue(0, pipe); 	//no context, that will come in the second message		
			//the full blob size of this message is very large to ensure we have room later...
			//this call allows for the following message to be written after this messages blob data
			int consumed = Pipe.writeTrailingCountOfBytesConsumed(outputStream.getPipe()); 
			assert(pipe.maxVarLen == consumed);
			Pipe.confirmLowLevelWrite(pipe); 
			//Stores this publish until the next message is complete and published
			Pipe.storeUnpublishedWrites(outputStream.getPipe());
	
			
			//logger.info("new empty block at {} {} ",block1HeaderBlobPosition, block1PositionOfLen);
	}
	
	public boolean publishHTTPResponseContinuation(HTTPFieldReader<?> w, 
										boolean hasContinuation, Writable writable) {
		return publishHTTPResponseContinuation(w.getConnectionId(),w.getSequenceCode(), hasContinuation, writable);
	}

	public boolean publishHTTPResponseContinuation(long connectionId, long sequenceCode, 
												   boolean hasContinuation, Writable writable) {
		
    	assert((0 != (initFeatures & NET_RESPONDER))) : "CommandChannel must be created with NET_RESPONDER flag";

		final int sequenceNo = 0xFFFFFFFF & (int)sequenceCode;
		final int parallelIndex = 0xFFFFFFFF & (int)(sequenceCode>>32);		
		
		assert(0==lastResponseWriterFinished) : "Unable to find write in progress, nothing to continue with";
		
		Pipe<ServerResponseSchema> pipe = netResponse.length>1 ? netResponse[parallelIndex] : netResponse[0];

		//logger.trace("calling publishHTTPResponseContinuation");
		
		if (!Pipe.hasRoomForWrite(pipe)) {
			return false;
		}
		
		
		///////////////////////////////////////
		//message 1 which contains the chunk length
		//////////////////////////////////////		
		holdEmptyBlock(connectionId, sequenceNo, pipe);
		
		///////////////////////////////////////
		//message 2 which contains the chunk
		//////////////////////////////////////	
		
		Pipe.addMsgIdx(pipe, ServerResponseSchema.MSG_TOCHANNEL_100);
		Pipe.addLongValue(connectionId, pipe);
		Pipe.addIntValue(sequenceNo, pipe);	
		NetResponseWriter outputStream = (NetResponseWriter)Pipe.outputStream(pipe);
	
		outputStream.openField(hasContinuation? 0: HTTPFieldReader.END_OF_RESPONSE);
		lastResponseWriterFinished = hasContinuation ? 0 : 1;		
		
		writable.write(outputStream); 
		//this is not the end of the data so we must close this block
		outputStream.write(RETURN_NEWLINE);
		

		if (1 == lastResponseWriterFinished) {			
			//this is the end of the data, we must close the block
			//and add the zero trailer
			
			//this adds 3, note the publishWithChunkPrefix also takes this into account
			Appendables.appendHexDigitsRaw(outputStream, 0);
			outputStream.write(AbstractAppendablePayloadResponseStage.RETURN_NEWLINE);
						
			//TODO: add trailing headers here. (no request for this feature yet)
			
			outputStream.write(AbstractAppendablePayloadResponseStage.RETURN_NEWLINE);
		
			
		}

		outputStream.publishWithChunkPrefix(block1HeaderBlobPosition, block1PositionOfLen);

		return true;
	}

	public static void publishGo(int count, int pipeIdx, MsgCommandChannel<?> gcc) {				
		if (null != gcc.goPipe) { //no 'go' needed if pipe is null
			assert(pipeIdx>=0);
			TrafficOrderSchema.publishGo(gcc.goPipe, pipeIdx, count);
		}
	}
	
	public static void publishBlockChannel(long durationNanos, MsgCommandChannel<?> gcc) {
		
		if (null != gcc.goPipe) {
			PipeWriter.presumeWriteFragment(gcc.goPipe, TrafficOrderSchema.MSG_BLOCKCHANNEL_22);
			PipeWriter.writeLong(gcc.goPipe,TrafficOrderSchema.MSG_BLOCKCHANNEL_22_FIELD_DURATIONNANOS_13, durationNanos);
			PipeWriter.publishWrites(gcc.goPipe);
		} else {
			logger.info("Unable to use block channel for ns without an additonal feature use or USE_DELAY can be added.");
		}
	}

	public static void publishBlockChannelUntil(long timeMS, MsgCommandChannel<?> gcc) {
		
		if (null != gcc.goPipe) {
			PipeWriter.presumeWriteFragment(gcc.goPipe, TrafficOrderSchema.MSG_BLOCKCHANNELUNTIL_23);
			PipeWriter.writeLong(gcc.goPipe,TrafficOrderSchema.MSG_BLOCKCHANNELUNTIL_23_FIELD_TIMEMS_14, timeMS);
			PipeWriter.publishWrites(gcc.goPipe);
		} else {
			logger.info("Unable to use block channel for ns without an additonal feature or USE_DELAY can be added.");
		}
	}
	
	public void setPrivateTopics(PublishPrivateTopics publishPrivateTopics) {
		this.publishPrivateTopics = publishPrivateTopics;
	}
	
	public boolean isGoPipe(Pipe<TrafficOrderSchema> target) {
		return (null==goPipe) || (target==goPipe);
	}

		
}