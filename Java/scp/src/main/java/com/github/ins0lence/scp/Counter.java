package com.github.ins0lence.scp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.LongStream;

import com.github.ins0lence.scp.Server.Command;
import com.github.ins0lence.scp.HeadSupervisor.CounterFinished;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.actor.typed.receptionist.Receptionist;

public class Counter extends AbstractBehavior<Counter.Command> {
 
    public enum Mode {
	AVG, FULL, TIMESTAMP, TOTALAVG
    }

    int aggregate = 0;
    long t00 = 0;
    List<Long> record = new ArrayList<Long>();
    Mode mode;
    String output;
    
    public static class CounterMessage implements Runnable {

	ActorRef<Counter.Command> target;
	Counter.Command command;

	public CounterMessage(ActorRef<Counter.Command> target, Counter.Command command) {
	    super();
	    this.target = target;
	    this.command = command;
	}

	@Override
	public void run() {
	    target.tell(command);
	}

    }

    public static class Command {}

    public static final class StartCounter extends Command {}

    public static final class Aggregate extends Command {
	public final long i;

	public Aggregate(long i) {
	    super();
	    this.i = i;
	}
    }

    public static final class TakeReading extends Command {}

    public static final class EndCounter extends Command {
	public final ActorRef<HeadSupervisor.Command> replyTo;

	public EndCounter(ActorRef<HeadSupervisor.Command> replyTo) {
	    super();
	    this.replyTo = replyTo;
	}
    }

    public static Behavior<Command> create(Mode mode, String output) {
	return Behaviors.setup(context ->  new Counter(context, mode, output));
    }

    private Counter(ActorContext<Command> context, Mode mode, String output) {
	super(context);
	this.mode= mode;
	this.output=output;
    }

    public Receive<Counter.Command> createReceive() {
	ReceiveBuilder<Counter.Command> builder = newReceiveBuilder();
	builder.onMessage(StartCounter.class, this::onStart);
	builder.onMessage(Aggregate.class, this::onAggregate);
	builder.onMessage(TakeReading.class, this::onReading);
	builder.onMessage(EndCounter.class, this::onEnd);
	return builder.build();
    }

    private Behavior<Counter.Command> onStart(StartCounter start) {
	t00 = System.currentTimeMillis();
	aggregate = 0;
	record = new ArrayList<Long>();
	return this;
    }

    private Behavior<Counter.Command> onAggregate(Aggregate agg) {
	switch (mode) {
	case TIMESTAMP: {
	    record.add(agg.i);
	    break;
	}
	default:{
	    aggregate += 1;
	}
	}
	return this;
    }

    private Behavior<Counter.Command> onReading(TakeReading req) {
	long throughput = Math.round((double) (aggregate* 1000) / (System.currentTimeMillis() - t00));
	record.add(throughput);
	aggregate = 0;
	t00 = System.currentTimeMillis();
	return this;
    }

    private Behavior<Counter.Command> onEnd(EndCounter req) {
	switch (mode) {
	case TOTALAVG: 
	case AVG: {
	    double average;
	    if (mode!=Mode.TOTALAVG) {
		average = record.stream().mapToLong(x -> x).average().getAsDouble();
	    } else {
		average = (double) (aggregate*1000)/(System.currentTimeMillis() - t00);
	    }
	    // System.out.println("Average throughput: " + average);
	    try {
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(new File(output), true)));
		out.println(average);
		out.close();

	    } catch (IOException e) {
		e.printStackTrace();
	    }
	    break;
	}
	case TIMESTAMP: 
	case FULL: {
	    try {
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(new File(output))));
		for (Long x : record) {
		    out.println(x);
		}
		out.close();

	    } catch (IOException e) {
		e.printStackTrace();
	    }
	    break;
	}
	default:
	    System.out.println("Unknown print type requested!");
	}
	req.replyTo.tell(new CounterFinished());
	return this;
    }
}
