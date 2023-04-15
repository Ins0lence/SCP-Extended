package com.github.ins0lence.scp;

import java.time.Duration;

import com.github.ins0lence.scp.Server.Command;
import com.github.ins0lence.scp.Server.Ping;
import com.github.ins0lence.scp.Server.Pong;

import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class Client extends AbstractBehavior<Server.Ping> 
{
	private class SendMSG implements Runnable{

		ActorRef<Server.Command> replyTo;
		long time;
		public SendMSG(ActorRef<Server.Command> replyTo, long time) {
			this.replyTo = replyTo;
			this.time = time;
		}
		public void run()
		{
			replyTo.tell(new Pong(contents, time));
		}

		
	}
	byte[] contents;
	Cancellable reply;
	ActorContext<Ping> context;
	Duration replyTime;
	
	public static Behavior<Ping> create(Duration replyTime){
		return Behaviors.setup(context -> new Client(context, replyTime));
	}
	private Client(ActorContext<Ping> context, Duration time) {
		super(context);
		this.context = context;
		this.replyTime = time;
	}
	@Override
	public Receive<Ping> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(Ping.class, this::onPing)
				.onSignal(PostStop.class, this::onPostStop)
				.build();
	}
	private Behavior<Ping> onPing(Ping ping){
		contents = ping.data.clone();
		context.getSystem().scheduler().scheduleOnce(replyTime, new SendMSG(ping.replyTo, ping.time), context.getSystem().executionContext());
		
		return this;
	}
	
	private Behavior<Ping> onPostStop(PostStop stop){
		if (reply != null) {
			reply.cancel();
		}
		return this;
	}
}
