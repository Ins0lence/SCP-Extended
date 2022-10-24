defmodule Benchmark do
  def start do
    Process.register(spawn(Benchmark, :benchmark, [:one@one, 0]), :sub)
  end
  def benchmark(fnode, system_pid) do
    receive do
      {"Start", npairs, nsups, nfails, sup_strat} ->
        pid = startSystem(npairs, nsups, nfails, sup_strat, fnode)
        benchmark(fnode, pid)
      "Stop" ->
        send(:counter, "stop")
        Process.sleep(1000)
        send({:injector, fnode}, "Stop")
        Process.exit(system_pid, :normal)
        IO.puts("Benchmark finished!")
        benchmark(fnode, 0)
      _ ->
        IO.puts("Unknown command received!")
    end
  end
  def startSystem(npairs, nsups, nfails, sup_strat, fnode) do
    totalPairs = npairs*nsups
    start_time = :erlang.system_time()
    IO.puts("placehold2!")
  end
  def klagg(0) do
    IO.puts("asdsad")
  end
  def klagg(n) do
    IO.puts(n)
    klagg(n-1)
  end
end


#Benchmark.start()

#send(:sub, {"Start", 2, 2, 2, 2})
