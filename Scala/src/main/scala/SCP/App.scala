package $package$

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory
import akka.actor.typed.SupervisorStrategy
import java.io._

object App {

    def main(args: Array[String]): Unit = {
        var job = args(0)

        job match {
            case "inject" =>
                var mode = args(1)
                val config = ConfigFactory.parseString(s"""
                akka.remote.artery.canonical.hostname=130.209.255.16
                akka.remote.artery.canonical.port=8888
                """).withFallback(ConfigFactory.load())

                val system: ActorSystem[InjectorMain.InjectorParams] = ActorSystem(InjectorMain(mode), "Cluster", config)
            case "process" =>
                val config = ConfigFactory.parseString(s"""
                akka.remote.artery.canonical.hostname=130.209.255.17
                akka.remote.artery.canonical.port=8889
                """).withFallback(ConfigFactory.load())

                val system: ActorSystem[SCPMain.SystemParams] = ActorSystem(SCPMain(), "Cluster", config)
                var benchmarkDuration = 0 
                var measureInterval = 0
                var replyTime = 0
                var messageLength = 0
                var supervisorStrategy : SupervisorStrategy = SupervisorStrategy.restart

                try{
                    benchmarkDuration = args(5).toInt
                } catch {
                    case e: Exception=> benchmarkDuration = 115
                }

                try{
                    replyTime = args(6).toInt
                } catch {
                    case e: Exception=> replyTime = 200
                }

                try{
                    measureInterval = args(7).toInt
                } catch {
                    case e: Exception=> measureInterval = 1000
                }
                
                try{
                    messageLength = args(8).toInt
                } catch {
                    case e: Exception=> messageLength = 500
                }

                try{
                    args(9) match {
                        case "restart" => supervisorStrategy = SupervisorStrategy.restart
                        case "stop" => supervisorStrategy = SupervisorStrategy.stop
                        case "resume" => supervisorStrategy = SupervisorStrategy.resume
                        case _ => supervisorStrategy = SupervisorStrategy.restart
                    }
                } catch {
                    case e: Exception=> supervisorStrategy = SupervisorStrategy.restart
                }
                
                println("Client reply time: " + replyTime)
                println("Bencmark duration: " + benchmarkDuration)
                println("Measure interval: " + measureInterval)
                system ! SCPMain.SystemParams(args(1).toInt, args(2).toInt, args(3).toFloat, replyTime, benchmarkDuration, measureInterval, args(4), messageLength, supervisorStrategy)

                val bw = new BufferedWriter(new FileWriter(new File("log3.txt"), true))

                if (args(3) == "0" ){
                    bw.write("Starting run: NSups - " + args(1) + ", SupSize - " + args(2) + "\n")
                    bw.close
                }
                Thread.sleep(benchmarkDuration*1000+20000)
                system.terminate()
            case _ =>
                println("Invalid job type!")
        }
    }
}