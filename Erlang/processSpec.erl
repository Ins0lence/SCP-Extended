-module(processSpec).
-export([spawnClient/1, spawnServer/3, spawnCounter/2, spawnLogger/1]).
-export([client/1, server/4, aggregator/4, logger/2]).

spawnClient(ReplyTime) ->
    {ok, spawn_link(?MODULE, client, [ReplyTime])}.

client(ReplyTime) ->
    receive
        {"Hello", Pid, Data} ->
            timer:sleep(ReplyTime),
            Pid ! {"Done", self(), Data},
            client(ReplyTime);
        _ ->
            io:format("Unexpected message received!~n")
    end.

spawnServer(Counter, Data, ReplyTime) ->
    Pid = spawn_link(?MODULE, server, [Counter, spawn_link(?MODULE, client, [ReplyTime]), 0, Data]),
    Pid ! "Start",
    {ok, Pid}.

server(Counter, Clients, MessageCount, Data) ->
    receive
        "Start" ->
            Clients ! {"Hello",self(), Data},
            server(Counter, Clients, MessageCount, Data);
        {"Done", Pid, Data} ->
            Counter ! 1,
            Pid ! {"Hello",self(), Data},
            server(Counter, Clients, MessageCount + 1, Data);
        "Stop" ->
            exit(kill);
        _ ->
            io:format("Unexpected message received!~n")
    end.

spawnCounter(Interval, SummationMode) ->
    Pid = spawn_link(?MODULE, aggregator, [0, erlang:system_time(millisecond), [], SummationMode]),
    register(counter, Pid),
    case SummationMode of
        totalavg-> ok ;
        _-> spawn_link(?MODULE, logger, [Pid, Interval])
    end,
    {ok, Pid}.

aggregator(N, StartTime, Record, SummationMode) ->
    receive
        "Start" ->
            aggregator(0, erlang:system_time(millisecond), [], SummationMode);
        "Update" ->
                ElapsedTime = (erlang:system_time(millisecond) - StartTime)/1000,
                Throughput = N/ElapsedTime,
                aggregator(0, erlang:system_time(millisecond), [Throughput | Record], SummationMode);
        "Stop" ->
            {ok, Fd} = file:open("result.txt", [append]),
            case SummationMode of
                full -> [io:format(Fd, "~.2f~n", [D]) || D <- lists:reverse(Record)];
                avg -> io:format(Fd, "~.3f~n", [lists:sum(Record) / length(Record)]);
                totalavg ->  io:format(Fd, "~.3f~n", [(N*1000)/(erlang:system_time(millisecond)-StartTime)])
            end,
            counter ! stop;
        MessagesCount ->
            aggregator(N+MessagesCount, StartTime, Record, SummationMode)
    end.

spawnLogger(Counter) ->
    {ok, spawn(?MODULE, logger, [Counter])}.

logger(Counter, Interval) ->
    timer:sleep(Interval),
    Counter ! "Update",
    logger(Counter, Interval).