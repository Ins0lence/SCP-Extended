defmodule FaultInjector do
  def start(mode, node) do
    Process.register(spawn(FaultInjector, :injector, [mode, [], node]), :injector)
  end
  def stop() do
    Process.exit(Process.whereis(:injector), :finish)
  end
  def injector(mode, proc_list, node) do
    receive do
      {"Start", sup_list, num_fails, injection_interval} ->
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
            spawnkiller(node, node, max_kills, sup_list, div(5000, num_killers), mode, num_killers, sup_per_killer)
          :stairs ->
            Process.sleep(6*injection_interval)
            killer_list = for n <- :lists.seq(1, num_sups, sup_per_killer),  do: spawn(__MODULE__, :stairs_killer, [node, max_kills, :lists.sublist(sup_list, n, sup_per_killer), injection_interval])
            IO.puts("#{num_sups}: #{length(killer_list)}")
            killer_list
          :uniform ->
            spawnkiller(node, node, max_kills, sup_list, div(div(1000, max_kills), num_killers), mode, num_killers, sup_per_killer)
          :rand ->
            spawnkiller(node, node, max_kills, sup_list, :rand.uniform(max(div(div(1000, max_kills), num_killers), 1)), mode, num_killers, sup_per_killer)
          _ ->
            []
        end
        injector(mode, [spawn(__MODULE__, :killer, [node, leftover, sup_list, mode]) | killer_list], node)

      "Stop" ->
        for pid <- proc_list, do: Process.exit(pid, :normal)
        injector(mode, [], node)
      end
  end
  def spawnkiller(_, _, _, _, _, _, 0, _) do
    []
  end
  def spawnkiller(node, node, num_fails, sup_list, interval, mode, num, step) do
    pid = spawn(__MODULE__, :killer, [node, num_fails, sup_list, mode])
    Process.sleep(interval)
    {head, tail} = :lists.split(step, sup_list)
    [pid | spawnkiller(node, node, num_fails, :lists.append(tail, head), interval, mode, num-1, step)]
  end

  def killer(node, num_fails, sup_list, :burst) do
    interval = 5
    start_time = :erlang.system_time(:millisecond)
    new_sup_list = burst_kill(node, sup_list, num_fails*interval)
    sleep = (interval * 1000) - (:erlang.system_time(:millisecond)-start_time)
    if sleep>0 do
      Process.sleep(sleep)
    end
    killer(node, num_fails, new_sup_list, :burst)
  end


  def killer(node, num_fails, [s1 | sup_list], :uniform) do
    start_time = :erlang.system_time(:millisecond)
    kill_process(node, s1)
    sleep = div(1000, num_fails) - (:erlang.system_time(:millisecond) - start_time)
    if sleep>0 do
      Process.sleep(sleep)
    end
    killer(node, num_fails, :lists.append(sup_list, [s1]), :uniform)
  end
  def killer(node, num_fails, sup_list, :rand) do
    type = :rand.uniform(2)
    size = :rand.uniform(10)

    new_sup_list = if type == 1 do
      rand_kill(node, sup_list, size, div(1000, num_fails))
    else
      start_time = :erlang.system_time(:millisecond)
      new_sup_list = burst_kill(node, sup_list, size)
      sleep = div(1000, num_fails)*size - :erlang.system_time(:millisecond) + start_time
      if sleep>0 do
        Process.sleep(sleep)
      end
      new_sup_list
    end
    killer(node, num_fails, new_sup_list, :rand)
  end

  def rand_kill(_, sup_list, 0, _) do
    sup_list
  end
  def rand_kill(node, [s1|sup_list], n, interval) do
    start_time = :erlang.system_time(:millisecond)
    kill_process(node, s1)
    elapsed_time = :erlang.system_time(:millisecond) - start_time
    if elapsed_time<interval do
      Process.sleep(interval - elapsed_time)
    end
    rand_kill(node, :lists.append(sup_list, [s1]), n-1, interval)
  end

  def stairs_killer(node, num_fails, sup_list, injection_interval) do
    Process.sleep(5*injection_interval)
    new_sup_list = burst_kill(node, sup_list, num_fails)
    killer(node, num_fails, new_sup_list, :stairs)
  end

  def burst_kill(_, sup_list, 0) do
    sup_list
  end
  def burst_kill(node, [s1 | sup_list], n) do
    kill_process(node, s1)
    burst_kill(node, :lists.append(sup_list, [s1]), n-1)
  end
  def kill_process(node, sup) do
    p_list = Supervisor.which_children(sup)
    {_, target, _, _} = :lists.nth(:rand.uniform(length(p_list)), p_list)
    status = :rpc.call(node, Process, :alive?, [target])
    if status do
      Process.exit(target, :kill)
    else
      kill_process(node, sup)
    end
  end
end
