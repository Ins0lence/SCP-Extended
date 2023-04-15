package com.github.ins0lence.scp;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.github.ins0lence.scp.Counter.EndCounter;
import com.github.ins0lence.scp.Counter.StartCounter;
import com.github.ins0lence.scp.InjectorMain.Mode;
import com.github.ins0lence.scp.Server.Command;
import com.github.ins0lence.scp.Server.Stop;

import akka.actor.Cancellable;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class Killer extends AbstractBehavior<InjectorMain.Mode> {

    Duration burstAndStairsInterval;
    List<ActorRef<Server.Command>[]> serverList;
    int supervisorChildren;
    int supervisorNum;
    int killsPerSecond;
    int startIndex;
    int interval;
    Cancellable curTask = null;
    Random generator = new Random();

    public static Behavior<InjectorMain.Mode> create(List<ActorRef<Server.Command>[]> serverList,
	    int supervisorChildren, int supervisorNum, int killsPerSecond, int startIndex, Duration burstAndStairsInterval) {
	return Behaviors.setup(context -> new Killer(context, serverList, supervisorChildren, supervisorNum,
		killsPerSecond, startIndex, burstAndStairsInterval));
    }
    
    public static Behavior<InjectorMain.Mode> create(List<ActorRef<Server.Command>[]> serverList,
	    int supervisorChildren, int supervisorNum, int killsPerSecond, int startIndex) {
	return Behaviors.setup(context -> new Killer(context, serverList, supervisorChildren, supervisorNum,
		killsPerSecond, startIndex, Duration.ZERO));
    }

    private Killer(ActorContext<InjectorMain.Mode> context, List<ActorRef<Server.Command>[]> serverList,
	    int supervisorChildren, int supervisorNum, int killsPerSecond, int startIndex, Duration burstAndStairsInterval) {
	super(context);
	this.supervisorChildren = supervisorChildren;
	this.supervisorNum = supervisorNum;
	this.killsPerSecond = killsPerSecond;
	this.startIndex = startIndex;
	this.interval = 1000/killsPerSecond;
	this.serverList = serverList;
	this.burstAndStairsInterval = burstAndStairsInterval;
    }

    @Override
    public Receive<InjectorMain.Mode> createReceive() {
	return newReceiveBuilder().onMessage(InjectorMain.Mode.class, this::onTask)
		.onSignal(PostStop.class, this::onPostStop).build();
    }

    private Behavior<InjectorMain.Mode> onTask(InjectorMain.Mode task) {
	switch (task) {
	case BURST: {
	    curTask = this.getContext().getSystem().scheduler().scheduleOnce(burstAndStairsInterval,
		    new KillerSender(this.getContext().getSelf(), task), this.getContext().getSystem().executionContext());
	    
	    for (int i = 1; i < burstAndStairsInterval.getSeconds() * killsPerSecond; i++) {
		killProcess();
	    }
	    break;
	}
	case STAIRS:{
	    curTask = this.getContext().getSystem().scheduler().scheduleOnce(burstAndStairsInterval,
		    new KillerSender(this.getContext().getSelf(), task), this.getContext().getSystem().executionContext());
	    for (int i = 0; i < killsPerSecond; i++) {
		serverList.get(startIndex / supervisorChildren)[startIndex % supervisorChildren].tell(new Server.Stop(0));
		startIndex = (startIndex + supervisorChildren) % (serverList.size() * supervisorChildren);
		startIndex = startIndex < supervisorChildren -1 ? startIndex + 1 : startIndex; 
		
	    }
	    break;
	}
	case UNIFORM:{
	    curTask = this.getContext().getSystem().scheduler().scheduleOnce(Duration.ofMillis(interval),
		    new KillerSender(this.getContext().getSelf(), task), this.getContext().getSystem().executionContext());
	    killProcess();
	    break;
	}
	case RAND:{
	    boolean burst = generator.nextBoolean();
	    int size = generator.nextInt(10);
	    curTask = this.getContext().getSystem().scheduler().scheduleOnce(Duration.ofMillis(interval),
		    new KillerSender(this.getContext().getSelf(), task), this.getContext().getSystem().executionContext());
	    if (burst) {
		for (int i = 0; i < size; i++) {
		    killProcess();
		}
	    } else {
		for (int i = 0; i < size; i++) {
		    this.getContext().getSystem().scheduler().scheduleOnce(Duration.ofMillis((i)*interval),
			    new singleKill(), this.getContext().getSystem().executionContext());
		}
	    }
	    break;
	}
	}
	return this;
    }
    
    private Behavior<InjectorMain.Mode> onPostStop(PostStop s){
	if (curTask!=null) {
	    curTask.cancel();
	}
	return this;
    }

    protected static class KillerSender implements Runnable {

	ActorRef<InjectorMain.Mode> killer;
	InjectorMain.Mode mode;

	public KillerSender(ActorRef<Mode> killer, Mode mode) {
	    super();
	    this.killer = killer;
	    this.mode = mode;
	}

	@Override
	public void run() {
	    killer.tell(mode);
	}
    }
    
    private class singleKill implements Runnable {

	public singleKill() {
	    super();
	}

	@Override
	public void run() {
	    killProcess();
	}
    }


    private void killProcess() {
	int target = generator.nextInt(supervisorChildren);
	serverList.get(startIndex)[target].tell(new Server.Stop(0));
	startIndex = (startIndex + 1) % supervisorNum;
    }
}
