package com.github.ins0lence.scp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.github.ins0lence.scp.Counter.Command;
import com.github.ins0lence.scp.Counter.TakeReading;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.github.ins0lence.scp.Counter.EndCounter;
import com.github.ins0lence.scp.Counter.Mode;
import com.github.ins0lence.scp.HeadSupervisor.FindInjector;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.commons.cli.*;
import akka.actor.Address;
import akka.actor.AddressFromURIString;
import akka.cluster.Member;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.JoinSeedNodes;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.Behaviors;

public class Main {
    enum Mode {
	INJECT, PROCESS
    }
    
    // usage: mvn package
    // java -cp .\target\scp-0.0.1-SNAPSHOT-allinone.jar com.github.ins0lence.scp.Main
    // 

    public static void main(String args[]) {
	
	Options options = new Options();
	Option sm = new Option("sm", true, "supervison mode");
	options.addOption(sm);
	Option cm = new Option("cm", true, "counter mode");
	options.addOption(cm);
	Option bd = new Option("bd", true, "benchmark duration");
	options.addOption(bd);
	Option pd = new Option("pd", true, "peer delay");
	options.addOption(pd);
	Option ms = new Option("ms", true, "message size");
	options.addOption(ms);
	Option mi = new Option("mi", true, "measurement interval");
	options.addOption(mi);
	Option ii = new Option("ii", true, "burst and stairs mode injection interval");
	options.addOption(ii);
	Option sn1 = new Option("sn1", true, "akka seed node specification");
	sn1.setRequired(true);
	options.addOption(sn1);
	Option sn2 = new Option("sn2", true, "akka seed node specification");
	sn2.setRequired(true);
	options.addOption(sn2);
	Option hn = new Option("hn", true, "host's name or address");
	hn.setRequired(true);
	options.addOption(hn);
	Option o = new Option("o", true, "output file");
	options.addOption(o);
	
	CommandLineParser parser = new DefaultParser();
	HelpFormatter formatter = new HelpFormatter();
	CommandLine cmd;

	try {
	    cmd = parser.parse(options, args);
	} catch (ParseException e) {
	    System.out.println(e.getMessage());
	    formatter.printHelp("Options", options);

	    return;
	}
	    
	Mode job;
	try {
	    job = Mode.valueOf(args[0].toUpperCase());
	} catch (IllegalArgumentException e) {
	    System.out.println("Illegal job type; please choose either \"inject\" or \"process\"");
	    return;
	} catch (ArrayIndexOutOfBoundsException e) {
	    System.out.println("Unspecified job type; please choose either \\\"inject\\\" or \\\"process\\\"");
	    return;
	}

	switch (job) {
	case INJECT: {
	    InjectorMain.Mode mode;
	    try {
		mode = InjectorMain.Mode.valueOf(args[1].toUpperCase());
	    } catch (IllegalArgumentException e) {
		System.out.println(
			"Illegal injector type; please choose either \"burst\", \"stairs\", \"uniform\" or \"rand\"");
		return;
	    } catch (ArrayIndexOutOfBoundsException e) {
		mode = InjectorMain.Mode.UNIFORM;
		System.out.println("Using default uniform");
	    }
	    Config config = ConfigFactory
		    .parseString(String.join("\n", "\nakka.remote.artery.canonical.hostname="+ cmd.getOptionValue(hn) ,
			    "akka.remote.artery.canonical.port=8888\n"))
		    .withFallback(ConfigFactory.load());
	    
	    ActorSystem<InjectorMain.Command> system = ActorSystem.create(InjectorMain.create(mode), "Cluster", config);
	    List<Address> seedNodes = new ArrayList<>();
	    seedNodes.add(AddressFromURIString.parse(cmd.getOptionValue(sn1)));
	    seedNodes.add(AddressFromURIString.parse(cmd.getOptionValue(sn2)));
	    Cluster.get(system).manager().tell(new JoinSeedNodes(seedNodes));
	    break;
	}
	case PROCESS: {
	    
	    Config config = ConfigFactory
		    .parseString(String.join("\n", "\nakka.remote.artery.canonical.hostname="+ cmd.getOptionValue(hn),
			    "akka.remote.artery.canonical.port=8889\n"))
		    .withFallback(ConfigFactory.load());

	    SupervisorStrategy strat;
	    String stratString=cmd.getOptionValue(sm, "restart");

	    switch (stratString.toLowerCase()) {
	    case "restart":
		strat = SupervisorStrategy.restart();
		break;
	    case "stop":
		strat = SupervisorStrategy.stop();
		break;
	    case "resume":
		strat = SupervisorStrategy.resume();
		break;
	    default:
		System.out.println(
			"Wrong supervison type; please choose either \"restart\", \"stop\" ,\"resume\" or leave it empty for restart");
		return;
	    }

	    Counter.Mode counterMode;
	    try {
		counterMode = Counter.Mode.valueOf(cmd.getOptionValue(cm, "avg").toUpperCase());
	    } catch (Exception e) {
		counterMode = Counter.Mode.AVG;
	    }

	    System.out.println("Using " + counterMode.toString().toLowerCase() + " summation mode");
	    Duration benchmarkDuration;
	    try {
		benchmarkDuration = Duration.ofSeconds(Integer.parseInt(cmd.getOptionValue(bd, "100")));
	    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
		benchmarkDuration = Duration.ofSeconds(100);
	    }
	    System.out.println("Benchmark duration: " + benchmarkDuration.toString());
	    
	    Duration replyTime;
	    try {
		replyTime = Duration.ofMillis(Long.parseLong(cmd.getOptionValue(pd, "200")));
	    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
		replyTime = Duration.ofMillis(200);
	    }
	    
	    System.out.println("Peer delay: "  + replyTime.toMillis() + " milliseconds");
	    
	    int messageLength;
	    try {
		messageLength = Integer.parseInt(cmd.getOptionValue(ms, "500"));
	    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
		messageLength = 500;
	    }
	    System.out.println("Message size: "  + messageLength + " bytes");

	    Duration measureInterval;
	    try {
		measureInterval = Duration.ofMillis(Long.parseLong(cmd.getOptionValue(mi, "1000")));
	    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
		measureInterval = Duration.ofMillis(1000);
	    }
	    System.out.println("Benchmark measured every "  + measureInterval.toMillis() + " milliseconds");
	    
	    Duration burstAndStairsInterval;
	    try {
		burstAndStairsInterval = Duration.ofMillis(Long.parseLong(cmd.getOptionValue(ii, "5000")));
	    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
		burstAndStairsInterval = Duration.ofMillis(5000);
	    }
	    System.out.println("Burst and stairs mode inject once every "  + burstAndStairsInterval.toMillis() + " milliseconds");

	   
	    ActorSystem<HeadSupervisor.Command> system = ActorSystem
		    .create(HeadSupervisor.create(Integer.parseInt(args[1]), Integer.parseInt(args[2]),
			    Float.parseFloat(args[3]), strat, benchmarkDuration, counterMode, replyTime, messageLength, measureInterval, burstAndStairsInterval, cmd.getOptionValue(o, "result.txt")), "Cluster", config);
	    List<Address> seedNodes = new ArrayList<>();
	    seedNodes.add(AddressFromURIString.parse(cmd.getOptionValue(sn1)));
	    seedNodes.add(AddressFromURIString.parse(cmd.getOptionValue(sn2)));
	    Cluster.get(system).manager().tell(new JoinSeedNodes(seedNodes));
	    break;
	}

	}
    }

}
