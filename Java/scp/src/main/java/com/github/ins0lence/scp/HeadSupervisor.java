package com.github.ins0lence.scp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.github.ins0lence.scp.Counter.Mode;
import com.github.ins0lence.scp.Counter.CounterMessage;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.actor.typed.receptionist.Receptionist;

public class HeadSupervisor extends AbstractBehavior<HeadSupervisor.Command> {

    Duration burstAndStairsInterval;
    Duration measureInterval;
    ActorRef<Counter.Command> counter;
    List<ActorRef<Void>> supervisors = new ArrayList<ActorRef<Void>>();
    ActorRef<InjectorMain.Command> injector;
    List<ActorRef<Server.Command>[]> servers = new ArrayList<ActorRef<Server.Command>[]>();
    int num;
    int size;
    float failNum;
    Duration length;
    Counter.Mode mode;

    public static Behavior<Command> create(int num, int size, float failNum, SupervisorStrategy strat, Duration length,
	    Counter.Mode mode, Duration replyTime, int messageLength, Duration measureInterval, Duration burstAndStairsInterval, String output) {
	return Behaviors.setup(context -> new HeadSupervisor(context, num, size, failNum, strat, length, mode,
		replyTime, messageLength, measureInterval, burstAndStairsInterval, output));
    }

    public HeadSupervisor(ActorContext<Command> context, int num, int size, float failNum, SupervisorStrategy strat,
	    Duration length, Counter.Mode mode, Duration replyTime, int messageLength, Duration measureInterval,
	    Duration burstAndStairsInterval, String output) {
	super(context);
	counter = context.spawn(Counter.create(mode, output), "counter");
	for (int i = 0; i < num; i++) {
	    String name = "supervisor" + Integer.toString(i);
	    // this is not really supervision
	    supervisors.add(context.spawn(
		    SubSupervisor.create(size, context.getSelf(), counter, strat, replyTime, messageLength), name));
	}
	this.num = num;
	this.size = size;
	this.failNum = failNum;
	this.length = length;
	this.mode = mode;
	this.measureInterval = measureInterval;
	this.burstAndStairsInterval = burstAndStairsInterval;
    }



    public static class Command {
    }

    public static final class CounterFinished extends Command {
	public CounterFinished() {
	    super();
	}
    }

    public static final class StartSystem extends Command {
	public StartSystem() {
	    super();
	}
    }

    public static final class FindInjector extends Command {

	public FindInjector() {
	    super();
	}
    }

    public static final class InjectorFinished extends Command implements CborSerializable {

	public InjectorFinished() {
	    super();
	}
    }

    public static final class ChildList extends Command {
	private ActorRef<Server.Command>[] children;

	public ChildList(ActorRef<Server.Command>[] children) {
	    super();
	    this.children = children;
	}

    }

    @Override
    public Receive<Command> createReceive() {
	ReceiveBuilder<Command> builder = newReceiveBuilder();
	builder.onMessage(ChildList.class, this::onList);
	builder.onMessage(StartSystem.class, this::onStart);
	builder.onMessage(FindInjector.class, this::onFindInjector);
	builder.onMessage(CounterFinished.class, this::onCounterFinished);
	builder.onMessage(InjectorFinished.class, this::onInjectorFinished);
	return builder.build();
    }

    private Behavior<Command> onList(ChildList list) {
	servers.add(list.children);
	if (servers.size() == num) {
	    this.getContext().getSelf().tell(new FindInjector());
	}
	return this;
    }

    private Behavior<Command> onStart(StartSystem start) {
	System.out.println("Starting system with " + num * size + " pairs across " + num + " supervisors");

	
	this.getContext().getSystem().scheduler().scheduleOnce(measureInterval.multipliedBy(5), new CounterMessage(counter, new Counter.StartCounter()),
		this.getContext().getSystem().executionContext());
	
	if (mode != Mode.TIMESTAMP && mode != Mode.TOTALAVG) {
	    this.getContext().getSystem().scheduler().scheduleAtFixedRate(measureInterval.multipliedBy(6), measureInterval,
			new CounterMessage(counter, new Counter.TakeReading()), this.getContext().getSystem().executionContext());
	}

	this.getContext().getSystem().scheduler().scheduleOnce(measureInterval.multipliedBy(5).plus(length),
		new CounterMessage(counter, new Counter.EndCounter(this.getContext().getSelf())),
		this.getContext().getSystem().executionContext());

	return this;
    }

    private Behavior<Command> onCounterFinished(CounterFinished finished) {
	injector.tell(new InjectorMain.StopInjector(this.getContext().getSelf()));
	return this;
    }

    private Behavior<Command> onInjectorFinished(InjectorFinished s) {
	return Behaviors.stopped();
    }

    private Behavior<Command> onFindInjector(FindInjector find) {
	try {
	    Thread.sleep(5000);
	} catch (InterruptedException e) {
	    this.getContext().getSelf().tell(new FindInjector());
	    return this;

	}

	this.getContext().ask(Receptionist.Listing.class, this.getContext().getSystem().receptionist(),
		Duration.ofSeconds(5), resRef -> Receptionist.find(InjectorMain.InjectorKey, resRef),
		(response, throwable) -> {
		    if (response != null) {
			Set<ActorRef<InjectorMain.Command>> instances = response
				.getServiceInstances(InjectorMain.InjectorKey);
			// size should be one
			if (instances.size() == 0) {
			    return new FindInjector();
			}
			for (ActorRef<InjectorMain.Command> injector : instances) {
			    this.injector = injector;
			    injector.tell(new InjectorMain.InjectorParams((int) (num * size * failNum / 100), servers, burstAndStairsInterval));
			    return new StartSystem();
			}
			return new FindInjector();
		    } else {
			// better response
			System.out.println("Receptionist unreachable");
			return new FindInjector();
		    }
		});

	return this;
    }

}
