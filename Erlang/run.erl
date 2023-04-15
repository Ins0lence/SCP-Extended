-module(run).
-export([bigrun/0, run/2, startRun/4, verybigrun/4]).



verybigrun(Size, ReplyTime, MeasureInterval, InjectionInterval) ->
    io:format("r_~B_~B.txt~n", [Size, ReplyTime]),
    Failures = [0, 1, 2.5, 5, 10, 20],    
    Ratios = [{1024,1}, {512,2}, {256,4}, {128,8}, {64,16}, {32,32}, {16,64}, {8,128}, {4,256}, {2,512}, {1,1024}],
    io:format("Beginning very big test~n"),
    [[[run(P, S, F, 100, Size, ReplyTime, MeasureInterval, InjectionInterval) ||  F <- Failures] || {P,S} <- Ratios] || _ <- lists:seq(1, 10)].

bigrun() ->
    Ratios = [{1024,1}, {512,2}, {256,4}, {128,8}, {64,16}, {32,32}, {16,64}, {8,128}, {4,256}, {2,512}, {1,1024}],
    io:format("Beginning big test~n"),
    [run(P, S) || {P,S} <- Ratios].


run(NPairs, NSups) ->
    Values = [0, 1, 2.5, 5, 10, 20],
    Strat = {one_for_one, 200000, 1},
    {ok, Fd} = file:open("result.txt", [append]),
    io:format(Fd, "Test started! Running ~p:~p ratio~n", [NPairs, NSups]),
    [startRun(NPairs, NSups, V, Strat) || V <- Values],
    io:format(Fd, "Test complete!~n", []).

startRun(P, S, F, Strat) ->
    sub ! {"Start", P, S, F, Strat},
    timer:sleep(70*1000),
    sub ! "Stop",
    timer:sleep(15*1000).


singleRun(P, S, F, Time) ->
    {ok, Fd} = file:open("result.txt", [append]),
    io:format(Fd, "Test Started~n", []),
    Strat = {one_for_one, 200000, 1},
    sub ! {"Start", P, S, F, Strat},
    timer:sleep(Time*1000),
    sub ! "Stop",
    timer:sleep(15*1000).


singleRun(P, S, F, Time, DataLength) ->
    {ok, Fd} = file:open("result.txt", [append]),
    io:format(Fd, "Test Started~n", []),
    Strat = {one_for_one, 200000, 1},
    sub ! {"Start", P, S, F, Strat, DataLength},
    timer:sleep(Time*1000),
    sub ! "Stop",
    timer:sleep(15*1000).

singleRun(P, S, F, Time, DataLength, ReplyTime, MeasureInterval, InjectionInterval) ->
    {ok, Fd} = file:open("result.txt", [append]),
    io:format(Fd, "Test Started~n", []),
    Strat = {one_for_one, 200000, 1},
    sub ! {"Start", P, S, F, Strat, DataLength, ReplyTime, MeasureInterval, InjectionInterval},
    timer:sleep(Time*1000),
    sub ! "Stop",
    timer:sleep(15*1000).

wait() ->
    case whereis(headSupervisor) of
        undefined -> ok;
        _ -> wait()
    end.

run(P, S, F, Time, DataLength, ReplyTime, MeasureInterval, InjectionInterval) ->
    wait(),
    Strat = {one_for_one, 200000, 1},
    sub ! {"Start", P, S, F, Strat, DataLength, ReplyTime, MeasureInterval, InjectionInterval, transient},
    timer:sleep(Time*1000),
    sub ! "Stop",
    timer:sleep(1000).