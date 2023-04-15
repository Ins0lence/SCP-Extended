package com.github.ins0lence.scp;

import java.lang.reflect.Array;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;

import akka.actor.typed.PreRestart;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.event.LoggingBus;
import akka.util.Helpers;

/**
 * Hello world!
 *
 */
public class Server extends AbstractBehavior<Server.Command> {
    long sendTime = 0;
    byte[] contents;

    private final ActorRef<Server.Ping> client;
    private final ActorRef<Counter.Command> manager;
    
    // the message types
    public static class Command {
    }

    public static final class Ping extends Command {
	public final byte[] data;
	public final ActorRef<Command> replyTo;
	long time;

	public Ping(byte[] data, ActorRef<Command> replyTo, long time) {
	    this.data = data;
	    this.replyTo = replyTo;
	    this.time = time;
	}
    }

    public static final class Pong extends Command {
	public final byte[] data;
	public final long time;

	public Pong(byte[] data, long time) {
	    this.data = data;
	    this.time = time;
	}
    }

    public static final class StartServer extends Command {
	public final byte[] data;

	public StartServer(byte[] data) {
	    this.data = data;
	}
    }

    public static final class Stop extends Command implements CborSerializable {
	public final int i;

	@JsonCreator
	public Stop(int i) {
	    this.i = i;
	}
    }

    public static Behavior<Command> create(ActorRef<Counter.Command> manager, Duration replyTime, int messageLength) {
	return Behaviors.setup(context -> new Server(context, manager, replyTime, messageLength));
    }

    private Server(ActorContext<Command> context, ActorRef<Counter.Command> manager, Duration replyTime, int messageLength) {
	super(context);
	this.manager = manager;
	this.client = context.spawn(Client.create(replyTime), "client");
	byte[] data = new byte[messageLength];
	Arrays.fill(data, (byte) 1);
	context.getSelf().tell(new StartServer(data));
    }

    private Behavior<Command> onPong(Pong pong) {
	if (pong.time == sendTime) {
	    contents = pong.data.clone();
	    sendTime = System.currentTimeMillis();
	    // this.getContext().getLog().info(Helpers.currentTimeMillisToUTCString(System.currentTimeMillis()));
	    // this.getContext().getLog().info(Long.toString(System.currentTimeMillis()));
	    client.tell(new Ping(contents, this.getContext().getSelf(), sendTime));
	    manager.tell(new Counter.Aggregate(System.currentTimeMillis()));
	}
	return this;
    }

    private Behavior<Command> onStart(StartServer start) {
	sendTime = System.currentTimeMillis();
	client.tell(new Ping(start.data, this.getContext().getSelf(), sendTime));
	return this;
    }

    private Behavior<Command> onStop(Stop stop) {
	int n = 1 / stop.i;
	return this;
    }
    
//    private Behavior<Command> onRestart(PreRestart restart) {
//	this.getContext().
//	return this;
//    }
    
    @Override
    public Receive<Command> createReceive() {
	ReceiveBuilder<Command> builder = newReceiveBuilder();
	builder.onMessage(Pong.class, this::onPong);
	builder.onMessage(StartServer.class, this::onStart);
	builder.onMessage(Stop.class, this::onStop);
	return builder.build();
    }

}
