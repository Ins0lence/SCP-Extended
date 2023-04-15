-module(faultInjector).
-compile(export_all).

start(Mode, Node) ->
    register(injector, spawn(faultInjector, injector, [Mode, [], Node])).

stop() -> 
    exit(whereis(injector), finish).

injector(Mode, ProcList, Node) ->
    receive
        {"Start", SupList, NFailures, InjectionInterval} ->
            io:format("Generating faultload: ~p per second, Mode: ~p~n", [NFailures, Mode]),

            NSups = length(SupList),                         % Number of Supervisors in the system
            MaxKills = 5,                                    % Max number of times a killer kills per second
            NKillers = NFailures div MaxKills,               % Number of killers required for current fault load
            Leftover = NFailures rem MaxKills,               % Extra killer needed if NFailures is not perfectly divisible by MaxKills
            
            if
                NSups > NKillers ->
                    if
                        NSups rem NKillers /= 0 ->
                            SupPerKiller = (NSups div NKillers) + 1;
                        true ->
                            SupPerKiller = NSups div NKillers
                    end;
                true ->
                    SupPerKiller = 1
            end,

            if 
                Mode == burst ->
                    KillerList = spawnkiller(Node, MaxKills, SupList, trunc(5000/NKillers), Mode, NKillers, SupPerKiller);
                Mode == stairs ->
                    timer:sleep(6*InjectionInterval),
                    KillerList = [spawn(?MODULE, stairsKiller, [Node, MaxKills, lists:sublist(SupList, N, SupPerKiller), InjectionInterval]) || N <- lists:seq(1, NSups, SupPerKiller)],
                    io:format("~p:~p~n", [NSups, length(KillerList)]);
                Mode == uniform ->
                    KillerList = spawnkiller(Node, MaxKills, SupList, trunc((1000/MaxKills)/NKillers), Mode, NKillers, SupPerKiller);
                Mode == rand ->
                    KillerList = spawnkiller(Node, MaxKills, SupList, rand:uniform(max(trunc((1000/MaxKills)/NKillers), 1)), Mode, NKillers, SupPerKiller);
                true ->
                    KillerList = []
            end,
            injector(Mode, [spawn(?MODULE, killer, [Node, Leftover, SupList, Mode]) | KillerList], Node);

        "Stop" ->
            [exit(Pid, normal) || Pid <- ProcList],
            injector(Mode, [], Node)
    end.

stairskiller(Node, NF, SupList, InjectionInterval) ->
    timer:sleep(5*InjectionInterval),
    NewSupList = burstKill(Node, SupList, NF),
    killer(Node, NF, NewSupList, stairs).

spawnkiller(_, _, _, _, _, 0, _) -> [];

spawnkiller(Node, NF, SupList, Interval, Mode, N, L) ->
    Pid = spawn(?MODULE, killer, [Node, NF, SupList, Mode]),
    timer:sleep(Interval),
    {HeadList, TailList} = lists:split(L, SupList),
    [Pid | spawnkiller(Node, NF, lists:append(TailList, HeadList), Interval, Mode, N-1, L)].

% Burst fault load specifications
% Single or Multiple Supervisor specification
killer(Node, NF, SupList, burst) ->
    Interval = 5,

    StartTime = erlang:system_time(millisecond),
    NewSupList = burstKill(Node, SupList, NF*Interval),
    Sleep = (Interval * 1000) - (erlang:system_time(millisecond) - StartTime),

    if
        Sleep > 0 ->
            timer:sleep(Sleep);
        true ->
            ok
    end,

    killer(Node, NF, NewSupList, burst);


% Uniform fault load specification    
killer(Node, NF, [S1 | SupList], uniform) ->
    StartTime = erlang:system_time(millisecond),
    killProcess(Node, S1),
    Sleep = (1000 / NF) - (erlang:system_time(millisecond) - StartTime),
    
    if
        Sleep > 0 ->
            timer:sleep(trunc(Sleep));
        true ->
            ok
    end,
    
    killer(Node, NF, lists:append(SupList, [S1]), uniform);

% Random fault load specification
killer(Node, NF, SupList, rand) ->
    Type = rand:uniform(2),
    Size = rand:uniform(10),

    if
        Type == 1 ->
            NewSupList = randKill(Node, SupList, Size, trunc(1000/NF));
        true ->
            StartTime = erlang:system_time(millisecond),
            NewSupList = burstKill(Node, SupList, Size),
            Sleep = ((1000 / NF) * Size) - (erlang:system_time(millisecond) - StartTime),
            if
                Sleep > 0 ->
                    timer:sleep(trunc(Sleep));
                true ->
                    ok
            end
    end,
    killer(Node, NF, NewSupList, rand).

randKill(_, SupList, 0, _) -> SupList;

randKill(Node, [S1 | SupList], N, Interval) ->
    StartTime = erlang:system_time(millisecond),
    killProcess(Node, S1),
    ElapsedTime = erlang:system_time(millisecond) - StartTime,

    if
        ElapsedTime < Interval ->
            timer:sleep(Interval - ElapsedTime);
        true ->
            ok
    end,
    randKill(Node, lists:append(SupList, [S1]), N-1, Interval).

burstKill(_, SupList, 0) -> SupList;

burstKill(Node, [S1 | SupList], N) ->
    killProcess(Node, S1),
    burstKill(Node, lists:append(SupList, [S1]), N-1).

% Process killer specifications
% Single Supervisor
killProcess(Node, Sup) ->
    PList = supervisor:which_children(Sup),
    {_, Target, _, _} = lists:nth(rand:uniform(length(PList)), PList),
    Status = rpc:call(Node, erlang, is_process_alive, [Target]),
    
    if
        Status ->
            exit(Target, kill);
        true ->
            killProcess(Node, Sup)
    end.