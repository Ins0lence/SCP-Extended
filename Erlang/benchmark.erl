-module(benchmark).
-compile(export_all).

start(Node) ->
    register(sub, spawn(?MODULE, wrapper, [Node, 0])),
    ok.

wrapper(FNode, SystemPid) ->
    try benchmark(FNode, SystemPid) of
        _ -> ok
    catch
         _:Error:Stacktrace -> 
            {ok, Fd} = file:open("error.txt", [append]),
            io:format(Fd, "~p~n~p~n", [Stacktrace, Error])
    end.
benchmark(FNode, SystemPid) ->
    receive
        {"Start", NPairs, NSups, NFails, SupStrat, DataLength, ReplyTime, MeasureInterval, InjectionInterval, SupervisionMode, SummationMode} ->
            Pid = startSystem(NPairs, NSups, NFails, SupStrat, FNode, DataLength, ReplyTime, MeasureInterval, InjectionInterval, SupervisionMode, SummationMode),
            benchmark(FNode, Pid);
        "Stop" ->
            counter ! "Stop",
            timer:sleep(1000),
            {injector, FNode} ! "Stop",
            exit(SystemPid, normal),
            io:format("Benchmark finished!~n"),
            benchmark(FNode, 0);
        _ ->
            io:format("Unknown command received!~n")
    end.

startSystem(NumPairs, NumSupervisors, NFailures, SupStrat, FNode, DataLength, ReplyTime, MeasureInterval, InjectionInterval, SupervisionMode, SummationMode) ->
    TotalPairs = NumPairs*NumSupervisors,
    StartTime = erlang:system_time(millisecond),
    {ok, HeadSupName} = processSup:start_link({headSupervisor, {SupStrat, [genChildSpec(counter, spawnCounter, [MeasureInterval, SummationMode], transient)]}}),
    [{_, Counter, _, _}] = supervisor:which_children(HeadSupName),
    Children = [genChildSpec(genName("server", N), spawnServer, [Counter, bytes_generate(DataLength), ReplyTime], SupervisionMode) || N <- lists:seq(1, NumPairs)],
    SupervisorList = [spawnSupervisor(HeadSupName, SupStrat, Children, N) || N <- lists:seq(1, NumSupervisors)],
    ElapsedTime = erlang:system_time(millisecond) - StartTime,
    io:format("~p Process Pairs spawned and started in ~p seconds.~n", [TotalPairs, ElapsedTime/1000]),
    io:format("Starting benchmark!~n"),
    if
        NFailures =/= 0 ->
            {injector, FNode} ! {"Start", SupervisorList, trunc(TotalPairs*5*(NFailures/100)), InjectionInterval},
            timer:sleep(5000);
        true -> ok   
    end,
    Counter ! "Start",
    HeadSupName.  

spawnSupervisor(HeadSup, SupStrategy, ChildList, SupervisorNum) ->
    NewName = genName("subSupervisor", SupervisorNum),
    {ok, NewSup} = supervisor:start_child(HeadSup, genSupervisorSpec(NewName, [{NewName, {SupStrategy, ChildList}}])),
    NewSup.

genSupervisorSpec(Name, Args) ->
    #{id => Name, 
    start => {processSup, start_link, Args},
    restart => transient, 
    type => supervisor, 
    modules => [processSup]}.

genChildSpec(Name, Spawner, Args, SupervisionMode) ->
    #{id => Name,
    start => {processSpec, Spawner, Args},
    restart => SupervisionMode,
    type => worker,
    modules => [processSpec]}.

bytes_generate(Size) ->
    bytes_generate(Size, []).

bytes_generate(0, Bytes) ->
    list_to_binary(Bytes);

bytes_generate(Size, Bytes) ->
    bytes_generate(Size - 1, [1 | Bytes]).

genName(Type, N) ->
    list_to_atom(string:concat(Type, integer_to_list(N))).