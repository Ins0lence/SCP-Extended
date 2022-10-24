defmodule Run do
  def run(num_pairs, num_sups) do
    IO.puts("Starting sample run")
    strat = {:one_for_one, 200000, 1}
    send(:sub, {"Start", num_pairs, num_sups, 10, strat})
    Process.sleep(70*1000)
    send(:sub, "Stop")
  end
end
