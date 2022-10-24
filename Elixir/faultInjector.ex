defmodule FaultInjector do
  def start(mode) do
    Process.register(spawn(FaultInjector, :injector, [mode, []]), :injector)
  end
  def stop() do
    Process.exit(Process.whereis(:injector), :finish)
  end
  def injector(mode, proc_list) do
    receive do
      {"Start", sup_list, num_fails} ->
        IO.puts("Generating faultload: #{num_fails} per second, Mode: #{mode}")

        num_sups = length(sup_list)
        max_kills = 5
        num_killers = div(num_fails, max_kills)
        leftover = rem(num_fails, max_kills)
        sup_per_killer = if num_sups>num_killers do
          remainder = if rem(num_sups, num_killers) != 0 do
            1
          else
            0
          end
          div(num_sups, num_killers)+remainder
        else
          1
        end
        killer_list = case mode do
          :burst ->
            spawnKiller(max_kills, sup_list, div(5000, num_killers), mode, num_killers, sup_per_killer)
          :stairs ->
            Process.sleep(6000)
            killer_list = for n <- :lists.seq(1, num_sups, sup_per_killer),  do: spawn(__MODULE__, :killer, [max_kills, :lists.sublist(sup_list, n, sup_per_killer)])
            IO.puts("#{num_sups}: #{length(killer_list)}")
            killer_list
          :uniform ->
            spawnKiller(max_kills, sup_list, div(div(1000, max_kills), num_killers), mode, num_killers, sup_per_killer)
          :rand ->
            spawnKiller(max_kills, sup_list, :rand.uniform(div(div(1000, max_kills), num_killers)), mode, num_killers, sup_per_killer)
          _ ->
            []
        end
        injector(mode, [spawn(__MODULE__, :killer, [leftover, sup_list, mode]) | killer_list])

      "Stop" ->
        for pid <- proc_list, do: Process.exit(pid, :normal)
        injector(mode, [])
      end
  end
  def spawnKiller(_, _, _, _, 0, _) do
    []
  end
  def spawnKiller(num_fails, sup_list, interval, mode, num, step) do
    pid = spawn(__MODULE__, :killer, [num_fails, sup_list, mode])
    Process.sleep(interval)
    {head, tail} = :lists.split(step, sup_list)
    [pid | spawnKiller(num_fails, :lists.append(tail, head), interval, mode, num-1, step)]
  end
  def killer(num_fails, sup_list, :burst) do
    interval = 5
    start_time = :erlang.system_time(:millisecond)
    new_sup_list = burst_kill(sup_list, num_fails*interval)
    sleep = (interval * 1000) - :erlang.system_time(:millisecond)-start_time
    if sleep>0 do
      Process.sleep(sleep)
    end
    killer(num_fails, new_sup_list, :burst)
  end
  def killer(num_fails, sup_list, :stairs) do
    Process.sleep(5000)
    new_sup_list = burst_kill(sup_list, num_fails)
    killer(num_fails, new_sup_list, :stairs)
  end
  def killer(num_fails, [s1 | sup_list], :uniform) do
    start_time = :erlang.system_time(:millisecond)
    kill_process(s1)
    sleep = div(1000, num_fails) - :erlang.system_time(:millisecond) + start_time
    if sleep>0 do
      Process.sleep(sleep)
    end
    killer(num_fails, :lists.append(sup_list, [s1]), :uniform)
  end
  def killer(num_fails, sup_list, :rand) do
    type = :rand.uniform(2)
    size = :rand.uniform(8)

    new_sup_list = if type == 1 do
      rand_kill(sup_list, size, div(1000, num_fails))
    else
      start_time = :erlang.system_time(:millisecond)
      new_sup_list = burst_kill(sup_list, size)
      sleep = div(1000, num_fails)*size - :erlang.system_time(:millisecond) + start_time
      if sleep>0 do
        Process.sleep(sleep)
      end
      new_sup_list
    end
    killer(num_fails, new_sup_list, :rand)
  end

  def rand_kill(sup_list, 0, _) do
    sup_list
  end
  def rand_kill([s1|sup_list], n, interval) do
    start_time = :erlang.system_time(:millisecond)
    kill_process(s1)
    elapsed_time = :erlang.system_time(:millisecond) - start_time
    if elapsed_time<interval do
      Process.sleep(interval - elapsed_time)
    end
    rand_kill(:lists.append(sup_list, [s1]), n-1, interval)
  end
  def burst_kill(sup_list, 0) do
    sup_list
  end
  def burst_kill([s1 | sup_list], n) do
    kill_process(s1)
    burst_kill(:lists.append(sup_list, [s1]), n-1)
  end
  def kill_process(sup) do
    p_list = Supervisor.which_children(sup)
    {_, target, _, _} = :lists.nth(:rand.uniform(length(p_list)), p_list)
    status = :rpc.call(:two@one, :erlang, :is_process_alive, [target])
    if status do
      Process.exit(target, :kill)
    else
      kill_process(sup)
    end
  end
end
