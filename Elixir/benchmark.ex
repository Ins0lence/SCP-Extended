defmodule Benchmark do
  def start do
    Process.register(spawn(Benchmark, :benchmark, [:on@one, 0]), :sub)
    :ok
  end
  def benchmark(fnode, system_pid) do
    receive do
      {"Start", num_pairs, num_sups, num_fails, sup_strat} ->
        pid = start_system(num_pairs, num_sups, num_fails, sup_strat, fnode)
        benchmark(fnode, pid)
      "Stop" ->
        send(:counter, "Stop")
        Process.sleep(1000)
        send({:injector, fnode}, "Stop")
        Process.exit(system_pid, :normal)
        IO.puts("Benchmark finished!")
        benchmark(fnode, 0)
      _ ->
        IO.puts("Unknown command received!")
    end
  end
  #Benchmark.start_system(100, 100, 10, {:one_for_one, 200000, 1}, :asd@asd)
  #maybe use builtin
  def start_system(num_pairs, num_sups, num_fails, sup_strat, fnode) do
    total_pairs = num_pairs*num_sups
    start_time = :erlang.system_time(:millisecond)
    #IO.puts({:head_supervisor, {sup_strat, [gen_child_spec(:counter, :spawn_counter, [])]}})
    {:ok, head_sup_name} = ProcessSup.start_link({:head_supervisor, {sup_strat, [gen_child_spec(:counter, :spawn_counter, [])]}})
    [{_, counter, _, _}] = Supervisor.which_children(head_sup_name)
    children = for n <- 1..num_pairs, do: gen_child_spec(gen_name("server", n), :spawn_server, [counter, bytes_generate(500)])
    supervisor_list = for n <- 1..num_sups, do: spawn_supervisor(head_sup_name, sup_strat, children, n)
    elapsed_time = :erlang.system_time(:millisecond) - start_time
    IO.puts("#{total_pairs} process pairs spawned and started in #{elapsed_time/1000} seconds.")
    IO.puts("Starting benchmark!")
    if num_fails != 0 do
      send({:injector, fnode}, {"Start", supervisor_list, trunc(total_pairs*5*num_fails/100)})
      Process.sleep(5000)
    end
    send(counter, "Start")
    head_sup_name
  end
  def spawn_supervisor(head_sup, sup_strategy, child_list, supervisor_num) do
    new_name = gen_name("sub_supervisor", supervisor_num)
    {:ok, new_sup} = :supervisor.start_child(head_sup, gen_supervisor_spec(new_name, [{new_name, {sup_strategy, child_list}}]))
    new_sup
  end
  def gen_supervisor_spec(name, args) do
    %{:id => name,
    :start => {ProcessSup, :start_link, args},
    :restart => :transient,
    :type => :supervisor,
    :modules => [ProcessSup]}
  end
  def gen_child_spec(name, spawner, args) do
    %{:id => name,
    :start => {ProcessSpec, spawner, args},
    :restart => :transient,
    :type => :worker,
    :modules => [ProcessSpec]}
  end
  def gen_name(type, n) do
    String.to_atom(type <> Integer.to_string(n))
  end
  def bytes_generate(size) do
    for _n <-1..size, do: 1
  end

end
