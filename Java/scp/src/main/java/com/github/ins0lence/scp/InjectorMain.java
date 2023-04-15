package com.github.ins0lence.scp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.github.ins0lence.scp.Counter.Aggregate;
import com.github.ins0lence.scp.Counter.EndCounter;
import com.github.ins0lence.scp.Counter.StartCounter;
import com.github.ins0lence.scp.Counter.TakeReading;
import com.github.ins0lence.scp.HeadSupervisor.InjectorFinished;

import akka.actor.Cancellable;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.github.ins0lence.scp.CborSerializable;

public class InjectorMain extends AbstractBehavior<InjectorMain.Command> {
    
    
    Mode mode;
    int maxKills = 5;
    List<ActorRef<InjectorMain.Mode>> killerList = new ArrayList<ActorRef<InjectorMain.Mode>>();
    List<Cancellable> taskList = new ArrayList<Cancellable>();

    public enum Mode {
	BURST, STAIRS, UNIFORM, RAND
    }

    public static class Command {}

    public static final class InjectorParams extends Command implements CborSerializable {
	int failsNum;
	List<ActorRef<Server.Command>[]> serverList;
	Duration burstAndStairsInterval;
	
	public InjectorParams(int failsNum, List<ActorRef<Server.Command>[]> serverList, Duration burstAndStairsInterval) {
	    super();
	    this.failsNum = failsNum;
	    this.serverList = serverList;
	    this.burstAndStairsInterval = burstAndStairsInterval;
	}
    }

    public static final class StopInjector extends Command implements CborSerializable {
	ActorRef<HeadSupervisor.Command> replyTo;
	
	@JsonCreator
	public StopInjector(ActorRef<HeadSupervisor.Command> replyTo) {
	    this.replyTo = replyTo;
	}
    }

    public static ServiceKey<InjectorMain.Command> InjectorKey = ServiceKey.create(InjectorMain.Command.class,
	    "Injector");

    public static Behavior<Command> create(Mode mode) {
	return Behaviors.setup(context -> new InjectorMain(context, mode));
    }

    public InjectorMain(ActorContext<Command> context, Mode mode) {
	super(context);
	this.mode = mode;
	context.getSystem().receptionist().tell(Receptionist.register(InjectorKey, context.getSelf()));
	System.out.println("Registering fault injector");
    }

    @Override
    public Receive<Command> createReceive() {
	ReceiveBuilder<Command> builder = newReceiveBuilder();
	builder.onMessage(InjectorParams.class, this::onParams);
	builder.onMessage(StopInjector.class, this::onStop);
	return builder.build();
    }

    private Behavior<Command> onParams(InjectorParams params) throws InterruptedException {
	Thread.sleep(1000);
	System.out.println("Generating faultload: " + params.failsNum + " per second, Mode: " + mode);

	int supervisorNum = params.serverList.size();
	int supervisorChildren = params.serverList.get(0).length;
	int start = 0;

	int killerNum = params.failsNum / maxKills;
	int leftover = params.failsNum % maxKills;
	int killerNumWithLeftover = killerNum + Integer.signum(leftover);
	int supervisorPerKiller;
	if (killerNum!=0) {
	    supervisorPerKiller = Math.max(1, supervisorNum / killerNum +  Integer.signum(supervisorNum % killerNum) );
//	    supervisorPerKiller = Math.max(1, supervisorNum / killerNum +  ((supervisorNum % killerNum != 0) ? 1 : 0));
	} else {
	    // these mostly do not matter
	    killerNum = 1;
	    supervisorPerKiller = supervisorNum;
	}
	    
	switch (mode) {
	case BURST: {
	    
	    for (int i = 0; i < killerNumWithLeftover; i++) {
		int kills = (i < killerNum) ?  maxKills : leftover;
		ActorRef<Mode> killer = this.getContext().spawnAnonymous(
			Killer.create(params.serverList, supervisorChildren, supervisorNum, kills, start, params.burstAndStairsInterval));
		killerList.add(killer);
		start = (start + supervisorPerKiller) % supervisorNum;
	    }

	    int delay = (int) (params.burstAndStairsInterval.toMillis() / killerNum);
	    int accumulatedDelay = 0;
	    for (ActorRef<Mode> killer : killerList) {
		Cancellable task = this.getContext().getSystem().scheduler().scheduleOnce(
			Duration.ofMillis(delay + accumulatedDelay), new Killer.KillerSender(killer, mode),
			this.getContext().getExecutionContext());
		taskList.add(task);
		accumulatedDelay += delay;
	    }
	    break;
	}
	case STAIRS: {
	    Thread.sleep(params.burstAndStairsInterval.multipliedBy(2).toMillis());
	    if (params.failsNum!=0) {
        	    for (int i = 0; i < supervisorNum; i += supervisorPerKiller) {
        		int max = Math.min((i + supervisorPerKiller), supervisorNum);
        		ActorRef<Mode> killer = this.getContext().spawnAnonymous(Killer.create(params.serverList.subList(i, max), supervisorChildren, supervisorNum, maxKills, 0, params.burstAndStairsInterval));
        		killerList.add(killer);
        	    }
        
        	    for (ActorRef<Mode> killer : killerList) {
        		killer.tell(mode);
        	    }
	    }
	    break;
	}
	case UNIFORM: {
	    int delay = Math.max((1000 / maxKills) / killerNum, 1);
	    
	    for (int i = 0; i < killerNumWithLeftover; i++) {
		int kills = (i < killerNum) ?  maxKills : leftover;
		ActorRef<Mode> killer = this.getContext().spawnAnonymous(
			Killer.create(params.serverList, supervisorChildren, supervisorNum, kills, start));
		Cancellable task = this.getContext().getSystem().scheduler().scheduleOnce(Duration.ofMillis(delay * i),
			new Killer.KillerSender(killer, mode), this.getContext().getExecutionContext());
		killerList.add(killer);
		taskList.add(task);
		start = (start + supervisorPerKiller) % supervisorNum;
	    }
	    break;
	}
	case RAND: {
	    int delay = Math.max((1000 / maxKills) / killerNum, 1);
	    int accumulatedDelay = 0;
	    Random generator = new Random();
	    for (int i = 0; i < killerNumWithLeftover; i++) {
		accumulatedDelay +=  generator.nextInt(delay);
		int kills = (i < killerNum) ?  maxKills : leftover;
		ActorRef<Mode> killer = this.getContext().spawnAnonymous(
			Killer.create(params.serverList, supervisorChildren, supervisorNum, kills, start));
		Cancellable task = this.getContext().getSystem().scheduler().scheduleOnce(Duration.ofMillis(accumulatedDelay),
			new Killer.KillerSender(killer, mode), this.getContext().getExecutionContext());
		killerList.add(killer);
		taskList.add(task);
		start = (start + supervisorPerKiller) % supervisorNum;
	    }
	    break;
	}
	}
	System.out.println(killerList.size() + " killers spawned");
	return this;
    }
    
    private Behavior<Command> onStop(StopInjector stop) {
	for (Cancellable i : taskList) {
	    i.cancel();
	}
	for (ActorRef<Mode> i : killerList) {
	    this.getContext().stop(i);
	}
	killerList = new ArrayList<ActorRef<InjectorMain.Mode>>();
	taskList = new ArrayList<Cancellable>();
	stop.replyTo.tell(new InjectorFinished());
	return this;
    }


}
