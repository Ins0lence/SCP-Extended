package com.github.ins0lence.scp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.ins0lence.scp.Server.Command;
import com.github.ins0lence.scp.Server.Pong;
import com.github.ins0lence.scp.Server.StartServer;
import com.github.ins0lence.scp.Server.Stop;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class SubSupervisor extends AbstractBehavior<Void> {

    List<ActorRef<Server.Command>> servers = new ArrayList<ActorRef<Server.Command>>();

    public static Behavior<Void> create(int num, ActorRef<HeadSupervisor.Command> parent,
	    ActorRef<Counter.Command> counter, SupervisorStrategy strat, Duration replyTime, int messageLength) {
	return Behaviors.setup(context -> new SubSupervisor(context, num, parent, counter, strat, replyTime, messageLength));
    }

    public SubSupervisor(ActorContext<Void> context, int num, ActorRef<HeadSupervisor.Command> parent,
	    ActorRef<Counter.Command> counter, SupervisorStrategy strat, Duration replyTime, int messageLength) {
	super(context);
	ActorRef<Server.Command>[] servers = new  ActorRef[num];
	for (int i = 0; i < num; i++) {
	    String name = "Server" + Integer.toString(i);
	    servers[i] = context.spawn(Behaviors.supervise(Server.create(counter, replyTime, messageLength)).onFailure(ArithmeticException.class,
		    strat), name);
	}
	parent.tell(new HeadSupervisor.ChildList(servers));

    }
    
    @Override
    public Receive<Void> createReceive() {
	return newReceiveBuilder().build();
    }

}
