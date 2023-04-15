defmodule Run do

  def verybigrun(size, reply_time) do
    failures = [0, 1, 2.5, 5, 10, 20]
    ratios = [{1024,1}, {512,2}, {256,4}, {128,8}, {64,16}, {32,32}, {16,64}, {8,128}, {4,256}, {2,512}, {1,1024}]
    IO.puts("Beginning very big test")
    for _ <- 1..10 do
      for {p,s} <- ratios do
        for f <- failures do
          start_runs(p, s, f, size, reply_time)
        end
      end
    end
  end

  def wait() do
    case Process.whereis(:head_supervisor) do
      :nil -> :ok
      _ -> wait()
    end
  end


  def start_runs(num_pairs, num_sups, num_fails, data_length, reply_time) do
  wait()
  strat = {:one_for_one, 200000, 1}
  send(:sub, {"Start", num_pairs, num_sups, num_fails, strat, data_length, reply_time, 1000, 1000, :transient, :avg_total})
  Process.sleep(100*1000)
  send(:sub, "Stop")
  Process.sleep(2*1000)
  end
end
